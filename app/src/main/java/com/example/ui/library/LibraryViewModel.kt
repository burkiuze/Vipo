package com.example.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SettingsDataStore
import com.example.data.model.CompatibilityLevel
import com.example.data.model.DeviceHardwareInfo
import com.example.data.model.DownloadedModel
import com.example.data.model.ModelCatalogItem
import com.example.data.model.ModelVariant
import com.example.download.DownloadStatus
import com.example.download.DownloadTask
import com.example.download.ModelDownloadManager
import com.example.engine.InferenceEngine
import com.example.engine.LlamaCppInferenceEngine
import com.example.engine.LoadResult
import com.example.repository.HardwareRepository
import com.example.repository.ModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val selectedCategory: String = LibraryCategories.RECOMMENDED,
    val searchQuery: String = "",
    val selectedVariants: Map<String, ModelVariant> = emptyMap(), // modelId -> variant
    val showCustomUrlDialog: Boolean = false,
    val expandedModelId: String? = null,
    val highlightedModelId: String? = null,
    val statusMessage: String? = null
)

object LibraryCategories {
    const val RECOMMENDED = "Recommended"
    const val ALL = "All"
    const val DOWNLOADED = "Downloaded"
    const val SAVED = "Saved"

    /** Category values as they appear in model_catalog.json. */
    val catalogCategories = listOf("Small & Fast", "Balanced", "Coding", "Reasoning")

    val ordered: List<String> = listOf(RECOMMENDED) + catalogCategories + listOf(DOWNLOADED, SAVED, ALL)
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val modelRepository = ModelRepository.getInstance(application)
    private val hardwareRepository = HardwareRepository(application)
    private val downloadManager = ModelDownloadManager.getInstance(application)
    private val settingsDataStore = SettingsDataStore(application)
    val engine: InferenceEngine = LlamaCppInferenceEngine.getInstance()

    val catalog: StateFlow<List<ModelCatalogItem>> = modelRepository.catalog
    val downloadedModels: StateFlow<List<DownloadedModel>> = modelRepository.downloadedModels
    val downloadTasks: StateFlow<Map<String, DownloadTask>> = downloadManager.tasks
    val favoriteIds: StateFlow<Set<String>> = modelRepository.favoriteIds

