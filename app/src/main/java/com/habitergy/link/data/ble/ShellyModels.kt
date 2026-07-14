package com.habitergy.link.data.ble

/** Shelly 1PM Gen3 / Gen4 — únicos modelos soportados en adopción. */
private val SUPPORTED_1PM_MODEL_IDS = setOf(
    0x1019, // Shelly 1PM Gen3
    0x1029, // Shelly 1PM Gen4
)

private val SHELLY_1PM_NAME_RE = Regex("^Shelly1PM", RegexOption.IGNORE_CASE)

fun isSupportedShelly1PmModel(modelId: Int?): Boolean =
    modelId != null && SUPPORTED_1PM_MODEL_IDS.contains(modelId)

fun isShelly1PmName(name: String): Boolean =
    name.isNotEmpty() && SHELLY_1PM_NAME_RE.containsMatchIn(name)

/** Construye un nombre legible "Shelly1PM-XXXXXX" con los últimos 6 dígitos de la MAC. */
fun buildShellyDisplayName(mac: String): String {
    val suffix = normalizeMac(mac).takeLast(6)
    return "Shelly1PM-$suffix"
}
