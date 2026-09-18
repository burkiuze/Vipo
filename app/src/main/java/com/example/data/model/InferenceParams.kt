package com.example.data.model

data class InferenceParams(
    val contextSize: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val minP: Float = 0.05f,
    val repeatPenalty: Float = 1.1f,
    val threads: Int = 4,
    val batchSize: Int = 64,
    val seed: Long = -1L
)

data class GenerationChunk(
    val token: String,
    val isFinished: Boolean = false,
    val stats: PerformanceStats? = null
)
