package com.example.engine

import android.os.SystemClock
import android.util.Log
import com.example.data.model.GenerationChunk
import com.example.data.model.GgufMetadata
import com.example.data.model.InferenceParams
import com.example.data.model.PerformanceStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs GGUF models with llama.cpp through [LlamaNative]. Everything happens on the device; there
 * is no remote fallback, so when the native library is unavailable loading simply fails.
 */
class LlamaCppInferenceEngine : InferenceEngine {

    companion object {
        private const val TAG = "LlamaCppEngine"

        @Volatile
        private var INSTANCE: LlamaCppInferenceEngine? = null

        fun getInstance(): LlamaCppInferenceEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LlamaCppInferenceEngine().also { INSTANCE = it }
            }
        }
    }

    // llama.cpp contexts are not thread safe: keep every native call on one thread.
    private val nativeDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vipo-llama").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val generationLock = Mutex()

    private var handle: Long = 0L
    private var currentMetadata: GgufMetadata? = null
    private var modelLoadTimeMs: Long = 0L
    private var lastPerformanceStats = PerformanceStats()

    override var activeModelPath: String? = null
        private set
    override var activeModelName: String? = null
        private set

    override val isLoaded: Boolean
        get() = handle != 0L

    override val isNativeAvailable: Boolean
        get() = LlamaNative.isAvailable()

    private val isStopRequested = AtomicBoolean(false)

    override suspend fun loadModel(
        modelPath: String,
        displayName: String?,
        params: InferenceParams
    ): LoadResult = withContext(nativeDispatcher) {
        val file = File(modelPath)
        if (!file.exists()) {
            return@withContext LoadResult.Error("Model file not found: $modelPath")
        }
        if (!LlamaNative.isAvailable()) {
            return@withContext LoadResult.Error(
                "The llama.cpp library is not available for this device's CPU architecture."
            )
        }

        unloadModelInternal()

        val start = SystemClock.elapsedRealtime()
        LlamaNative.ensureInitialised()

        val fileMetadata = try {
            GgufParser.parse(file)
        } catch (e: Exception) {
            Log.w(TAG, "GGUF header could not be parsed: ${e.message}")
            GgufMetadata(modelName = file.nameWithoutExtension, fileSizeBytes = file.length())
        }

        val newHandle = try {
            LlamaNative.nativeLoadModel(
                path = modelPath,
                nCtx = params.contextSize,
                nThreads = params.threads,
                nGpuLayers = 0
            )
        } catch (t: Throwable) {
            Log.e(TAG, "native load failed: ${t.message}", t)
            0L
        }

        if (newHandle == 0L) {
            return@withContext LoadResult.Error(
                "${file.name} could not be loaded. The file may be incomplete, or the device may not have enough free memory."
            )
        }

        handle = newHandle
        modelLoadTimeMs = SystemClock.elapsedRealtime() - start
        activeModelPath = modelPath
        activeModelName = displayName ?: fileMetadata.modelName.ifBlank { file.nameWithoutExtension }

        val contextSize = LlamaNative.nativeContextSize(newHandle)
        val architecture = LlamaNative.nativeModelMeta(newHandle, "general.architecture")
            .ifBlank { fileMetadata.architecture }

        val metadata = fileMetadata.copy(
            architecture = architecture,
            contextLength = if (contextSize > 0) contextSize else fileMetadata.contextLength,
            fileSizeBytes = file.length()
        )
        currentMetadata = metadata

        lastPerformanceStats = PerformanceStats(
            contextMaxTokens = if (contextSize > 0) contextSize else params.contextSize,
            modelLoadTimeMs = modelLoadTimeMs,
            ramEstimateMb = (file.length() / (1024 * 1024)).toInt()
        )

        Log.i(TAG, "loaded ${file.name} in ${modelLoadTimeMs}ms (ctx=$contextSize)")
        LoadResult.Success(metadata, modelLoadTimeMs)
    }

    override suspend fun unloadModel() = withContext(nativeDispatcher) {
        unloadModelInternal()
    }

    private fun unloadModelInternal() {
        isStopRequested.set(true)
        if (handle != 0L) {
            try {
                LlamaNative.nativeFreeModel(handle)
            } catch (t: Throwable) {
                Log.e(TAG, "native free failed: ${t.message}")
            }
            handle = 0L
        }
        currentMetadata = null
        activeModelPath = null
        activeModelName = null
    }

    /** True when the loaded model's chat template produces a reasoning block. */
    fun supportsThinking(): Boolean =
        handle != 0L && runCatching { LlamaNative.nativeSupportsThinking(handle) }.getOrDefault(false)

    override fun generate(
        messages: List<ChatTurn>,
        systemPrompt: String?,
        params: InferenceParams
    ): Flow<GenerationChunk> = flow {
        if (handle == 0L) {
            emit(
                GenerationChunk(
                    token = "No model is loaded. Open the library and download a model first.",
                    isFinished = true
                )
            )
            return@flow
        }

        generationLock.withLock {
            isStopRequested.set(false)
            val startTime = SystemClock.elapsedRealtime()

            val turns = buildList {
                if (!systemPrompt.isNullOrBlank()) {
                    add(ChatTurn(ChatTurn.ROLE_SYSTEM, systemPrompt.trim()))
                }
                addAll(messages)
            }

            val startCode = LlamaNative.nativeStartCompletion(
                handle = handle,
                roles = turns.map { it.role }.toTypedArray(),
                contents = turns.map { it.content }.toTypedArray(),
                temperature = params.temperature,
                topP = params.topP,
                topK = params.topK,
                minP = params.minP,
                repeatPenalty = params.repeatPenalty,
                seed = params.seed,
                nPredict = maxOf(64, params.contextSize / 2)
            )

            if (startCode != 0) {
                emit(
                    GenerationChunk(
                        token = "The prompt could not be processed (error $startCode).",
                        isFinished = true,
                        stats = lastPerformanceStats
                    )
                )
                return@withLock
            }

            val promptTokens = LlamaNative.nativePromptTokens(handle)
            val contextMax = LlamaNative.nativeContextSize(handle).takeIf { it > 0 } ?: params.contextSize
            var timeToFirstTokenMs = 0L
            var generated = 0

            while (true) {
                if (isStopRequested.get()) {
                    LlamaNative.nativeStopCompletion(handle)
                    break
                }

                val piece = LlamaNative.nativeNextToken(handle) ?: break
                if (piece.isEmpty()) continue

                generated++
                if (timeToFirstTokenMs == 0L) {
                    timeToFirstTokenMs = SystemClock.elapsedRealtime() - startTime
                }

                val elapsedSec = (SystemClock.elapsedRealtime() - startTime) / 1000f
                val tokensPerSecond = if (elapsedSec > 0.05f) generated / elapsedSec else 0f
                val usedTokens = promptTokens + generated

                val stats = PerformanceStats(
                    tokensPerSecond = (tokensPerSecond * 10).toInt() / 10f,
                    timeToFirstTokenMs = timeToFirstTokenMs,
                    promptTokens = promptTokens,
                    generatedTokens = generated,
                    contextUsagePercentage = ((usedTokens.toFloat() / contextMax) * 1000).toInt() / 10f,
                    contextUsedTokens = usedTokens,
                    contextMaxTokens = contextMax,
                    modelLoadTimeMs = modelLoadTimeMs,
                    ramEstimateMb = lastPerformanceStats.ramEstimateMb
                )
                lastPerformanceStats = stats

                emit(GenerationChunk(token = piece, isFinished = false, stats = stats))
            }

            emit(GenerationChunk(token = "", isFinished = true, stats = lastPerformanceStats))
        }
    }.flowOn(nativeDispatcher)

    override fun stopGeneration() {
        isStopRequested.set(true)
        if (handle != 0L) {
            runCatching { LlamaNative.nativeStopCompletion(handle) }
        }
    }

    override fun getMetadata(): GgufMetadata? = currentMetadata

    override fun getPerformanceStats(): PerformanceStats = lastPerformanceStats
}
