package com.habitergy.link.data.ble

import com.habitergy.link.domain.model.ShellyProvisionStep
import com.habitergy.link.domain.model.Step4Error
import com.habitergy.link.domain.model.Step4ProvisionException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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
 *
 * Cada fallo lanza [Step4ProvisionException] con el código de diagnóstico
 * correspondiente (ERROR 7–13).
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
        callRpc(
            error = Step4Error.RPC_CLOUD,
            method = "Cloud.SetConfig",
            params = buildJsonObject {
                put("config", buildJsonObject { put("enable", false) })
            },
        )

        onStep(ShellyProvisionStep.SetDeviceName)
        callRpc(
            error = Step4Error.RPC_SYS,
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
        callRpc(
            error = Step4Error.RPC_WIFI,
            method = "Wifi.SetConfig",
            params = buildJsonObject { put("config", wifiConfig) },
        )

        onStep(ShellyProvisionStep.ConfigureMqtt)
        val clientId = normalizeMac(macAddress)
        callRpc(
            error = Step4Error.RPC_MQTT,
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
        callRpc(
            error = Step4Error.RPC_REBOOT,
            method = "Shelly.Reboot",
            params = buildJsonObject {
                put("delay_ms", REBOOT_DELAY_MS)
            },
        )

        onStep(ShellyProvisionStep.SetAdminAuth)
        val deviceInfo = callRpc(
            error = Step4Error.RPC_GET_DEVICE_INFO,
            method = "Shelly.GetDeviceInfo",
        )
        val deviceId = deviceInfo["id"]?.jsonPrimitive?.content
            ?: throw Step4ProvisionException(
                Step4Error.RPC_GET_DEVICE_INFO,
                "Respuesta sin campo id.",
            )
        val ha1 = computeAdminHa1(deviceId, ShellyProvisioningConfig.ADMIN_PASSWORD)
        callRpc(
            error = Step4Error.RPC_SET_AUTH,
            method = "Shelly.SetAuth",
            params = buildJsonObject {
                put("user", "admin")
                put("realm", deviceId)
                put("ha1", ha1)
            },
        )
    }

    private suspend fun callRpc(
        error: Step4Error,
        method: String,
        params: JsonObject? = null,
    ): JsonObject = try {
        rpcClient.call(method = method, params = params)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: ShellyBleRpcException) {
        throw Step4ProvisionException(
            error = error,
            detail = exception.message ?: "Fallo RPC $method",
            cause = exception,
        )
    } catch (exception: Exception) {
        throw Step4ProvisionException(
            error = error,
            detail = exception.message ?: "Fallo inesperado en $method",
            cause = exception,
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
