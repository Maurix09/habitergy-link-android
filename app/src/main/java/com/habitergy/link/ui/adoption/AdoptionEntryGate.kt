package com.habitergy.link.ui.adoption

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habitergy.link.domain.model.AdoptionEntryState
import com.habitergy.link.ui.components.HabitergyPrimaryButton
import com.habitergy.link.ui.theme.HabitergyColors

@Composable
fun AdoptionEntryGate(
    state: AdoptionEntryState,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(HabitergyColors.Surface, HabitergyColors.SurfaceContainer),
                ),
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = HabitergyColors.Card,
            tonalElevation = 1.dp,
        ) {
            when (state) {
                AdoptionEntryState.Loading -> LoadingGate()
                AdoptionEntryState.NoSession -> MessageGate(
                    icon = Icons.Default.Link,
                    title = "Abrí Link desde Manager",
                    message = "Habitergy Link no funciona de forma independiente. " +
                        "Iniciá una adopción desde Habitergy Manager para continuar.",
                )
                AdoptionEntryState.Invalid -> MessageGate(
                    icon = Icons.Default.ErrorOutline,
                    title = "Sesión no válida",
                    message = "El enlace de adopción no es válido. Volvé a Manager e iniciá una nueva sesión.",
                    isError = true,
                )
                AdoptionEntryState.Expired -> MessageGate(
                    icon = Icons.Default.ErrorOutline,
                    title = "La sesión expiró",
                    message = "Por seguridad, iniciá una nueva adopción desde Habitergy Manager.",
                    isError = true,
                )
                AdoptionEntryState.NetworkError -> MessageGate(
                    icon = Icons.Default.WifiOff,
                    title = "Sin conexión",
                    message = "No pudimos verificar la sesión. Revisá tu conexión a internet e intentá de nuevo.",
                    actionLabel = "Reintentar",
                    onAction = onRetry,
                    isError = true,
                )
                is AdoptionEntryState.Ready -> Unit
            }
        }
    }
}

@Composable
private fun LoadingGate() {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = HabitergyColors.Primary,
        )
        Text(
            text = "Verificando sesión de Manager…",
            style = MaterialTheme.typography.titleLarge,
            color = HabitergyColors.TextTitle,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MessageGate(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    isError: Boolean = false,
) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = if (isError) HabitergyColors.Error else HabitergyColors.Primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = HabitergyColors.TextTitle,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = HabitergyColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        actionLabel?.let {
            HabitergyPrimaryButton(
                label = it,
                onClick = onAction,
                showArrow = false,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
