package com.habitergy.link.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habitergy.link.domain.DeviceCode
import com.habitergy.link.domain.model.DEVICE_CODE_LENGTH
import com.habitergy.link.domain.model.DeviceLookupState
import com.habitergy.link.ui.theme.HabitergyColors

@Composable
fun DeviceCodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    onCodeComplete: (String) -> Unit,
    lookupState: DeviceLookupState,
    resolvedModel: String?,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember { List(DEVICE_CODE_LENGTH) { FocusRequester() } }
    val focusedSlots = remember { mutableStateListOf(*Array(DEVICE_CODE_LENGTH) { false }) }
    val currentOnCodeChange by rememberUpdatedState(onCodeChange)
    val currentOnCodeComplete by rememberUpdatedState(onCodeComplete)

    // Modelo de slots controlado: preserva posiciones (incluye huecos) para que
    // borrar un recuadro intermedio no reordene los siguientes.
    val slots = remember {
        mutableStateListOf<Char?>(*Array(DEVICE_CODE_LENGTH) { code.getOrNull(it) })
    }

    // Resync sólo cuando el VM limpia el código desde afuera (p. ej. volver al paso 1).
    LaunchedEffect(code) {
        if (code.isEmpty()) repeat(DEVICE_CODE_LENGTH) { slots[it] = null }
    }

    // Auto-foco del primer recuadro al entrar a la pantalla.
    LaunchedEffect(Unit) {
        runCatching { focusRequesters[0].requestFocus() }
    }

    val isLooking = lookupState == DeviceLookupState.Looking
    val stateColor: Color? = when (lookupState) {
        DeviceLookupState.Invalid,
        DeviceLookupState.Assigned,
        DeviceLookupState.Unavailable,
        DeviceLookupState.NotFound,
        DeviceLookupState.NetworkError -> HabitergyColors.Error
        DeviceLookupState.Available -> HabitergyColors.Primary
        DeviceLookupState.Looking -> HabitergyColors.Primary
        DeviceLookupState.Idle -> null
    }

    val transition = rememberInfiniteTransition(label = "codeBoxBorder")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "codeBoxAngle",
    )

    val focusedIndex = focusedSlots.indexOf(true)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SH-",
                style = MaterialTheme.typography.titleLarge,
                color = HabitergyColors.TextTitle,
            )

            repeat(DEVICE_CODE_LENGTH) { index ->
                CodeCharBox(
                    value = slots[index]?.toString() ?: "",
                    isFocused = focusedIndex == index,
                    stateColor = stateColor,
                    angle = angle,
                    focusRequester = focusRequesters[index],
                    imeAction = if (index == DEVICE_CODE_LENGTH - 1) ImeAction.Done else ImeAction.Next,
                    onValueChange = { raw ->
                        handleBoxInput(
                            index = index,
                            raw = raw,
                            slots = slots,
                            onCodeChange = currentOnCodeChange,
                            onCodeComplete = currentOnCodeComplete,
                            focusRequesters = focusRequesters,
                        )
                    },
                    onFocusChanged = { focused -> focusedSlots[index] = focused },
                )
            }

            if (isLooking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = HabitergyColors.Primary,
                )
            }
        }

        LookupMessage(lookupState = lookupState, resolvedModel = resolvedModel)
    }
}

