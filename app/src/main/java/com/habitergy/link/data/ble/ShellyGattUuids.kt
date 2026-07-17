package com.habitergy.link.data.ble

import java.util.UUID

/**
 * GATT UUIDs for Shelly Gen2+ RPC-over-BLE.
 * @see https://kb.shelly.cloud/knowledge-base/kbsa-communicating-with-shelly-devices-via-bluetoo
 */
object ShellyGattUuids {
    val SERVICE: UUID = UUID.fromString("5f6d4f53-5f52-5043-5f53-56435f49445f")
    val DATA: UUID = UUID.fromString("5f6d4f53-5f52-5043-5f64-6174615f5f5f")
    val TX_CONTROL: UUID = UUID.fromString("5f6d4f53-5f52-5043-5f74-785f63746c5f")
    val RX_CONTROL: UUID = UUID.fromString("5f6d4f53-5f52-5043-5f72-785f63746c5f")
}
