package com.habitergy.link.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.habitergy.link.ui.theme.HabitergyColors
import com.habitergy.link.ui.theme.HabitergyPillShape

@Composable
fun AdoptionScreenScaffold(
    currentStep: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(HabitergyColors.Surface, HabitergyColors.SurfaceContainer),
                ),
            ),
    ) {
        AdoptionStepHeader(
            currentStep = currentStep,
            totalSteps = totalSteps,
            onBack = onBack,
            showBackButton = showBackButton,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 32.dp),
        ) {
            footer()
        }
    }
}

@Composable
fun AdoptionStepHeader(
    currentStep: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 8.dp)
            .padding(horizontal = 16.dp),
    ) {
        if (showBackButton) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = HabitergyColors.Primary,
                )
            }
        } else {
            Spacer(modifier = Modifier.size(40.dp).align(Alignment.CenterStart))
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "PASO $currentStep DE $totalSteps",
                style = MaterialTheme.typography.labelSmall,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(totalSteps) { index ->
                    val step = index + 1
                    val isActive = step <= currentStep
                    val isCurrent = step == currentStep
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(if (isCurrent) 24.dp else 20.dp)
                            .clip(HabitergyPillShape)
                            .background(
                                if (isActive) HabitergyColors.Secondary else HabitergyColors.OutlineVariant,
                            ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(40.dp).align(Alignment.CenterEnd))
    }
}

@Composable
fun ScreenTitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleBottomPadding: Dp = 12.dp,
    contentBottomPadding: Dp = 24.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = contentBottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = titleBottomPadding),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge.copy(color = HabitergyColors.TextSecondary),
            textAlign = TextAlign.Center,
        )
    }
}
