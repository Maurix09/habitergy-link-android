package com.habitergy.link.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
    val normalizedCode = normalizeCode(code)
    val currentOnCodeChange by rememberUpdatedState(newValue = onCodeChange)
    val currentOnCodeComplete by rememberUpdatedState(newValue = onCodeComplete)
    val focusRequester = remember { FocusRequester() }
    val messageUi = remember(lookupState, resolvedModel) {
        lookupMessageUi(lookupState = lookupState, resolvedModel = resolvedModel)
    }
    var isFocused by remember { mutableStateOf(false) }
    var fieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = normalizedCode,
                selection = TextRange(normalizedCode.length),
            ),
        )
    }

    LaunchedEffect(normalizedCode) {
        val selectionStart = fieldValue.selection.start.coerceIn(0, normalizedCode.length)
        val selectionEnd = fieldValue.selection.end.coerceIn(0, normalizedCode.length)
        if (fieldValue.text != normalizedCode ||
            fieldValue.selection.start != selectionStart ||
            fieldValue.selection.end != selectionEnd
        ) {
            fieldValue = TextFieldValue(
                text = normalizedCode,
                selection = TextRange(selectionStart, selectionEnd),
            )
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val isLooking = lookupState == DeviceLookupState.Looking
    val stateColor: Color? = when (lookupState) {
        DeviceLookupState.Invalid,
        DeviceLookupState.Assigned,
        DeviceLookupState.Unavailable,
        DeviceLookupState.NotFound,
        DeviceLookupState.NetworkError -> HabitergyColors.Error
        DeviceLookupState.Available,
        DeviceLookupState.Looking -> HabitergyColors.Primary
        DeviceLookupState.Idle -> null
    }
    val focusedSlotIndex = if (isFocused) fieldValue.focusedSlotIndex() else -1

    BasicTextField(
        value = fieldValue,
        onValueChange = { incoming ->
            val nextValue = reduceCodeEdit(previous = fieldValue, incoming = incoming)
            fieldValue = nextValue
            if (nextValue.text.length == DEVICE_CODE_LENGTH) {
                currentOnCodeComplete(nextValue.text)
            } else {
                currentOnCodeChange(nextValue.text)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .semantics {
                contentDescription = "Código del controlador"
                stateDescription = buildString {
                    append("Prefijo SH. ")
                    append("Completaste ${normalizedCode.length} de $DEVICE_CODE_LENGTH caracteres. ")
                    append(messageUi.text)
                }
                if (messageUi.isError) {
                    error(messageUi.text)
                }
            },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics {},
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SH-",
                        style = MaterialTheme.typography.titleLarge,
                        color = HabitergyColors.TextTitle,
                    )

                    repeat(DEVICE_CODE_LENGTH) { index ->
                        CodeCharBox(
                            value = normalizedCode.getOrNull(index)?.toString().orEmpty(),
                            isFocused = focusedSlotIndex == index,
                            stateColor = stateColor,
                            onClick = {
                                fieldValue = fieldValue.copy(
                                    selection = selectionForSlot(index = index, codeLength = normalizedCode.length),
                                )
                                runCatching { focusRequester.requestFocus() }
                            },
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

                LookupMessage(messageUi = messageUi)
            }
        },
    )
}

@Composable
private fun LookupMessage(
    messageUi: LookupMessageUi,
    modifier: Modifier = Modifier,
) {
    Text(
        text = messageUi.text,
        color = messageUi.color,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CodeCharBox(
    value: String,
    isFocused: Boolean,
    stateColor: Color?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val focusColor = stateColor ?: HabitergyColors.Primary
    val borderColor = when {
        isFocused -> focusColor
        stateColor != null -> stateColor
        value.isNotEmpty() -> HabitergyColors.Primary
        else -> HabitergyColors.BorderNormal
    }
    val borderWidth = if (isFocused) 2.dp else 1.5.dp

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(shape)
            .background(HabitergyColors.Card)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = HabitergyColors.TextTitle,
            textAlign = TextAlign.Center,
        )
    }
}

private data class LookupMessageUi(
    val text: String,
    val color: Color,
    val isError: Boolean = false,
)

private fun lookupMessageUi(
    lookupState: DeviceLookupState,
    resolvedModel: String?,
): LookupMessageUi = when (lookupState) {
    DeviceLookupState.Idle -> LookupMessageUi(
        text = "Ingresá los 5 caracteres para verificar el controlador.",
        color = HabitergyColors.TextSecondary,
    )
    DeviceLookupState.Looking -> LookupMessageUi(
        text = "Verificando el código del controlador...",
        color = HabitergyColors.Primary,
    )
    DeviceLookupState.Available -> LookupMessageUi(
        text = resolvedModel
            ?.let { "Controlador encontrado: $it. Ya podés continuar." }
            ?: "Controlador encontrado. Ya podés continuar.",
        color = HabitergyColors.Primary,
    )
    DeviceLookupState.Invalid -> LookupMessageUi(
        text = "El código no es válido. Revisá los 5 caracteres e intentá de nuevo.",
        color = HabitergyColors.Error,
        isError = true,
    )
    DeviceLookupState.NotFound -> LookupMessageUi(
        text = "El código es válido, pero no encontramos un controlador con ese identificador.",
        color = HabitergyColors.Error,
        isError = true,
    )
    DeviceLookupState.Assigned -> LookupMessageUi(
        text = "Encontramos el controlador, pero ya está asignado a otro alojamiento.",
        color = HabitergyColors.Error,
        isError = true,
    )
    DeviceLookupState.Unavailable -> LookupMessageUi(
        text = "Encontramos el controlador, pero su estado actual no permite adoptarlo.",
        color = HabitergyColors.Error,
        isError = true,
    )
    DeviceLookupState.NetworkError -> LookupMessageUi(
        text = "No pudimos verificar el código por un problema de conexión. Probá otra vez.",
        color = HabitergyColors.Error,
        isError = true,
    )
}

private fun normalizeCode(raw: String): String =
    sanitizeRawInput(raw).take(DEVICE_CODE_LENGTH)

private fun sanitizeRawInput(raw: String): String =
    raw.uppercase().filter { it in DeviceCode.ALPHABET }

private fun selectionForSlot(index: Int, codeLength: Int): TextRange = when {
    index < codeLength -> TextRange(index, (index + 1).coerceAtMost(codeLength))
    else -> TextRange(codeLength)
}

private fun TextFieldValue.focusedSlotIndex(): Int {
    val slot = when {
        selection.collapsed -> selection.start
        else -> selection.start
    }.coerceAtMost(DEVICE_CODE_LENGTH - 1)
    return if (text.isEmpty()) 0 else slot
}

private fun reduceCodeEdit(
    previous: TextFieldValue,
    incoming: TextFieldValue,
): TextFieldValue {
    val previousText = normalizeCode(previous.text)
    val incomingText = sanitizeRawInput(incoming.text)

    if (incomingText.length <= DEVICE_CODE_LENGTH) {
        val selectionStart = incoming.selection.start.coerceIn(0, incomingText.length)
        val selectionEnd = incoming.selection.end.coerceIn(0, incomingText.length)
        return TextFieldValue(
            text = incomingText,
            selection = TextRange(selectionStart, selectionEnd),
        )
    }

    val selectionStart = previous.selection.start.coerceIn(0, previousText.length)
    val selectionEnd = previous.selection.end.coerceIn(selectionStart, previousText.length)
    val prefix = previousText.take(selectionStart)
    val suffix = previousText.drop(selectionEnd)
    val suffixStart = incomingText.length - suffix.length
    val suffixMatches = suffix.isEmpty() || (suffixStart >= 0 && incomingText.substring(suffixStart) == suffix)

    if (incomingText.startsWith(prefix) && suffixMatches && suffixStart >= prefix.length) {
        val inserted = incomingText.substring(prefix.length, suffixStart)
        val nextText = if (inserted.length >= DEVICE_CODE_LENGTH) {
            inserted.take(DEVICE_CODE_LENGTH)
        } else {
            (prefix + inserted + suffix).take(DEVICE_CODE_LENGTH)
        }
        val caret = if (inserted.length >= DEVICE_CODE_LENGTH) {
            DEVICE_CODE_LENGTH
        } else {
            (prefix.length + inserted.length).coerceIn(0, nextText.length)
        }
        return TextFieldValue(
            text = nextText,
            selection = TextRange(caret),
        )
    }

    return TextFieldValue(
        text = incomingText.take(DEVICE_CODE_LENGTH),
        selection = TextRange(incoming.selection.end.coerceIn(0, DEVICE_CODE_LENGTH)),
    )
}
