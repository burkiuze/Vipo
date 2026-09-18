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

interface InferenceEngine {
    val isLoaded: Boolean
    val activeModelPath: String?
    val activeModelName: String?

    suspend fun loadModel(modelPath: String, displayName: String? = null, params: InferenceParams = InferenceParams()): LoadResult
    suspend fun unloadModel()
    fun generate(prompt: String, systemPrompt: String? = null, params: InferenceParams = InferenceParams()): Flow<GenerationChunk>
    fun stopGeneration()
    fun getMetadata(): GgufMetadata?
    fun getPerformanceStats(): PerformanceStats
}
