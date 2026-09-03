package com.myai.offline.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.myai.offline.data.model.ModelInfo
import com.myai.offline.data.model.TextToSpeechEngine
import com.myai.offline.data.model.VoiceOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max

class TtsManager(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private val TAG = "TtsManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var androidTts: TextToSpeech? = null
    private var isAndroidInitialized = false

    private val kokoroEngine = KokoroTtsEngine(context)
    private var kokoroPlaybackJob: Job? = null
    private var activeAudioTrack: AudioTrack? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _availableLanguages = MutableStateFlow<List<Locale>>(emptyList())
    val availableLanguages: StateFlow<List<Locale>> = _availableLanguages.asStateFlow()

    private val _teluguSupported = MutableStateFlow(false)
    val teluguSupported: StateFlow<Boolean> = _teluguSupported.asStateFlow()

    private val _preferredEngine = MutableStateFlow(TextToSpeechEngine.KOKORO)
    val preferredEngine: StateFlow<TextToSpeechEngine> = _preferredEngine.asStateFlow()

    private val _activeEngine = MutableStateFlow(TextToSpeechEngine.ANDROID_TTS)
    val activeEngine: StateFlow<TextToSpeechEngine> = _activeEngine.asStateFlow()

    private val _availableVoices = MutableStateFlow(kokoroEngine.availableVoices)
    val availableVoices: StateFlow<List<VoiceOption>> = _availableVoices.asStateFlow()

    private val _selectedVoiceId = MutableStateFlow(DEFAULT_KOKORO_VOICE_ID)
    val selectedVoiceId: StateFlow<Int> = _selectedVoiceId.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        androidTts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isAndroidInitialized = true
            Log.i(TAG, "Android TextToSpeech initialized successfully")

            androidTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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
                    _lastError.value = "Android TTS error code: $errorCode"
                    Log.w(TAG, "Android TTS utterance error code: $errorCode")
                }
            })

            val teluguLocale = Locale("te", "IN")
            val teluguAvailability = androidTts?.isLanguageAvailable(teluguLocale)
                ?: TextToSpeech.LANG_NOT_SUPPORTED
            _teluguSupported.value = teluguAvailability >= TextToSpeech.LANG_AVAILABLE

            _availableLanguages.value = androidTts?.availableLanguages?.toList() ?: listOf(Locale.US)
            onInitComplete(true)
        } else {
            isAndroidInitialized = false
            _lastError.value = "Android TextToSpeech initialization failed"
            Log.e(TAG, "Failed to initialize Android TextToSpeech")
            onInitComplete(false)
        }
    }

    suspend fun loadKokoroModel(modelInfo: ModelInfo): Boolean {
        val loaded = kokoroEngine.loadModel(modelInfo)
        if (!loaded) {
            _lastError.value = "Kokoro initialization failed"
            if (_preferredEngine.value == TextToSpeechEngine.KOKORO) {
                _activeEngine.value = TextToSpeechEngine.ANDROID_TTS
            }
            return false
        }

        _lastError.value = null
        if (_preferredEngine.value == TextToSpeechEngine.KOKORO) {
            _activeEngine.value = TextToSpeechEngine.KOKORO
        }
        return true
    }

    fun unloadKokoroModel() {
        stop()
        kokoroEngine.unloadModel()
        if (_activeEngine.value == TextToSpeechEngine.KOKORO) {
            _activeEngine.value = TextToSpeechEngine.ANDROID_TTS
        }
    }

    fun setPreferredEngine(engine: TextToSpeechEngine) {
        _preferredEngine.value = engine
        _activeEngine.value = when (engine) {
            TextToSpeechEngine.KOKORO -> {
                if (kokoroEngine.isModelLoaded) {
                    TextToSpeechEngine.KOKORO
                } else {
                    TextToSpeechEngine.ANDROID_TTS
                }
            }

            TextToSpeechEngine.ANDROID_TTS -> TextToSpeechEngine.ANDROID_TTS
        }
    }

    fun setKokoroVoice(voiceId: Int) {
        val exists = _availableVoices.value.any { it.id == voiceId }
        if (exists) {
            _selectedVoiceId.value = voiceId
        }
    }

    fun speak(text: String, rate: Float = 1.0f, pitch: Float = 1.0f, preferredLang: String = "auto"): Boolean {
        stop()

        val shouldUseKokoro = _preferredEngine.value == TextToSpeechEngine.KOKORO && kokoroEngine.isModelLoaded
        return if (shouldUseKokoro) {
            _activeEngine.value = TextToSpeechEngine.KOKORO
            speakWithKokoro(text = text, rate = rate)
        } else {
            _activeEngine.value = TextToSpeechEngine.ANDROID_TTS
            speakWithAndroid(text = text, rate = rate, pitch = pitch, preferredLang = preferredLang)
        }
    }

    private fun speakWithKokoro(text: String, rate: Float): Boolean {
        val voiceId = _selectedVoiceId.value
        kokoroPlaybackJob = scope.launch {
            _isSpeaking.value = true
            val speed = rate.coerceIn(0.6f, 1.8f)
            val generated = kokoroEngine.synthesize(text, voiceId, speed)

            if (generated == null) {
                _isSpeaking.value = false
                _lastError.value = "Kokoro synthesis failed"
                _activeEngine.value = TextToSpeechEngine.ANDROID_TTS
                return@launch
            }

            val playbackOk = playGeneratedAudio(generated)
            if (!playbackOk) {
                _lastError.value = "Kokoro playback failed"
                _activeEngine.value = TextToSpeechEngine.ANDROID_TTS
            } else {
                _lastError.value = null
            }
            _isSpeaking.value = false
        }

        return true
    }

    private fun playGeneratedAudio(result: KokoroSynthesisResult): Boolean {
        return try {
            val shortSamples = ShortArray(result.samples.size)
            for (i in result.samples.indices) {
                val scaled = (result.samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
                shortSamples[i] = scaled.toShort()
            }

            val minBufferSize = AudioTrack.getMinBufferSize(
                result.sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(result.sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                max(minBufferSize, shortSamples.size * 2),
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            activeAudioTrack = track
            track.play()

            var offset = 0
            while (offset < shortSamples.size) {
                val written = track.write(shortSamples, offset, minOf(2048, shortSamples.size - offset))
                if (written <= 0) {
                    break
                }
                offset += written
            }

            runCatching { track.stop() }
            runCatching { track.release() }
            activeAudioTrack = null
            true
        } catch (e: Exception) {
            Log.e(TAG, "Kokoro playback failed", e)
            runCatching { activeAudioTrack?.stop() }
            runCatching { activeAudioTrack?.release() }
            activeAudioTrack = null
            false
        }
    }

    private fun speakWithAndroid(text: String, rate: Float, pitch: Float, preferredLang: String): Boolean {
        if (!isAndroidInitialized || androidTts == null) {
            _lastError.value = "Android TTS not ready"
            Log.w(TAG, "Android TTS not ready")
            return false
        }

        androidTts?.setSpeechRate(rate)
        androidTts?.setPitch(pitch)

        val targetLocale = when (preferredLang) {
            "te-IN" -> if (_teluguSupported.value) Locale("te", "IN") else Locale.US
            "en-US" -> Locale.US
            else -> {
                val hasTelugu = text.any { it in '\u0C00'..'\u0C7F' }
                if (hasTelugu && _teluguSupported.value) Locale("te", "IN") else Locale.US
            }
        }

        runCatching { androidTts?.language = targetLocale }
            .onFailure { androidTts?.language = Locale.US }

        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("[#*`_~>\\[\\]]"), "")
            .trim()

        if (cleanText.isBlank()) {
            return false
        }

        val utteranceId = "utterance_${System.currentTimeMillis()}"
        val result = androidTts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        return result == TextToSpeech.SUCCESS
    }

    fun stop() {
        runCatching {
            kokoroPlaybackJob?.cancel()
            kokoroPlaybackJob = null
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
            activeAudioTrack = null
            androidTts?.stop()
            _isSpeaking.value = false
        }.onFailure {
            Log.w(TAG, "Error stopping TTS playback: ${it.message}")
        }
    }

    fun shutdown() {
        stop()
        runCatching { kokoroEngine.unloadModel() }

        runCatching {
            androidTts?.shutdown()
            androidTts = null
        }.onFailure {
            Log.w(TAG, "Error shutting down Android TTS: ${it.message}")
        }

        scope.cancel()
    }

    companion object {
        private const val DEFAULT_KOKORO_VOICE_ID = 0
    }
}
