package com.example.data.model

data class GgufMetadata(
    val magic: String = "GGUF",
    val version: Int = 3,
    val tensorCount: Long = 0L,
    val kvCount: Long = 0L,
    val modelName: String = "Unknown GGUF",
    val architecture: String = "llama",
    val contextLength: Int = 2048,
    val embeddingLength: Int = 0,
    val blockCount: Int = 0,
    val feedForwardLength: Int = 0,
    val tokenizerModel: String = "llama",
    val quantizationVersion: Int = 2,
    val fileSizeBytes: Long = 0L,
    val keyValues: Map<String, Any> = emptyMap()
)
