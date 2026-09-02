package com.myai.offline.voice

import android.util.Log

object NativeWhisperBridge {
    private const val TAG = "NativeWhisperBridge"
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("myai_native")
            isLoaded = true
            Log.i(TAG, "Native whisper bridge loaded successfully.")
            nativeInit()
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "libmyai_native.so not found for whisper: ${e.message}")
            isLoaded = false
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing whisper JNI", e)
            isLoaded = false
        }
    }

    fun isAvailable(): Boolean = isLoaded

    external fun nativeInit(): Boolean
    external fun nativeLoadModel(modelPath: String): Long
    external fun nativeUnloadModel(modelHandle: Long)
    external fun nativeIsModelLoaded(): Boolean
    external fun nativeTranscribe(modelHandle: Long, pcmData: FloatArray, language: String): String
}
