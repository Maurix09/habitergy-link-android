package com.habitergy.link.ui.adoption

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habitergy.link.domain.model.AdoptionUiState
import com.habitergy.link.ui.components.AdoptionScreenScaffold
import com.habitergy.link.ui.components.HabitergyPrimaryButton
import com.habitergy.link.ui.components.ScreenTitle
import com.habitergy.link.ui.theme.HabitergyColors

@Composable
fun Step6SuccessScreen(
    state: AdoptionUiState,
    siteName: String?,
    onRetryReturn: () -> Unit,
) {
    val navigationError = state.returnNavigationErrorMessage
    AdoptionScreenScaffold(
        currentStep = state.currentStep,
        totalSteps = state.totalSteps,
        onBack = {},
        showBackButton = false,
        content = {
            ScreenTitle(
                title = "Controlador listo",
                subtitle = if (siteName != null) {
                    "El controlador quedó asignado a $siteName. Volviendo a Habitergy Manager…"
                } else {
                    "El controlador quedó asignado. Volviendo a Habitergy Manager…"
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = HabitergyColors.Primary,
                    modifier = Modifier.size(80.dp),
                )
                state.resolvedDevice?.deviceCode?.let { code ->
                    Text(
                        text = code,
                        style = MaterialTheme.typography.titleLarge,
                        color = HabitergyColors.TextTitle,
                        textAlign = TextAlign.Center,
                    )
                }
                navigationError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = HabitergyColors.Error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        footer = {
            if (navigationError != null) {
                HabitergyPrimaryButton(
                    label = "Volver a Manager",
                    onClick = onRetryReturn,
                    showArrow = false,
                )
            }
        },
    )
}
