package com.habitergy.link.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitergy.link.domain.model.DiscoveredBleDevice
import com.habitergy.link.ui.theme.HabitergyCardShape
import com.habitergy.link.ui.theme.HabitergyColors

@Composable
fun BleDiscoveryList(
    devices: List<DiscoveredBleDevice>,
    modifier: Modifier = Modifier,
    header: String = "Dispositivos Bluetooth detectados",
) {
    if (devices.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = header,
            style = MaterialTheme.typography.bodyMedium,
            color = HabitergyColors.TextSecondary,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            devices.forEach { device ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(280)) +
                        slideInVertically(
                            animationSpec = tween(280),
                            initialOffsetY = { it / 3 },
                        ),
                ) {
                    DiscoveredBleDeviceRow(device = device)
                }
            }
        }
    }
}

@Composable
private fun DiscoveredBleDeviceRow(
    device: DiscoveredBleDevice,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (device.isMatched) {
        HabitergyColors.PrimaryContainer
    } else {
        HabitergyColors.Card
    }
    val border = if (device.isMatched) {
        BorderStroke(2.dp, HabitergyColors.Primary)
    } else {
        null
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = HabitergyCardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (device.isMatched) 0.dp else 1.dp),
        border = border,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (device.isMatched) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Controlador encontrado",
                    tint = HabitergyColors.Primary,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = HabitergyColors.Secondary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (device.isMatched) HabitergyColors.OnPrimaryContainer else HabitergyColors.TextTitle,
                )
                Text(
                    text = device.macAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (device.isMatched) HabitergyColors.OnPrimaryContainer else HabitergyColors.TextSecondary,
                )
                device.shellyMacAddress?.takeIf { it != device.macAddress }?.let { shellyMac ->
                    Text(
                        text = "WiFi: $shellyMac",
                        style = MaterialTheme.typography.labelSmall,
                        color = HabitergyColors.TextSecondary,
                    )
                }
            }

            Text(
                text = "${device.rssi} dBm",
                style = MaterialTheme.typography.labelSmall,
                color = HabitergyColors.TextSecondary,
            )
        }
    }
}
