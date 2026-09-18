package com.example.ui.chat

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.SettingsDataStore
import com.example.data.model.DownloadedModel
import com.example.data.model.InferenceParams
import com.example.data.model.PerformanceStats
import com.example.data.model.PluginRegistry
import com.example.engine.InferenceEngine
import com.example.engine.LlamaCppInferenceEngine
import com.example.engine.LoadResult
import com.example.repository.ConversationRepository
import com.example.repository.ModelRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val currentConversation: ConversationEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val streamingContent: String = "",
    val activeModelName: String? = null,
    val isModelLoaded: Boolean = false,
    val performanceStats: PerformanceStats = PerformanceStats(),
    val showPerformancePanel: Boolean = false,
    val showSystemPromptDialog: Boolean = false,
    val showModelSwitchDialog: Boolean = false,
    val systemPromptDraft: String = "",
    val editingMessageId: String? = null,
    val editingMessageText: String = "",
    val errorMessage: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val conversationRepository = ConversationRepository(db.conversationDao(), db.chatMessageDao())
    val modelRepository = ModelRepository.getInstance(application)
    val settingsDataStore = SettingsDataStore(application)
    val engine: InferenceEngine = LlamaCppInferenceEngine.getInstance()

    val conversations: StateFlow<List<ConversationEntity>> = conversationRepository.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedModels: StateFlow<List<DownloadedModel>> = modelRepository.downloadedModels
    val showPerformanceSetting: StateFlow<Boolean> = settingsDataStore.showPerformanceStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val enabledPluginIds: StateFlow<Set<String>> = settingsDataStore.enabledPluginIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PluginRegistry.defaultEnabledIds)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeGenerationJob: Job? = null

    init {
        viewModelScope.launch {
            modelRepository.initialize()

            // Initialize or load conversation
            val all = conversations.first()
            if (all.isNotEmpty()) {
                selectConversation(all.first().id)
            } else {
                newConversation()
            }

            // Auto-load last used model or first available model
            val autoLoad = settingsDataStore.autoLoadLastModel.first()
            val lastPath = settingsDataStore.activeModelPath.first()
            val lastName = settingsDataStore.activeModelName.first()

            if (autoLoad && !lastPath.isNullOrBlank() && java.io.File(lastPath).exists()) {
                loadModelInternal(lastPath, lastName)
            } else {
                val available = modelRepository.downloadedModels.value
                if (available.isNotEmpty()) {
                    loadModelInternal(available.first().filePath, available.first().displayName)
                }
            }
        }
    }

    fun selectConversation(conversationId: String) {
        viewModelScope.launch {
            val conv = conversationRepository.getConversationById(conversationId) ?: return@launch
            _uiState.update { it.copy(currentConversation = conv, systemPromptDraft = conv.systemPrompt) }

            conversationRepository.getMessages(conversationId).collect { msgList ->
                _uiState.update { it.copy(messages = msgList) }
            }
        }
    }

    fun newConversation() {
        viewModelScope.launch {
            val modelName = engine.activeModelName ?: "Default"
            val modelPath = engine.activeModelPath ?: ""
            val conv = conversationRepository.createConversation(
                title = "New Chat",
                modelUsed = modelName,
                modelPath = modelPath
            )
            selectConversation(conv.id)
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            conversationRepository.renameConversation(id, newTitle)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(id)
            val remaining = conversationRepository.conversations.first()
            if (remaining.isNotEmpty()) {
                selectConversation(remaining.first().id)
            } else {
                newConversation()
            }
        }
    }

    fun togglePin(id: String, isPinned: Boolean) {
        viewModelScope.launch {
            conversationRepository.togglePin(id, isPinned)
        }
    }

    fun duplicateConversation(id: String) {
        viewModelScope.launch {
            val dup = conversationRepository.duplicateConversation(id)
            if (dup != null) {
                selectConversation(dup.id)
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val conv = _uiState.value.currentConversation ?: return
        if (text.isBlank() || _uiState.value.isGenerating) return

        _uiState.update { it.copy(inputText = "", isGenerating = true, streamingContent = "") }

        viewModelScope.launch {
            // Save User message
            conversationRepository.insertMessage(
                conversationId = conv.id,
                role = "user",
                content = text
            )

            executeInference(conv.id, text, conv.systemPrompt)
        }
    }

    fun regenerateLastMessage() {
        val conv = _uiState.value.currentConversation ?: return
        val messages = _uiState.value.messages
        if (messages.isEmpty() || _uiState.value.isGenerating) return

        val lastUserMsg = messages.lastOrNull { it.role == "user" } ?: return

        viewModelScope.launch {
            // Delete subsequent assistant messages
            conversationRepository.deleteMessagesFrom(conv.id, lastUserMsg.timestamp + 1)
            _uiState.update { it.copy(isGenerating = true, streamingContent = "") }

            executeInference(conv.id, lastUserMsg.content, conv.systemPrompt)
        }
    }

    fun startEditMessage(messageId: String, currentContent: String) {
        _uiState.update { it.copy(editingMessageId = messageId, editingMessageText = currentContent) }
    }

    fun cancelEditMessage() {
        _uiState.update { it.copy(editingMessageId = null, editingMessageText = "") }
    }

    fun onEditMessageTextChanged(text: String) {
        _uiState.update { it.copy(editingMessageText = text) }
    }

    fun saveEditedMessageAndRegenerate() {
        val msgId = _uiState.value.editingMessageId ?: return
        val newText = _uiState.value.editingMessageText.trim()
        val conv = _uiState.value.currentConversation ?: return
        if (newText.isBlank()) return

        _uiState.update { it.copy(editingMessageId = null, editingMessageText = "", isGenerating = true, streamingContent = "") }

        viewModelScope.launch {
            val messages = _uiState.value.messages
            val targetMsg = messages.firstOrNull { it.id == msgId } ?: return@launch
            conversationRepository.updateMessage(msgId, newText)

            // Delete subsequent messages and generate anew
            conversationRepository.deleteMessagesFrom(conv.id, targetMsg.timestamp + 1)
            executeInference(conv.id, newText, conv.systemPrompt)
        }
    }

    private fun executeInference(conversationId: String, prompt: String, systemPrompt: String) {
        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            val params = settingsDataStore.inferenceParams.first()
            val enabledPlugins = settingsDataStore.enabledPluginIds.first()
            val effectiveSystemPrompt = PluginRegistry.buildSystemPrompt(systemPrompt, enabledPlugins)
            val fullResponseBuilder = StringBuilder()

            try {
                engine.generate(prompt, effectiveSystemPrompt, params).collect { chunk ->
                    if (chunk.token.isNotEmpty()) {
                        fullResponseBuilder.append(chunk.token)
                        _uiState.update {
                            it.copy(
                                streamingContent = fullResponseBuilder.toString(),
                                performanceStats = chunk.stats ?: it.performanceStats
                            )
                        }
                    }

                    if (chunk.isFinished) {
                        val finalResponse = fullResponseBuilder.toString().ifBlank {
                            "Response generated on local device."
                        }
                        val stats = chunk.stats ?: engine.getPerformanceStats()

                        conversationRepository.insertMessage(
                            conversationId = conversationId,
                            role = "assistant",
                            content = finalResponse,
                            tokensCount = stats.generatedTokens,
                            generationTimeMs = if (stats.tokensPerSecond > 0) ((stats.generatedTokens / stats.tokensPerSecond) * 1000).toLong() else 0L,
                            tokensPerSec = stats.tokensPerSecond
                        )

                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                streamingContent = "",
                                performanceStats = stats
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                val stats = engine.getPerformanceStats()
                conversationRepository.insertMessage(
                    conversationId = conversationId,
                    role = "assistant",
                    content = fullResponseBuilder.toString().ifBlank {
                        "Generation stopped."
                    }
                )
                _uiState.update { it.copy(isGenerating = false, streamingContent = "") }
            }
        }
    }

    fun stopGeneration() {
        engine.stopGeneration()
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _uiState.update { it.copy(isGenerating = false) }
    }

    fun switchModel(filePath: String, displayName: String) {
        viewModelScope.launch {
            stopGeneration()
            _uiState.update { it.copy(showModelSwitchDialog = false) }
            loadModelInternal(filePath, displayName)

            // Update active conversation model
            _uiState.value.currentConversation?.let { conv ->
                conversationRepository.updateModelUsed(conv.id, displayName, filePath)
                _uiState.update { it.copy(currentConversation = it.currentConversation?.copy(modelUsed = displayName, modelPath = filePath)) }
            }
        }
    }

    private suspend fun loadModelInternal(filePath: String, displayName: String?) {
        val params = settingsDataStore.inferenceParams.first()
        when (val result = engine.loadModel(filePath, displayName, params)) {
            is LoadResult.Success -> {
                settingsDataStore.setActiveModel(filePath, engine.activeModelName, result.metadata.architecture)
                _uiState.update {
                    it.copy(
                        isModelLoaded = true,
                        activeModelName = engine.activeModelName,
                        performanceStats = engine.getPerformanceStats(),
                        errorMessage = null
                    )
                }
            }
            is LoadResult.Error -> {
                _uiState.update {
                    it.copy(
                        isModelLoaded = false,
                        activeModelName = null,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun setPerformancePanelVisible(visible: Boolean) {
        _uiState.update { it.copy(showPerformancePanel = visible) }
    }

    fun setSystemPromptDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showSystemPromptDialog = visible) }
    }

    /** Called when the chat screen becomes visible again, e.g. after downloading in the library. */
    fun refreshModels() {
        viewModelScope.launch { modelRepository.refreshDownloadedModels() }
    }

    fun setModelSwitchDialogVisible(visible: Boolean) {
        viewModelScope.launch {
            modelRepository.refreshDownloadedModels()
            _uiState.update { it.copy(showModelSwitchDialog = visible) }
        }
    }

    fun onSystemPromptDraftChanged(text: String) {
        _uiState.update { it.copy(systemPromptDraft = text) }
    }

    fun saveSystemPrompt() {
        val conv = _uiState.value.currentConversation ?: return
        val newPrompt = _uiState.value.systemPromptDraft.trim()
        viewModelScope.launch {
            conversationRepository.updateSystemPrompt(conv.id, newPrompt)
            _uiState.update {
                it.copy(
                    showSystemPromptDialog = false,
                    currentConversation = it.currentConversation?.copy(systemPrompt = newPrompt)
                )
            }
        }
    }

    fun clearCurrentChat() {
        val conv = _uiState.value.currentConversation ?: return
        viewModelScope.launch {
            conversationRepository.clearMessages(conv.id)
        }
    }
}
