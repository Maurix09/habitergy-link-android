package com.habitergy.link.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.habitergy.link.domain.model.ScannedShellyDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Falla del escaneo reportada por el sistema (ScanCallback.onScanFailed). */
class BleScanFailedException(val errorCode: Int) :
    Exception("BLE scan failed with code $errorCode")

/** El adaptador o el escáner BLE no están disponibles. */
class BleUnavailableException : Exception("BLE scanner unavailable")

/**
 * Escáner BLE de controladores Shelly. Filtra por el manufacturer ID de
 * Allterco y parsea el anuncio para obtener MAC/modelo (réplica en Kotlin de
 * la lógica de `apps/manager/src/lib/bluetooth`).
 */
class ShellyBleScanner(context: Context) {

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter?
        get() = manager?.adapter

    fun isSupported(): Boolean = adapter != null

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    /**
     * Emite cada controlador Shelly 1PM compatible detectado mientras el
     * colector esté activo. El escaneo se detiene automáticamente al cancelar
     * la colección (por timeout, match o navegación).
     */
    @SuppressLint("MissingPermission")
    fun scan(): Flow<ScannedShellyDevice> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(BleUnavailableException())
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                parseScanResult(result)?.let { trySend(it) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result -> parseScanResult(result)?.let { trySend(it) } }
            }

            override fun onScanFailed(errorCode: Int) {
                close(BleScanFailedException(errorCode))
            }
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setManufacturerData(ALLTERCO_MANUFACTURER_ID, ByteArray(0))
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, callback)
        } catch (error: SecurityException) {
            close(error)
            return@callbackFlow
        }

        awaitClose {
            runCatching { scanner.stopScan(callback) }
        }
    }

    private fun parseScanResult(result: ScanResult): ScannedShellyDevice? {
        val record = result.scanRecord
        val manufacturerData = record?.getManufacturerSpecificData(ALLTERCO_MANUFACTURER_ID)
        val parsed = parseShellyManufacturerData(manufacturerData)
        val advertisedName = record?.deviceName?.trim().orEmpty()

        val nameLooksSupported = isShelly1PmName(advertisedName)
        val modelSupported = isSupportedShelly1PmModel(parsed?.modelId)
        if (!modelSupported && !nameLooksSupported) {
            return null
        }

        val deviceAddress = result.device?.address
        val rawMac = parsed?.mac ?: deviceAddress ?: return null
        val mac = formatMac(rawMac)
        if (normalizeMac(mac).length < 6) {
            return null
        }

        val name = if (advertisedName.isNotEmpty() && nameLooksSupported) {
            advertisedName
        } else {
            buildShellyDisplayName(mac)
        }

        return ScannedShellyDevice(
            id = deviceAddress ?: normalizeMac(mac),
            name = name,
            macAddress = mac,
            rssi = result.rssi,
        )
    }
}
