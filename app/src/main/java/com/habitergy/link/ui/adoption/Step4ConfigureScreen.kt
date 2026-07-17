package com.habitergy.link.ui.adoption

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import com.habitergy.link.domain.model.ProvisionPhase
import com.habitergy.link.domain.model.ShellyProvisionStep
import com.habitergy.link.ui.components.AdoptionScreenScaffold
import com.habitergy.link.ui.components.HabitergyPrimaryButton
import com.habitergy.link.ui.components.HabitergySecondaryButton
import com.habitergy.link.ui.components.ScreenTitle
import com.habitergy.link.ui.theme.HabitergyColors

@Composable
fun Step4ConfigureScreen(
    state: AdoptionUiState,
    onStartProvisioning: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        onStartProvisioning()
    }

    val subtitle = when (state.provisionPhase) {
        ProvisionPhase.Idle,
        ProvisionPhase.ProvisioningBroker,
        -> "Registramos el controlador en el broker y le enviamos la configuración por Bluetooth."
        ProvisionPhase.ConnectingBle -> "Conectando al controlador por Bluetooth…"
        ProvisionPhase.ConfiguringShelly -> "Aplicando WiFi, MQTT y seguridad en el Shelly…"
        ProvisionPhase.Rebooting -> "Reiniciando el controlador para que se conecte a internet…"
        ProvisionPhase.Done -> "Configuración completada."
        ProvisionPhase.Error -> state.provisionErrorMessage
            ?: "No pudimos completar la configuración."
    }

    AdoptionScreenScaffold(
        currentStep = state.currentStep,
        totalSteps = state.totalSteps,
        onBack = onBack,
        content = {
            ScreenTitle(
                title = "Configurá el controlador",
                subtitle = subtitle,
            )

            when (state.provisionPhase) {
                ProvisionPhase.Error -> ErrorBanner(message = subtitle)
                ProvisionPhase.Done -> SuccessBanner()
                else -> ProgressBanner(phase = state.provisionPhase)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProvisionChecklistItem(
                    label = "Broker MQTT",
                    status = checklistStatus(
                        phase = state.provisionPhase,
                        activePhase = ProvisionPhase.ProvisioningBroker,
                        doneAfter = ProvisionPhase.ConnectingBle,
                    ),
                )
                ProvisionChecklistItem(
                    label = "Conexión Bluetooth",
                    status = checklistStatus(
                        phase = state.provisionPhase,
                        activePhase = ProvisionPhase.ConnectingBle,
                        doneAfter = ProvisionPhase.ConfiguringShelly,
                    ),
                )
                ProvisionChecklistItem(
                    label = "Shelly Cloud desactivado",
                    status = shellyStepStatus(state, ShellyProvisionStep.DisableCloud),
                )
                ProvisionChecklistItem(
                    label = "Nombre del dispositivo",
                    status = shellyStepStatus(state, ShellyProvisionStep.SetDeviceName),
                )
                ProvisionChecklistItem(
                    label = "Red WiFi",
                    status = shellyStepStatus(state, ShellyProvisionStep.ConfigureWifi),
                )
                ProvisionChecklistItem(
                    label = "MQTT Habitergy",
                    status = shellyStepStatus(state, ShellyProvisionStep.ConfigureMqtt),
                )
                ProvisionChecklistItem(
                    label = "Contraseña de administrador",
                    status = shellyStepStatus(state, ShellyProvisionStep.SetAdminAuth),
                )
                ProvisionChecklistItem(
                    label = "Reinicio del controlador",
                    status = when {
                        state.provisionPhase == ProvisionPhase.Rebooting -> ChecklistStatus.Active
                        state.provisionPhase == ProvisionPhase.Done -> ChecklistStatus.Done
                        state.shellyProvisionStep == ShellyProvisionStep.Reboot -> ChecklistStatus.Active
                        state.shellyProvisionStep != null &&
                            state.shellyProvisionStep.ordinal > ShellyProvisionStep.SetAdminAuth.ordinal ->
                            ChecklistStatus.Done
                        else -> ChecklistStatus.Pending
                    },
                )
            }
        },
        footer = {
            when (state.provisionPhase) {
                ProvisionPhase.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HabitergyPrimaryButton(
                            label = "Reintentar",
                            showArrow = false,
                            onClick = onRetry,
                        )
                        HabitergySecondaryButton(label = "Volver", onClick = onBack)
                    }
                }
                ProvisionPhase.Done -> Unit
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = HabitergyColors.Primary,
                            strokeWidth = 3.dp,
                        )
                        Text(
                            text = "Configurando…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HabitergyColors.TextSecondary,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
    )
}

private enum class ChecklistStatus {
    Pending,
    Active,
    Done,
}

@Composable
private fun ProvisionChecklistItem(
    label: String,
    status: ChecklistStatus,
) {
    val (icon, tint) = when (status) {
        ChecklistStatus.Pending -> Icons.Default.RadioButtonUnchecked to HabitergyColors.OutlineVariant
        ChecklistStatus.Active -> Icons.Default.HourglassTop to HabitergyColors.Secondary
        ChecklistStatus.Done -> Icons.Default.CheckCircle to HabitergyColors.Primary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = HabitergyColors.TextPrimary,
        )
    }
}

@Composable
private fun ProgressBanner(phase: ProvisionPhase) {
    StatusBanner(
        icon = Icons.Default.HourglassTop,
        message = when (phase) {
            ProvisionPhase.ProvisioningBroker -> "Registrando usuario MQTT en el broker…"
            ProvisionPhase.ConnectingBle -> "Estableciendo conexión BLE…"
            ProvisionPhase.ConfiguringShelly -> "Enviando configuración al Shelly…"
            ProvisionPhase.Rebooting -> "Reiniciando el controlador…"
            else -> "Preparando configuración…"
        },
        tint = HabitergyColors.Secondary,
    )
}

@Composable
private fun ErrorBanner(message: String) {
    StatusBanner(
        icon = Icons.Default.ErrorOutline,
        message = message,
        tint = HabitergyColors.Error,
    )
}

@Composable
private fun SuccessBanner() {
    StatusBanner(
        icon = Icons.Default.CheckCircle,
        message = "Listo. Ahora esperamos que el controlador se conecte a internet.",
        tint = HabitergyColors.Primary,
    )
}

@Composable
private fun StatusBanner(
    icon: ImageVector,
    message: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = HabitergyColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private fun checklistStatus(
    phase: ProvisionPhase,
    activePhase: ProvisionPhase,
    doneAfter: ProvisionPhase,
): ChecklistStatus {
    return when {
        phase.ordinal >= doneAfter.ordinal -> ChecklistStatus.Done
        phase == activePhase -> ChecklistStatus.Active
        else -> ChecklistStatus.Pending
    }
}

private fun shellyStepStatus(
    state: AdoptionUiState,
    step: ShellyProvisionStep,
): ChecklistStatus {
    val current = state.shellyProvisionStep ?: return if (
        state.provisionPhase.ordinal >= ProvisionPhase.ConfiguringShelly.ordinal &&
        state.provisionPhase != ProvisionPhase.ConfiguringShelly
    ) {
        ChecklistStatus.Done
    } else {
        ChecklistStatus.Pending
    }
    return when {
        current.ordinal > step.ordinal -> ChecklistStatus.Done
        current == step -> ChecklistStatus.Active
        else -> ChecklistStatus.Pending
    }
}
