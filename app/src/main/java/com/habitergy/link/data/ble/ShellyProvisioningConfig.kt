package com.habitergy.link.data.ble

/**
 * Valores fijos de aprovisionamiento Shelly para Habitergy Link.
 * La API key del broker provisioner no va aquí — solo en el backend.
 */
object ShellyProvisioningConfig {
    const val BROKER_HOST = "broker.habitergy.com:1883"
    const val MQTT_PASSWORD = "Habitergy2129+"
    const val ADMIN_PASSWORD = "Habitergy2129+"
    const val DEVICE_CODE_PREFIX = "SH-"

    fun mqttTopicPrefix(shortCode: String): String = "habitergy/v1/$shortCode"

    fun deviceDisplayName(shortCode: String): String = "$DEVICE_CODE_PREFIX$shortCode"
}
