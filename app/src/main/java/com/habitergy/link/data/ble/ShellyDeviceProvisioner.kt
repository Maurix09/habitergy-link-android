package com.habitergy.link.data.ble

import com.habitergy.link.domain.model.ShellyProvisionStep
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest

/**
 * Orquesta la secuencia RPC-over-BLE del paso 4:
 * Cloud off → nombre → WiFi → MQTT → reboot (con delay) → auth admin.
 *
 * El reboot se agenda **antes** de SetAuth: al activar autenticación, el canal
 * BLE exige digest auth en el siguiente RPC y `Shelly.Reboot` falla (p. ej.
 * "Unsupported Media Type"). Con `delay_ms` el dispositivo reinicia después
 * de aplicar la contraseña en flash.
 */
class ShellyDeviceProvisioner(
    private val rpcClient: ShellyBleRpcClient,
) {
    suspend fun configure(
        shortCode: String,
        wifiSsid: String,
        wifiPassword: String,
        macAddress: String,
        onStep: (ShellyProvisionStep) -> Unit,
    ) {
        onStep(ShellyProvisionStep.DisableCloud)
        rpcClient.call(
            method = "Cloud.SetConfig",
            params = buildJsonObject {
                put("config", buildJsonObject { put("enable", false) })
            },
        )

        onStep(ShellyProvisionStep.SetDeviceName)
        rpcClient.call(
            method = "Sys.SetConfig",
            params = buildJsonObject {
                put("config", buildJsonObject {
                    put("device", buildJsonObject {
                        put("name", ShellyProvisioningConfig.deviceDisplayName(shortCode))
                    })
                })
            },
        )

        onStep(ShellyProvisionStep.ConfigureWifi)
        val wifiConfig = buildJsonObject {
            put("sta", buildJsonObject {
                put("ssid", wifiSsid)
                put("enable", true)
                if (wifiPassword.isNotBlank()) {
                    put("pass", wifiPassword)
                }
            })
        }
        rpcClient.call(
            method = "Wifi.SetConfig",
            params = buildJsonObject { put("config", wifiConfig) },
        )

        onStep(ShellyProvisionStep.ConfigureMqtt)
        val clientId = normalizeMac(macAddress)
        rpcClient.call(
            method = "Mqtt.SetConfig",
            params = buildJsonObject {
                put("config", buildJsonObject {
                    put("enable", true)
                    put("server", ShellyProvisioningConfig.BROKER_HOST)
                    put("client_id", clientId)
                    put("user", shortCode)
                    put("pass", ShellyProvisioningConfig.MQTT_PASSWORD)
                    put("ssl_ca", JsonNull)
                    put("topic_prefix", ShellyProvisioningConfig.mqttTopicPrefix(shortCode))
                    put("enable_control", true)
                    put("enable_rpc", true)
                    put("rpc_ntf", false)
                    put("status_ntf", false)
                })
            },
        )

        // Agendar reboot antes de SetAuth (ver KDoc de la clase).
        onStep(ShellyProvisionStep.Reboot)
        rpcClient.call(
            method = "Shelly.Reboot",
            params = buildJsonObject {
                put("delay_ms", REBOOT_DELAY_MS)
            },
        )

        onStep(ShellyProvisionStep.SetAdminAuth)
        val deviceInfo = rpcClient.call(method = "Shelly.GetDeviceInfo")
        val deviceId = deviceInfo["id"]?.jsonPrimitive?.content
            ?: throw ShellyBleRpcException("No pudimos leer el ID del controlador Shelly.")
        val ha1 = computeAdminHa1(deviceId, ShellyProvisioningConfig.ADMIN_PASSWORD)
        rpcClient.call(
            method = "Shelly.SetAuth",
            params = buildJsonObject {
                put("user", "admin")
                put("realm", deviceId)
                put("ha1", ha1)
            },
        )
    }

    private fun computeAdminHa1(deviceId: String, password: String): String {
        val input = "admin:$deviceId:$password"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        /** Tiempo suficiente para SetAuth + disconnect antes del reinicio. */
        const val REBOOT_DELAY_MS = 2_500
    }
}
