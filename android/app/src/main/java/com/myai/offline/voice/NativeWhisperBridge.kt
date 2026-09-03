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
            try {
                nativeInit()
            } catch (t: Throwable) {
                Log.w(TAG, "Native whisper initialization deferred: ${t.message}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "libmyai_native.so not found for whisper: ${t.message}")
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
