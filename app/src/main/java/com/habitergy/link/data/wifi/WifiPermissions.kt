package com.habitergy.link.data.wifi

import android.Manifest
import android.content.Context
import android.location.LocationManager
import android.os.Build
import com.habitergy.link.data.RuntimePermissions

/**
 * Permisos de runtime para leer la red actual y escanear SSIDs cercanos.
 *
 * - Android 13+ (API 33): `NEARBY_WIFI_DEVICES` (con `neverForLocation` en el manifiesto).
 * - Android 12 y anteriores: `ACCESS_FINE_LOCATION` + ubicación del sistema activa.
 */
object WifiPermissions {
    val required: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun allGranted(context: Context): Boolean =
        RuntimePermissions.allGranted(context, required)

    fun missing(context: Context): Array<String> =
        RuntimePermissions.missing(context, required)

    /** En API ≤32 el stack WiFi no entrega SSIDs si la ubicación del sistema está apagada. */
    fun isLocationRequiredForScan(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    fun isLocationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }
}
