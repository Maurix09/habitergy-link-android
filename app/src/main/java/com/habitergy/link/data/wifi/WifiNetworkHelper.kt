package com.habitergy.link.data.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.habitergy.link.domain.model.WifiNetwork
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Lectura de la red WiFi actual y escaneo de SSIDs cercanos para el paso 3.
 *
 * Importante: varias APIs de WifiManager / WifiInfo pueden lanzar
 * [SecurityException] si faltan permisos o la ubicación del sistema está
 * apagada. Nunca deben tumbar la Activity — siempre devolvemos null / vacío.
 */
class WifiNetworkHelper(
    private val context: Context,
) {
    private val appContext = context.applicationContext
    private val wifiManager: WifiManager? =
        appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun isWifiEnabled(): Boolean = try {
        wifiManager?.isWifiEnabled == true
    } catch (_: SecurityException) {
        false
    } catch (_: Exception) {
        false
    }

    /**
     * SSID de la red a la que está conectado el teléfono, o `null` si no hay
     * WiFi activo / faltan permisos / el sistema oculta el nombre / falla la API.
     */
    @SuppressLint("MissingPermission")
    fun getCurrentSsid(): String? {
        return try {
            if (!WifiPermissions.allGranted(appContext)) return null

            // En API ≤32 el stack suele exigir ubicación del sistema ON.
            if (WifiPermissions.isLocationRequiredForScan() &&
                !WifiPermissions.isLocationEnabled(appContext)
            ) {
                return null
            }

            // Preferido en Android 10+: WifiInfo vía NetworkCapabilities.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val connectivity =
                    appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val network = connectivity?.activeNetwork
                val caps = network?.let { connectivity.getNetworkCapabilities(it) }
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val fromTransport = (caps.transportInfo as? WifiInfo)
                        ?.ssid
                        ?.let { sanitizeSsid(it) }
                    if (!fromTransport.isNullOrBlank()) return fromTransport
                }
            }

            @Suppress("DEPRECATION")
            val fromWifiInfo = wifiManager?.connectionInfo
                ?.ssid
                ?.let { sanitizeSsid(it) }
            if (!fromWifiInfo.isNullOrBlank()) return fromWifiInfo

            null
        } catch (error: SecurityException) {
            Log.w(TAG, "getCurrentSsid blocked by SecurityException", error)
            null
        } catch (error: Exception) {
            Log.w(TAG, "getCurrentSsid failed", error)
            null
        }
    }

    /**
     * Dispara un escaneo WiFi y espera el broadcast de resultados.
     * Puede devolver lista vacía si el sistema limíta el rate de scan.
     */
    @SuppressLint("MissingPermission")
    suspend fun scanNearbyNetworks(): Result<List<WifiNetwork>> {
        val manager = wifiManager
            ?: return Result.failure(IllegalStateException("WiFi no disponible en este dispositivo."))

        try {
            if (!manager.isWifiEnabled) {
                return Result.failure(IllegalStateException("WIFI_OFF"))
            }
        } catch (error: SecurityException) {
            return Result.failure(error)
        }

        return suspendCancellableCoroutine { continuation ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                    try {
                        appContext.unregisterReceiver(this)
                    } catch (_: IllegalArgumentException) {
                        // ya desregistrado
                    }
                    if (!continuation.isActive) return

                    val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, true)
                    } else {
                        true
                    }

                    val networks = safeScanResults(manager)

                    if (!success && networks.isEmpty()) {
                        continuation.resume(
                            Result.failure(
                                IllegalStateException(
                                    "No pudimos actualizar la lista de redes. Intentá de nuevo.",
                                ),
                            ),
                        )
                    } else {
                        continuation.resume(Result.success(networks))
                    }
                }
            }

            try {
                val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    appContext.registerReceiver(receiver, filter)
                }
            } catch (error: Exception) {
                if (continuation.isActive) {
                    continuation.resume(Result.failure(error))
                }
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation {
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (_: IllegalArgumentException) {
                    // ignore
                }
            }

            try {
                @Suppress("DEPRECATION")
                val started = manager.startScan()
                if (!started) {
                    try {
                        appContext.unregisterReceiver(receiver)
                    } catch (_: IllegalArgumentException) {
                        // ignore
                    }
                    if (!continuation.isActive) return@suspendCancellableCoroutine
                    continuation.resume(Result.success(safeScanResults(manager)))
                }
            } catch (error: SecurityException) {
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (_: IllegalArgumentException) {
                    // ignore
                }
                if (continuation.isActive) {
                    continuation.resume(Result.failure(error))
                }
            } catch (error: Exception) {
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (_: IllegalArgumentException) {
                    // ignore
                }
                if (continuation.isActive) {
                    continuation.resume(Result.failure(error))
                }
            }
        }
    }

    private fun safeScanResults(manager: WifiManager): List<WifiNetwork> = try {
        manager.scanResults
            .orEmpty()
            .mapNotNull { it.toWifiNetwork() }
            .distinctBy { it.ssid.lowercase() }
            .sortedByDescending { it.rssi }
    } catch (error: SecurityException) {
        Log.w(TAG, "scanResults blocked by SecurityException", error)
        emptyList()
    } catch (error: Exception) {
        Log.w(TAG, "scanResults failed", error)
        emptyList()
    }

    private fun ScanResult.toWifiNetwork(): WifiNetwork? {
        val name = sanitizeSsid(SSID) ?: return null
        if (name.isBlank()) return null
        return WifiNetwork(
            ssid = name,
            rssi = level,
            isSecured = capabilitiesIndicateSecured(capabilities),
        )
    }

    private fun capabilitiesIndicateSecured(capabilities: String?): Boolean {
        val caps = capabilities.orEmpty()
        if (caps.isBlank()) return false
        return caps.contains("WEP") ||
            caps.contains("WPA") ||
            caps.contains("SAE") ||
            caps.contains("EAP") ||
            caps.contains("PSK") ||
            caps.contains("OWE")
    }

    private fun sanitizeSsid(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var value = raw.trim()
        if (value.equals(UNKNOWN_SSID, ignoreCase = true)) return null
        if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
            value = value.substring(1, value.length - 1)
        }
        return value.takeIf { it.isNotBlank() }
    }

    private companion object {
        private const val TAG = "WifiNetworkHelper"
        private const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
