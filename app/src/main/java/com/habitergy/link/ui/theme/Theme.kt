package com.habitergy.link.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val HabitergyLightColorScheme = lightColorScheme(
    primary = HabitergyColors.Primary,
    onPrimary = HabitergyColors.OnPrimary,
    primaryContainer = HabitergyColors.PrimaryContainer,
    onPrimaryContainer = HabitergyColors.OnPrimaryContainer,
    secondary = HabitergyColors.Secondary,
    secondaryContainer = HabitergyColors.SecondaryContainer,
    tertiary = HabitergyColors.Tertiary,
    background = HabitergyColors.Surface,
    onBackground = HabitergyColors.TextPrimary,
    surface = HabitergyColors.Card,
    onSurface = HabitergyColors.TextPrimary,
    surfaceVariant = HabitergyColors.SurfaceVariant,
    onSurfaceVariant = HabitergyColors.TextSecondary,
    outline = HabitergyColors.Outline,
    outlineVariant = HabitergyColors.OutlineVariant,
    error = HabitergyColors.Error,
)

@Composable
fun HabitergyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HabitergyLightColorScheme,
        typography = HabitergyTypography,
        shapes = HabitergyShapes,
        content = content,
    )
}
