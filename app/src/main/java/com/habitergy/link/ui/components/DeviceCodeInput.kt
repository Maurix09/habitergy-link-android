package com.habitergy.link.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habitergy.link.domain.model.DEVICE_CODE_LENGTH
import com.habitergy.link.ui.theme.HabitergyColors

@Composable
fun DeviceCodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    isLookingUp: Boolean,
    lookupError: String?,
    resolvedLabel: String?,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember { List(DEVICE_CODE_LENGTH) { FocusRequester() } }
    val chars = List(DEVICE_CODE_LENGTH) { index -> code.getOrElse(index) { ' ' }.let { if (it == ' ') "" else it.toString() } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(DEVICE_CODE_LENGTH) { index ->
                CodeCharBox(
                    value = chars[index],
                    isError = lookupError != null,
                    focusRequester = focusRequesters[index],
                    imeAction = if (index == DEVICE_CODE_LENGTH - 1) ImeAction.Done else ImeAction.Next,
                    onValueChange = { newValue ->
                        val sanitized = newValue.uppercase().filter { it.isLetterOrDigit() }
                        when {
                            sanitized.length > 1 -> {
                                val merged = buildCodeFromPaste(code, index, sanitized)
                                onCodeChange(merged)
                                val focusIndex = merged.length.coerceAtMost(DEVICE_CODE_LENGTH - 1)
                                focusRequesters[focusIndex].requestFocus()
                            }
                            sanitized.length == 1 -> {
                                val updated = replaceCharAt(code, index, sanitized.first())
                                onCodeChange(updated)
                                if (index < DEVICE_CODE_LENGTH - 1) {
                                    focusRequesters[index + 1].requestFocus()
                                }
                            }
                            else -> {
                                val updated = removeCharAt(code, index)
                                onCodeChange(updated)
                                if (index > 0) {
                                    focusRequesters[index - 1].requestFocus()
                                }
                            }
                        }
                    },
                )
            }

            if (isLookingUp) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = HabitergyColors.Primary,
                )
            }
        }

        when {
            lookupError != null -> Text(
                text = lookupError,
                color = HabitergyColors.Error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            resolvedLabel != null -> Text(
                text = "Encontrado: $resolvedLabel",
                color = HabitergyColors.Primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CodeCharBox(
    value: String,
    isError: Boolean,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
) {
    val borderColor = when {
        isError -> HabitergyColors.Error
        value.isNotEmpty() -> HabitergyColors.Primary
        else -> HabitergyColors.BorderNormal
    }

    BasicTextField(
        value = TextFieldValue(value, selection = TextRange(value.length)),
        onValueChange = onValueChange,
        modifier = Modifier
            .size(52.dp)
            .focusRequester(focusRequester),
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(
            textAlign = TextAlign.Center,
            color = HabitergyColors.TextTitle,
        ),
        cursorBrush = SolidColor(HabitergyColors.Primary),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { /* lookup triggered from ViewModel */ }),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .border(
                        width = 1.5.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                innerTextField()
            }
        },
    )
}

private fun replaceCharAt(code: String, index: Int, char: Char): String {
    val chars = code.padEnd(DEVICE_CODE_LENGTH, ' ').toCharArray()
    if (index in chars.indices) {
        chars[index] = char
    }
    return chars.concatToString().trimEnd()
}

private fun removeCharAt(code: String, index: Int): String {
    if (code.isEmpty()) return ""
    val chars = code.padEnd(DEVICE_CODE_LENGTH, ' ').toCharArray()
    if (index in chars.indices) {
        chars[index] = ' '
    }
    return chars.concatToString().trimEnd()
}

private fun buildCodeFromPaste(current: String, startIndex: Int, pasted: String): String {
    val base = current.padEnd(DEVICE_CODE_LENGTH, ' ').toCharArray()
    pasted.forEachIndexed { offset, char ->
        val targetIndex = startIndex + offset
        if (targetIndex < DEVICE_CODE_LENGTH) {
            base[targetIndex] = char
        }
    }
    return base.concatToString().trimEnd().take(DEVICE_CODE_LENGTH)
}
