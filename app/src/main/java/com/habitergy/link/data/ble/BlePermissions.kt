package com.habitergy.link.data.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Permisos de runtime y precondiciones del sistema para escanear por BLE.
 *
 * - Android 12+ (API 31): `BLUETOOTH_SCAN` (+ `neverForLocation` en el manifiesto)
 *   y `BLUETOOTH_CONNECT` para el diálogo de encendido (`ACTION_REQUEST_ENABLE`).
 * - Android 11 y anteriores: el escaneo BLE requiere `ACCESS_FINE_LOCATION` y
 *   que los **servicios de ubicación** del sistema estén activos.
 */
object BlePermissions {
    val required: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun allGranted(context: Context): Boolean = required.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * En Android ≤11 el stack BLE no entrega anuncios si la ubicación del
     * sistema está apagada. En 12+ con `neverForLocation` no hace falta.
     */
    fun isLocationRequiredForScan(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S

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
