package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.data.model.CompatibilityLevel
import com.example.data.model.DeviceHardwareInfo
import com.example.ui.theme.VipoAmber
import com.example.ui.theme.VipoGreen
import com.example.ui.theme.VipoOrange
import com.example.ui.theme.VipoRed

/** Compact, flat readout of what this device can run. */
@Composable
fun DeviceSpecsCard(
    hardwareInfo: DeviceHardwareInfo,
    onAutoSelectClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hardwareInfo.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${hardwareInfo.cpuCores} cores · ${hardwareInfo.availableRamGb} GB RAM free · " +
                        "${hardwareInfo.freeStorageGb} GB storage free",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (onAutoSelectClick != null) {
                TextButton(onClick = onAutoSelectClick) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto select", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        MeterRow(
            label = "Memory",
            value = "${hardwareInfo.availableRamGb} / ${hardwareInfo.totalRamGb} GB",
            progress = (hardwareInfo.availableRamGb / hardwareInfo.totalRamGb.coerceAtLeast(1f)).coerceIn(0f, 1f)
        )

        Spacer(modifier = Modifier.height(10.dp))

        MeterRow(
            label = "Storage",
            value = "${hardwareInfo.freeStorageGb} / ${hardwareInfo.totalStorageGb} GB",
            progress = (hardwareInfo.freeStorageGb / hardwareInfo.totalStorageGb.coerceAtLeast(1f)).coerceIn(0f, 1f)
        )
    }
}

@Composable
private fun MeterRow(label: String, value: String, progress: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            trackColor = MaterialTheme.colorScheme.surfaceContainer,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
    }
}

@Composable
fun CompatibilityBadge(
    level: CompatibilityLevel,
    modifier: Modifier = Modifier
) {
    val color = when (level) {
        CompatibilityLevel.EXCELLENT, CompatibilityLevel.GOOD -> VipoGreen
        CompatibilityLevel.USABLE -> VipoAmber
        CompatibilityLevel.SLOW -> VipoOrange
        CompatibilityLevel.MEMORY_RISK -> VipoRed
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = level.label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