@Composable
private fun LookupMessage(
    lookupState: DeviceLookupState,
    resolvedModel: String?,
    modifier: Modifier = Modifier,
) {
    val (text, color) = when (lookupState) {
        DeviceLookupState.Invalid -> "Código inválido" to HabitergyColors.Error
        DeviceLookupState.Available ->
            "Controlador encontrado: ${resolvedModel ?: ""}".trimEnd() to HabitergyColors.Primary
        DeviceLookupState.NotFound ->
            "No encontramos un controlador con ese código" to HabitergyColors.Error
        DeviceLookupState.Assigned ->
            "Este controlador ya está asignado" to HabitergyColors.Error
        DeviceLookupState.Unavailable ->
            "Este controlador no está disponible para adoptar" to HabitergyColors.Error
        DeviceLookupState.NetworkError ->
            "No pudimos verificar el código. Revisá tu conexión." to HabitergyColors.Error
        DeviceLookupState.Looking, DeviceLookupState.Idle -> return
    }

    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CodeCharBox(
    value: String,
    isFocused: Boolean,
    stateColor: Color?,
    angle: Float,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    val focusColor = stateColor ?: HabitergyColors.Primary
    val staticColor = when {
        stateColor != null -> stateColor
        value.isNotEmpty() -> HabitergyColors.Primary
        else -> HabitergyColors.BorderNormal
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .drawBehind {
                val cornerPx = 12.dp.toPx()
                if (isFocused) {
                    val strokeWidth = 2.dp.toPx()
                    // Gradiente que rota recorriendo el perímetro: el segmento
                    // brillante viaja alrededor del borde del recuadro con foco.
                    rotate(angle) {
                        drawRoundRect(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    focusColor,
                                    focusColor.copy(alpha = 0.15f),
                                    focusColor,
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                            ),
                            style = Stroke(width = strokeWidth),
                            cornerRadius = CornerRadius(cornerPx),
                        )
                    }
                } else {
                    drawRoundRect(
                        color = staticColor,
                        style = Stroke(width = 1.5.dp.toPx()),
                        cornerRadius = CornerRadius(cornerPx),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                textAlign = TextAlign.Center,
                color = HabitergyColors.TextTitle,
            ),
            // Sin cursor visible dentro del recuadro: el foco se indica con el borde.
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = imeAction,
            ),
        )
    }
}

/**
 * Lógica de un recuadro. Cada caja acepta un solo carácter: al tipear reemplaza
 * el contenido del slot actual y avanza el foco sin tocar el siguiente. El
 * backspace borra el slot actual si tiene contenido, o retrocede al anterior si
 * está vacío. El pegado rellena varios slots desde el actual. La validación
 * (checksum + lookup) se dispara sólo cuando el carácter cae en el último slot.
 */
private fun handleBoxInput(
    index: Int,
    raw: String,
    slots: MutableList<Char?>,
    onCodeChange: (String) -> Unit,
    onCodeComplete: (String) -> Unit,
    focusRequesters: List<FocusRequester>,
) {
    val sanitized = raw.uppercase().filter { it in DeviceCode.ALPHABET }
    val oldChar = slots.getOrNull(index)
    val lastIndex = DEVICE_CODE_LENGTH - 1

    when {
        sanitized.isEmpty() -> {
            if (oldChar != null) {
                // Slot actual con contenido: lo borra y se queda aquí.
                slots[index] = null
                onCodeChange(denseCode(slots))
            } else if (index > 0) {
                // Slot actual vacío: retrocede y borra el anterior.
                slots[index - 1] = null
                onCodeChange(denseCode(slots))
                runCatching { focusRequesters[index - 1].requestFocus() }
            }
        }

        sanitized.length == 1 -> {
            slots[index] = sanitized[0]
            if (index == lastIndex) {
                onCodeComplete(denseCode(slots))
            } else {
                onCodeChange(denseCode(slots))
                runCatching { focusRequesters[index + 1].requestFocus() }
            }
        }

        else -> {
            if (oldChar == null) {
                // Pegado en slot vacío: rellena desde el actual hacia adelante.
                var lastFilled = index
                sanitized.forEachIndexed { offset, ch ->
                    val target = index + offset
                    if (target < DEVICE_CODE_LENGTH) {
                        slots[target] = ch
                        lastFilled = target
                    }
                }
                if (lastFilled == lastIndex && denseCode(slots).length == DEVICE_CODE_LENGTH) {
                    onCodeComplete(denseCode(slots))
                } else {
                    onCodeChange(denseCode(slots))
                    val next = (lastFilled + 1).coerceAtMost(lastIndex)
                    runCatching { focusRequesters[next].requestFocus() }
                }
            } else {
                // Tipeo sobre un slot ya escrito: reemplaza con el último char y avanza.
                slots[index] = sanitized.last()
                if (index == lastIndex) {
                    onCodeComplete(denseCode(slots))
                } else {
                    onCodeChange(denseCode(slots))
                    runCatching { focusRequesters[index + 1].requestFocus() }
                }
            }
        }
    }
}

private fun denseCode(slots: List<Char?>): String = slots.filterNotNull().joinToString("")
