package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SettingsDataStore
import com.example.data.model.DownloadedModel
import com.example.data.model.InferenceParams
import com.example.download.DownloadTask
import com.example.engine.InferenceEngine
import com.example.engine.LlamaCppInferenceEngine
import com.example.repository.HardwareRepository
import com.example.repository.ModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val params: InferenceParams = InferenceParams(),
    val pureBlack: Boolean = true,
    val showPerformanceStats: Boolean = true,
    val autoLoadLastModel: Boolean = true,
    val totalStorageUsedFormatted: String = "0 B",
    val freeStorageFormatted: String = "0 GB",
    val maxCpuCores: Int = 8,
    val infoMessage: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val modelRepository = ModelRepository.getInstance(application)
    private val hardwareRepository = HardwareRepository(application)
    val engine: InferenceEngine = LlamaCppInferenceEngine.getInstance()

    val downloadedModels: StateFlow<List<DownloadedModel>> = modelRepository.downloadedModels
    val inferenceParams: StateFlow<InferenceParams> = settingsDataStore.inferenceParams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InferenceParams())

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            modelRepository.initialize()
            refreshStorageInfo()

            settingsDataStore.pureBlackTheme.collect { black ->
                _uiState.update { it.copy(pureBlack = black) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.showPerformanceStats.collect { show ->
                _uiState.update { it.copy(showPerformanceStats = show) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.autoLoadLastModel.collect { auto ->
                _uiState.update { it.copy(autoLoadLastModel = auto) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.inferenceParams.collect { p ->
                _uiState.update { it.copy(params = p) }
            }
        }
    }

    private fun refreshStorageInfo() {
        val usedBytes = modelRepository.getTotalStorageUsedBytes()
        val hw = hardwareRepository.getDeviceHardwareInfo()
        _uiState.update {
            it.copy(
                totalStorageUsedFormatted = DownloadTask.formatBytes(usedBytes),
                freeStorageFormatted = "${hw.freeStorageGb} GB",
                maxCpuCores = hw.cpuCores
            )
        }
    }

    fun updateContextSize(size: Int) {
        val updated = _uiState.value.params.copy(contextSize = size)
        saveParams(updated)
    }

    fun updateTemperature(temp: Float) {
        val updated = _uiState.value.params.copy(temperature = (temp * 10).toInt() / 10f)
        saveParams(updated)
    }

    fun updateTopP(topP: Float) {
        val updated = _uiState.value.params.copy(topP = (topP * 100).toInt() / 100f)
        saveParams(updated)
    }

    fun updateTopK(topK: Int) {
        val updated = _uiState.value.params.copy(topK = topK)
        saveParams(updated)
    }

    fun updateRepeatPenalty(penalty: Float) {
        val updated = _uiState.value.params.copy(repeatPenalty = (penalty * 100).toInt() / 100f)
        saveParams(updated)
    }

    fun updateThreads(threads: Int) {
        val updated = _uiState.value.params.copy(threads = threads)
        saveParams(updated)
    }

    fun updateBatchSize(batchSize: Int) {
        val updated = _uiState.value.params.copy(batchSize = batchSize)
        saveParams(updated)
    }

    fun resetToDefaults() {
        val defaultThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 8)
        val defaultParams = InferenceParams(
            contextSize = 2048,
            temperature = 0.7f,
            topP = 0.9f,
            topK = 40,
            repeatPenalty = 1.1f,
            threads = defaultThreads,
            batchSize = 64
        )
        saveParams(defaultParams)
        _uiState.update { it.copy(infoMessage = "Inference parameters reset to defaults.") }
    }

    private fun saveParams(params: InferenceParams) {
        viewModelScope.launch {
            settingsDataStore.updateInferenceParams(params)
        }
    }

    fun setPureBlack(black: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setPureBlackTheme(black)
        }
    }

    fun setShowPerformance(show: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setShowPerformanceStats(show)
        }
    }

    fun setAutoLoad(auto: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAutoLoadLastModel(auto)
        }
    }

    fun deleteModel(filePath: String) {
        viewModelScope.launch {
            if (engine.activeModelPath == filePath) {
                engine.unloadModel()
                settingsDataStore.setActiveModel(null, null)
            }
            modelRepository.deleteModel(filePath)
            refreshStorageInfo()
            _uiState.update { it.copy(infoMessage = "Model deleted.") }
        }
    }

    fun clearIncompleteDownloads() {
        viewModelScope.launch {
            val count = modelRepository.deleteIncompleteDownloads()
            refreshStorageInfo()
            _uiState.update { it.copy(infoMessage = "Removed $count incomplete download files.") }
        }
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }
}
