package com.habitergy.link.domain.model

/**
 * Longitud del sufijo que tipea el usuario (cuerpo de 4 + checksum de 1).
 * El prefijo "SH-" es fijo en la UI y no se cuenta aquí.
 */
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

/**
 * Estado del lookup del device_code en el paso 1. Implica color y mensaje:
 * Available → verde; el resto → rojo; Looking → spinner.
 */
enum class DeviceLookupState {
    Idle,
    Invalid,
    Looking,
    Available,
    Assigned,
    Unavailable,
    NotFound,
    NetworkError,
}

/** Dispositivo resuelto desde la API a partir del deviceCode. */
data class ResolvedDevice(
    val deviceCode: String,
    val macAddress: String,
    val model: String,
)

/** Dispositivo BLE crudo detectado durante el escaneo (sin filtrar por Shelly). */
data class DiscoveredBleDevice(
    val id: String,
    /** Nombre anunciado o la MAC si el dispositivo no tiene nombre. */
    val displayName: String,
    val macAddress: String,
    val rssi: Int,
    /** MAC WiFi extraída del manufacturer data Shelly, si está presente. */
    val shellyMacAddress: String? = null,
    val isMatched: Boolean = false,
)

/** Controlador Shelly detectado por escaneo BLE. */
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

/** Fases del paso 2 (escaneo BLE). */
enum class BleScanPhase {
    /** Estado inicial al entrar al paso; la UI dispara la verificación. */
    Idle,

    /** Faltan permisos de runtime para escanear. */
    PermissionRequired,

    /** El adaptador Bluetooth está apagado. */
    BluetoothOff,

    /** Escaneando controladores. */
    Scanning,

    /** Se encontró el controlador cuya MAC coincide con la del paso 1. */
    Matched,

    /** Sin código: se muestra la lista de Shelly cercanos para elegir. */
    DeviceList,

    /** Con código: terminó el escaneo sin encontrar la MAC objetivo. */
    NotFound,

    /** Sin código: terminó el escaneo sin ningún Shelly cercano. */
    Empty,

    /** Error de Bluetooth (no soportado, permiso denegado, fallo del escáner). */
    Error,
}

data class AdoptionUiState(
    val currentStep: Int = 1,
    val totalSteps: Int = 6,
    // Paso 1
    val deviceCodeInput: String = "",
    val identificationMode: IdentificationMode = IdentificationMode.WithCode,
    val resolvedDevice: ResolvedDevice? = null,
    val lookupState: DeviceLookupState = DeviceLookupState.Idle,
    // Paso 2
    val bleScanPhase: BleScanPhase = BleScanPhase.Idle,
    val discoveredBleDevices: List<DiscoveredBleDevice> = emptyList(),
    val scannedDevices: List<ScannedShellyDevice> = emptyList(),
    val matchedDevice: ScannedShellyDevice? = null,
    val selectedDeviceId: String? = null,
    val bleErrorMessage: String? = null,
) {
    val isUnknownDeviceCode: Boolean
        get() = deviceCodeInput == UNKNOWN_DEVICE_CODE

    val isLookingUp: Boolean
        get() = lookupState == DeviceLookupState.Looking

    val canProceedFromStep1: Boolean
        get() = lookupState == DeviceLookupState.Available

    val targetMacAddress: String?
        get() = if (isUnknownDeviceCode) null else resolvedDevice?.macAddress

    val selectedDevice: ScannedShellyDevice?
        get() = matchedDevice
            ?: scannedDevices.find { it.id == selectedDeviceId }
}
