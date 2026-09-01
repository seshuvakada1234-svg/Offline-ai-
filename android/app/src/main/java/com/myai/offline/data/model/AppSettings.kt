package com.myai.offline.data.model

data class AppSettings(
    val language: String = "auto", // "en-US", "te-IN", "auto"
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val autoSpeakResponse: Boolean = false,
    val inferenceThreads: Int = 4,
    val contextLength: Int = 4096,
    val maxOutputTokens: Int = 1024,
    val keepModelLoaded: Boolean = true,
    val showPerformanceStats: Boolean = true,
    val isDarkTheme: Boolean = true
)
