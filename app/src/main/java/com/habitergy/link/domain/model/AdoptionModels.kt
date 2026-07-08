package com.habitergy.link.domain.model

/** Longitud fija del código de instalación impreso en el kit. */
const val DEVICE_CODE_LENGTH = 5

/** Código reservado cuando el partner adopta sin código conocido. */
const val UNKNOWN_DEVICE_CODE = "00000"

/** Modo de identificación elegido en el paso 1. */
enum class IdentificationMode {
    /** Partner ingresó o escaneó un deviceCode. */
    WithCode,

    /** Partner adopta sin código (deviceCodeInput = [UNKNOWN_DEVICE_CODE]). */
    NoCode,
}

/** Dispositivo resuelto desde la API (mock) a partir del deviceCode. */
data class ResolvedDevice(
    val deviceCode: String,
    val macAddress: String,
    val model: String,
)

/** Controlador Shelly detectado por escaneo BLE (mock o real). */
data class ScannedShellyDevice(
    val id: String,
    val name: String,
    val macAddress: String,
    val rssi: Int,
) {
    val signalLabel: String
        get() = when {
            rssi >= -60 -> "Excelente"
            rssi >= -75 -> "Buena"
            else -> "Débil"
        }
}

enum class BleScanPhase {
    Idle,
    Scanning,
    Matched,
    SelectDevice,
    Empty,
    Error,
}

data class AdoptionUiState(
    val currentStep: Int = 1,
    val totalSteps: Int = 6,
    // Paso 1
    val deviceCodeInput: String = "",
    val identificationMode: IdentificationMode = IdentificationMode.WithCode,
    val resolvedDevice: ResolvedDevice? = null,
    val lookupError: String? = null,
    val isLookingUp: Boolean = false,
    // Paso 2
    val bleScanPhase: BleScanPhase = BleScanPhase.Idle,
    val scannedDevices: List<ScannedShellyDevice> = emptyList(),
    val matchedDevice: ScannedShellyDevice? = null,
    val selectedDeviceId: String? = null,
    val bleErrorMessage: String? = null,
) {
    val isUnknownDeviceCode: Boolean
        get() = deviceCodeInput == UNKNOWN_DEVICE_CODE

    val canProceedFromStep1: Boolean
        get() = resolvedDevice != null && lookupError == null && !isLookingUp

    val targetMacAddress: String?
        get() = if (isUnknownDeviceCode) null else resolvedDevice?.macAddress

    val selectedDevice: ScannedShellyDevice?
        get() = matchedDevice
            ?: scannedDevices.find { it.id == selectedDeviceId }
}
