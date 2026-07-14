package com.habitergy.link.data.ble

/** Allterco / Shelly manufacturer ID en anuncios BLE. */
const val ALLTERCO_MANUFACTURER_ID = 0x0BA9

private const val BLOCK_TYPE_FLAGS = 0x01
private const val BLOCK_TYPE_MAC = 0x0A
private const val BLOCK_TYPE_MODEL = 0x0B

private const val FLAG_RPC_OVER_BLE_ENABLED = 1 shl 2

/** Datos relevantes del manufacturer data Allterco (réplica de shellyManufacturerData.ts). */
data class ParsedShellyManufacturerData(
    val flags: Int? = null,
    val mac: String? = null,
    val modelId: Int? = null,
)

/**
 * Parsea el bloque de manufacturer data de Allterco en flags / MAC / modelId.
 * Devuelve null si el buffer está vacío o no contiene ningún bloque conocido.
 */
fun parseShellyManufacturerData(data: ByteArray?): ParsedShellyManufacturerData? {
    if (data == null || data.isEmpty()) return null

    var flags: Int? = null
    var mac: String? = null
    var modelId: Int? = null
    var offset = 0

    while (offset < data.size) {
        val blockType = data[offset].toInt() and 0xFF
        offset += 1

        when (blockType) {
            BLOCK_TYPE_FLAGS -> {
                if (offset + 2 > data.size) break
                flags = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
                offset += 2
            }
            BLOCK_TYPE_MAC -> {
                if (offset + 6 > data.size) break
                val macBytes = data.copyOfRange(offset, offset + 6)
                mac = macBytes
                    .reversed()
                    .joinToString(":") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
                    .uppercase()
                offset += 6
            }
            BLOCK_TYPE_MODEL -> {
                if (offset + 2 > data.size) break
                modelId = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
                offset += 2
            }
            else -> break
        }
    }

    return if (flags == null && mac == null && modelId == null) {
        null
    } else {
        ParsedShellyManufacturerData(flags = flags, mac = mac, modelId = modelId)
    }
}

/** Indica si el anuncio expone el flag de RPC-over-BLE habilitado. */
fun hasRpcOverBle(data: ByteArray?): Boolean {
    val flags = parseShellyManufacturerData(data)?.flags ?: return false
    return (flags and FLAG_RPC_OVER_BLE_ENABLED) != 0
}
