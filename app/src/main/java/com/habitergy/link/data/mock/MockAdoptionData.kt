package com.habitergy.link.data.mock

import com.habitergy.link.domain.model.ResolvedDevice
import com.habitergy.link.domain.model.ScannedShellyDevice
import kotlinx.coroutines.delay

/**
 * Datos mock para desarrollo UI en emulador.
 * Códigos de prueba (5 caracteres): CX123, AB123, T3ST1
 */
object MockAdoptionData {

    private val deviceRegistry = mapOf(
        "CX123" to ResolvedDevice(
            deviceCode = "CX123",
            macAddress = "3C:E8:1A:12:34:56",
            model = "Shelly 1PM Gen3",
        ),
        "AB123" to ResolvedDevice(
            deviceCode = "AB123",
            macAddress = "3C:E8:1A:12:34:56",
            model = "Shelly 1PM Gen3",
        ),
        "T3ST1" to ResolvedDevice(
            deviceCode = "T3ST1",
            macAddress = "8A:13:BF:AB:CD:EF",
            model = "Shelly 1PM Gen4",
        ),
    )

    private val bleDevices = listOf(
        ScannedShellyDevice(
            id = "ble-1",
            name = "Shelly1PM-3CE81A",
            macAddress = "3C:E8:1A:12:34:56",
            rssi = -58,
        ),
        ScannedShellyDevice(
            id = "ble-2",
            name = "Shelly1PM-8A13BF",
            macAddress = "8A:13:BF:AB:CD:EF",
            rssi = -71,
        ),
        ScannedShellyDevice(
            id = "ble-3",
            name = "Shelly1PM-D84210",
            macAddress = "D8:42:10:11:22:33",
            rssi = -82,
        ),
    )

    /** Simula lookup en API: GET /api/devices/code/{deviceCode} */
    suspend fun lookupDeviceCode(rawCode: String): Result<ResolvedDevice> {
        delay(600)
        val normalized = normalizeDeviceCode(rawCode)
        if (normalized.length != 5) {
            return Result.failure(
                IllegalArgumentException("El código debe tener 5 caracteres."),
            )
        }
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("Ingresá el código del controlador."))
        }
        val device = deviceRegistry[normalized]
            ?: return Result.failure(
                IllegalArgumentException("No encontramos un controlador con el código $normalized."),
            )
        return Result.success(device)
    }

    /** Simula escaneo BLE de ~2 s. */
    suspend fun scanBleDevices(): List<ScannedShellyDevice> {
        delay(2000)
        return bleDevices
    }

    /** Código mock devuelto al escanear QR (futuro). */
    const val MOCK_QR_DEVICE_CODE = "CX123"

    fun normalizeDeviceCode(raw: String): String {
        return raw.trim().uppercase().replace("\\s".toRegex(), "")
    }

    fun normalizeMac(mac: String): String {
        return mac.uppercase().replace(":", "").replace("-", "")
    }

    fun macsMatch(a: String, b: String): Boolean {
        return normalizeMac(a) == normalizeMac(b)
    }
}