    val hardwareInfo: DeviceHardwareInfo = hardwareRepository.getDeviceHardwareInfo()

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            modelRepository.initialize()
            val initialVariants = catalog.value.associate { item ->
                item.id to (item.variants.firstOrNull { it.name.contains("Q4") } ?: item.variants.first())
            }
            _uiState.update { it.copy(selectedVariants = initialVariants) }
        }

        // A finished download has to show up as "downloaded" right away, without a restart.
        viewModelScope.launch {
            val alreadyHandled = mutableSetOf<String>()
            downloadTasks.collect { tasks ->
                val completed = tasks.values.filter { it.status == DownloadStatus.COMPLETED }
                val fresh = completed.filterNot { alreadyHandled.contains(it.id) }
                if (fresh.isNotEmpty()) {
                    fresh.forEach { alreadyHandled.add(it.id) }
                    modelRepository.refreshDownloadedModels()
                    _uiState.update {
                        it.copy(statusMessage = "${fresh.first().modelName} downloaded and ready to use.")
                    }
                }
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectVariant(modelId: String, variant: ModelVariant) {
        _uiState.update { it.copy(selectedVariants = it.selectedVariants + (modelId to variant)) }
    }

    fun toggleExpanded(modelId: String) {
        _uiState.update { it.copy(expandedModelId = if (it.expandedModelId == modelId) null else modelId) }
    }

    fun getCompatibility(variant: ModelVariant): CompatibilityLevel {
        return hardwareRepository.evaluateCompatibility(variant.ramRequiredGb, hardwareInfo)
    }

    /** Returns the models to show for the current category, search query and download state. */
    fun visibleModels(
        catalog: List<ModelCatalogItem>,
        downloaded: List<DownloadedModel>,
        favorites: Set<String>,
        state: LibraryUiState
    ): List<ModelCatalogItem> {
        var list = when (val category = state.selectedCategory) {
            LibraryCategories.RECOMMENDED -> catalog.filter { item ->
                val variant = state.selectedVariants[item.id]
                    ?: item.variants.firstOrNull { it.name.contains("Q4") }
                    ?: item.variants.first()
                val level = getCompatibility(variant)
                level == CompatibilityLevel.EXCELLENT || level == CompatibilityLevel.GOOD
            }
            LibraryCategories.DOWNLOADED -> {
                val ids = downloaded.mapNotNull { it.modelId }.toSet()
                catalog.filter { ids.contains(it.id) }
            }
            LibraryCategories.SAVED -> catalog.filter { favorites.contains(it.id) }
            LibraryCategories.ALL -> catalog
            else -> catalog.filter { it.category.equals(category, ignoreCase = true) }
        }

        val query = state.searchQuery.trim().lowercase()
        if (query.isNotEmpty()) {
            list = list.filter {
                it.name.lowercase().contains(query) ||
                    it.author.lowercase().contains(query) ||
                    it.architecture.lowercase().contains(query) ||
                    it.description.lowercase().contains(query) ||
                    it.parameters.lowercase().contains(query)
            }
        }
        return list
    }

    fun autoSelect() {
        val recommended = hardwareRepository.findRecommendedModelAndVariant(catalog.value, hardwareInfo)
        if (recommended == null) {
            _uiState.update { it.copy(statusMessage = "No model in the catalog fits this device's free memory.") }
            return
        }
        val (model, variant) = recommended
        _uiState.update {
            it.copy(
                selectedCategory = LibraryCategories.ALL,
                searchQuery = "",
                highlightedModelId = model.id,
                expandedModelId = model.id,
                selectedVariants = it.selectedVariants + (model.id to variant),
                statusMessage = "${model.name} (${variant.name}) fits your ${hardwareInfo.availableRamGb} GB of free RAM."
            )
        }
    }

    fun startDownload(model: ModelCatalogItem, variant: ModelVariant) {
        val neededGb = variant.fileSizeBytes / (1024f * 1024f * 1024f)
        if (hardwareInfo.freeStorageGb > 0f && neededGb > hardwareInfo.freeStorageGb) {
            _uiState.update {
                it.copy(statusMessage = "Not enough storage: ${variant.fileSize} needed, ${hardwareInfo.freeStorageGb} GB free.")
            }
            return
        }
        downloadManager.startDownload(
            modelId = model.id,
            modelName = model.name,
            variantName = variant.name,
            url = variant.downloadUrl,
            expectedBytes = variant.fileSizeBytes
        )
        _uiState.update { it.copy(statusMessage = "Downloading ${model.name} ${variant.name}...") }
    }

    fun pauseDownload(id: String) = downloadManager.pauseDownload(id)
    fun resumeDownload(id: String) = downloadManager.resumeDownload(id)
    fun cancelDownload(id: String) = downloadManager.cancelDownload(id)
    fun retryDownload(id: String) = downloadManager.retryDownload(id)

    fun loadModel(filePath: String, displayName: String) {
        viewModelScope.launch {
            val params = settingsDataStore.inferenceParams.first()
            _uiState.update { it.copy(statusMessage = "Loading $displayName...") }
            when (val res = engine.loadModel(filePath, displayName, params)) {
                is LoadResult.Success -> {
                    settingsDataStore.setActiveModel(filePath, displayName, res.metadata.architecture)
                    _uiState.update { it.copy(statusMessage = "$displayName is active.") }
                }
                is LoadResult.Error -> {
                    _uiState.update { it.copy(statusMessage = "Could not load: ${res.message}") }
                }
            }
        }
    }

    fun deleteModel(model: DownloadedModel) {
        viewModelScope.launch {
            if (engine.activeModelPath == model.filePath) {
                engine.unloadModel()
                settingsDataStore.setActiveModel(null, null, null)
            }
            val deleted = modelRepository.deleteModel(model.filePath)
            _uiState.update {
                it.copy(
                    statusMessage = if (deleted) "${model.displayName} deleted." else "Could not delete ${model.displayName}."
                )
            }
        }
    }

    fun importGguf(uri: Uri, fileName: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Importing GGUF file...") }
            val imported = downloadManager.importGgufFromUri(uri, fileName)
            if (imported != null) {
                modelRepository.refreshDownloadedModels()
                _uiState.update { it.copy(statusMessage = "Imported ${imported.displayName}.") }
            } else {
                _uiState.update { it.copy(statusMessage = "Import failed. Check that the file is a .gguf model.") }
            }
        }
    }

    fun downloadFromUrl(url: String, name: String) {
        downloadManager.downloadFromCustomUrl(url, name)
        _uiState.update { it.copy(statusMessage = "Download started.") }
    }

    fun refresh() {
        viewModelScope.launch { modelRepository.refreshDownloadedModels() }
    }

    fun toggleFavorite(modelId: String) = modelRepository.toggleFavorite(modelId)

    fun setShowCustomUrlDialog(show: Boolean) {
        _uiState.update { it.copy(showCustomUrlDialog = show) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
