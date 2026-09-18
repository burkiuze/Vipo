package com.example.ui.hub

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CompatibilityLevel
import com.example.data.model.ModelCatalogItem
import com.example.data.model.ModelVariant
import com.example.download.DownloadStatus
import com.example.ui.components.DeviceSpecsCard
import com.example.ui.theme.VipoCyan
import com.example.ui.theme.VipoGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelHubScreen(
    viewModel: ModelHubViewModel,
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

    // GGUF File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast("/")
            viewModel.importGguf(uri, fileName)
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val categories = listOf(
        "Recommended",
        "Small & Fast",
        "Balanced",
        "High Intelligence",
        "Coding",
        "Reasoning",
        "All Models",
        "Downloaded",
        "Favorites"
    )

    // Filter models
    val filteredModels = remember(catalog, downloadedModels, favoriteIds, uiState.selectedCategory, uiState.searchQuery, uiState.filterQuantization) {
        var list = when (uiState.selectedCategory) {
            "Recommended" -> {
                catalog.filter { item ->
                    val defaultVar = item.variants.firstOrNull { it.name.contains("Q4") } ?: item.variants.first()
                    val comp = viewModel.getCompatibility(defaultVar)
                    comp == CompatibilityLevel.EXCELLENT || comp == CompatibilityLevel.GOOD
                }
            }
            "Small & Fast" -> catalog.filter { it.category == "small" }
            "Balanced" -> catalog.filter { it.category == "balanced" }
            "High Intelligence" -> catalog.filter { it.category == "intelligence" }
            "Coding" -> catalog.filter { it.category == "coding" }
            "Reasoning" -> catalog.filter { it.category == "reasoning" }
            "Downloaded" -> {
                val downloadedIds = downloadedModels.mapNotNull { it.modelId }.toSet()
                catalog.filter { downloadedIds.contains(it.id) || downloadedModels.any { dm -> dm.displayName.contains(it.name, ignoreCase = true) } }
            }
            "Favorites" -> catalog.filter { favoriteIds.contains(it.id) }
            else -> catalog
        }

        if (uiState.searchQuery.isNotBlank()) {
            val q = uiState.searchQuery.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                it.author.lowercase().contains(q) ||
                it.architecture.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.parameters.lowercase().contains(q)
            }
        }

        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Model Hub",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    // Import GGUF button
                    IconButton(onClick = {
                        try {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open file picker", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Import GGUF", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    // Direct URL download
                    IconButton(onClick = { viewModel.setShowCustomUrlDialog(true) }) {
                        Icon(Icons.Default.Link, contentDescription = "Download URL", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Device Hardware Specs Card with Auto Select
            item {
                DeviceSpecsCard(
                    hardwareInfo = viewModel.hardwareInfo,
                    onAutoSelectClick = { viewModel.autoSelect() }
                )
            }

            // Auto-Select notification banner
            if (uiState.autoSelectMessage != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.autoSelectMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Search 100+ models, architectures, sizes...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }

            // Category Horizontal Scroll
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        val isSelected = uiState.selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectCategory(category) },
                            label = {
                                Text(
                                    text = category,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }
            }

            // Models Count Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredModels.size} Models Available",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "llama.cpp GGUF",
                        style = MaterialTheme.typography.labelSmall,
                        color = VipoGreen
                    )
                }
            }

            // Model Cards
            items(filteredModels, key = { it.id }) { model ->
                val selectedVariant = uiState.selectedVariants[model.id]
                    ?: model.variants.firstOrNull { it.name.contains("Q4") }
                    ?: model.variants.first()

                val compatibility = viewModel.getCompatibility(selectedVariant)

                val downloadedModel = downloadedModels.firstOrNull { dm ->
                    dm.fileName.startsWith(model.id) ||
                    dm.displayName.contains(model.name, ignoreCase = true) ||
                    (dm.modelId != null && dm.modelId == model.id)
                }

                val isDownloaded = downloadedModel != null
                val isLoaded = isDownloaded && viewModel.engine.activeModelPath == downloadedModel.filePath
                val task = downloadTasks[model.id]

                ModelCard(
                    model = model,
                    selectedVariant = selectedVariant,
                    compatibility = compatibility,
                    isDownloaded = isDownloaded,
                    isLoaded = isLoaded,
                    downloadTask = task,
                    isFavorite = favoriteIds.contains(model.id),
                    onVariantSelect = { v -> viewModel.selectVariant(model.id, v) },
                    onDownloadClick = { v -> viewModel.startDownload(model, v) },
                    onPauseDownload = { viewModel.pauseDownload(model.id) },
                    onResumeDownload = { viewModel.resumeDownload(model.id) },
                    onCancelDownload = { viewModel.cancelDownload(model.id) },
                    onRetryDownload = { viewModel.retryDownload(model.id) },
                    onLoadModel = {
                        if (downloadedModel != null) {
                            viewModel.loadModel(downloadedModel.filePath, model.name)
                        }
                    },
                    onChatWithModel = {
                        if (downloadedModel != null) {
                            onOpenChatWithModel(downloadedModel.filePath, model.name)
                        }
                    },
                    onToggleFavorite = { viewModel.toggleFavorite(model.id) }
                )
            }

            // Empty State
            if (filteredModels.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No models match your search or filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = {
                            viewModel.onSearchQueryChanged("")
                            viewModel.selectCategory("All Models")
                        }) {
                            Text("Reset Filters")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Direct URL Download Dialog
    if (uiState.showCustomUrlDialog) {
        DownloadCustomUrlDialog(
            onDismiss = { viewModel.setShowCustomUrlDialog(false) },
            onDownload = { url, name ->
                viewModel.downloadFromUrl(url, name)
            }
        )
    }
}
