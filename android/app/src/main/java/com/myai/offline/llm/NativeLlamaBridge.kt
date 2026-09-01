package com.myai.offline.llm

import android.util.Log

object NativeLlamaBridge {
    private const val TAG = "NativeLlamaBridge"
    private var isNativeLibraryLoaded = false

    init {
        try {
            System.loadLibrary("myai_native")
            isNativeLibraryLoaded = true
            Log.i(TAG, "Successfully loaded libmyai_native.so")
            nativeInit()
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "libmyai_native.so not loaded (using fallback runtime emulator if running without NDK): ${e.message}")
            isNativeLibraryLoaded = false
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing native llama library", e)
            isNativeLibraryLoaded = false
        }
    }

    fun isAvailable(): Boolean = isNativeLibraryLoaded

    external fun nativeInit(): Boolean
    external fun nativeLoadModel(modelPath: String, nThreads: Int, nCtx: Int): Long
    external fun nativeUnloadModel(modelHandle: Long)
    external fun nativeIsModelLoaded(): Boolean
    external fun nativeStopGeneration()
    external fun nativeGetSystemInfo(): String
}
