package com.habitergy.link.ui.adoption

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitergy.link.data.mock.MockAdoptionData
import com.habitergy.link.domain.model.AdoptionUiState
import com.habitergy.link.domain.model.BleScanPhase
import com.habitergy.link.domain.model.DEVICE_CODE_LENGTH
import com.habitergy.link.domain.model.IdentificationMode
import com.habitergy.link.domain.model.UNKNOWN_DEVICE_CODE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AdoptionViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AdoptionUiState())
    val uiState: StateFlow<AdoptionUiState> = _uiState.asStateFlow()

    fun onDeviceCodeChange(value: String) {
        val sanitized = value
            .uppercase()
            .filter { it.isLetterOrDigit() }
            .take(DEVICE_CODE_LENGTH)

        _uiState.update {
            it.copy(
                deviceCodeInput = sanitized,
                identificationMode = IdentificationMode.WithCode,
                resolvedDevice = null,
                lookupError = null,
            )
        }

        if (sanitized.length == DEVICE_CODE_LENGTH) {
            lookupDeviceCode()
        }
    }

    fun onScanQrClick() {
        // Placeholder hasta integrar CameraX + lector QR.
    }

    fun proceedWithoutKnownCode() {
        _uiState.update {
            it.copy(
                deviceCodeInput = UNKNOWN_DEVICE_CODE,
                identificationMode = IdentificationMode.NoCode,
                resolvedDevice = null,
                lookupError = null,
                isLookingUp = false,
            )
        }
        navigateToStep2()
    }

    fun lookupDeviceCode() {
        val code = _uiState.value.deviceCodeInput
        if (code.length < DEVICE_CODE_LENGTH) {
            return
        }

        if (code == UNKNOWN_DEVICE_CODE) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLookingUp = true, lookupError = null) }
            MockAdoptionData.lookupDeviceCode(code)
                .onSuccess { device ->
                    _uiState.update {
                        it.copy(
                            isLookingUp = false,
                            resolvedDevice = device,
                            lookupError = null,
                            identificationMode = IdentificationMode.WithCode,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLookingUp = false,
                            resolvedDevice = null,
                            lookupError = error.message,
                        )
                    }
                }
        }
    }

    fun proceedToStep2() {
        val state = _uiState.value
        if (state.isLookingUp || state.resolvedDevice == null) return
        navigateToStep2()
    }

    private fun navigateToStep2() {
        _uiState.update {
            it.copy(
                currentStep = 2,
                bleScanPhase = BleScanPhase.Scanning,
                scannedDevices = emptyList(),
                matchedDevice = null,
                selectedDeviceId = null,
                bleErrorMessage = null,
            )
        }
        startBleScan()
    }

    fun goBackToStep1() {
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

    fun retryBleScan() {
        _uiState.update {
            it.copy(
                bleScanPhase = BleScanPhase.Scanning,
                scannedDevices = emptyList(),
                matchedDevice = null,
                selectedDeviceId = null,
                bleErrorMessage = null,
            )
        }
        startBleScan()
    }

    fun selectDevice(deviceId: String) {
        _uiState.update { it.copy(selectedDeviceId = deviceId) }
    }

    private fun startBleScan() {
        viewModelScope.launch {
            try {
                val devices = MockAdoptionData.scanBleDevices()
                val targetMac = _uiState.value.targetMacAddress

                if (targetMac != null) {
                    val match = devices.find { MockAdoptionData.macsMatch(it.macAddress, targetMac) }
                    if (match != null) {
                        _uiState.update {
                            it.copy(
                                bleScanPhase = BleScanPhase.Matched,
                                matchedDevice = match,
                                scannedDevices = devices,
                                selectedDeviceId = match.id,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                bleScanPhase = BleScanPhase.Error,
                                scannedDevices = devices,
                                bleErrorMessage =
                                    "No encontramos el controlador $targetMac cerca. " +
                                    "Verificá que esté encendido y en modo de configuración.",
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            bleScanPhase = if (devices.isEmpty()) {
                                BleScanPhase.Empty
                            } else {
                                BleScanPhase.SelectDevice
                            },
                            scannedDevices = devices,
                        )
                    }
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        bleScanPhase = BleScanPhase.Error,
                        bleErrorMessage = "No pudimos completar el escaneo Bluetooth. Intentá de nuevo.",
                    )
                }
            }
        }
    }
}
