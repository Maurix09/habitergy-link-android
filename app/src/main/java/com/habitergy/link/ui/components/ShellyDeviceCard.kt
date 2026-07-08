package com.habitergy.link.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitergy.link.domain.model.ScannedShellyDevice
import com.habitergy.link.ui.theme.HabitergyCardShape
import com.habitergy.link.ui.theme.HabitergyColors

@Composable
fun ShellyDeviceCard(
    device: ScannedShellyDevice,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect),
            shape = HabitergyCardShape,
            colors = CardDefaults.cardColors(containerColor = HabitergyColors.Card),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = if (selected) {
                BorderStroke(2.dp, HabitergyColors.Primary)
            } else {
                null
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(
                    selected = selected,
                    onClick = onSelect,
                    colors = RadioButtonDefaults.colors(selectedColor = HabitergyColors.Primary),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                    )
                    Text(
                        text = device.macAddress,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = HabitergyColors.Secondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = device.signalLabel,
                        style = MaterialTheme.typography.labelSmall.copy(color = HabitergyColors.TextSecondary),
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = HabitergyColors.OutlineVariant,
            )
        }
    }
}

@Composable
fun ControllerFoundBanner(
    device: ScannedShellyDevice,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = HabitergyCardShape,
        colors = CardDefaults.cardColors(containerColor = HabitergyColors.PrimaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = HabitergyColors.Primary,
            )
            Column {
                Text(
                    text = "Controlador encontrado",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                    color = HabitergyColors.OnPrimaryContainer,
                )
                Text(
                    text = "${device.name} · ${device.macAddress}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HabitergyColors.OnPrimaryContainer,
                )
            }
        }
    }
}
