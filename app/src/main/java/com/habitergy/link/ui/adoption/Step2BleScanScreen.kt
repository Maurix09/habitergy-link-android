package com.habitergy.link.ui.adoption

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habitergy.link.data.RuntimePermissions
import com.habitergy.link.data.ble.BlePermissions
import com.habitergy.link.data.ble.formatMac
import com.habitergy.link.data.findActivity
import com.habitergy.link.domain.model.AdoptionUiState
import com.habitergy.link.domain.model.BleScanPhase
import com.habitergy.link.ui.components.AdoptionScreenScaffold
import com.habitergy.link.ui.components.BleDiscoveryList
import com.habitergy.link.ui.components.HabitergyPrimaryButton
import com.habitergy.link.ui.components.HabitergySecondaryButton
import com.habitergy.link.ui.components.ScreenTitle
import com.habitergy.link.ui.components.ShellyDeviceCard
import com.habitergy.link.ui.theme.HabitergyColors

/**
 * Paso 2 — Conectá por Bluetooth.
 *
 * Mientras escanea, muestra en vivo todos los dispositivos BLE (hagan o no
 * match). Cuando aparece el que coincide con la MAC del paso 1, lo resalta.
 */
@Composable
fun Step2BleScanScreen(
    state: AdoptionUiState,
    onCheckReadiness: () -> Unit,
    onRetry: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var permissionRequestedOnce by rememberSaveable { mutableStateOf(false) }
    var openSettingsInstead by remember {
        mutableStateOf(
            activity != null &&
                RuntimePermissions.shouldOpenAppSettings(
                    activity = activity,
                    permissions = BlePermissions.required,
                    alreadyRequested = permissionRequestedOnce,
                ),
        )
    }

    fun refreshPermissionStrategy() {
        openSettingsInstead = activity != null &&
            RuntimePermissions.shouldOpenAppSettings(
                activity = activity,
                permissions = BlePermissions.required,
                alreadyRequested = permissionRequestedOnce,
            )
    }

    val appSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { onCheckReadiness() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionRequestedOnce = true
        refreshPermissionStrategy()
        onCheckReadiness()
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { onCheckReadiness() }

    val enableLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { onCheckReadiness() }

    fun requestBlePermissionOrOpenSettings() {
        refreshPermissionStrategy()
        if (openSettingsInstead) {
            appSettingsLauncher.launch(RuntimePermissions.appDetailsSettingsIntent(context))
        } else {
            val missing = BlePermissions.missing(context)
            if (missing.isEmpty()) {
                onCheckReadiness()
            } else {
                permissionLauncher.launch(missing)
            }
        }
    }

    LaunchedEffect(Unit) {
        // Al volver del paso 3 conservamos Matched/DeviceList; no re-escanear.
        if (state.bleScanPhase == BleScanPhase.Idle) {
            onCheckReadiness()
        }
    }

    val showLiveDiscovery = state.bleScanPhase in setOf(
        BleScanPhase.Scanning,
        BleScanPhase.Matched,
        BleScanPhase.NotFound,
        BleScanPhase.Empty,
        BleScanPhase.Error,
    )
    val targetMacLabel = state.targetMacAddress?.let { formatMac(it) }

    AdoptionScreenScaffold(
        currentStep = state.currentStep,
        totalSteps = state.totalSteps,
        onBack = onBack,
        content = {
            ScreenTitle(
                title = "Conectá por Bluetooth",
                subtitle = when (state.bleScanPhase) {
                    BleScanPhase.Scanning ->
                        "Buscando dispositivos… Mantené el botón del controlador 5 s si no aparece."
                    BleScanPhase.Matched ->
                        "Encontramos tu controlador. Verificá que coincida con la MAC registrada."
                    else ->
                        "Vamos a buscar tu controlador Shelly por Bluetooth. " +
                            "Acercá el teléfono al controlador y mantenelo encendido."
                },
            )

            when (state.bleScanPhase) {
                BleScanPhase.PermissionRequired -> BleStatus(
                    icon = Icons.Default.LocationOn,
                    message = if (openSettingsInstead) {
                        "El permiso de Bluetooth está bloqueado. Abrí los ajustes de la app " +
                            "y habilitalo para buscar el controlador."
                    } else {
                        "Necesitamos permiso de Bluetooth para buscar el controlador cercano."
                    },
                    action = {
                        HabitergyPrimaryButton(
                            label = if (openSettingsInstead) "Abrir ajustes" else "Otorgar permisos",
                            showArrow = false,
                            onClick = ::requestBlePermissionOrOpenSettings,
                        )
                    },
                )

                BleScanPhase.BluetoothOff -> BleStatus(
                    icon = Icons.Default.BluetoothDisabled,
                    message = "El Bluetooth está apagado. Encendelo para buscar el controlador.",
                    action = {
                        HabitergyPrimaryButton(
                            label = "Encender Bluetooth",
                            showArrow = false,
                            onClick = {
                                enableBluetoothLauncher.launch(
                                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                                )
                            },
                        )
                    },
                )

                BleScanPhase.LocationOff -> BleStatus(
                    icon = Icons.Default.LocationOff,
                    message = "La ubicación del teléfono está apagada. Activala para que " +
                        "podamos detectar dispositivos Bluetooth cercanos.",
                    action = {
                        HabitergyPrimaryButton(
                            label = "Activar ubicación",
                            showArrow = false,
                            onClick = {
                                enableLocationLauncher.launch(
                                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                                )
                            },
                        )
                    },
                )

                BleScanPhase.Scanning -> BleScanningIndicator(
                    deviceCount = state.discoveredBleDevices.size,
                    targetMac = targetMacLabel,
                )

                BleScanPhase.Matched -> MatchedIndicator(targetMac = targetMacLabel)

                BleScanPhase.DeviceList -> DeviceList(
                    state = state,
                    onSelectDevice = onSelectDevice,
                )

                BleScanPhase.NotFound -> BleStatus(
                    icon = Icons.Default.SearchOff,
                    message = buildString {
                        append(
                            "No encontramos el controlador por Bluetooth. Verificá que esté " +
                                "encendido y cerca del teléfono, y volvé a intentar.",
                        )
                        if (targetMacLabel != null) {
                            append("\n\nMAC buscada: $targetMacLabel")
                        }
                    },
                )

                BleScanPhase.Empty -> BleStatus(
                    icon = Icons.Default.SearchOff,
                    message = if (state.discoveredBleDevices.isEmpty()) {
                        "No detectamos ningún dispositivo Bluetooth cerca. Acercá el " +
                            "teléfono y volvé a intentar."
                    } else {
                        "Detectamos dispositivos Bluetooth, pero ninguno coincide con tu controlador."
                    },
                )

                BleScanPhase.Error -> BleStatus(
                    icon = Icons.Default.ErrorOutline,
                    message = state.bleErrorMessage
                        ?: "Ocurrió un problema con el Bluetooth. Intentá de nuevo.",
                    tint = HabitergyColors.Error,
                )

                BleScanPhase.Idle -> Unit
            }

            if (showLiveDiscovery) {
                BleDiscoveryList(
                    devices = state.discoveredBleDevices,
                    modifier = Modifier.padding(top = 20.dp),
                    header = when (state.bleScanPhase) {
                        BleScanPhase.Matched -> "Controlador identificado"
                        BleScanPhase.Scanning -> "Dispositivos Bluetooth detectados"
                        else -> "Dispositivos detectados en el último escaneo"
                    },
                    emptyMessage = when (state.bleScanPhase) {
                        BleScanPhase.Scanning ->
                            "Todavía no detectamos nada. Acercá el teléfono al controlador…"
                        else -> null
                    },
                )
            }
        },
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state.bleScanPhase) {
                    BleScanPhase.Scanning -> {
                        HabitergySecondaryButton(
                            label = "Buscar de nuevo",
                            onClick = onRetry,
                        )
                    }

                    BleScanPhase.NotFound, BleScanPhase.Empty, BleScanPhase.Error -> {
                        HabitergyPrimaryButton(
                            label = "Buscar de nuevo",
                            showArrow = false,
                            onClick = onRetry,
                        )
                    }

                    else -> Unit
                }

                HabitergyPrimaryButton(
                    label = "Siguiente",
                    enabled = state.canProceedFromStep2,
                    onClick = onNext,
                )
            }
        },
    )
}

