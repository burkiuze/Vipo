package com.example.engine

import android.util.Log

object LlamaNative {
    private const val TAG = "LlamaNative"
    private var isNativeAvailable = false

    init {
        try {
            System.loadLibrary("llama")
            isNativeAvailable = true
            Log.i(TAG, "libllama.so successfully loaded.")
        } catch (t: Throwable) {
            isNativeAvailable = false
            Log.w(TAG, "libllama.so not found or could not be loaded: ${t.message}. Using built-in local inference engine.")
        }
    }

    fun isAvailable(): Boolean = isNativeAvailable

    // Native JNI definitions for llama.cpp bridge
    external fun nativeLoadModel(path: String, nCtx: Int, nThreads: Int, nGpuLayers: Int): Long
    external fun nativeUnloadModel(handle: Long)
    external fun nativeInitContext(handle: Long, prompt: String, temp: Float, topP: Float): Long
    external fun nativeSampleNextToken(contextHandle: Long): String?
    external fun nativeFreeContext(contextHandle: Long)
}
