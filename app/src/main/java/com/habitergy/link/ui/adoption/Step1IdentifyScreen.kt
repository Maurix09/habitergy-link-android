package com.habitergy.link.ui.adoption

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.habitergy.link.R
import com.habitergy.link.domain.model.AdoptionUiState
import com.habitergy.link.ui.components.AdoptionScreenScaffold
import com.habitergy.link.ui.components.DeviceCodeInput
import com.habitergy.link.ui.components.HabitergyPrimaryButton
import com.habitergy.link.ui.components.OnboardingHeroSection
import com.habitergy.link.ui.theme.HabitergyColors
import kotlinx.coroutines.launch

@Composable
fun Step1IdentifyScreen(
    state: AdoptionUiState,
    onDeviceCodeChange: (String) -> Unit,
    onDeviceCodeComplete: (String) -> Unit,
    onScanQrClick: () -> Unit,
    onProceedWithoutCode: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
    ) { _ ->
        AdoptionScreenScaffold(
            currentStep = state.currentStep,
            totalSteps = state.totalSteps,
            onBack = onBack,
            showBackButton = false,
            content = {
                OnboardingHeroSection(
                    imageRes = R.drawable.device_qr,
                    imageContentDescription = "Código QR del controlador en el kit de instalación",
                    title = "Identificá el controlador",
                    subtitle = "En el kit de instalación vas a encontrar un código único " +
                        "(ej: SH-KX67W) junto con un QR. Ingresá los 5 caracteres después de SH- " +
                        "para verificarlo o escaneá el código con la cámara.",
                )

                DeviceCodeInput(
                    code = state.deviceCodeInput,
                    onCodeChange = onDeviceCodeChange,
                    onCodeComplete = onDeviceCodeComplete,
                    lookupState = state.lookupState,
                    resolvedModel = state.resolvedDevice?.model,
                )

                TextButton(
                    enabled = !state.isLookingUp,
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Escaneo QR: proximamente")
                        }
                        onScanQrClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = HabitergyColors.Primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Escanear código",
                        color = HabitergyColors.Primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                NoCodeOptionCard(
                    onClick = onProceedWithoutCode,
                    enabled = !state.isLookingUp,
                    modifier = Modifier.padding(top = 16.dp),
                )
            },
            footer = {
                HabitergyPrimaryButton(
                    label = if (state.isLookingUp) "Verificando…" else "Siguiente",
                    onClick = onNext,
                    enabled = state.canProceedFromStep1,
                )
            },
        )
    }
}

@Composable
private fun NoCodeOptionCard(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HabitergyColors.SecondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = HabitergyColors.Secondary,
                modifier = Modifier.size(28.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "¿No tenés el código?",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.85f),
                    color = HabitergyColors.TextTitle,
                )
                Text(
                    text = "Si no tenés el código, seguí por Bluetooth en el siguiente paso para " +
                        "identificar el controlador compatible.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HabitergyColors.TextPrimary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = HabitergyColors.IconNormal,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
