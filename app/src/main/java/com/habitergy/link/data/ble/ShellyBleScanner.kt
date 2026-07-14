package com.habitergy.link.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Falla del escaneo reportada por el sistema (ScanCallback.onScanFailed). */
class BleScanFailedException(val errorCode: Int) :
    Exception("BLE scan failed with code $errorCode")

/** El adaptador o el escáner BLE no están disponibles. */
class BleUnavailableException : Exception("BLE scanner unavailable")

/** Anuncio BLE sin filtrar, tal como lo entrega el escáner de Android. */
data class BleAdvertisement(
    val address: String,
    val advertisedName: String?,
    val rssi: Int,
    val shellyMacAddress: String?,
)

/**
 * Escáner BLE. Expone un flujo sin filtros de hardware para listar todos los
 * dispositivos que el celular detecta; el ViewModel aplica el match por MAC.
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
     * Emite cada anuncio BLE que detecta el celular (sin filtros de manufacturer
     * ni modelo). El mismo dispositivo puede emitir varias veces con RSSI distinto.
     */
    @SuppressLint("MissingPermission")
    fun scanAll(): Flow<BleAdvertisement> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(BleUnavailableException())
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                parseAdvertisement(result)?.let { trySend(it) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result -> parseAdvertisement(result)?.let { trySend(it) } }
            }

            override fun onScanFailed(errorCode: Int) {
                close(BleScanFailedException(errorCode))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, callback)
        } catch (error: SecurityException) {
            close(error)
            return@callbackFlow
        }

        awaitClose {
            runCatching { scanner.stopScan(callback) }
        }
    }

    private fun parseAdvertisement(result: ScanResult): BleAdvertisement? {
        val address = result.device?.address ?: return null
        val record = result.scanRecord
        val advertisedName = record?.deviceName?.trim()?.takeIf { it.isNotEmpty() }
        val manufacturerData = record?.getManufacturerSpecificData(ALLTERCO_MANUFACTURER_ID)
        val shellyMac = parseShellyManufacturerData(manufacturerData)?.mac?.let { formatMac(it) }

        return BleAdvertisement(
            address = address,
            advertisedName = advertisedName,
            rssi = result.rssi,
            shellyMacAddress = shellyMac,
        )
    }
}
