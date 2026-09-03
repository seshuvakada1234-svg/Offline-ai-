package com.myai.offline.voice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.myai.offline.data.model.ModelInfo
import com.myai.offline.data.model.VoiceOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class KokoroSynthesisResult(
    val samples: FloatArray,
    val sampleRate: Int
)

class KokoroTtsEngine(private val context: Context) {
    private val TAG = "KokoroTtsEngine"

    private var tts: OfflineTts? = null
    private var loadedModelDir: File? = null

    val isModelLoaded: Boolean
        get() = tts != null

    val availableVoices: List<VoiceOption> = listOf(
        VoiceOption(0, "af"),
        VoiceOption(1, "af_bella"),
        VoiceOption(2, "af_nicole"),
        VoiceOption(3, "af_sarah"),
        VoiceOption(4, "af_sky"),
        VoiceOption(5, "am_adam"),
        VoiceOption(6, "am_michael"),
        VoiceOption(7, "bf_emma"),
        VoiceOption(8, "bf_isabella"),
        VoiceOption(9, "bm_george"),
        VoiceOption(10, "bm_lewis")
    )

    suspend fun loadModel(modelInfo: ModelInfo): Boolean = withContext(Dispatchers.IO) {
        val modelDir = resolveModelDirectory(modelInfo)
        if (modelDir == null || !modelDir.exists()) {
            Log.w(TAG, "[KOKORO_INIT] Model directory missing for ${modelInfo.name}")
            unloadModel()
            return@withContext false
        }

        if (isModelLoaded && loadedModelDir?.absolutePath == modelDir.absolutePath) {
            return@withContext true
        }

        unloadModel()

        val modelPath = findRequiredFile(modelDir, "model.int8.onnx")
        val voicesPath = findRequiredFile(modelDir, "voices.bin")
        val tokensPath = findRequiredFile(modelDir, "tokens.txt")
        val dataDir = findRequiredDirectory(modelDir, "espeak-ng-data")

        if (modelPath == null || voicesPath == null || tokensPath == null || dataDir == null) {
            Log.e(TAG, "[KOKORO_INIT] Required model files are missing under ${modelDir.absolutePath}")
            return@withContext false
        }

        return@withContext try {
            val kokoroConfig = OfflineTtsKokoroModelConfig().apply {
                model = modelPath.absolutePath
                voices = voicesPath.absolutePath
                tokens = tokensPath.absolutePath
                this.dataDir = dataDir.absolutePath
                dictDir = dataDir.absolutePath
                lang = "en-us"
                lengthScale = 1.0f
            }

            val modelConfig = OfflineTtsModelConfig().apply {
                kokoro = kokoroConfig
                numThreads = 2
                provider = "cpu"
            }

            val ttsConfig = OfflineTtsConfig().apply {
                model = modelConfig
                maxNumSentences = 1
                silenceScale = 1.0f
            }

            tts = OfflineTts(context.assets, ttsConfig)
            loadedModelDir = modelDir
            Log.i(TAG, "[KOKORO_INIT] Kokoro initialized from ${modelDir.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "[KOKORO_INIT] Failed to initialize Kokoro", e)
            unloadModel()
            false
        }
    }

    fun unloadModel() {
        runCatching { tts?.release() }
        tts = null
        loadedModelDir = null
    }

    suspend fun synthesize(text: String, speakerId: Int, speed: Float): KokoroSynthesisResult? = withContext(Dispatchers.Default) {
        val normalized = text
            .replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("[#*`_~>\\[\\]]"), "")
            .trim()

        if (normalized.isBlank()) {
            return@withContext null
        }

        val ttsInstance = tts ?: return@withContext null

        return@withContext try {
            val generated = ttsInstance.generate(normalized, speakerId, speed)
            if (generated.samples.isEmpty()) {
                null
            } else {
                KokoroSynthesisResult(
                    samples = generated.samples,
                    sampleRate = generated.sampleRate
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "[KOKORO_SYNTH] Failed to synthesize", e)
            null
        }
    }

    private fun resolveModelDirectory(modelInfo: ModelInfo): File? {
        val groupedPath = File(context.filesDir, "models/tts/${modelInfo.id.rawValue}")
        if (groupedPath.exists()) {
            return groupedPath
        }

        val legacyPath = File(context.filesDir, "models/${modelInfo.id.rawValue}")
        if (legacyPath.exists()) {
            return legacyPath
        }

        return null
    }

    private fun findRequiredFile(modelDir: File, filename: String): File? {
        val direct = File(modelDir, filename)
        if (direct.exists()) {
            return direct
        }
        return modelDir.walkTopDown().firstOrNull { it.isFile && it.name == filename }
    }

    private fun findRequiredDirectory(modelDir: File, name: String): File? {
        val direct = File(modelDir, name)
        if (direct.exists() && direct.isDirectory) {
            return direct
        }
        return modelDir.walkTopDown().firstOrNull { it.isDirectory && it.name == name }
    }
}
