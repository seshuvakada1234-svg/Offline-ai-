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
    private var loadedModelFile: File? = null

    val isModelLoaded: Boolean
        get() = isLoaded && activeModelHandle != 0L

    suspend fun loadModel(modelInfo: ModelInfo): Boolean = withContext(Dispatchers.IO) {
        val modelFile = resolveModelFile(modelInfo)
        if (modelFile == null || !modelFile.exists() || modelFile.length() <= 0L) {
            activeModelHandle = 0L
            isLoaded = false
            loadedModelFile = null
            Log.e(TAG, "[WHISPER_INIT] Whisper model file missing or empty for ${modelInfo.name}")
            return@withContext false
        }

        Log.i(TAG, "[WHISPER_INIT] Loading Whisper model from ${modelFile.absolutePath}")

        if (NativeWhisperBridge.isAvailable()) {
            activeModelHandle = NativeWhisperBridge.nativeLoadModel(modelFile.absolutePath)
            isLoaded = (activeModelHandle != 0L)
            loadedModelFile = if (isLoaded) modelFile else null
            if (isLoaded) {
                Log.i(TAG, "[WHISPER_INIT] Whisper model initialized successfully")
            } else {
                Log.e(TAG, "[WHISPER_INIT] Whisper initialization failed")
            }
        } else {
            activeModelHandle = 0L
            isLoaded = false
            loadedModelFile = null
            Log.e(TAG, "[WHISPER_INIT] Whisper native backend unavailable")
        }
        isLoaded
    }

    private fun resolveModelFile(modelInfo: ModelInfo): File? {
        val sttPath = File(context.filesDir, "models/stt/${modelInfo.id.rawValue}/${modelInfo.filename}")
        if (sttPath.exists() && sttPath.length() > 0) return sttPath

        val legacyPath = File(context.filesDir, "models/${modelInfo.id.rawValue}/${modelInfo.filename}")
        if (legacyPath.exists() && legacyPath.length() > 0) return legacyPath

        return null
    }

    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        if (activeModelHandle != 0L) {
            if (NativeWhisperBridge.isAvailable()) {
                NativeWhisperBridge.nativeUnloadModel(activeModelHandle)
            }
            activeModelHandle = 0L
        }
        isLoaded = false
        loadedModelFile = null
    }

    /**
     * Transcribes normalized PCM audio float buffer.
     * Supports English, Telugu, and code-mixed speech.
     */
    suspend fun transcribe(pcmSamples: FloatArray, language: String = "auto"): String = withContext(Dispatchers.Default) {
        if (pcmSamples.isEmpty()) {
            return@withContext ""
        }

        Log.i(TAG, "[WHISPER_TRANSCRIPTION_START] samples=${pcmSamples.size} lang=$language model=${loadedModelFile?.name ?: "none"}")

        if (NativeWhisperBridge.isAvailable() && activeModelHandle != 0L) {
            val result = NativeWhisperBridge.nativeTranscribe(activeModelHandle, pcmSamples, language)
            if (result.isNotBlank()) {
                Log.i(TAG, "[WHISPER_TRANSCRIPTION_COMPLETE] chars=${result.length}")
                return@withContext result
            }
        }

        Log.e(TAG, "[WHISPER_TRANSCRIPTION_COMPLETE] empty_result")

        ""
    }
}
