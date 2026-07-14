package com.habitergy.link.ui.adoption

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * Muestra en vivo todos los dispositivos BLE que detecta el celular. Cuando
 * aparece el que coincide con la MAC del paso 1, lo resalta con un tilde.
 */
@Composable
fun Step2BleScanScreen(
    state: AdoptionUiState,
    onCheckReadiness: () -> Unit,
    onRetry: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onBack: () -> Unit,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onCheckReadiness() }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { onCheckReadiness() }

    LaunchedEffect(Unit) { onCheckReadiness() }

    val showDiscoveryList = state.discoveredBleDevices.isNotEmpty() &&
        state.bleScanPhase in setOf(
            BleScanPhase.Scanning,
            BleScanPhase.Matched,
            BleScanPhase.NotFound,
            BleScanPhase.Empty,
            BleScanPhase.Error,
        )

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
                    message = "Necesitamos permiso de Bluetooth para buscar el controlador cercano.",
                    action = {
                        HabitergyPrimaryButton(
                            label = "Otorgar permisos",
                            showArrow = false,
                            onClick = { permissionLauncher.launch(bleRequiredPermissions()) },
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

                BleScanPhase.Scanning -> BleScanningIndicator()

                BleScanPhase.Matched -> MatchedIndicator()

                BleScanPhase.DeviceList -> DeviceList(
                    state = state,
                    onSelectDevice = onSelectDevice,
                )

                BleScanPhase.NotFound -> BleStatus(
                    icon = Icons.Default.SearchOff,
                    message = "No encontramos el controlador por Bluetooth. Verificá que esté " +
                        "encendido y cerca del teléfono, y volvé a intentar.",
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

            if (showDiscoveryList) {
                BleDiscoveryList(
                    devices = state.discoveredBleDevices,
                    modifier = Modifier.padding(top = 20.dp),
                    header = when (state.bleScanPhase) {
                        BleScanPhase.Matched -> "Controlador identificado"
                        BleScanPhase.Scanning -> "Dispositivos Bluetooth detectados"
                        else -> "Dispositivos detectados en el último escaneo"
                    },
                )
            }
        },
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state.bleScanPhase) {
                    BleScanPhase.Matched, BleScanPhase.DeviceList -> {
                        HabitergyPrimaryButton(
                            label = "Siguiente (próximamente)",
                            enabled = false,
                            onClick = {},
                        )
                        HabitergySecondaryButton(label = "Volver", onClick = onBack)
                    }

                    BleScanPhase.Scanning -> {
                        HabitergySecondaryButton(
                            label = "Buscar de nuevo",
                            onClick = onRetry,
                        )
                        HabitergySecondaryButton(label = "Volver", onClick = onBack)
                    }

                    BleScanPhase.NotFound, BleScanPhase.Empty, BleScanPhase.Error -> {
                        HabitergyPrimaryButton(
                            label = "Buscar de nuevo",
                            showArrow = false,
                            onClick = onRetry,
                        )
                        HabitergySecondaryButton(label = "Volver", onClick = onBack)
                    }

                    else -> HabitergySecondaryButton(label = "Volver", onClick = onBack)
                }
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
private fun BleScanningIndicator() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
            contentDescription = null,
            tint = HabitergyColors.Primary,
            modifier = Modifier.size(48.dp),
        )
        CircularProgressIndicator(color = HabitergyColors.Primary)
        Text(
            text = "Buscando dispositivos Bluetooth…",
            style = MaterialTheme.typography.bodyMedium,
            color = HabitergyColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MatchedIndicator() {
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

private fun bleRequiredPermissions(): Array<String> =
    com.habitergy.link.data.ble.BlePermissions.required
