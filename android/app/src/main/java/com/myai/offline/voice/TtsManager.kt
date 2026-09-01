package com.myai.offline.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private val TAG = "TtsManager"
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _availableLanguages = MutableStateFlow<List<Locale>>(emptyList())
    val availableLanguages: StateFlow<List<Locale>> = _availableLanguages.asStateFlow()

    private val _teluguSupported = MutableStateFlow(false)
    val teluguSupported: StateFlow<Boolean> = _teluguSupported.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            Log.i(TAG, "Android TextToSpeech initialized successfully")

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    Log.w(TAG, "TTS Utterance error code: $errorCode")
                }
            })

            // Check Telugu support
            val teluguLocale = Locale("te", "IN")
            val teCheck = tts?.isLanguageAvailable(teluguLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            _teluguSupported.value = (teCheck >= TextToSpeech.LANG_AVAILABLE)

            val available = tts?.availableLanguages?.toList() ?: listOf(Locale.US)
            _availableLanguages.value = available

            onInitComplete(true)
        } else {
            isInitialized = false
            Log.e(TAG, "Failed to initialize Android TextToSpeech")
            onInitComplete(false)
        }
    }

    fun speak(text: String, rate: Float = 1.0f, pitch: Float = 1.0f, preferredLang: String = "auto"): Boolean {
        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS not ready yet")
            return false
        }

        stop()

        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)

        // Set Language
        val targetLocale = when (preferredLang) {
            "te-IN" -> {
                if (_teluguSupported.value) Locale("te", "IN") else Locale.US
            }
            "en-US" -> Locale.US
            else -> {
                // Auto detect if text contains Telugu unicode characters [0x0C00 - 0x0C7F]
                val hasTelugu = text.any { it in '\u0C00'..'\u0C7F' }
                if (hasTelugu && _teluguSupported.value) Locale("te", "IN") else Locale.US
            }
        }

        try {
            tts?.language = targetLocale
        } catch (e: Exception) {
            tts?.language = Locale.US
        }

        val cleanText = text.replace(Regex("```[\\s\\S]*?```"), "") // Strip code blocks before reading
            .replace(Regex("[#*`_~>\\[\\]]"), "")
            .trim()

        if (cleanText.isBlank()) return false

        val utteranceId = "utterance_${System.currentTimeMillis()}"
        val result = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        return result == TextToSpeech.SUCCESS
    }

    fun stop() {
        try {
            tts?.stop()
            _isSpeaking.value = false
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS: ${e.message}")
        }
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS: ${e.message}")
        }
    }
}
