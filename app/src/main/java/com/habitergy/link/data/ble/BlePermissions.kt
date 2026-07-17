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
 * - Android 12+ (API 31): `BLUETOOTH_SCAN` para descubrir + `BLUETOOTH_CONNECT`
 *   para el diálogo de encendido (`ACTION_REQUEST_ENABLE`). GATT del paso 3
 *   reutilizará `BLUETOOTH_CONNECT`.
 * - Android 11 y anteriores: el escaneo BLE requiere `ACCESS_FINE_LOCATION`.
 *
 * Además, mientras el manifiesto no declare `neverForLocation` en
 * `BLUETOOTH_SCAN`, Android no entrega resultados de escaneo si los
 * **servicios de ubicación** del sistema están apagados (también en ≤11).
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
     * Sin `neverForLocation` en el manifiesto, el stack BLE exige ubicación
     * del sistema activa para devolver anuncios.
     */
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
