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
import com.habitergy.link.ui.components.ScreenTitle
import com.habitergy.link.ui.theme.HabitergyColors

/**
 * Paso 6 — placeholder hasta implementar asignación de alojamiento.
 */
@Composable
fun Step6SuccessScreen(
    state: AdoptionUiState,
    onBack: () -> Unit,
) {
    AdoptionScreenScaffold(
        currentStep = state.currentStep,
        totalSteps = state.totalSteps,
        onBack = onBack,
        content = {
            ScreenTitle(
                title = "Controlador listo",
                subtitle = "El controlador ya está conectado a Habitergy. La asignación al alojamiento se habilitará en una próxima versión.",
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
            }
        },
        footer = {},
    )
}
