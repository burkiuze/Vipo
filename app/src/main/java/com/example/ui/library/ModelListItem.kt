package com.example.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CompatibilityLevel
import com.example.data.model.DownloadedModel
import com.example.data.model.ModelCatalogItem
import com.example.data.model.ModelVariant
import com.example.download.DownloadStatus
import com.example.download.DownloadTask
import com.example.ui.components.ModelLogo
import com.example.ui.theme.VipoAmber
import com.example.ui.theme.VipoGreen
import com.example.ui.theme.VipoOrange
import com.example.ui.theme.VipoRed

/**
 * A model card: vendor logo, name, download size, a short description, capability tags and one
 * action. Tapping the card opens the quantization picker, which also states what will be
 * downloaded.
 */
@Composable
fun ModelListItem(
    model: ModelCatalogItem,
    selectedVariant: ModelVariant,
    compatibility: CompatibilityLevel,
    expanded: Boolean,
    highlighted: Boolean,
    downloadedModel: DownloadedModel?,
    isLoaded: Boolean,
    downloadTask: DownloadTask?,
    isFavorite: Boolean,
    onToggleExpanded: () -> Unit,
    onVariantSelect: (ModelVariant) -> Unit,
    onDownloadClick: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onLoadModel: () -> Unit,
    onChatWithModel: () -> Unit,
    onDeleteModel: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDownloading = downloadTask != null && downloadTask.status != DownloadStatus.COMPLETED
    val isDownloaded = downloadedModel != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (highlighted) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                } else {
                    Modifier
                }
            )
            .clickable { onToggleExpanded() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            ModelLogo(
                modelName = model.name,
                author = model.author,
                architecture = model.architecture,
                size = 36.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "· ${selectedVariant.fileSize}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) 6 else 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modelTags(model, selectedVariant, compatibility).forEach { tag ->
                        TagChip(tag)
                    }
                }
            }

            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Save",
                    tint = if (isFavorite) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        AnimatedVisibility(visible = expanded && !isDownloaded && !isDownloading) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = "Quantization",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    model.variants.forEach { variant ->
                        VariantChip(
                            variant = variant,
                            selected = variant.name == selectedVariant.name,
                            onClick = { onVariantSelect(variant) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${selectedVariant.label} · needs ${selectedVariant.ramRequiredGb} GB RAM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isDownloading && downloadTask != null -> DownloadProgress(
                task = downloadTask,
                quantization = selectedVariant.name,
                onPause = onPauseDownload,
                onResume = onResumeDownload,
                onCancel = onCancelDownload,
                onRetry = onRetryDownload
            )

            isDownloaded -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDeleteModel,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = if (isLoaded) onChatWithModel else onLoadModel,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(if (isLoaded) "Open chat" else "Use model")
                }
            }

            else -> Button(
                onClick = onDownloadClick,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download ${selectedVariant.name} · ${selectedVariant.fileSize}")
            }
        }
    }
}

/** Short capability labels, in the spirit of the model cards people know from other local apps. */
private fun modelTags(
    model: ModelCatalogItem,
    variant: ModelVariant,
    compatibility: CompatibilityLevel
): List<String> {
    val tags = mutableListOf<String>()
    when (model.category.lowercase()) {
        "coding" -> tags += "coding"
        "reasoning" -> tags += "thinking"
        "small & fast" -> tags += "fast"
        "balanced" -> tags += "everyday"
    }
    if (variant.ramRequiredGb <= 1.5f) tags += "light"
    if (compatibility == CompatibilityLevel.EXCELLENT) tags += "recommended"
    if (compatibility == CompatibilityLevel.MEMORY_RISK) tags += "too big"
    return tags.distinct().take(3)
}

@Composable
private fun TagChip(tag: String) {
    val color = when (tag) {
        "recommended" -> VipoGreen
        "too big" -> VipoRed
        "thinking" -> VipoAmber
        "coding" -> VipoOrange
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun VariantChip(
    variant: ModelVariant,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = "${variant.name} · ${variant.fileSize}",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DownloadProgress(
    task: DownloadTask,
    quantization: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { (task.progressPercent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainer,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val variantLabel = task.variantName.ifBlank { quantization }
            Text(
                text = when (task.status) {
                    DownloadStatus.DOWNLOADING ->
                        "$variantLabel · ${task.downloadedFormatted} / ${task.totalFormatted} · ${task.speedFormatted}"
                    DownloadStatus.PAUSED -> "$variantLabel · paused at ${task.progressPercent}%"
                    DownloadStatus.FAILED -> task.errorMessage ?: "Download failed"
                    else -> "$variantLabel · preparing"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (task.status == DownloadStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row {
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> IconButton(onClick = onPause, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = "Pause",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DownloadStatus.PAUSED -> IconButton(onClick = onResume, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DownloadStatus.FAILED -> IconButton(onClick = onRetry, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    else -> {}
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
