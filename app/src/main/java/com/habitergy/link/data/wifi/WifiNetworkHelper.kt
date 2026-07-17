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
import androidx.annotation.RequiresApi
import com.habitergy.link.domain.model.WifiNetwork
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Lectura de la red WiFi actual y escaneo de SSIDs cercanos para el paso 3.
 *
 * Importante: varias APIs de WifiManager / WifiInfo pueden lanzar
 * [SecurityException] si faltan permisos o la ubicación del sistema está
 * apagada. Nunca deben tumbar la Activity: el escaneo propaga el error mediante
 * [Result] para que la UI pueda explicar la causa real.
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

            // El escaneo/SSID requieren Location Services aun en Android moderno.
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
                        ?.compatible24GhzSsid()
                    if (!fromTransport.isNullOrBlank()) return fromTransport
                }
            }

            @Suppress("DEPRECATION")
            val fromWifiInfo = wifiManager?.connectionInfo
                ?.compatible24GhzSsid()
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

    /** Dispara un escaneo WiFi con timeout y fallback controlado a la caché. */
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

        val result = withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                awaitScanWithCallback(manager)
            } else {
                awaitScanWithBroadcast(manager)
            }
        }
        return result ?: Result.failure(
            WifiScanTimeoutException(
                "La búsqueda tardó demasiado. Esperá unos segundos e intentá de nuevo.",
            ),
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    private suspend fun awaitScanWithCallback(
        manager: WifiManager,
    ): Result<List<WifiNetwork>> = suspendCancellableCoroutine { continuation ->
        val callback = object : WifiManager.ScanResultsCallback() {
            override fun onScanResultsAvailable() {
                unregisterScanCallback(manager, this)
                if (!continuation.isActive) return
                continuation.resume(readScanResultsResult(manager))
            }
        }

        try {
            manager.registerScanResultsCallback(appContext.mainExecutor, callback)
        } catch (error: Exception) {
            continuation.resume(Result.failure(error))
            return@suspendCancellableCoroutine
        }

        continuation.invokeOnCancellation {
            unregisterScanCallback(manager, callback)
        }

        if (!continuation.isActive) return@suspendCancellableCoroutine
        requestActiveScan(
            manager = manager,
            unregister = { unregisterScanCallback(manager, callback) },
            resume = { result ->
                if (continuation.isActive) continuation.resume(result)
            },
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitScanWithBroadcast(
        manager: WifiManager,
    ): Result<List<WifiNetwork>> = suspendCancellableCoroutine { continuation ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                unregisterScanReceiver(this)
                if (!continuation.isActive) return

                val success = intent.getBooleanExtra(
                    WifiManager.EXTRA_RESULTS_UPDATED,
                    false,
                )
                continuation.resume(scanCompletionResult(manager, success))
            }
        }

        try {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            appContext.registerReceiver(receiver, filter)
        } catch (error: Exception) {
            continuation.resume(Result.failure(error))
            return@suspendCancellableCoroutine
        }

        continuation.invokeOnCancellation {
            unregisterScanReceiver(receiver)
        }

        if (!continuation.isActive) return@suspendCancellableCoroutine
        requestActiveScan(
            manager = manager,
            unregister = { unregisterScanReceiver(receiver) },
            resume = { result ->
                if (continuation.isActive) continuation.resume(result)
            },
        )
    }

    @SuppressLint("MissingPermission")
    private fun requestActiveScan(
        manager: WifiManager,
        unregister: () -> Unit,
        resume: (Result<List<WifiNetwork>>) -> Unit,
    ) {
        try {
            @Suppress("DEPRECATION")
            val started = manager.startScan()
            if (!started) {
                Log.w(TAG, "startScan rejected; using cached scan results")
                unregister()
                val cached = readScanResultsResult(manager)
                resume(
                    cached.fold(
                        onSuccess = { networks ->
                            if (networks.isNotEmpty()) {
                                Result.success(networks)
                            } else {
                                Result.failure(
                                    WifiScanTemporarilyUnavailableException(
                                        "Android no pudo iniciar una búsqueda nueva; puede haber " +
                                            "un límite temporal. Esperá unos segundos antes de reintentar.",
                                    ),
                                )
                            }
                        },
                        onFailure = { Result.failure(it) },
                    ),
                )
            }
        } catch (error: Exception) {
            unregister()
            resume(Result.failure(error))
        }
    }

    private fun scanCompletionResult(
        manager: WifiManager,
        resultsUpdated: Boolean,
    ): Result<List<WifiNetwork>> {
        val result = readScanResultsResult(manager)
        return result.fold(
            onSuccess = { networks ->
                if (!resultsUpdated && networks.isEmpty()) {
                    Result.failure(
                        IllegalStateException(
                            "Android no pudo actualizar la lista de redes. Intentá de nuevo.",
                        ),
                    )
                } else {
                    Result.success(networks)
                }
            },
            onFailure = { Result.failure(it) },
        )
    }

    private fun readScanResultsResult(manager: WifiManager): Result<List<WifiNetwork>> {
        return try {
            Result.success(
                manager.scanResults
                    .orEmpty()
                    .mapNotNull { it.toWifiNetwork() }
                    .distinctBy { it.ssid.lowercase() }
                    .sortedByDescending { it.rssi },
            )
        } catch (error: SecurityException) {
            Log.w(TAG, "WiFi scan results blocked by permissions", error)
            Result.failure(error)
        } catch (error: Exception) {
            Log.w(TAG, "Unable to read WiFi scan results", error)
            Result.failure(error)
        }
    }

    private fun unregisterScanReceiver(receiver: BroadcastReceiver) {
        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Ya estaba desregistrado.
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun unregisterScanCallback(
        manager: WifiManager,
        callback: WifiManager.ScanResultsCallback,
    ) {
        try {
            manager.unregisterScanResultsCallback(callback)
        } catch (_: IllegalArgumentException) {
            // Ya estaba desregistrado.
        }
    }

    @Suppress("DEPRECATION")
    private fun ScanResult.toWifiNetwork(): WifiNetwork? {
        if (frequency !in MIN_24_GHZ_FREQUENCY..MAX_24_GHZ_FREQUENCY) return null
        val rawSsid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wifiSsid?.toString()
        } else {
            SSID
        }
        val name = sanitizeSsid(rawSsid) ?: return null
        if (name.isBlank()) return null
        return WifiNetwork(
            ssid = name,
            rssi = level,
            isSecured = capabilitiesIndicateSecured(capabilities),
        )
    }

    @Suppress("DEPRECATION")
    private fun WifiInfo.compatible24GhzSsid(): String? {
        if (frequency !in MIN_24_GHZ_FREQUENCY..MAX_24_GHZ_FREQUENCY) return null
        return sanitizeSsid(ssid)
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
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val MIN_24_GHZ_FREQUENCY = 2_400
        private const val MAX_24_GHZ_FREQUENCY = 2_500
    }
}

class WifiScanTimeoutException(message: String) : IllegalStateException(message)

class WifiScanTemporarilyUnavailableException(message: String) : IllegalStateException(message)
