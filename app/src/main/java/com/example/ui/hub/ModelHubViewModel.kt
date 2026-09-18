package com.example.ui.hub

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModelHubUiState(
    val selectedCategory: String = "Recommended",
    val searchQuery: String = "",
    val selectedVariants: Map<String, ModelVariant> = emptyMap(), // modelId -> variant
    val filterQuantization: String = "ALL", // "ALL", "Q3", "Q4", "Q5", "Q8"
    val showCustomUrlDialog: Boolean = false,
    val isAutoSelectHighlightId: String? = null,
    val autoSelectMessage: String? = null,
    val statusMessage: String? = null
)

class ModelHubViewModel(application: Application) : AndroidViewModel(application) {

    private val modelRepository = ModelRepository(application)
    private val hardwareRepository = HardwareRepository(application)
    private val downloadManager = ModelDownloadManager.getInstance(application)
    private val settingsDataStore = SettingsDataStore(application)
    val engine: InferenceEngine = LlamaCppInferenceEngine.getInstance()

    val catalog: StateFlow<List<ModelCatalogItem>> = modelRepository.catalog
    val downloadedModels: StateFlow<List<DownloadedModel>> = modelRepository.downloadedModels
    val downloadTasks: StateFlow<Map<String, DownloadTask>> = downloadManager.tasks
    val favoriteIds: StateFlow<Set<String>> = modelRepository.favoriteIds

    val hardwareInfo: DeviceHardwareInfo = hardwareRepository.getDeviceHardwareInfo()

    private val _uiState = MutableStateFlow(ModelHubUiState())
    val uiState: StateFlow<ModelHubUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            modelRepository.initialize()
            // Set default selected variants to Q4_K_M
            val items = catalog.value
            val initialVariants = mutableMapOf<String, ModelVariant>()
            for (item in items) {
                val q4 = item.variants.firstOrNull { it.name.contains("Q4") } ?: item.variants.first()
                initialVariants[item.id] = q4
            }
            _uiState.update { it.copy(selectedVariants = initialVariants) }
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onFilterQuantizationChanged(filter: String) {
        _uiState.update { it.copy(filterQuantization = filter) }
    }

    fun selectVariant(modelId: String, variant: ModelVariant) {
        _uiState.update {
            it.copy(selectedVariants = it.selectedVariants + (modelId to variant))
        }
    }

    fun getCompatibility(variant: ModelVariant): CompatibilityLevel {
        return hardwareRepository.evaluateCompatibility(variant.ramRequiredGb, hardwareInfo)
    }

    fun autoSelect() {
        val recommended = hardwareRepository.findRecommendedModelAndVariant(catalog.value, hardwareInfo)
        if (recommended != null) {
            val (model, variant) = recommended
            _uiState.update {
                it.copy(
                    isAutoSelectHighlightId = model.id,
                    selectedVariants = it.selectedVariants + (model.id to variant),
                    autoSelectMessage = "Auto Selected: ${model.name} (${variant.name}) perfectly fits your ${hardwareInfo.availableRamGb} GB free RAM."
                )
            }
        }
    }

    fun startDownload(model: ModelCatalogItem, variant: ModelVariant) {
        downloadManager.startDownload(
            modelId = model.id,
            modelName = model.name,
            variantName = variant.name,
            url = variant.downloadUrl,
            expectedBytes = variant.fileSizeBytes
        )
    }

    fun pauseDownload(id: String) = downloadManager.pauseDownload(id)
    fun resumeDownload(id: String) = downloadManager.resumeDownload(id)
    fun cancelDownload(id: String) = downloadManager.cancelDownload(id)
    fun retryDownload(id: String) = downloadManager.retryDownload(id)

    fun loadModel(filePath: String, displayName: String) {
        viewModelScope.launch {
            val params = settingsDataStore.inferenceParams.first()
            _uiState.update { it.copy(statusMessage = "Loading $displayName into memory...") }
            when (val res = engine.loadModel(filePath, displayName, params)) {
                is LoadResult.Success -> {
                    settingsDataStore.setActiveModel(filePath, displayName, res.metadata.architecture)
                    _uiState.update { it.copy(statusMessage = "$displayName loaded successfully (${res.loadTimeMs}ms)!") }
                }
                is LoadResult.Error -> {
                    _uiState.update { it.copy(statusMessage = "Load failed: ${res.message}") }
                }
            }
        }
    }

    fun importGguf(uri: Uri, fileName: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Importing GGUF file from device...") }
            val imported = downloadManager.importGgufFromUri(uri, fileName)
            if (imported != null) {
                modelRepository.refreshDownloadedModels()
                _uiState.update { it.copy(statusMessage = "Successfully imported ${imported.displayName}!") }
            } else {
                _uiState.update { it.copy(statusMessage = "Failed to import GGUF. Check file format.") }
            }
        }
    }

    fun downloadFromUrl(url: String, name: String) {
        downloadManager.downloadFromCustomUrl(url, name)
    }

    fun toggleFavorite(modelId: String) {
        modelRepository.toggleFavorite(modelId)
    }

    fun setShowCustomUrlDialog(show: Boolean) {
        _uiState.update { it.copy(showCustomUrlDialog = show) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null, autoSelectMessage = null) }
    }
}
