package com.example.repository

import android.content.Context
import android.util.Log
import com.example.data.model.DownloadedModel
import com.example.data.model.ModelCatalogItem
import com.example.data.model.ModelVariant
import com.example.download.DownloadTask
import com.example.engine.GgufParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

class ModelRepository(private val context: Context) {

    companion object {
        private const val TAG = "ModelRepository"

        @Volatile
        private var INSTANCE: ModelRepository? = null

        /**
         * Chat and Library share one repository so a model downloaded in the library shows up in
         * the chat model picker without restarting the app.
         */
        fun getInstance(context: Context): ModelRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _catalog = MutableStateFlow<List<ModelCatalogItem>>(emptyList())
    val catalog: StateFlow<List<ModelCatalogItem>> = _catalog.asStateFlow()

    private val _downloadedModels = MutableStateFlow<List<DownloadedModel>>(emptyList())
    val downloadedModels: StateFlow<List<DownloadedModel>> = _downloadedModels.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(setOf("llama-3.2-1b-instruct", "smollm2-360m-instruct"))
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        loadCatalog()
        refreshDownloadedModels()
    }

    suspend fun loadCatalog(): List<ModelCatalogItem> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.assets.open("model_catalog.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val items = mutableListOf<ModelCatalogItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val variantsArr = obj.getJSONArray("variants")
                val variants = mutableListOf<ModelVariant>()

                for (v in 0 until variantsArr.length()) {
                    val varObj = variantsArr.getJSONObject(v)
                    variants.add(
                        ModelVariant(
                            name = varObj.getString("name"),
                            label = varObj.getString("label"),
                            fileSize = varObj.getString("fileSize"),
                            fileSizeBytes = varObj.getLong("fileSizeBytes"),
                            ramRequiredGb = varObj.getDouble("ramRequiredGb").toFloat(),
                            downloadUrl = varObj.getString("downloadUrl"),
                            quantizationType = varObj.getString("quantizationType")
                        )
                    )
                }

                items.add(
                    ModelCatalogItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        author = obj.getString("author"),
                        parameters = obj.getString("parameters"),
                        description = obj.getString("description"),
                        architecture = obj.getString("architecture"),
                        license = obj.getString("license"),
                        contextLength = obj.getInt("contextLength"),
                        recommendedContext = obj.getInt("recommendedContext"),
                        ramRecommendationGb = obj.getDouble("ramRecommendationGb").toFloat(),
                        category = obj.getString("category"),
                        variants = variants
                    )
                )
            }
            _catalog.value = items
            items
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model catalog: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun refreshDownloadedModels(): List<DownloadedModel> = withContext(Dispatchers.IO) {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val files = modelsDir.listFiles { _, name -> name.endsWith(".gguf") } ?: emptyArray()
        val list = mutableListOf<DownloadedModel>()

        for (file in files) {
            val catalogMatch = matchCatalogEntry(file.name)
            val variantName = catalogMatch?.let { id ->
                file.nameWithoutExtension.removePrefix(id + "_").takeIf { it.isNotBlank() }
            }
            try {
                val metadata = GgufParser.parse(file)
                val catalogName = catalogMatch?.let { id -> _catalog.value.firstOrNull { it.id == id }?.name }
                val displayName = catalogName
                    ?: metadata.modelName.ifBlank { file.nameWithoutExtension.replace("_", " ") }
                list.add(
                    DownloadedModel(
                        id = file.name,
                        displayName = displayName,
                        fileName = file.name,
                        filePath = file.absolutePath,
                        fileSizeBytes = file.length(),
                        formattedSize = DownloadTask.formatBytes(file.length()),
                        modelId = catalogMatch,
                        variantName = variantName,
                        architecture = metadata.architecture,
                        isImported = catalogMatch == null
                    )
                )
            } catch (e: Exception) {
                list.add(
                    DownloadedModel(
                        id = file.name,
                        displayName = file.nameWithoutExtension,
                        fileName = file.name,
                        filePath = file.absolutePath,
                        fileSizeBytes = file.length(),
                        formattedSize = DownloadTask.formatBytes(file.length()),
                        modelId = catalogMatch,
                        variantName = variantName,
                        architecture = "unknown",
                        isImported = catalogMatch == null
                    )
                )
            }
        }
        _downloadedModels.value = list
        list
    }

    /**
     * Downloads are stored as "<catalogId>_<variant>.gguf", so the longest catalog id the file name
     * starts with identifies the catalog entry. Imported or custom files match nothing.
     */
    private fun matchCatalogEntry(fileName: String): String? {
        val base = fileName.removeSuffix(".gguf")
        return _catalog.value
            .map { it.id }
            .filter { base == it || base.startsWith(it + "_") }
            .maxByOrNull { it.length }
    }

    suspend fun deleteModel(filePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val deleted = if (file.exists()) file.delete() else false
        refreshDownloadedModels()
        deleted
    }

    suspend fun deleteIncompleteDownloads(): Int = withContext(Dispatchers.IO) {
        val modelsDir = File(context.filesDir, "models")
        val parts = modelsDir.listFiles { _, name -> name.endsWith(".part") } ?: emptyArray()
        var deletedCount = 0
        for (p in parts) {
            if (p.delete()) deletedCount++
        }
        deletedCount
    }

    fun toggleFavorite(modelId: String) {
        _favoriteIds.update { current ->
            if (current.contains(modelId)) current - modelId else current + modelId
        }
    }

    fun getTotalStorageUsedBytes(): Long {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) return 0L
        return modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
