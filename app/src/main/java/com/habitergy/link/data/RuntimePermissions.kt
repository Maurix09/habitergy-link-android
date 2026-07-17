package com.habitergy.link.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helpers compartidos para permisos de runtime (BLE paso 2, WiFi paso 3).
 *
 * Flujo esperado:
 * 1. Pedir el diálogo del sistema (`RequestMultiplePermissions`).
 * 2. Si el usuario marcó «No volver a preguntar» / denegó de forma permanente,
 *    abrir la ficha de la app en Ajustes para que pueda otorgar el permiso.
 */
object RuntimePermissions {

    fun missing(context: Context, permissions: Array<String>): Array<String> =
        permissions.filterNot { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    fun allGranted(context: Context, permissions: Array<String>): Boolean =
        missing(context, permissions).isEmpty()

    fun appDetailsSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }

    /**
     * Tras al menos un pedido explícito, si sigue denegado y Android ya no
     * muestra el diálogo (rationale = false), hay que ir a Ajustes.
     */
    fun shouldOpenAppSettings(
        activity: Activity,
        permissions: Array<String>,
        alreadyRequested: Boolean,
    ): Boolean {
        if (!alreadyRequested) return false
        val missing = missing(activity, permissions)
        if (missing.isEmpty()) return false
        return missing.none { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
