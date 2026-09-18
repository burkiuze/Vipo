package com.example.engine

import com.example.data.model.GenerationChunk
import com.example.data.model.GgufMetadata
import com.example.data.model.InferenceParams
import com.example.data.model.PerformanceStats
import kotlinx.coroutines.flow.Flow

sealed class LoadResult {
    data class Success(val metadata: GgufMetadata, val loadTimeMs: Long) : LoadResult()
    data class Error(val message: String, val throwable: Throwable? = null) : LoadResult()
}

/** One turn of a conversation as the chat template sees it. */
data class ChatTurn(val role: String, val content: String) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

interface InferenceEngine {
    val isLoaded: Boolean
    val activeModelPath: String?
    val activeModelName: String?

    /** False when the bundled llama.cpp library is missing for this device's ABI. */
    val isNativeAvailable: Boolean

    suspend fun loadModel(modelPath: String, displayName: String? = null, params: InferenceParams = InferenceParams()): LoadResult
    suspend fun unloadModel()

    /**
     * Streams the answer to [messages], which is the whole conversation so far in order.
     * [systemPrompt] is prepended as a system turn when it is not blank.
     */
    fun generate(
        messages: List<ChatTurn>,
        systemPrompt: String? = null,
        params: InferenceParams = InferenceParams()
    ): Flow<GenerationChunk>

    fun stopGeneration()
    fun getMetadata(): GgufMetadata?
    fun getPerformanceStats(): PerformanceStats
}
