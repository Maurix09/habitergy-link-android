package com.habitergy.link.data.ble

/** Deja solo los dígitos hexadecimales de una MAC, en mayúsculas. */
fun normalizeMac(mac: String): String =
    mac.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.uppercase()

/** Formatea una MAC como AA:BB:CC:DD:EE:FF cuando tiene los 12 dígitos. */
fun formatMac(mac: String): String {
    val clean = normalizeMac(mac)
    if (clean.length != 12) return mac.uppercase()
    return clean.chunked(2).joinToString(":")
}
