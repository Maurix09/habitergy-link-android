package com.habitergy.link.ui.adoption

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habitergy.link.data.AdoptionLookupResult
import com.habitergy.link.data.AdoptionRepository
import com.habitergy.link.data.ble.BlePermissions
import com.habitergy.link.data.ble.ShellyBleScanner
import com.habitergy.link.data.ble.normalizeMac
import com.habitergy.link.domain.DeviceCode
import com.habitergy.link.domain.model.AdoptionUiState
import com.habitergy.link.domain.model.BleScanPhase
import com.habitergy.link.domain.model.DEVICE_CODE_LENGTH
import com.habitergy.link.domain.model.DeviceLookupState
import com.habitergy.link.domain.model.IdentificationMode
import com.habitergy.link.domain.model.ResolvedDevice
import com.habitergy.link.domain.model.ScannedShellyDevice
import com.habitergy.link.domain.model.UNKNOWN_DEVICE_CODE
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AdoptionViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val repository: AdoptionRepository = AdoptionRepository()
    private val bleScanner: ShellyBleScanner = ShellyBleScanner(application)

    private val _uiState = MutableStateFlow(AdoptionUiState())
    val uiState: StateFlow<AdoptionUiState> = _uiState.asStateFlow()
    private var lookupJob: Job? = null
    private var activeLookupSuffix: String? = null
    private var scanJob: Job? = null

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
        _uiState.update {
            it.copy(
                currentStep = 2,
                bleScanPhase = BleScanPhase.Idle,
                scannedDevices = emptyList(),
                matchedDevice = null,
                selectedDeviceId = null,
                bleErrorMessage = null,
            )
        }
    }

    fun goBackToStep1() {
        cancelLookup()
        cancelScan()
        _uiState.update {
            it.copy(
                currentStep = 1,
                bleScanPhase = BleScanPhase.Idle,
                scannedDevices = emptyList(),
                matchedDevice = null,
                selectedDeviceId = null,
                bleErrorMessage = null,
                deviceCodeInput = if (it.isUnknownDeviceCode) "" else it.deviceCodeInput,
                identificationMode = IdentificationMode.WithCode,
            )
        }
    }

    /**
     * Verifica las precondiciones del escaneo BLE (soporte, permisos, adaptador
     * encendido) y, si todo está listo, arranca el escaneo. La UI la llama al
     * entrar al paso 2 y tras conceder permisos o encender el Bluetooth.
     */
    fun refreshBleReadiness() {
        when {
            !bleScanner.isSupported() -> updateBlePhase(
                BleScanPhase.Error,
                "Este dispositivo no es compatible con Bluetooth LE.",
            )
            !BlePermissions.allGranted(getApplication()) ->
                updateBlePhase(BleScanPhase.PermissionRequired)
            !bleScanner.isEnabled() ->
                updateBlePhase(BleScanPhase.BluetoothOff)
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
                scannedDevices = emptyList(),
                matchedDevice = null,
                selectedDeviceId = null,
                bleErrorMessage = null,
            )
        }

        scanJob = viewModelScope.launch {
            val found = LinkedHashMap<String, ScannedShellyDevice>()
            try {
                val matched = withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                    bleScanner.scan()
                        .onEach { device ->
                            found[device.id] = device
                            if (target == null) {
                                _uiState.update { state ->
                                    state.copy(scannedDevices = found.values.sortedByDescending { it.rssi })
                                }
                            }
                        }
                        .firstOrNull { device -> target != null && macMatches(device, target) }
                }

                when {
                    target != null && matched != null -> _uiState.update {
                        it.copy(
                            bleScanPhase = BleScanPhase.Matched,
                            matchedDevice = matched,
                            scannedDevices = listOf(matched),
                        )
                    }
                    target != null -> _uiState.update { it.copy(bleScanPhase = BleScanPhase.NotFound) }
                    found.isNotEmpty() -> _uiState.update {
                        it.copy(
                            bleScanPhase = BleScanPhase.DeviceList,
                            scannedDevices = found.values.sortedByDescending { device -> device.rssi },
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

    private fun macMatches(device: ScannedShellyDevice, target: String): Boolean =
        normalizeMac(device.macAddress) == target || normalizeMac(device.id) == target

    private fun updateBlePhase(phase: BleScanPhase, message: String? = null) {
        cancelScan()
        _uiState.update {
            it.copy(
                bleScanPhase = phase,
                bleErrorMessage = message,
                scannedDevices = emptyList(),
                matchedDevice = null,
            )
        }
    }

    private fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    override fun onCleared() {
        cancelScan()
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
