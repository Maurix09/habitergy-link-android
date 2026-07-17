package com.habitergy.link.data.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import com.habitergy.link.domain.model.WifiNetwork
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Lectura de la red WiFi actual y escaneo de SSIDs cercanos para el paso 3.
 */
class WifiNetworkHelper(
    private val context: Context,
) {
    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun isWifiEnabled(): Boolean = wifiManager?.isWifiEnabled == true

    /**
     * SSID de la red a la que está conectado el teléfono, o `null` si no hay
     * WiFi activo / faltan permisos / el sistema oculta el nombre.
     */
    fun getCurrentSsid(): String? {
        if (!WifiPermissions.allGranted(context)) return null

        val fromWifiInfo = wifiManager?.connectionInfo
            ?.ssid
            ?.let { sanitizeSsid(it) }
        if (!fromWifiInfo.isNullOrBlank()) return fromWifiInfo

        // Fallback Android 10+: NetworkCapabilities / TransportInfo.
        val connectivity =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
        val network = connectivity.activeNetwork ?: return null
        val caps = connectivity.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        @Suppress("DEPRECATION")
        val legacy = connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            ?.extraInfo
            ?.let { sanitizeSsid(it) }
        return legacy?.takeIf { it.isNotBlank() }
    }

    /**
     * Dispara un escaneo WiFi y espera el broadcast de resultados.
     * Puede devolver lista vacía si el sistema limíta el rate de scan.
     */
    suspend fun scanNearbyNetworks(): Result<List<WifiNetwork>> {
        val manager = wifiManager
            ?: return Result.failure(IllegalStateException("WiFi no disponible en este dispositivo."))

        if (!manager.isWifiEnabled) {
            return Result.failure(IllegalStateException("WIFI_OFF"))
        }

        return suspendCancellableCoroutine { continuation ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: IllegalArgumentException) {
                        // ya desregistrado
                    }
                    if (!continuation.isActive) return

                    val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, true)
                    } else {
                        true
                    }

                    val networks = manager.scanResults
                        .orEmpty()
                        .mapNotNull { it.toWifiNetwork() }
                        .distinctBy { it.ssid.lowercase() }
                        .sortedByDescending { it.rssi }

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

            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }

            continuation.invokeOnCancellation {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: IllegalArgumentException) {
                    // ignore
                }
            }

            @Suppress("DEPRECATION")
            val started = manager.startScan()
            if (!started) {
                // Aun si startScan falla (throttle), intentar devolver el último cache.
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: IllegalArgumentException) {
                    // ignore
                }
                if (!continuation.isActive) return@suspendCancellableCoroutine
                val cached = manager.scanResults
                    .orEmpty()
                    .mapNotNull { it.toWifiNetwork() }
                    .distinctBy { it.ssid.lowercase() }
                    .sortedByDescending { it.rssi }
                continuation.resume(Result.success(cached))
            }
        }
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
        if (value == UNKNOWN_SSID || value == "<unknown ssid>") return null
        if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
            value = value.substring(1, value.length - 1)
        }
        return value.takeIf { it.isNotBlank() }
    }

    private companion object {
        private const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
