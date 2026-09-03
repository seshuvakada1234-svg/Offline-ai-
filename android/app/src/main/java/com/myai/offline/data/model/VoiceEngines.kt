package com.myai.offline.data.model

enum class SpeechToTextEngine {
    MOONSHINE_TINY,
    WHISPER_BASE
}

enum class TextToSpeechEngine {
    KOKORO,
    ANDROID_TTS
}

data class VoiceOption(
    val id: Int,
    val label: String
)
