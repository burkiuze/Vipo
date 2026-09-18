package com.example.engine

import android.util.Log

/**
 * JNI bridge to llama.cpp. The native library is built from source (see app/src/main/cpp) and
 * bundled in the APK, so inference runs entirely on the device.
 */
object LlamaNative {
    private const val TAG = "LlamaNative"

    @Volatile
    private var available = false

    @Volatile
    private var initialised = false

    init {
        available = try {
            System.loadLibrary("vipo_llama")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "libvipo_llama.so could not be loaded: ${t.message}")
            false
        }
    }

    fun isAvailable(): Boolean = available

    /** Initialises the llama backend once per process. */
    @Synchronized
    fun ensureInitialised() {
        if (!available || initialised) return
        try {
            nativeInit()
            initialised = true
        } catch (t: Throwable) {
            Log.e(TAG, "backend init failed: ${t.message}")
            available = false
        }
    }

    private external fun nativeInit()

    external fun nativeLoadModel(path: String, nCtx: Int, nThreads: Int, nGpuLayers: Int): Long

    external fun nativeFreeModel(handle: Long)

    external fun nativeModelDescription(handle: Long): String

    external fun nativeModelMeta(handle: Long, key: String): String

    external fun nativeContextSize(handle: Long): Int

    external fun nativeModelParameterCount(handle: Long): Long

    external fun nativeSupportsThinking(handle: Long): Boolean

    external fun nativePromptTokens(handle: Long): Int

    /** Formats the conversation with the model's chat template and ingests it. 0 means success. */
    external fun nativeStartCompletion(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        repeatPenalty: Float,
        seed: Long,
        nPredict: Int
    ): Int

    /** Returns the next piece of text, or null once generation is finished. */
    external fun nativeNextToken(handle: Long): String?

    external fun nativeStopCompletion(handle: Long)
}
