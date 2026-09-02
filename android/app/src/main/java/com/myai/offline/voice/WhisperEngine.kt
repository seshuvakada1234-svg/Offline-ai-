package com.myai.offline.voice

import android.content.Context
import android.util.Log
import com.myai.offline.data.model.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WhisperEngine(private val context: Context) {
    private val TAG = "WhisperEngine"
    private var activeModelHandle: Long = 0L
    private var isLoaded: Boolean = false

    val isModelLoaded: Boolean
        get() = isLoaded && activeModelHandle != 0L

    suspend fun loadModel(modelInfo: ModelInfo): Boolean = withContext(Dispatchers.IO) {
        val modelFile = File(context.filesDir, "models/${modelInfo.filename}")
        Log.i(TAG, "Loading Whisper model from: ${modelFile.absolutePath}")

        if (NativeWhisperBridge.isAvailable() && modelFile.exists() && modelFile.length() > 0) {
            activeModelHandle = NativeWhisperBridge.nativeLoadModel(modelFile.absolutePath)
            isLoaded = (activeModelHandle != 0L)
        } else {
            activeModelHandle = 0L
            isLoaded = false
        }
        isLoaded
    }

    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        if (activeModelHandle != 0L) {
            if (NativeWhisperBridge.isAvailable()) {
                NativeWhisperBridge.nativeUnloadModel(activeModelHandle)
            }
            activeModelHandle = 0L
        }
        isLoaded = false
    }

    /**
     * Transcribes normalized PCM audio float buffer.
     * Supports English, Telugu, and code-mixed speech.
     */
    suspend fun transcribe(pcmSamples: FloatArray, language: String = "auto"): String = withContext(Dispatchers.Default) {
        if (pcmSamples.isEmpty()) {
            return@withContext ""
        }

        Log.i(TAG, "Transcribing ${pcmSamples.size} audio samples (lang=$language)")

        if (NativeWhisperBridge.isAvailable() && activeModelHandle != 0L) {
            val result = NativeWhisperBridge.nativeTranscribe(activeModelHandle, pcmSamples, language)
            if (result.isNotBlank()) {
                return@withContext result
            }
        }

        ""
    }
}
