package com.myai.offline.data.model

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

enum class ModelId(val rawValue: String) {
    QWEN3_1_7B("qwen3-1.7b"),
    GEMMA3_1B("gemma3-1b"),
    QWEN3_4B("qwen3-4b"),
    PHI4_MINI("phi4-mini"),
    GEMMA3_4B("gemma3-4b"),
    WHISPER_BASE("whisper-base"),
    MOONSHINE_TINY_EN("moonshine-tiny-en"),
    KOKORO_EN_INT8("kokoro-en-int8");

    companion object {
        fun fromRaw(raw: String): ModelId =
            entries.firstOrNull { it.rawValue == raw } ?: QWEN3_1_7B
    }
}

enum class ModelType {
    CHAT_LLM,
    SPEECH_TO_TEXT,
    TEXT_TO_SPEECH
}

enum class ModelPackageType {
    SINGLE_FILE,
    TAR_BZ2_ARCHIVE
}

enum class ModelState {
    NOT_INSTALLED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    READY,
    LOADING,
    ACTIVE,
    ERROR
}

data class ModelManifestEntry(
    val id: ModelId,
    val displayName: String,
    val tag: String,
    val description: String,
    val repository: String?,
    val filename: String,
    val expectedSizeBytes: Long,
    val sha256Expected: String?,
    val quantization: String,
    val modelType: ModelType,
    val contextLength: Int,
    val capabilities: List<String>,
    val architecture: String,
    val ramRequired: String,
    val directDownloadUrl: String? = null,
    val packageType: ModelPackageType = ModelPackageType.SINGLE_FILE,
    val requiredFiles: List<String> = emptyList(),
    val isDefault: Boolean = false,
    val isDownloadable: Boolean = true
) {
    val downloadUrl: String?
        get() = directDownloadUrl ?: repository?.let { ModelManifest.buildHuggingFaceResolveUrl(it, filename) }

    val sizeFormatted: String
        get() = ModelManifest.formatBytes(expectedSizeBytes)
}

data class ModelInfo(
    val id: ModelId,
    val name: String,
    val tag: String,
    val description: String,
    val sizeFormatted: String,
    val sizeBytes: Long,
    val isDefault: Boolean = false,
    val sha256Expected: String? = null,
    val repository: String? = null,
    val filename: String,
    val sourceUrl: String? = null,
    val contextSize: Int,
    val quant: String,
    val modelType: ModelType,
    val capabilities: List<String> = emptyList(),
    val architecture: String,
    val ramRequired: String = "1.5 GB",
    val isDownloadable: Boolean = true,
    val packageType: ModelPackageType = ModelPackageType.SINGLE_FILE,
    val requiredFiles: List<String> = emptyList(),
    val downloadSpeed: String? = null,
    val downloadedBytes: Long = 0L,
    val state: ModelState = ModelState.NOT_INSTALLED,
    val errorMessage: String? = null,
    val isLoaded: Boolean = false
) {
    val backend: String
        get() = when (modelType) {
            ModelType.CHAT_LLM -> "llama.cpp"
            ModelType.SPEECH_TO_TEXT -> if (isWhisper) "whisper.cpp" else "sherpa-onnx"
            ModelType.TEXT_TO_SPEECH -> "sherpa-onnx"
        }

    val isWhisper: Boolean
        get() = architecture.equals("whisper", ignoreCase = true)

    val isSpeechToText: Boolean
        get() = modelType == ModelType.SPEECH_TO_TEXT

    val isTextToSpeech: Boolean
        get() = modelType == ModelType.TEXT_TO_SPEECH

    val isChatModel: Boolean
        get() = modelType == ModelType.CHAT_LLM

    val downloadProgress: Float
        get() {
            if (sizeBytes <= 0L || downloadedBytes <= 0L) return 0f
            val ratio = (downloadedBytes.toDouble() / sizeBytes.toDouble()).toFloat()
            return if (ratio.isNaN() || ratio.isInfinite()) 0f else ratio.coerceIn(0f, 1f)
        }
}

