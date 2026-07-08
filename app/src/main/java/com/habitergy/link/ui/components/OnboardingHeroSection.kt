package com.habitergy.link.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.habitergy.link.ui.theme.HabitergyColors
import com.habitergy.link.ui.theme.HabitergyDimens

/**
 * Hero image + title + subtitle block matching Manager `RegisterStep1` layout.
 */
@Composable
fun OnboardingHeroSection(
    @DrawableRes imageRes: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    imageContentDescription: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = HabitergyDimens.HeroSectionBottomGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MaterialShapeImage(
            imageRes = imageRes,
            imageSize = HabitergyDimens.HeroShapeDisplaySize,
            contentDescription = imageContentDescription,
            modifier = Modifier.offset {
                IntOffset(x = 0, y = -HabitergyDimens.HeroShapeTopOverlap.roundToPx())
            },
        )

        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = HabitergyDimens.HeroTitleBottomGap),
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge.copy(color = HabitergyColors.TextSecondary),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = HabitergyDimens.HeroSubtitleMaxWidth),
        )
    }
}
