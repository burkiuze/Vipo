package com.example.ui.library

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.components.DeviceSpecsCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateBack: () -> Unit,
    onOpenChatWithModel: (modelPath: String, modelName: String) -> Unit
) {
    val catalog by viewModel.catalog.collectAsState()
    val downloadedModels by viewModel.downloadedModels.collectAsState()
    val downloadTasks by viewModel.downloadTasks.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importGguf(uri, uri.lastPathSegment?.substringAfterLast("/"))
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearStatusMessage()
        }
    }

    val models = remember(catalog, downloadedModels, favoriteIds, uiState) {
        viewModel.visibleModels(catalog, downloadedModels, favoriteIds, uiState)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import .gguf file") },
                                leadingIcon = {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    menuExpanded = false
                                    try {
                                        filePickerLauncher.launch(arrayOf("*/*"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No file picker available", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Download from URL") },
                                leadingIcon = {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.setShowCustomUrlDialog(true)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search models", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LibraryCategories.ordered.forEach { category ->
                        CategoryChip(
                            label = category,
                            selected = uiState.selectedCategory == category,
                            onClick = { viewModel.selectCategory(category) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${models.size} models",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { viewModel.autoSelect() }) {
                        Text("Pick for my device", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            item {
                DeviceSpecsCard(hardwareInfo = viewModel.hardwareInfo)
            }

            val installedIds = downloadedModels.mapNotNull { it.modelId }.toSet()
            val installed = models.filter { installedIds.contains(it.id) }
            val available = models.filterNot { installedIds.contains(it.id) }

            if (installed.isNotEmpty()) {
                item(key = "header-installed") { SectionHeader("On this device") }
            }

            items(installed, key = { "installed-${it.id}" }) { model ->
                val selectedVariant = uiState.selectedVariants[model.id]
                    ?: model.variants.firstOrNull { it.name.contains("Q4") }
                    ?: model.variants.first()

                val downloadedModel = downloadedModels.firstOrNull { it.modelId == model.id }
                val isLoaded = downloadedModel != null && viewModel.engine.activeModelPath == downloadedModel.filePath

                ModelListItem(
                    model = model,
                    selectedVariant = selectedVariant,
                    compatibility = viewModel.getCompatibility(selectedVariant),
                    expanded = uiState.expandedModelId == model.id,
                    highlighted = uiState.highlightedModelId == model.id,
                    downloadedModel = downloadedModel,
                    isLoaded = isLoaded,
                    downloadTask = downloadTasks[model.id],
                    isFavorite = favoriteIds.contains(model.id),
                    onToggleExpanded = { viewModel.toggleExpanded(model.id) },
                    onVariantSelect = { viewModel.selectVariant(model.id, it) },
                    onDownloadClick = { viewModel.startDownload(model, selectedVariant) },
                    onPauseDownload = { viewModel.pauseDownload(model.id) },
                    onResumeDownload = { viewModel.resumeDownload(model.id) },
                    onCancelDownload = { viewModel.cancelDownload(model.id) },
                    onRetryDownload = { viewModel.retryDownload(model.id) },
                    onLoadModel = {
                        downloadedModel?.let { viewModel.loadModel(it.filePath, model.name) }
                    },
                    onChatWithModel = {
                        downloadedModel?.let { onOpenChatWithModel(it.filePath, model.name) }
                    },
                    onDeleteModel = {
                        downloadedModel?.let { viewModel.deleteModel(it) }
                    },
                    onToggleFavorite = { viewModel.toggleFavorite(model.id) }
                )
            }

            if (available.isNotEmpty()) {
                item(key = "header-browse") { SectionHeader("Browse models") }
            }

            items(available, key = { "browse-${it.id}" }) { model ->
                val selectedVariant = uiState.selectedVariants[model.id]
                    ?: model.variants.firstOrNull { it.name.contains("Q4") }
                    ?: model.variants.first()

                val downloadedModel = downloadedModels.firstOrNull { it.modelId == model.id }
                val isLoaded = downloadedModel != null && viewModel.engine.activeModelPath == downloadedModel.filePath

                ModelListItem(
                    model = model,
                    selectedVariant = selectedVariant,
                    compatibility = viewModel.getCompatibility(selectedVariant),
                    expanded = uiState.expandedModelId == model.id,
                    highlighted = uiState.highlightedModelId == model.id,
                    downloadedModel = downloadedModel,
                    isLoaded = isLoaded,
                    downloadTask = downloadTasks[model.id],
                    isFavorite = favoriteIds.contains(model.id),
                    onToggleExpanded = { viewModel.toggleExpanded(model.id) },
                    onVariantSelect = { viewModel.selectVariant(model.id, it) },
                    onDownloadClick = { viewModel.startDownload(model, selectedVariant) },
                    onPauseDownload = { viewModel.pauseDownload(model.id) },
                    onResumeDownload = { viewModel.resumeDownload(model.id) },
                    onCancelDownload = { viewModel.cancelDownload(model.id) },
                    onRetryDownload = { viewModel.retryDownload(model.id) },
                    onLoadModel = {
                        downloadedModel?.let { viewModel.loadModel(it.filePath, model.name) }
                    },
                    onChatWithModel = {
                        downloadedModel?.let { onOpenChatWithModel(it.filePath, model.name) }
                    },
                    onDeleteModel = {
                        downloadedModel?.let { viewModel.deleteModel(it) }
                    },
                    onToggleFavorite = { viewModel.toggleFavorite(model.id) }
                )
            }

            if (models.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (uiState.selectedCategory == LibraryCategories.DOWNLOADED) {
                                "No models downloaded yet."
                            } else {
                                "Nothing matches this filter."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = {
                            viewModel.onSearchQueryChanged("")
                            viewModel.selectCategory(LibraryCategories.ALL)
                        }) {
                            Text("Show all models")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (uiState.showCustomUrlDialog) {
        DownloadCustomUrlDialog(
            onDismiss = { viewModel.setShowCustomUrlDialog(false) },
            onDownload = { url, name -> viewModel.downloadFromUrl(url, name) }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
