package com.habitergy.link.ui.adoption

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habitergy.link.domain.model.AdoptionUiState
import com.habitergy.link.domain.model.BleScanPhase
import com.habitergy.link.ui.components.AdoptionScreenScaffold
import com.habitergy.link.ui.components.ControllerFoundBanner
import com.habitergy.link.ui.components.HabitergyPrimaryButton
import com.habitergy.link.ui.components.HabitergySecondaryButton
import com.habitergy.link.ui.components.ScreenTitle
import com.habitergy.link.ui.components.ShellyDeviceCard
import com.habitergy.link.ui.theme.HabitergyColors

@Composable
fun Step2BleScanScreen(
    state: AdoptionUiState,
    onSelectDevice: (String) -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    var showStep3Placeholder by remember { mutableStateOf(false) }

    AdoptionScreenScaffold(
        currentStep = state.currentStep,
        totalSteps = state.totalSteps,
        onBack = onBack,
        content = {
            val subtitle = when {
                state.isUnknownDeviceCode ->
                    "Elegí cuál Shelly cercano querés adoptar."
                else ->
                    "Buscamos tu controlador por Bluetooth usando la MAC registrada."
            }

            ScreenTitle(
                title = "Conectá por Bluetooth",
                subtitle = subtitle,
            )

            when (state.bleScanPhase) {
                BleScanPhase.Idle, BleScanPhase.Scanning -> ScanningContent()
                BleScanPhase.Matched -> {
                    state.matchedDevice?.let { device ->
                        ControllerFoundBanner(device = device)
                    }
                }
                BleScanPhase.SelectDevice -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Detectamos varios controladores Shelly cerca:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        state.scannedDevices.forEachIndexed { index, device ->
                            ShellyDeviceCard(
                                device = device,
                                selected = state.selectedDeviceId == device.id,
                                onSelect = { onSelectDevice(device.id) },
                                showDivider = index < state.scannedDevices.lastIndex,
                            )
                        }
                    }
                }
                BleScanPhase.Empty -> EmptyContent()
                BleScanPhase.Error -> ErrorContent(message = state.bleErrorMessage)
            }
        },
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state.bleScanPhase) {
                    BleScanPhase.Matched -> {
                        HabitergyPrimaryButton(
                            label = "Continuar",
                            onClick = {
                                showStep3Placeholder = true
                                onContinue()
                            },
                        )
                    }
                    BleScanPhase.SelectDevice -> {
                        HabitergyPrimaryButton(
                            label = "Continuar",
                            onClick = {
                                showStep3Placeholder = true
                                onContinue()
                            },
                            enabled = state.selectedDeviceId != null,
                        )
                        HabitergySecondaryButton(
                            label = "Volver a buscar",
                            onClick = onRetry,
                        )
                    }
                    BleScanPhase.Empty, BleScanPhase.Error -> {
                        HabitergySecondaryButton(
                            label = "Volver a buscar",
                            onClick = onRetry,
                        )
                    }
                    else -> Unit
                }
            }
        },
    )

    if (showStep3Placeholder) {
        AlertDialog(
            onDismissRequest = { showStep3Placeholder = false },
            title = { Text("Paso 3") },
            text = {
                Text(
                    "El provisioning WiFi se implementará en la próxima iteración. " +
                        "Dispositivo seleccionado: ${state.selectedDevice?.name ?: "—"}",
                )
            },
            confirmButton = {
                TextButton(onClick = { showStep3Placeholder = false }) {
                    Text("Entendido", color = HabitergyColors.Primary)
                }
            },
        )
    }
}

@Composable
private fun ScanningContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = HabitergyColors.Primary,
            )
            Text(
                text = "Escaneando controladores Shelly…",
                style = MaterialTheme.typography.bodyLarge,
                color = HabitergyColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyContent() {
    Text(
        text = "No detectamos controladores Shelly cerca. Verificá que esté encendido " +
            "y que el Bluetooth de tu dispositivo esté activo.",
        style = MaterialTheme.typography.bodyMedium,
        color = HabitergyColors.TextSecondary,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorContent(message: String?) {
    Text(
        text = message ?: "No pudimos completar el escaneo Bluetooth.",
        style = MaterialTheme.typography.bodyMedium,
        color = HabitergyColors.Error,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}
