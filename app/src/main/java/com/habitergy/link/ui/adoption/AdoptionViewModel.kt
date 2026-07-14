package com.habitergy.link.ui.adoption

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitergy.link.data.AdoptionLookupResult
import com.habitergy.link.data.AdoptionRepository
import com.habitergy.link.domain.DeviceCode
import com.habitergy.link.domain.model.AdoptionUiState
import com.habitergy.link.domain.model.BleScanPhase
import com.habitergy.link.domain.model.DEVICE_CODE_LENGTH
import com.habitergy.link.domain.model.DeviceLookupState
import com.habitergy.link.domain.model.IdentificationMode
import com.habitergy.link.domain.model.ResolvedDevice
import com.habitergy.link.domain.model.UNKNOWN_DEVICE_CODE
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AdoptionViewModel(
    private val repository: AdoptionRepository = AdoptionRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdoptionUiState())
    val uiState: StateFlow<AdoptionUiState> = _uiState.asStateFlow()
    private var lookupJob: Job? = null
    private var activeLookupSuffix: String? = null

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
        _uiState.update {
            it.copy(
                currentStep = 2,
                bleScanPhase = BleScanPhase.NotImplemented,
                scannedDevices = emptyList(),
                matchedDevice = null,
                selectedDeviceId = null,
                bleErrorMessage = null,
            )
        }
    }

    fun goBackToStep1() {
        cancelLookup()
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
        // BLE real pendiente — sin acción por ahora.
    }

    fun selectDevice(deviceId: String) {
        _uiState.update { it.copy(selectedDeviceId = deviceId) }
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
    }
}
