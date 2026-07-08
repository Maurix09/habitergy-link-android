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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habitergy.link.R
import com.habitergy.link.domain.model.AdoptionUiState
import com.habitergy.link.ui.components.AdoptionScreenScaffold
import com.habitergy.link.ui.components.DeviceCodeInput
import com.habitergy.link.ui.components.HabitergyPrimaryButton
import com.habitergy.link.ui.components.MaterialShapeImage
import com.habitergy.link.ui.components.ScreenTitle
import com.habitergy.link.ui.theme.HabitergyColors
import kotlinx.coroutines.launch

@Composable
fun Step1IdentifyScreen(
    state: AdoptionUiState,
    onDeviceCodeChange: (String) -> Unit,
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
            content = {
                MaterialShapeImage(
                    imageRes = R.drawable.device_qr,
                    fillMaxWidth = true,
                    maxSize = 220.dp,
                    contentDescription = "Código QR del controlador en el kit de instalación",
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                ScreenTitle(
                    title = "Identificá el controlador",
                    subtitle = "En el kit de instalación vas a encontrar un código único de 5 caracteres " +
                        "(ej: CX123) junto con un QR. Ingresalo abajo o escanealo con la cámara.",
                    titleSingleLine = true,
                    titleBottomPadding = 8.dp,
                    contentBottomPadding = 16.dp,
                )

                DeviceCodeInput(
                    code = state.deviceCodeInput,
                    onCodeChange = onDeviceCodeChange,
                    isLookingUp = state.isLookingUp,
                    lookupError = state.lookupError,
                    resolvedLabel = state.resolvedDevice?.let { device ->
                        "${device.model} · ${device.macAddress}"
                    },
                )

                TextButton(
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Coming soon")
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
                    modifier = Modifier.padding(top = 16.dp),
                )

                MockHintCard(modifier = Modifier.padding(top = 16.dp))
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
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
                    text = "Si perdiste el código o querés adoptar un controlador Shelly compatible " +
                        "con Habitergy, tocá aquí.",
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

@Composable
private fun MockHintCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HabitergyColors.SurfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = HabitergyColors.Secondary,
                )
                Text(
                    text = "Modo desarrollo (mock)",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                text = "Códigos válidos: CX123, AB123, T3ST1. Cualquier otro código de 5 caracteres fallará.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
