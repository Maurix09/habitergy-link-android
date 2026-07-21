package com.habitergy.link.ui.adoption

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habitergy.link.domain.model.AdoptionUiState
import com.habitergy.link.domain.model.CompletionPhase
import com.habitergy.link.domain.model.OnlineWaitPhase
import com.habitergy.link.ui.components.AdoptionScreenScaffold
import com.habitergy.link.ui.components.HabitergyPrimaryButton
import com.habitergy.link.ui.components.HabitergySecondaryButton
import com.habitergy.link.ui.components.ScreenTitle
import com.habitergy.link.ui.theme.HabitergyColors

@Composable
fun Step5WaitingScreen(
    state: AdoptionUiState,
    onStartWaiting: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        onStartWaiting()
    }

    val subtitle = when {
        state.completionPhase == CompletionPhase.Completing ->
            "El controlador está en línea. Estamos completando la asignación en Manager."
        state.completionPhase == CompletionPhase.Error ->
            state.completionErrorMessage ?: "No pudimos completar la asignación."
        else -> when (state.onlineWaitPhase) {
            OnlineWaitPhase.Waiting ->
                "El controlador se está conectando a WiFi y al broker MQTT. Esto puede tardar un minuto."
            OnlineWaitPhase.Online ->
                "¡Controlador conectado! Completando la adopción…"
            OnlineWaitPhase.Timeout ->
                "Todavía no detectamos al controlador en línea. Verificá que el WiFi tenga internet."
            OnlineWaitPhase.Error ->
                state.onlineWaitErrorMessage ?: "No pudimos verificar la conexión."
            OnlineWaitPhase.Idle ->
                "Esperando que el controlador se conecte…"
        }
    }

    AdoptionScreenScaffold(
        currentStep = state.currentStep,
        totalSteps = state.totalSteps,
        onBack = onBack,
        showBackButton = state.completionPhase == CompletionPhase.Idle,
        content = {
            ScreenTitle(
                title = "Esperando conexión",
                subtitle = subtitle,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when {
                    state.completionPhase == CompletionPhase.Error -> {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = HabitergyColors.Error,
                            modifier = Modifier.size(72.dp),
                        )
                    }
                    state.completionPhase == CompletionPhase.Completing -> WaitingAnimation()
                    state.onlineWaitPhase == OnlineWaitPhase.Online -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = HabitergyColors.Primary,
                            modifier = Modifier.size(72.dp),
                        )
                    }
                    state.onlineWaitPhase == OnlineWaitPhase.Timeout ||
                        state.onlineWaitPhase == OnlineWaitPhase.Error -> {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = HabitergyColors.Error,
                            modifier = Modifier.size(72.dp),
                        )
                    }
                    else -> WaitingAnimation()
                }

                Text(
                    text = when {
                        state.completionPhase == CompletionPhase.Completing -> "Completando adopción…"
                        state.completionPhase == CompletionPhase.Error -> "No pudimos asignarlo"
                        state.onlineWaitPhase == OnlineWaitPhase.Online -> "Controlador en línea"
                        state.onlineWaitPhase == OnlineWaitPhase.Timeout -> "Sin conexión todavía"
                        state.onlineWaitPhase == OnlineWaitPhase.Error -> "Error de verificación"
                        else -> "Consultando cada 3 segundos…"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = HabitergyColors.TextTitle,
                    textAlign = TextAlign.Center,
                )
            }
        },
        footer = {
            when {
                state.completionPhase == CompletionPhase.Error -> {
                    HabitergyPrimaryButton(
                        label = "Reintentar asignación",
                        showArrow = false,
                        onClick = onRetry,
                    )
                }
                state.onlineWaitPhase == OnlineWaitPhase.Timeout ||
                    state.onlineWaitPhase == OnlineWaitPhase.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HabitergyPrimaryButton(
                            label = "Reintentar",
                            showArrow = false,
                            onClick = onRetry,
                        )
                        HabitergySecondaryButton(label = "Volver", onClick = onBack)
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = HabitergyColors.Primary,
                            strokeWidth = 3.dp,
                        )
                        Text(
                            text = if (state.completionPhase == CompletionPhase.Completing) {
                                "Completando asignación…"
                            } else {
                                "Esperando conexión…"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = HabitergyColors.TextSecondary,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun WaitingAnimation() {
    val transition = rememberInfiniteTransition(label = "waiting")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )
    Icon(
        imageVector = Icons.Default.Wifi,
        contentDescription = null,
        tint = HabitergyColors.Primary,
        modifier = Modifier
            .size(72.dp)
            .rotate(rotation),
    )
}
