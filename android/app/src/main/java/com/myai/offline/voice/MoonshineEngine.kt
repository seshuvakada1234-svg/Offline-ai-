package com.myai.offline.voice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.myai.offline.data.model.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MoonshineEngine(private val context: Context) {
    private val TAG = "MoonshineEngine"

    private var recognizer: OfflineRecognizer? = null
    private var loadedModelDir: File? = null

    val isModelLoaded: Boolean
        get() = recognizer != null

    suspend fun loadModel(modelInfo: ModelInfo): Boolean = withContext(Dispatchers.IO) {
        val modelDir = resolveModelDirectory(modelInfo)
        if (modelDir == null || !modelDir.exists()) {
            Log.w(TAG, "[MOONSHINE_INIT] Model directory missing for ${modelInfo.name}")
            unloadModel()
            return@withContext false
        }

        if (isModelLoaded && loadedModelDir?.absolutePath == modelDir.absolutePath) {
            return@withContext true
        }

        unloadModel()

        val preprocess = findRequired(modelDir, "preprocess.onnx")
        val encoder = findRequired(modelDir, "encode.int8.onnx")
        val uncachedDecoder = findRequired(modelDir, "uncached_decode.int8.onnx")
        val cachedDecoder = findRequired(modelDir, "cached_decode.int8.onnx")
        val tokens = findRequired(modelDir, "tokens.txt")

        if (preprocess == null || encoder == null || uncachedDecoder == null || cachedDecoder == null || tokens == null) {
            Log.e(TAG, "[MOONSHINE_INIT] Required model files are missing under ${modelDir.absolutePath}")
            return@withContext false
        }

        return@withContext try {
            val moonshineConfig = OfflineMoonshineModelConfig().apply {
                this.preprocessor = preprocess.absolutePath
                this.encoder = encoder.absolutePath
                this.uncachedDecoder = uncachedDecoder.absolutePath
                this.cachedDecoder = cachedDecoder.absolutePath
            }

            val modelConfig = OfflineModelConfig().apply {
                moonshine = moonshineConfig
                this.tokens = tokens.absolutePath
                numThreads = 2
                provider = "cpu"
            }

            val recognizerConfig = OfflineRecognizerConfig().apply {
                featConfig = FeatureConfig().apply {
                    sampleRate = AudioRecorder.SAMPLE_RATE
                    featureDim = 80
                    dither = 0f
                }
                this.modelConfig = modelConfig
                decodingMethod = "greedy_search"
            }

            recognizer = OfflineRecognizer(context.assets, recognizerConfig)
            loadedModelDir = modelDir
            Log.i(TAG, "[MOONSHINE_INIT] Moonshine initialized from ${modelDir.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "[MOONSHINE_INIT] Failed to initialize Moonshine", e)
            unloadModel()
            false
        }
    }

    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        runCatching { recognizer?.release() }
        recognizer = null
        loadedModelDir = null
    }

    suspend fun transcribe(pcmSamples: FloatArray): String = withContext(Dispatchers.Default) {
        if (pcmSamples.isEmpty()) {
            return@withContext ""
        }

        val recognizerInstance = recognizer ?: return@withContext ""

        return@withContext try {
            val stream = recognizerInstance.createStream()
            try {
                stream.acceptWaveform(pcmSamples, AudioRecorder.SAMPLE_RATE)
                recognizerInstance.decode(stream)
                recognizerInstance.getResult(stream).text.orEmpty().trim()
            } finally {
                runCatching { stream.release() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[MOONSHINE_TRANSCRIBE] Failed", e)
            ""
        }
    }

    private fun resolveModelDirectory(modelInfo: ModelInfo): File? {
        val groupedPath = File(context.filesDir, "models/stt/${modelInfo.id.rawValue}")
        if (groupedPath.exists()) {
            return groupedPath
        }

        val legacyPath = File(context.filesDir, "models/${modelInfo.id.rawValue}")
        if (legacyPath.exists()) {
            return legacyPath
        }

        return null
    }

    private fun findRequired(modelDir: File, filename: String): File? {
        val direct = File(modelDir, filename)
        if (direct.exists()) {
            return direct
        }

        return modelDir.walkTopDown().firstOrNull { it.isFile && it.name == filename }
    }
}
