package com.habitergy.link.ui.adoption

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habitergy.link.data.AdoptionLookupResult
import com.habitergy.link.data.AdoptionRepository
import com.habitergy.link.data.ble.BleAdvertisement
import com.habitergy.link.data.ble.BlePermissions
import com.habitergy.link.data.ble.ShellyBleScanner
import com.habitergy.link.data.ble.buildShellyDisplayName
import com.habitergy.link.data.ble.formatMac
import com.habitergy.link.data.ble.normalizeMac
import com.habitergy.link.data.wifi.WifiNetworkHelper
import com.habitergy.link.data.wifi.WifiPermissions
import com.habitergy.link.domain.DeviceCode
import com.habitergy.link.domain.model.AdoptionUiState
import com.habitergy.link.domain.model.BleScanPhase
import com.habitergy.link.domain.model.DEVICE_CODE_LENGTH
import com.habitergy.link.domain.model.DeviceLookupState
import com.habitergy.link.domain.model.DiscoveredBleDevice
import com.habitergy.link.domain.model.IdentificationMode
import com.habitergy.link.domain.model.ResolvedDevice
import com.habitergy.link.domain.model.ScannedShellyDevice
import com.habitergy.link.domain.model.UNKNOWN_DEVICE_CODE
import com.habitergy.link.domain.model.WifiScanPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AdoptionViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val repository: AdoptionRepository = AdoptionRepository()
    private val bleScanner: ShellyBleScanner = ShellyBleScanner(application)
    private val wifiHelper: WifiNetworkHelper = WifiNetworkHelper(application)

    private val _uiState = MutableStateFlow(AdoptionUiState())
    val uiState: StateFlow<AdoptionUiState> = _uiState.asStateFlow()
    private var lookupJob: Job? = null
    private var activeLookupSuffix: String? = null
    private var scanJob: Job? = null
    private var wifiScanJob: Job? = null

    /**
     * Mantiene una única fuente de verdad para el código ingresado. Si el cambio
     * deja el sufijo completo (5 caracteres), vuelve a validar y relanza el
     * lookup aunque el usuario haya corregido un recuadro intermedio.
     */
    fun onDeviceCodeChange(value: String) {
        updateDeviceCode(value)
    }

    fun onDeviceCodeComplete(value: String) {
        updateDeviceCode(value)
    }

    fun onScanQrClick() {
        // Placeholder hasta integrar CameraX + lector QR (snackbar "Coming soon" en la UI).
    }

    fun proceedWithoutKnownCode() {
        cancelLookup()
        _uiState.update {
            it.copy(
                deviceCodeInput = UNKNOWN_DEVICE_CODE,
                identificationMode = IdentificationMode.NoCode,
                resolvedDevice = null,
                lookupState = DeviceLookupState.Idle,
            )
        }
        navigateToStep2()
    }

    fun proceedToStep2() {
        if (_uiState.value.canProceedFromStep1) {
            navigateToStep2()
        }
    }

    fun proceedToStep3() {
        if (!_uiState.value.canProceedFromStep2) return
        cancelScan()
        // Navegar primero: leer el SSID nunca debe bloquear ni tumbar el paso 3.
        _uiState.update {
            it.copy(
                currentStep = 3,
                wifiSsidTouched = false,
                showWifiNetworkSheet = false,
                wifiScanPhase = WifiScanPhase.Idle,
                nearbyWifiNetworks = emptyList(),
                wifiScanErrorMessage = null,
            )
        }
        prefillCurrentWifiSsid()
    }

    /**
     * Intenta completar el SSID con la red actual del teléfono.
     * Fallos de permiso / SecurityException se ignoran (campo queda vacío).
     */
    private fun prefillCurrentWifiSsid() {
        viewModelScope.launch {
            val ssid = runCatching { wifiHelper.getCurrentSsid() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return@launch
            _uiState.update { state ->
                if (state.currentStep != 3) return@update state
                // No pisar si el usuario ya empezó a escribir.
                if (state.wifiSsid.isNotBlank() && state.wifiSsidTouched) return@update state
                if (state.wifiSsid.isNotBlank()) return@update state
                state.copy(wifiSsid = ssid)
            }
        }
    }

    /**
     * Guarda las credenciales WiFi en el estado. El aprovisionamiento BLE
     * (paso 4) todavía no está implementado: la UI muestra un snackbar.
     */
    fun proceedFromStep3(): Boolean {
        _uiState.update { it.copy(wifiSsidTouched = true) }
        return _uiState.value.canProceedFromStep3
    }

    fun onWifiSsidChange(value: String) {
        _uiState.update {
            it.copy(
                wifiSsid = value,
                wifiSsidTouched = true,
            )
        }
    }

    fun onWifiPasswordChange(value: String) {
        _uiState.update { it.copy(wifiPassword = value) }
    }

    fun toggleWifiPasswordVisibility() {
        _uiState.update { it.copy(wifiPasswordVisible = !it.wifiPasswordVisible) }
    }

    fun openWifiNetworkSheet() {
        _uiState.update {
            it.copy(
                showWifiNetworkSheet = true,
                wifiScanPhase = WifiScanPhase.Idle,
                nearbyWifiNetworks = emptyList(),
                wifiScanErrorMessage = null,
            )
        }
        refreshWifiScanReadiness()
    }

    fun dismissWifiNetworkSheet() {
        cancelWifiScan()
        _uiState.update {
            it.copy(
                showWifiNetworkSheet = false,
                wifiScanPhase = WifiScanPhase.Idle,
                wifiScanErrorMessage = null,
            )
        }
    }

    fun selectWifiNetwork(ssid: String) {
        cancelWifiScan()
        _uiState.update {
            it.copy(
                wifiSsid = ssid,
                wifiSsidTouched = true,
                showWifiNetworkSheet = false,
                wifiScanPhase = WifiScanPhase.Idle,
                wifiScanErrorMessage = null,
            )
        }
    }

    fun refreshWifiScanReadiness() {
        if (!_uiState.value.showWifiNetworkSheet) return
        val app = getApplication<Application>()
        when {
            !WifiPermissions.allGranted(app) ->
                _uiState.update { it.copy(wifiScanPhase = WifiScanPhase.PermissionRequired) }
            WifiPermissions.isLocationRequiredForScan() &&
                !WifiPermissions.isLocationEnabled(app) ->
                _uiState.update { it.copy(wifiScanPhase = WifiScanPhase.LocationOff) }
            !wifiHelper.isWifiEnabled() ->
                _uiState.update { it.copy(wifiScanPhase = WifiScanPhase.WifiOff) }
            else -> startWifiScan()
        }
    }

    fun retryWifiScan() {
        refreshWifiScanReadiness()
    }

    private fun startWifiScan() {
        cancelWifiScan()
        _uiState.update {
            it.copy(
                wifiScanPhase = WifiScanPhase.Scanning,
                nearbyWifiNetworks = emptyList(),
                wifiScanErrorMessage = null,
            )
        }
        wifiScanJob = viewModelScope.launch {
            val result = wifiHelper.scanNearbyNetworks()
            result.fold(
                onSuccess = { networks ->
                    _uiState.update {
                        it.copy(
                            nearbyWifiNetworks = networks,
                            wifiScanPhase = if (networks.isEmpty()) {
                                WifiScanPhase.Empty
                            } else {
                                WifiScanPhase.Results
                            },
                        )
                    }
                },
                onFailure = { error ->
                    when {
                        error.message == "WIFI_OFF" ->
                            _uiState.update { it.copy(wifiScanPhase = WifiScanPhase.WifiOff) }
                        error is SecurityException ->
                            _uiState.update {
                                it.copy(
                                    wifiScanPhase = WifiScanPhase.PermissionRequired,
                                    wifiScanErrorMessage = null,
                                )
                            }
                        else ->
                            _uiState.update {
                                it.copy(
                                    wifiScanPhase = WifiScanPhase.Error,
                                    wifiScanErrorMessage = error.message
                                        ?: "No pudimos buscar redes WiFi. Intentá de nuevo.",
                                )
                            }
                    }
                },
            )
        }
    }

    private fun updateDeviceCode(value: String) {
        val sanitized = DeviceCode.normalizeSuffix(value).take(DEVICE_CODE_LENGTH)
        cancelLookup()

        _uiState.update {
            it.copy(
                deviceCodeInput = sanitized,
                identificationMode = IdentificationMode.WithCode,
                resolvedDevice = null,
                lookupState = DeviceLookupState.Idle,
            )
        }

        if (sanitized.length == DEVICE_CODE_LENGTH && sanitized != UNKNOWN_DEVICE_CODE) {
            resolveDeviceCode(sanitized)
        }
    }

    private fun resolveDeviceCode(suffix: String) {
        if (!DeviceCode.isValidSuffix(suffix)) {
            _uiState.update {
                it.copy(lookupState = DeviceLookupState.Invalid, resolvedDevice = null)
            }
            return
        }

        val fullCode = DeviceCode.fullCode(suffix)
        activeLookupSuffix = suffix

        lookupJob = viewModelScope.launch {
            _uiState.update { it.copy(lookupState = DeviceLookupState.Looking, resolvedDevice = null) }

            val result = repository.lookup(fullCode)
            if (!shouldApplyLookupResult(suffix)) return@launch

            when (result) {
                is AdoptionLookupResult.Found -> {
                    when (result.status) {
                        STATUS_AVAILABLE -> _uiState.update {
                            it.copy(
                                lookupState = DeviceLookupState.Available,
                                resolvedDevice = ResolvedDevice(
                                    deviceCode = result.deviceCode,
                                    macAddress = result.macAddress,
                                    model = result.model,
                                ),
                            )
                        }
                        STATUS_ASSIGNED -> _uiState.update {
                            it.copy(lookupState = DeviceLookupState.Assigned, resolvedDevice = null)
                        }
                        else -> _uiState.update {
                            it.copy(lookupState = DeviceLookupState.Unavailable, resolvedDevice = null)
                        }
                    }
                }
                AdoptionLookupResult.NotFound -> _uiState.update {
                    it.copy(lookupState = DeviceLookupState.NotFound, resolvedDevice = null)
                }
                AdoptionLookupResult.NetworkError -> _uiState.update {
                    it.copy(lookupState = DeviceLookupState.NetworkError, resolvedDevice = null)
                }
            }

            if (activeLookupSuffix == suffix) {
                lookupJob = null
            }
        }
    }

    private fun navigateToStep2() {
        cancelLookup()
        cancelScan()
        cancelWifiScan()
        _uiState.update {
            it.copy(
                currentStep = 2,
                bleScanPhase = BleScanPhase.Idle,
                discoveredBleDevices = emptyList(),
                scannedDevices = emptyList(),
                matchedDevice = null,
                selectedDeviceId = null,
                bleErrorMessage = null,
                showWifiNetworkSheet = false,
            )
        }
    }

    fun goBackToStep1() {
        cancelLookup()
        cancelScan()
        cancelWifiScan()
        _uiState.update {
            it.copy(
                currentStep = 1,
                bleScanPhase = BleScanPhase.Idle,
                discoveredBleDevices = emptyList(),
                scannedDevices = emptyList(),
                matchedDevice = null,
                selectedDeviceId = null,
                bleErrorMessage = null,
                deviceCodeInput = if (it.isUnknownDeviceCode) "" else it.deviceCodeInput,
                identificationMode = IdentificationMode.WithCode,
                showWifiNetworkSheet = false,
            )
        }
    }

    fun goBackToStep2() {
        cancelWifiScan()
        _uiState.update {
            it.copy(
                currentStep = 2,
                showWifiNetworkSheet = false,
                wifiScanPhase = WifiScanPhase.Idle,
                wifiScanErrorMessage = null,
                // No reiniciar el escaneo BLE: conservamos el match/selección.
            )
        }
    }

    /**
     * Verifica las precondiciones del escaneo BLE (soporte, permisos, adaptador
     * encendido, ubicación del sistema) y, si todo está listo, arranca el
     * escaneo. La UI la llama al entrar al paso 2 y tras conceder permisos,
     * encender el Bluetooth o activar la ubicación.
     */
    fun refreshBleReadiness() {
        val app = getApplication<Application>()
        when {
            !bleScanner.isSupported() -> updateBlePhase(
                BleScanPhase.Error,
                "Este dispositivo no es compatible con Bluetooth LE.",
            )
            !BlePermissions.allGranted(app) ->
                updateBlePhase(BleScanPhase.PermissionRequired)
            !bleScanner.isEnabled() ->
                updateBlePhase(BleScanPhase.BluetoothOff)
            BlePermissions.isLocationRequiredForScan() &&
                !BlePermissions.isLocationEnabled(app) ->
                updateBlePhase(BleScanPhase.LocationOff)
            else -> startBleScan()
        }
    }

    fun retryBleScan() {
        refreshBleReadiness()
    }

    fun selectDevice(deviceId: String) {
        _uiState.update { it.copy(selectedDeviceId = deviceId) }
    }

    private fun startBleScan() {
        cancelScan()
        val target = _uiState.value.targetMacAddress
            ?.let { normalizeMac(it) }
            ?.takeIf { it.length >= MIN_MAC_HEX }

        _uiState.update {
            it.copy(
                bleScanPhase = BleScanPhase.Scanning,
                discoveredBleDevices = emptyList(),
                scannedDevices = emptyList(),
                matchedDevice = null,
                selectedDeviceId = null,
                bleErrorMessage = null,
            )
        }

        scanJob = viewModelScope.launch {
            val found = LinkedHashMap<String, DiscoveredBleDevice>()
            var matched: ScannedShellyDevice? = null
            try {
                withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                    bleScanner.scanAll().collect { advertisement ->
                        val device = upsertDiscoveredDevice(
                            found = found,
                            advertisement = advertisement,
                            targetMac = target,
                        )
                        found[device.id] = device

                        _uiState.update { state ->
                            state.copy(
                                discoveredBleDevices = found.values
                                    .sortedByDescending { it.rssi }
                                    .toList(),
                            )
                        }

                        if (target != null && device.isMatched && matched == null) {
                            matched = device.toScannedShellyDevice()
                            _uiState.update {
                                it.copy(
                                    bleScanPhase = BleScanPhase.Matched,
                                    matchedDevice = matched,
                                    scannedDevices = listOf(matched!!),
                                )
                            }
                            currentCoroutineContext()[Job]?.cancel()
                        }
                    }
                }

                val finalState = _uiState.value
                if (finalState.bleScanPhase == BleScanPhase.Matched) return@launch

                when {
                    target != null -> _uiState.update { it.copy(bleScanPhase = BleScanPhase.NotFound) }
                    found.isNotEmpty() -> _uiState.update {
                        it.copy(
                            bleScanPhase = BleScanPhase.DeviceList,
                            scannedDevices = found.values
                                .map { device -> device.toScannedShellyDevice() }
                                .sortedByDescending { device -> device.rssi },
                        )
                    }
                    else -> _uiState.update { it.copy(bleScanPhase = BleScanPhase.Empty) }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _uiState.update {
                    it.copy(
                        bleScanPhase = BleScanPhase.Error,
                        bleErrorMessage = "No pudimos completar la búsqueda Bluetooth. Intentá de nuevo.",
                    )
                }
            }
        }
    }

    private fun upsertDiscoveredDevice(
        found: LinkedHashMap<String, DiscoveredBleDevice>,
        advertisement: BleAdvertisement,
        targetMac: String?,
    ): DiscoveredBleDevice {
        val bleMac = formatMac(advertisement.address)
        val displayName = advertisement.advertisedName ?: bleMac
        val isMatched = targetMac != null && macMatchesAdvertisement(advertisement, targetMac)
        val previous = found[advertisement.address]

        return DiscoveredBleDevice(
            id = advertisement.address,
            displayName = displayName,
            macAddress = bleMac,
            rssi = advertisement.rssi,
            shellyMacAddress = advertisement.shellyMacAddress,
            isMatched = isMatched || (previous?.isMatched == true),
        )
    }

    private fun macMatchesAdvertisement(advertisement: BleAdvertisement, target: String): Boolean {
        if (normalizeMac(advertisement.address) == target) return true
        advertisement.shellyMacAddress?.let { if (normalizeMac(it) == target) return true }
        return false
    }

    private fun DiscoveredBleDevice.toScannedShellyDevice(): ScannedShellyDevice {
        val mac = shellyMacAddress ?: macAddress
        val name = if (displayName != macAddress && !displayName.contains(':')) {
            displayName
        } else {
            buildShellyDisplayName(mac)
        }
        return ScannedShellyDevice(
            id = id,
            name = name,
            macAddress = mac,
            rssi = rssi,
        )
    }

    private fun updateBlePhase(phase: BleScanPhase, message: String? = null) {
        cancelScan()
        _uiState.update {
            it.copy(
                bleScanPhase = phase,
                bleErrorMessage = message,
                discoveredBleDevices = emptyList(),
                scannedDevices = emptyList(),
                matchedDevice = null,
            )
        }
    }

    private fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    private fun cancelWifiScan() {
        wifiScanJob?.cancel()
        wifiScanJob = null
    }

    override fun onCleared() {
        cancelScan()
        cancelWifiScan()
        super.onCleared()
    }

    private fun cancelLookup() {
        lookupJob?.cancel()
        lookupJob = null
        activeLookupSuffix = null
    }

    private fun shouldApplyLookupResult(suffix: String): Boolean {
        val state = _uiState.value
        return activeLookupSuffix == suffix &&
            state.identificationMode == IdentificationMode.WithCode &&
            state.deviceCodeInput == suffix
    }

    private companion object {
        private const val STATUS_AVAILABLE = "available"
        private const val STATUS_ASSIGNED = "assigned"
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val MIN_MAC_HEX = 6
    }
}
