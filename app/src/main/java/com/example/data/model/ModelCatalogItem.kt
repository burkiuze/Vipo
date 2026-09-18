package com.example.data.model

data class ModelCatalogItem(
    val id: String,
    val name: String,
    val author: String,
    val parameters: String,
    val description: String,
    val architecture: String,
    val license: String,
    val contextLength: Int,
    val recommendedContext: Int,
    val ramRecommendationGb: Float,
    val category: String,
    val variants: List<ModelVariant>
)

data class ModelVariant(
    val name: String,
    val label: String,
    val fileSize: String,
    val fileSizeBytes: Long,
    val ramRequiredGb: Float,
    val downloadUrl: String,
    val quantizationType: String
)

data class DownloadedModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val formattedSize: String,
    val modelId: String?,
    val variantName: String?,
    val architecture: String? = null,
    val isImported: Boolean = false,
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)