object ModelManifest {
    private fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
    }

    fun buildHuggingFaceResolveUrl(repository: String, filename: String): String {
        val parts = repository.split("/", limit = 2)
        require(parts.size == 2) { "Repository must be in '<owner>/<repo>' format. Received: $repository" }

        val owner = encodePathSegment(parts[0])
        val repo = encodePathSegment(parts[1])
        val encodedFilename = filename
            .split("/")
            .joinToString("/") { encodePathSegment(it) }

        return "https://huggingface.co/$owner/$repo/resolve/main/$encodedFilename"
    }

    fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.0f MB", mb)
        }
    }

    val entries: List<ModelManifestEntry> = listOf(
        ModelManifestEntry(
            id = ModelId.QWEN3_1_7B,
            displayName = "Qwen3 1.7B",
            tag = "Fast Assistant (Default)",
            description = "Fast, low-memory local assistant for on-device chat tasks.",
            repository = "ggml-org/Qwen3-1.7B-GGUF",
            filename = "Qwen3-1.7B-Q4_K_M.gguf",
            expectedSizeBytes = 1282439264L,
            sha256Expected = "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5",
            quantization = "Q4_K_M",
            modelType = ModelType.CHAT_LLM,
            contextLength = 40960,
            capabilities = listOf("chat", "reasoning", "tool-calling"),
            architecture = "qwen3",
            ramRequired = "1.6 GB",
            isDefault = true,
            isDownloadable = true
        ),
        ModelManifestEntry(
            id = ModelId.GEMMA3_1B,
            displayName = "Gemma 3 1B",
            tag = "4 GB Optimized",
            description = "Ultra-light Gemma model tuned for reliable low-memory on-device voice flows.",
            repository = "google/gemma-3-1b-it-qat-q4_0-gguf",
            filename = "gemma-3-1b-it-q4_0.gguf",
            expectedSizeBytes = 1003541152L,
            sha256Expected = null,
            quantization = "Q4_0 (QAT)",
            modelType = ModelType.CHAT_LLM,
            contextLength = 32768,
            capabilities = listOf("chat", "reasoning", "tool-calling"),
            architecture = "gemma3",
            ramRequired = "1.2 GB",
            isDownloadable = true
        ),
        ModelManifestEntry(
            id = ModelId.QWEN3_4B,
            displayName = "Qwen3 4B",
            tag = "Higher Quality Reasoning",
            description = "Higher quality local responses with stronger reasoning depth.",
            repository = "ggml-org/Qwen3-4B-GGUF",
            filename = "Qwen3-4B-Q4_K_M.gguf",
            expectedSizeBytes = 2497280640L,
            sha256Expected = "ab27b9bfa375a178d6cba48f3ad892b94b7739659dcc7aae8058ce0ffed6b328",
            quantization = "Q4_K_M",
            modelType = ModelType.CHAT_LLM,
            contextLength = 40960,
            capabilities = listOf("chat", "reasoning", "tool-calling"),
            architecture = "qwen3",
            ramRequired = "3.2 GB",
            isDownloadable = true
        ),
        ModelManifestEntry(
            id = ModelId.PHI4_MINI,
            displayName = "Phi-4 Mini",
            tag = "Coding / Reasoning",
            description = "Compact model optimized for coding and structured reasoning tasks.",
            repository = "second-state/Phi-4-mini-instruct-GGUF",
            filename = "Phi-4-mini-instruct-Q4_K_M.gguf",
            expectedSizeBytes = 2491874624L,
            sha256Expected = "55239fbd0dc947146be056e7850d2fa3a55d0091aa2cc873767e686c3b15eeed",
            quantization = "Q4_K_M",
            modelType = ModelType.CHAT_LLM,
            contextLength = 131072,
            capabilities = listOf("chat", "coding", "reasoning"),
            architecture = "phi4",
            ramRequired = "3.0 GB",
            isDownloadable = true
        ),
        ModelManifestEntry(
            id = ModelId.GEMMA3_4B,
            displayName = "Gemma 3 4B",
            tag = "General Assistant",
            description = "General-purpose Gemma model tuned for instruction-following.",
            repository = "bartowski/google_gemma-3-4b-it-GGUF",
            filename = "google_gemma-3-4b-it-Q4_K_M.gguf",
            expectedSizeBytes = 2489758112L,
            sha256Expected = "4996030242583a40aa151ff93f49ed787ac8c25e4120c3ae4588b2e2a7d1ae94",
            quantization = "Q4_K_M",
            modelType = ModelType.CHAT_LLM,
            contextLength = 131072,
            capabilities = listOf("chat", "reasoning", "analysis"),
            architecture = "gemma3",
            ramRequired = "3.0 GB",
            isDownloadable = true
        ),
        ModelManifestEntry(
            id = ModelId.WHISPER_BASE,
            displayName = "Whisper",
            tag = "Speech-to-Text",
            description = "Local whisper.cpp base.en model used automatically for microphone transcription.",
            repository = "ggerganov/whisper.cpp",
            filename = "ggml-base.en.bin",
            expectedSizeBytes = 147964211L,
            sha256Expected = "a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002",
            quantization = "FP16",
            modelType = ModelType.SPEECH_TO_TEXT,
            contextLength = 1500,
            capabilities = listOf("speech-to-text", "voice"),
            architecture = "whisper",
            ramRequired = "0.5 GB",
            isDownloadable = true
        ),
        ModelManifestEntry(
            id = ModelId.MOONSHINE_TINY_EN,
            displayName = "Moonshine Tiny EN",
            tag = "Speech-to-Text (Default)",
            description = "Fast English on-device STT via sherpa-onnx Moonshine Tiny INT8.",
            repository = null,
            filename = "sherpa-onnx-moonshine-tiny-en-int8.tar.bz2",
            expectedSizeBytes = 107600538L,
            sha256Expected = "d5fe6ec4334fef36255b2a4010412cad4c007e33103fec62fb5d17cad88086f2",
            quantization = "INT8",
            modelType = ModelType.SPEECH_TO_TEXT,
            contextLength = 16000,
            capabilities = listOf("speech-to-text", "voice", "fast-commands"),
            architecture = "moonshine",
            ramRequired = "0.6 GB",
            directDownloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-tiny-en-int8.tar.bz2",
            packageType = ModelPackageType.TAR_BZ2_ARCHIVE,
            requiredFiles = listOf(
                "preprocess.onnx",
                "encode.int8.onnx",
                "uncached_decode.int8.onnx",
                "cached_decode.int8.onnx",
                "tokens.txt"
            ),
            isDefault = true,
            isDownloadable = true
        ),
        ModelManifestEntry(
            id = ModelId.KOKORO_EN_INT8,
            displayName = "Kokoro EN INT8",
            tag = "Text-to-Speech (Default)",
            description = "Offline sherpa-onnx Kokoro text-to-speech with selectable voices.",
            repository = null,
            filename = "kokoro-int8-en-v0_19.tar.bz2",
            expectedSizeBytes = 103248205L,
            sha256Expected = "c9f0dd393615805b0bab050c340834d5e684e732aec91c0e860cd30e982c08bd",
            quantization = "INT8",
            modelType = ModelType.TEXT_TO_SPEECH,
            contextLength = 2048,
            capabilities = listOf("text-to-speech", "voice"),
            architecture = "kokoro",
            ramRequired = "0.8 GB",
            directDownloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-en-v0_19.tar.bz2",
            packageType = ModelPackageType.TAR_BZ2_ARCHIVE,
            requiredFiles = listOf(
                "model.int8.onnx",
                "voices.bin",
                "tokens.txt",
                "espeak-ng-data/"
            ),
            isDefault = true,
            isDownloadable = true
        )
    )
}

object ModelConstants {
    val INITIAL_MODELS: List<ModelInfo> = ModelManifest.entries.map { entry ->
        ModelInfo(
            id = entry.id,
            name = entry.displayName,
            tag = entry.tag,
            description = entry.description,
            sizeFormatted = entry.sizeFormatted,
            sizeBytes = entry.expectedSizeBytes,
            isDefault = entry.isDefault,
            sha256Expected = entry.sha256Expected,
            repository = entry.repository,
            filename = entry.filename,
            sourceUrl = entry.downloadUrl,
            contextSize = entry.contextLength,
            quant = entry.quantization,
            modelType = entry.modelType,
            capabilities = entry.capabilities,
            architecture = entry.architecture,
            ramRequired = entry.ramRequired,
            isDownloadable = entry.isDownloadable,
            packageType = entry.packageType,
            requiredFiles = entry.requiredFiles,
            state = ModelState.NOT_INSTALLED,
            isLoaded = false
        )
    }
}
