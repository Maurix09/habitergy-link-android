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

/**
 * Orquesta la secuencia RPC-over-BLE del paso 4:
 * GetDeviceInfo → Cloud off → nombre → WiFi → MQTT → auth admin → reboot.
 *
 * [Shelly.GetDeviceInfo] va primero porque es el único RPC que sigue
 * disponible con auth habilitada (reintentos tras un SetAuth previo).
 * Tras SetAuth, el cliente usa digest SHA-256 para Reboot y demás RPC.
 *
 * El reboot va **al final**: tras agendarlo el firmware rechaza más RPC
 * (`shutting down in X ms`). El `delay_ms` da margen para desconectar GATT.
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
        // GetDeviceInfo no requiere auth; sirve para realm y para reintentos.
        val deviceInfo = callRpc(
            error = Step4Error.RPC_GET_DEVICE_INFO,
            method = "Shelly.GetDeviceInfo",
        )
        val deviceId = deviceInfo["id"]?.jsonPrimitive?.content
            ?: throw Step4ProvisionException(
                Step4Error.RPC_GET_DEVICE_INFO,
                "Respuesta sin campo id.",
            )
        rpcClient.setDigestAuth(
            realm = deviceId,
            password = ShellyProvisioningConfig.ADMIN_PASSWORD,
        )

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

        onStep(ShellyProvisionStep.SetAdminAuth)
        val ha1 = ShellyDigestAuth.sha256Hex(
            "admin:$deviceId:${ShellyProvisioningConfig.ADMIN_PASSWORD}",
        )
        callRpc(
            error = Step4Error.RPC_SET_AUTH,
            method = "Shelly.SetAuth",
            params = buildJsonObject {
                put("user", "admin")
                put("realm", deviceId)
                put("ha1", ha1)
            },
        )

        onStep(ShellyProvisionStep.Reboot)
        callRpc(
            error = Step4Error.RPC_REBOOT,
            method = "Shelly.Reboot",
            params = buildJsonObject {
                put("delay_ms", REBOOT_DELAY_MS)
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

    private companion object {
        /** Margen para disconnect GATT antes del reinicio real del dispositivo. */
        const val REBOOT_DELAY_MS = 2_500
    }
}
