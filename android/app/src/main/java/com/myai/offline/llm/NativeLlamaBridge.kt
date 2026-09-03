package com.myai.offline.llm

import android.util.Log

fun interface LlamaTokenCallback {
    fun onToken(token: String): Boolean
}

object NativeLlamaBridge {
    private const val TAG = "NativeLlamaBridge"
    private var isNativeLibraryLoaded = false

    init {
        try {
            System.loadLibrary("myai_native")
            isNativeLibraryLoaded = true
            Log.i(TAG, "Successfully loaded libmyai_native.so")
            try {
                nativeInit()
            } catch (t: Throwable) {
                Log.w(TAG, "Native llama backend initialization deferred: ${t.message}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "libmyai_native.so not loaded: ${t.message}")
            isNativeLibraryLoaded = false
        }
    }

    fun isAvailable(): Boolean = isNativeLibraryLoaded

    external fun nativeInit(): Boolean
    external fun nativeLoadModel(modelPath: String, nThreads: Int, nCtx: Int): Long
    external fun nativeUnloadModel(modelHandle: Long)
    external fun nativeIsModelLoaded(modelHandle: Long): Boolean
    external fun nativeStopGeneration()
    external fun nativeGetSystemInfo(): String
    external fun nativeGenerate(
        modelHandle: Long,
        prompt: String,
        maxTokens: Int,
        callback: LlamaTokenCallback
    ): Int
}
