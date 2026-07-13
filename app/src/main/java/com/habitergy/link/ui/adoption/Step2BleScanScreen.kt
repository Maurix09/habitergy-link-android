package com.habitergy.link.ui.adoption

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
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
import com.habitergy.link.ui.components.HabitergySecondaryButton
import com.habitergy.link.ui.components.ScreenTitle
import com.habitergy.link.ui.theme.HabitergyColors

/**
 * Paso 2 — Conectá por Bluetooth.
 *
 * El escaneo BLE real (BluetoothLeScanner + filtro Allterco) aún no está
 * implementado. Esta pantalla es un placeholder informativo; no usa datos
 * mock. Cuando se implemente BLE real, restaurar la máquina de estados
 * BleScanPhase (Matched/SelectDevice/Empty/Error) contra un repositorio BLE.
 */
@Composable
fun Step2BleScanScreen(
    state: AdoptionUiState,
    onBack: () -> Unit,
) {
    AdoptionScreenScaffold(
        currentStep = state.currentStep,
        totalSteps = state.totalSteps,
        onBack = onBack,
        content = {
            ScreenTitle(
                title = "Conectá por Bluetooth",
                subtitle = "Vamos a buscar tu controlador Shelly por Bluetooth usando la MAC registrada.",
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.BluetoothDisabled,
                    contentDescription = null,
                    tint = HabitergyColors.Secondary,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = "El escaneo Bluetooth real se habilitará en una próxima versión. " +
                        "Por ahora, la adopción se detiene en este paso.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HabitergyColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        footer = {
            HabitergySecondaryButton(
                label = "Volver",
                onClick = onBack,
            )
        },
    )
}
