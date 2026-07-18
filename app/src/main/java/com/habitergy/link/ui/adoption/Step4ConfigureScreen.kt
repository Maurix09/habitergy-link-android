package com.habitergy.link.ui.adoption

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    val statusLine = provisionStatusLine(state)
    val progress = provisionProgress(state)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "step4ProvisionProgress",
    )

    AdoptionScreenScaffold(
        currentStep = state.currentStep,
        totalSteps = state.totalSteps,
        onBack = onBack,
        content = {
            ScreenTitle(
                title = "Configurá el controlador",
                subtitle = when (state.provisionPhase) {
                    ProvisionPhase.Error -> statusLine
                    ProvisionPhase.Done -> "Listo. Ahora esperamos que se conecte a internet."
                    else -> "Esto puede tardar unos segundos. No apagues el teléfono ni el controlador."
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                when (state.provisionPhase) {
                    ProvisionPhase.Error -> {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = HabitergyColors.Error,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.bodyLarge,
                            color = HabitergyColors.Error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    ProvisionPhase.Done -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = HabitergyColors.Primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.bodyLarge,
                            color = HabitergyColors.TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> {
                        CircularWavyProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(WavyProgressIndicatorDefaults.CircularContainerSize),
                            color = HabitergyColors.Primary,
                            trackColor = HabitergyColors.SecondaryContainer,
                            stroke = WavyProgressIndicatorDefaults.circularIndicatorStroke,
                            trackStroke = WavyProgressIndicatorDefaults.circularTrackStroke,
                        )
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.bodyLarge,
                            color = HabitergyColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
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
                else -> Unit
            }
        },
    )
}

/** Textos pensados para el partner (sin jerga MQTT / broker / Shelly Cloud). */
private fun provisionStatusLine(state: AdoptionUiState): String {
    return when (state.provisionPhase) {
        ProvisionPhase.Idle -> "Preparando…"
        ProvisionPhase.ProvisioningBroker -> "Preparando el sistema…"
        ProvisionPhase.ConnectingBle -> "Conectando al controlador…"
        ProvisionPhase.ConfiguringShelly -> when (state.shellyProvisionStep) {
            ShellyProvisionStep.DisableCloud,
            ShellyProvisionStep.SetDeviceName,
            null,
            -> "Configurando el controlador…"
            ShellyProvisionStep.ConfigureWifi -> "Configurando la red WiFi…"
            ShellyProvisionStep.ConfigureMqtt -> "Vinculando con Habitergy…"
            ShellyProvisionStep.Reboot -> "Aplicando la configuración…"
            ShellyProvisionStep.SetAdminAuth -> "Asegurando el controlador…"
        }
        ProvisionPhase.Rebooting -> "Reiniciando el controlador…"
        ProvisionPhase.Done -> "Configuración completada"
        ProvisionPhase.Error -> state.provisionErrorMessage
            ?: "No pudimos completar la configuración."
    }
}

private fun provisionProgress(state: AdoptionUiState): Float {
    return when (state.provisionPhase) {
        ProvisionPhase.Idle -> 0.05f
        ProvisionPhase.ProvisioningBroker -> 0.12f
        ProvisionPhase.ConnectingBle -> 0.28f
        ProvisionPhase.ConfiguringShelly -> {
            val step = state.shellyProvisionStep
            val stepCount = ShellyProvisionStep.entries.size
            val index = step?.ordinal?.plus(1) ?: 0
            0.35f + 0.50f * (index.toFloat() / stepCount)
        }
        ProvisionPhase.Rebooting -> 0.92f
        ProvisionPhase.Done -> 1f
        ProvisionPhase.Error -> {
            val step = state.shellyProvisionStep
            if (step != null) {
                0.35f + 0.50f * ((step.ordinal + 1).toFloat() / ShellyProvisionStep.entries.size)
            } else {
                0.2f
            }
        }
    }
}
