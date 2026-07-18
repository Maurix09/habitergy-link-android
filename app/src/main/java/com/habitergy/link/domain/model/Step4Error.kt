package com.habitergy.link.domain.model

/**
 * Códigos de diagnóstico del paso 4 (aprovisionamiento).
 * Si falla la adopción, el número indica exactamente en qué llamada cortó.
 *
 * | Código | Etapa |
 * |--------|--------|
 * | 1 | Datos previos incompletos |
 * | 2 | API provision — red / timeout |
 * | 3 | API provision — 404 |
 * | 4 | API provision — 409 |
 * | 5 | API provision — otro error HTTP |
 * | 6 | Conexión BLE GATT |
 * | 7 | RPC Cloud.SetConfig |
 * | 8 | RPC Sys.SetConfig |
 * | 9 | RPC Wifi.SetConfig |
 * | 10 | RPC Mqtt.SetConfig |
 * | 11 | RPC Shelly.Reboot |
 * | 12 | RPC Shelly.GetDeviceInfo |
 * | 13 | RPC Shelly.SetAuth |
 * | 99 | Error inesperado |
 */
enum class Step4Error(
    val code: Int,
    /** Etiqueta corta para la UI (partner + diagnóstico). */
    val label: String,
) {
    MISSING_PREREQUISITES(1, "Datos previos"),
    BROKER_NETWORK(2, "API provision (red)"),
    BROKER_NOT_FOUND(3, "API provision (404)"),
    BROKER_CONFLICT(4, "API provision (409)"),
    BROKER_API(5, "API provision"),
    BLE_CONNECT(6, "Conexión BLE"),
    RPC_CLOUD(7, "Cloud.SetConfig"),
    RPC_SYS(8, "Sys.SetConfig"),
    RPC_WIFI(9, "Wifi.SetConfig"),
    RPC_MQTT(10, "Mqtt.SetConfig"),
    RPC_REBOOT(11, "Shelly.Reboot"),
    RPC_GET_DEVICE_INFO(12, "Shelly.GetDeviceInfo"),
    RPC_SET_AUTH(13, "Shelly.SetAuth"),
    UNKNOWN(99, "Desconocido"),
    ;

    /** Línea principal: `ERROR 11 — Shelly.Reboot`. */
    fun codeLine(): String = "ERROR $code — $label"

    /**
     * Mensaje completo para `provisionErrorMessage`.
     * Ejemplo:
     * ```
     * ERROR 11 — Shelly.Reboot
     * RPC Shelly.Reboot falló: Unsupported Media Type
     * ```
     */
    fun formatMessage(detail: String?): String {
        val trimmed = detail?.trim().orEmpty()
        return if (trimmed.isEmpty()) {
            codeLine()
        } else {
            "${codeLine()}\n$trimmed"
        }
    }
}

/** Excepción del pipeline del paso 4 con código de diagnóstico. */
class Step4ProvisionException(
    val error: Step4Error,
    val detail: String,
    cause: Throwable? = null,
) : Exception(error.formatMessage(detail), cause)
