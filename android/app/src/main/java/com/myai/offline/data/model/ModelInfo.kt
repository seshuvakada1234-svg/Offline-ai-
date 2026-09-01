package com.myai.offline.data.model

enum class ModelId(val rawValue: String) {
    QWEN3_1_7B("qwen3-1.7b"),
    QWEN3_4B("qwen3-4b"),
    PHI4_MINI("phi4-mini"),
    GEMMA3_4B("gemma3-4b"),
    GEMMA3_270M("gemma3-270m"),
    WHISPER_BASE("whisper-base");

    companion object {
        fun fromRaw(raw: String): ModelId =
            values().firstOrNull { it.rawValue == raw } ?: QWEN3_1_7B
    }
}

enum class ModelState {
    NOT_INSTALLED,
    CHECKING_STORAGE,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    READY,
    LOADING,
    ERROR
}

data class ModelInfo(
    val id: ModelId,
    val name: String,
    val tag: String,
    val description: String,
    val sizeFormatted: String,
    val sizeBytes: Long,
    val isDefault: Boolean = false,
    val sha256Expected: String,
    val filename: String,
    val sourceUrl: String,
    val contextSize: Int,
    val quant: String,
    val backend: String, // "llama.cpp" or "whisper.cpp"
    val architecture: String,
    val downloadSpeed: String? = null,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val state: ModelState = ModelState.NOT_INSTALLED,
    val errorMessage: String? = null,
    val isLoaded: Boolean = false
)

object ModelConstants {
    const val TOTAL_DEVICE_STORAGE_BYTES: Long = 64L * 1024L * 1024L * 1024L // 64 GB
    const val SYSTEM_USED_STORAGE_BYTES: Long = 26L * 1024L * 1024L * 1024L // 26 GB baseline

    val INITIAL_MODELS = listOf(
        ModelInfo(
            id = ModelId.QWEN3_1_7B,
            name = "Qwen3 1.7B",
            tag = "Fast Assistant (Default)",
            description = "Ultra-fast, highly optimized model for Android voice & chat assistant tasks with low memory footprint.",
            sizeFormatted = "1.18 GB",
            sizeBytes = 1266679808L,
            isDefault = true,
            sha256Expected = "9a4f218c347b0e3568c09a842183e29f032e1858a74e9b98031d234ea576ef12",
            filename = "qwen3-1.7b-instruct-q4_k_m.gguf",
            sourceUrl = "https://huggingface.co/Qwen/Qwen3-1.7B-Instruct-GGUF/resolve/main/qwen3-1.7b-instruct-q4_k_m.gguf",
            contextSize = 4096,
            quant = "Q4_K_M",
            backend = "llama.cpp",
            architecture = "qwen3",
            state = ModelState.READY, // Default ready for immediate local testing
            isLoaded = true
        ),
        ModelInfo(
            id = ModelId.QWEN3_4B,
            name = "Qwen3 4B",
            tag = "Higher Quality Reasoning",
            description = "Enhanced conversational depth, complex step-by-step logic, and advanced task execution.",
            sizeFormatted = "2.49 GB",
            sizeBytes = 2673868800L,
            isDefault = false,
            sha256Expected = "d64e819b56f8a8475c879d09c2a38210f845012586b5107936a2185bcdef0139",
            filename = "qwen3-4b-instruct-q4_k_m.gguf",
            sourceUrl = "https://huggingface.co/Qwen/Qwen3-4B-Instruct-GGUF/resolve/main/qwen3-4b-instruct-q4_k_m.gguf",
            contextSize = 8192,
            quant = "Q4_K_M",
            backend = "llama.cpp",
            architecture = "qwen3",
            state = ModelState.NOT_INSTALLED,
            isLoaded = false
        ),
        ModelInfo(
            id = ModelId.PHI4_MINI,
            name = "Phi-4 Mini",
            tag = "Coding / Reasoning",
            description = "Microsoft compact model engineered for mathematical reasoning and code generation.",
            sizeFormatted = "2.31 GB",
            sizeBytes = 2480406528L,
            isDefault = false,
            sha256Expected = "f87a220d91c7809247610190823528bca612e45781a9501869e6b41829e012fe",
            filename = "phi-4-mini-instruct-q4_k_m.gguf",
            sourceUrl = "https://huggingface.co/microsoft/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-q4_k_m.gguf",
            contextSize = 4096,
            quant = "Q4_K_M",
            backend = "llama.cpp",
            architecture = "phi4",
            state = ModelState.NOT_INSTALLED,
            isLoaded = false
        ),
        ModelInfo(
            id = ModelId.GEMMA3_4B,
            name = "Gemma 3 4B",
            tag = "General / Multimodal",
            description = "Google Gemma 3 architecture for high-precision understanding and balanced execution.",
            sizeFormatted = "2.72 GB",
            sizeBytes = 2920577024L,
            isDefault = false,
            sha256Expected = "3a18e0018f912c759083510c490a612574e892019a584031a7428e19c017d45e",
            filename = "gemma-3-4b-it-q4_k_m.gguf",
            sourceUrl = "https://huggingface.co/google/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-q4_k_m.gguf",
            contextSize = 8192,
            quant = "Q4_K_M",
            backend = "llama.cpp",
            architecture = "gemma3",
            state = ModelState.NOT_INSTALLED,
            isLoaded = false
        ),
        ModelInfo(
            id = ModelId.GEMMA3_270M,
            name = "Gemma 3 270M",
            tag = "Ultra-Fast Simple Tasks",
            description = "Lightweight micro-model for instantaneous offline keyword and intent triage on edge devices.",
            sizeFormatted = "190 MB",
            sizeBytes = 199229440L,
            isDefault = false,
            sha256Expected = "7b801a2c94d6e90185a7304192d6e4092185c7429185a034185c0192e4781290",
            filename = "gemma-3-270m-it-q4_k_m.gguf",
            sourceUrl = "https://huggingface.co/google/gemma-3-270m-it-GGUF/resolve/main/gemma-3-270m-it-q4_k_m.gguf",
            contextSize = 2048,
            quant = "Q4_K_M",
            backend = "llama.cpp",
            architecture = "gemma3",
            state = ModelState.NOT_INSTALLED,
            isLoaded = false
        ),
        ModelInfo(
            id = ModelId.WHISPER_BASE,
            name = "Whisper Base STT",
            tag = "Local Speech Recognition",
            description = "Multilingual automatic speech recognition supporting English, Telugu, and code-mixed speech.",
            sizeFormatted = "142 MB",
            sizeBytes = 148897792L,
            isDefault = false,
            sha256Expected = "a2073a44e761665b35073406774ee80f9308264734f703407ec43a184d0b2cb3",
            filename = "ggml-base.bin",
            sourceUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
            contextSize = 1500,
            quant = "FP16",
            backend = "whisper.cpp",
            architecture = "whisper",
            state = ModelState.READY,
            isLoaded = true
        )
    )
}