@Composable
private fun DeviceList(
    state: AdoptionUiState,
    onSelectDevice: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Elegí tu controlador de la lista:",
            style = MaterialTheme.typography.bodyMedium,
            color = HabitergyColors.TextSecondary,
        )
        state.scannedDevices.forEach { device ->
            ShellyDeviceCard(
                device = device,
                selected = state.selectedDeviceId == device.id,
                onSelect = { onSelectDevice(device.id) },
            )
        }
    }
}

@Composable
private fun BleScanningIndicator(
    deviceCount: Int,
    targetMac: String?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                contentDescription = null,
                tint = HabitergyColors.Primary,
                modifier = Modifier.size(32.dp),
            )
            CircularProgressIndicator(
                color = HabitergyColors.Primary,
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
            )
        }
        Text(
            text = if (deviceCount == 0) {
                "Buscando dispositivos Bluetooth…"
            } else {
                "Detectados: $deviceCount · seguimos buscando…"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = HabitergyColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (targetMac != null) {
            Text(
                text = "Buscando MAC: $targetMac",
                style = MaterialTheme.typography.labelSmall,
                color = HabitergyColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MatchedIndicator(targetMac: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = HabitergyColors.Primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "Controlador encontrado",
            style = MaterialTheme.typography.titleLarge,
            color = HabitergyColors.Primary,
            textAlign = TextAlign.Center,
        )
        if (targetMac != null) {
            Text(
                text = targetMac,
                style = MaterialTheme.typography.bodyMedium,
                color = HabitergyColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BleStatus(
    icon: ImageVector,
    message: String,
    tint: androidx.compose.ui.graphics.Color = HabitergyColors.Secondary,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = HabitergyColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        action?.let {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { it() }
        }
    }
}
