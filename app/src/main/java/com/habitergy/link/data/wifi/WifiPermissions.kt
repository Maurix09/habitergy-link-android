package com.habitergy.link.data.wifi

import android.Manifest
import android.content.Context
import android.location.LocationManager
import android.os.Build
import com.habitergy.link.data.RuntimePermissions

/**
 * Permisos de runtime para leer la red actual y escanear SSIDs cercanos.
 *
 * `WifiManager.startScan()` y `getScanResults()` siguen requiriendo ubicación
 * precisa en Android 13+. En Android 12+ FINE y COARSE deben solicitarse juntas.
 * `NEARBY_WIFI_DEVICES` se agrega en API 33+ para las APIs WiFi modernas usadas
 * durante el aprovisionamiento.
 */
object WifiPermissions {
    val required: Array<String> = buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()

    fun allGranted(context: Context): Boolean =
        RuntimePermissions.allGranted(context, required)

    fun missing(context: Context): Array<String> =
        RuntimePermissions.missing(context, required)

    /** El escaneo activo de puntos de acceso requiere Location Services en este rango de SDK. */
    fun isLocationRequiredForScan(): Boolean = true

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
