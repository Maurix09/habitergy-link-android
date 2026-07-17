package com.habitergy.link.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

class ShellyBleRpcException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Cliente RPC-over-BLE para Shelly Gen2+ (Gen3/Gen4).
 * Conecta por GATT, envía JSON-RPC 2.0 y lee la respuesta.
 */
class ShellyBleRpcClient(
    private val context: Context,
) {
    private val rpcId = AtomicInteger(1)
    private var gatt: BluetoothGatt? = null
    private var dataChar: BluetoothGattCharacteristic? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private var rxChar: BluetoothGattCharacteristic? = null

    private var connectDeferred: CompletableDeferred<Unit>? = null
    private var discoverDeferred: CompletableDeferred<Unit>? = null
    private var writeDeferred: CompletableDeferred<Unit>? = null
    private var readDeferred: CompletableDeferred<ByteArray>? = null

  @SuppressLint("MissingPermission")
    suspend fun connect(deviceAddress: String) = withContext(Dispatchers.IO) {
        disconnect()
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: throw ShellyBleRpcException("Bluetooth no disponible en este dispositivo.")
        val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)

        withTimeout(CONNECT_TIMEOUT_MS) {
            connectDeferred = CompletableDeferred()
            discoverDeferred = CompletableDeferred()

            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, gattCallback)
            } ?: throw ShellyBleRpcException("No se pudo iniciar la conexión BLE.")

            connectDeferred?.await()
            discoverDeferred?.await()
        }

        if (dataChar == null || txChar == null || rxChar == null) {
            throw ShellyBleRpcException("No encontramos el servicio RPC de Shelly en el controlador.")
        }
    }

    suspend fun call(method: String, params: JsonObject = buildJsonObject {}): JsonObject =
        withContext(Dispatchers.IO) {
            val request = buildJsonObject {
                put("id", rpcId.getAndIncrement())
                put("method", method)
                put("params", params)
                put("src", "habitergy-link")
            }
            val payload = Json.encodeToString(JsonObject.serializer(), request).toByteArray(Charsets.UTF_8)
            sendRpcPayload(payload)
            val responseBytes = readRpcPayload()
            val responseText = responseBytes.toString(Charsets.UTF_8).trim()
            if (responseText.isEmpty()) {
                return@withContext buildJsonObject {}
            }
            val response = Json.parseToJsonElement(responseText).jsonObject
            if (response.containsKey("error")) {
                val error = response["error"]?.jsonObject
                val message = error?.get("message")?.jsonPrimitive?.content ?: responseText
                throw ShellyBleRpcException("RPC $method falló: $message")
            }
            response["result"]?.jsonObject ?: buildJsonObject {}
        }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        dataChar = null
        txChar = null
        rxChar = null
        connectDeferred?.cancel()
        discoverDeferred?.cancel()
        writeDeferred?.cancel()
        readDeferred?.cancel()
        connectDeferred = null
        discoverDeferred = null
        writeDeferred = null
        readDeferred = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendRpcPayload(payload: ByteArray) {
        val tx = txChar ?: throw ShellyBleRpcException("Canal TX no disponible.")
        val data = dataChar ?: throw ShellyBleRpcException("Canal de datos no disponible.")

        val lengthBytes = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(payload.size)
            .array()

        writeCharacteristic(tx, lengthBytes)
        writeCharacteristic(data, payload)
    }

  @SuppressLint("MissingPermission")
    private suspend fun readRpcPayload(): ByteArray {
        val rx = rxChar ?: throw ShellyBleRpcException("Canal RX no disponible.")
        val data = dataChar ?: throw ShellyBleRpcException("Canal de datos no disponible.")

        val lengthBytes = readCharacteristic(rx)
        if (lengthBytes.size < 4) {
            return ByteArray(0)
        }
        val length = ByteBuffer.wrap(lengthBytes.copyOf(4))
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
        if (length <= 0) {
            return ByteArray(0)
        }

        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val chunk = readCharacteristic(data)
            if (chunk.isEmpty()) break
            val copyLen = minOf(chunk.size, length - offset)
            System.arraycopy(chunk, 0, buffer, offset, copyLen)
            offset += copyLen
            if (chunk.size < (dataChar?.value?.size ?: chunk.size)) {
                break
            }
        }
        return buffer.copyOf(offset)
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        val g = gatt ?: throw ShellyBleRpcException("GATT desconectado.")
        writeDeferred = CompletableDeferred()
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = value
        if (!g.writeCharacteristic(characteristic)) {
            writeDeferred = null
            throw ShellyBleRpcException("No se pudo escribir en el controlador Shelly.")
        }
        withTimeout(RPC_TIMEOUT_MS) {
            writeDeferred?.await()
        }
        writeDeferred = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun readCharacteristic(characteristic: BluetoothGattCharacteristic): ByteArray {
        val g = gatt ?: throw ShellyBleRpcException("GATT desconectado.")
        readDeferred = CompletableDeferred()
        if (!g.readCharacteristic(characteristic)) {
            readDeferred = null
            throw ShellyBleRpcException("No se pudo leer del controlador Shelly.")
        }
        val result = withTimeout(RPC_TIMEOUT_MS) {
            readDeferred?.await() ?: ByteArray(0)
        }
        readDeferred = null
        return result
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.requestMtu(512)
                gatt.discoverServices()
                connectDeferred?.complete(Unit)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectDeferred?.completeExceptionally(
                    ShellyBleRpcException("Conexión BLE interrumpida (status=$status)."),
                )
                discoverDeferred?.completeExceptionally(
                    ShellyBleRpcException("Conexión BLE interrumpida."),
                )
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            // MTU negotiation complete; service discovery already triggered.
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                discoverDeferred?.completeExceptionally(
                    ShellyBleRpcException("Error al descubrir servicios BLE (status=$status)."),
                )
                return
            }
            val service = gatt.getService(ShellyGattUuids.SERVICE)
            if (service == null) {
                discoverDeferred?.completeExceptionally(
                    ShellyBleRpcException("Servicio GATT Shelly no encontrado."),
                )
                return
            }
            dataChar = service.getCharacteristic(ShellyGattUuids.DATA)
            txChar = service.getCharacteristic(ShellyGattUuids.TX_CONTROL)
            rxChar = service.getCharacteristic(ShellyGattUuids.RX_CONTROL)
            discoverDeferred?.complete(Unit)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeDeferred?.complete(Unit)
            } else {
                writeDeferred?.completeExceptionally(
                    ShellyBleRpcException("Error al escribir BLE (status=$status)."),
                )
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readDeferred?.complete(characteristic.value ?: ByteArray(0))
            } else {
                readDeferred?.completeExceptionally(
                    ShellyBleRpcException("Error al leer BLE (status=$status)."),
                )
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readDeferred?.complete(value)
            } else {
                readDeferred?.completeExceptionally(
                    ShellyBleRpcException("Error al leer BLE (status=$status)."),
                )
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            // Not used.
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val RPC_TIMEOUT_MS = 15_000L
    }
}
