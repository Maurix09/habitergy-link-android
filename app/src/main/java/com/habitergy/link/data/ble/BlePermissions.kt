package com.habitergy.link.data.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Permisos de runtime necesarios para escanear por BLE.
 *
 * - Android 12+ (API 31): basta con BLUETOOTH_SCAN para descubrir dispositivos.
 * - Android 11 y anteriores: el escaneo BLE requiere ACCESS_FINE_LOCATION.
 *
 * BLUETOOTH_CONNECT (necesario para conectar por GATT) se solicitará en el
 * paso 3 cuando se implemente el provisioning; para escanear no hace falta.
 */
object BlePermissions {
    val required: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun allGranted(context: Context): Boolean = required.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
