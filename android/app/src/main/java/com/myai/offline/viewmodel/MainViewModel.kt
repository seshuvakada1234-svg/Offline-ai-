package com.myai.offline.viewmodel

import android.app.Application
import android.content.Context
import android.os.BatteryManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myai.offline.actions.AndroidActionHandler
import com.myai.offline.actions.ActionValidator
import com.myai.offline.assistant.AssistantResponsePipeline
import com.myai.offline.assistant.ResponsePhase
import androidx.room.withTransaction
import com.myai.offline.data.database.AppDatabase
import com.myai.offline.data.database.ConversationEntity
import com.myai.offline.data.database.MessageEntity
import com.myai.offline.data.model.AssistantAction
import com.myai.offline.data.model.InferenceMetrics
import com.myai.offline.data.model.ModelId
import com.myai.offline.data.model.ModelInfo
import com.myai.offline.data.model.ModelState
import com.myai.offline.data.model.SpeechToTextEngine
import com.myai.offline.data.model.TextToSpeechEngine
import com.myai.offline.data.model.VoiceOption
import com.myai.offline.data.model.VoiceState
import com.myai.offline.data.repository.ModelRepository
import com.myai.offline.llm.ILocalLLMEngine
import com.myai.offline.llm.LocalLLMEngine
import com.myai.offline.voice.AudioRecorder
import com.myai.offline.voice.MoonshineEngine
import com.myai.offline.voice.TtsManager
import com.myai.offline.voice.WhisperEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MainViewModel"
    private val db = AppDatabase.getInstance(application)
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()
    private val modelRepository = ModelRepository(application)
    private val llmEngine: ILocalLLMEngine = LocalLLMEngine(application)
    private val whisperEngine = WhisperEngine(application)
    private val moonshineEngine = MoonshineEngine(application)
    private val audioRecorder = AudioRecorder(application)
    private val actionHandler = AndroidActionHandler(application)
    private val ttsManager = TtsManager(application)

    val conversations = conversationDao.getAllConversations()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    val models: StateFlow<List<ModelInfo>> = modelRepository.models

    private val _selectedModelId = MutableStateFlow(ModelId.QWEN3_1_7B)
    val selectedModelId: StateFlow<ModelId> = _selectedModelId.asStateFlow()

    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _responsePhase = MutableStateFlow(ResponsePhase.IDLE)
    val responsePhase: StateFlow<ResponsePhase> = _responsePhase.asStateFlow()

    private val _streamingMessage = MutableStateFlow("")
    val streamingMessage: StateFlow<String> = _streamingMessage.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _voiceTranscript = MutableStateFlow("")
    val voiceTranscript: StateFlow<String> = _voiceTranscript.asStateFlow()

    private val _selectedSttEngine = MutableStateFlow(SpeechToTextEngine.MOONSHINE_TINY)
    val selectedSttEngine: StateFlow<SpeechToTextEngine> = _selectedSttEngine.asStateFlow()

    val selectedTtsEngine: StateFlow<TextToSpeechEngine> = ttsManager.preferredEngine
    val activeTtsEngine: StateFlow<TextToSpeechEngine> = ttsManager.activeEngine
    val availableKokoroVoices: StateFlow<List<VoiceOption>> = ttsManager.availableVoices
    val selectedKokoroVoiceId: StateFlow<Int> = ttsManager.selectedVoiceId

    val audioLevel: StateFlow<Float> = audioRecorder.audioLevel

    val isTtsSpeaking: StateFlow<Boolean> = ttsManager.isSpeaking
    private val _currentlySpeakingMessageId = MutableStateFlow<String?>(null)
    val currentlySpeakingMessageId: StateFlow<String?> = _currentlySpeakingMessageId.asStateFlow()

    private val _deviceRamUsageMb = MutableStateFlow(0L)
    val deviceRamUsageMb: StateFlow<Long> = _deviceRamUsageMb.asStateFlow()

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val responsePipeline = AssistantResponsePipeline()
    private val backgroundTasks = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class Request(
        val conversationId: String,
        val userText: String,
        val assistantMessageId: String = UUID.randomUUID().toString(),
        val startedAt: Long = System.currentTimeMillis(),
        var partialText: String = "",
        var finalText: String? = null
    )
    private var activeRequest: Request? = null
    private var messageCollectionJob: Job? = null
    private var activeGenerationJob: Job? = null
    private var voiceJob: Job? = null
    private var voiceSession = 0L
    private val speechMutex = Mutex()
    private val modelActivationMutex = Mutex()
    private var moonshineAutoInitAttempted = false
    private var whisperAutoInitAttempted = false
    private var kokoroAutoInitAttempted = false
    private var activeListeningSttEngine = SpeechToTextEngine.MOONSHINE_TINY

    init {
        viewModelScope.launch {
            modelRepository.selectedModelId.collect { selected ->
                _selectedModelId.value = selected
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            modelRepository.models.collect { modelList ->
                autoInitializeSpeechModels(modelList)
            }
        }

        viewModelScope.launch {
            ttsManager.isSpeaking.collect { speaking ->
                if (!speaking && _voiceState.value == VoiceState.SPEAKING) {
                    _voiceState.value = VoiceState.IDLE
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { createNewConversation() }
            modelRepository.checkLocalModelFiles()
            activateStartupDefaultModel()
            updateDeviceMetrics()
        }
    }

    private suspend fun activateStartupDefaultModel() {
        val preferredIds = buildList {
            add(ModelId.QWEN3_1_7B)
            add(modelRepository.selectedModelId.value)
            addAll(
                modelRepository.models.value
                    .filter { it.isChatModel && it.state == ModelState.READY }
                    .map { it.id }
            )
        }.distinct()

        for (id in preferredIds) {
            if (activateModelInternal(id, persistSelection = true)) {
                return
            }
        }
    }

    private suspend fun autoInitializeSpeechModels(modelList: List<ModelInfo>) = speechMutex.withLock {
        try {
            val readyStates = setOf(ModelState.READY, ModelState.ACTIVE)

        val moonshineModel = modelList.firstOrNull { it.id == ModelId.MOONSHINE_TINY_EN }
        val whisperModel = modelList.firstOrNull { it.id == ModelId.WHISPER_BASE }
        val kokoroModel = modelList.firstOrNull { it.id == ModelId.KOKORO_EN_INT8 }

        val moonshineReady = moonshineModel?.state in readyStates
        if (!moonshineReady) {
            moonshineAutoInitAttempted = false
        } else if (!moonshineEngine.isModelLoaded && !moonshineAutoInitAttempted && moonshineModel != null) {
            moonshineAutoInitAttempted = true
            val loaded = moonshineEngine.loadModel(moonshineModel)
            if (loaded) {
                Log.i(TAG, "[MOONSHINE_AUTO_INIT] Moonshine initialized automatically")
            } else {
                Log.w(TAG, "[MOONSHINE_AUTO_INIT] Moonshine model present but initialization failed")
                if (_selectedSttEngine.value == SpeechToTextEngine.MOONSHINE_TINY) {
                    _selectedSttEngine.value = SpeechToTextEngine.WHISPER_BASE
                }
            }
        }

        val whisperReady = whisperModel?.state in readyStates
        if (!whisperReady) {
            whisperAutoInitAttempted = false
        } else if (!whisperEngine.isModelLoaded && !whisperAutoInitAttempted && whisperModel != null) {
            whisperAutoInitAttempted = true
            val loaded = whisperEngine.loadModel(whisperModel)
            if (loaded) {
                Log.i(TAG, "[WHISPER_AUTO_INIT] Whisper initialized automatically")
            } else {
                Log.w(TAG, "[WHISPER_AUTO_INIT] Whisper model present but initialization failed")
            }
        }

        val kokoroReady = kokoroModel?.state in readyStates
        if (!kokoroReady) {
            kokoroAutoInitAttempted = false
        } else if (!ttsManager.isKokoroLoaded && !kokoroAutoInitAttempted && kokoroModel != null) {
            kokoroAutoInitAttempted = true
            val loaded = ttsManager.loadKokoroModel(kokoroModel)
            if (loaded) {
                Log.i(TAG, "[KOKORO_AUTO_INIT] Kokoro initialized automatically")
            } else {
                Log.w(TAG, "[KOKORO_AUTO_INIT] Kokoro model present but initialization failed")
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Log.e(TAG, "[SPEECH_AUTO_INIT] Failed during speech auto-init: ${t.message}", t)
    }
    Unit
}

    fun updateComposerText(text: String) {
        _composerText.value = text
    }

    fun createNewConversation() {
        cancelVoice()
        val newId = UUID.randomUUID().toString()
        _currentConversationId.value = newId
        _messages.value = emptyList()

        // Persist the conversation atomically with its first user message.
        observeConversation(newId)
    }

    fun selectConversation(id: String) {
        cancelVoice()
        _currentConversationId.value = id
        observeConversation(id)
    }

    private fun observeConversation(id: String) {
        messageCollectionJob?.cancel()
        messageCollectionJob = viewModelScope.launch {
            messageDao.getMessagesForConversation(id).collect { list ->
                _messages.value = list
            }
        }
    }

    fun selectModel(id: ModelId) {
        viewModelScope.launch {
            cancelVoice()
            val activated = activateModelInternal(id, persistSelection = true)
            if (!activated) {
                val modelName = modelRepository.getModel(id)?.name ?: id.rawValue
                Log.e(TAG, "[MODEL_LOAD_FAILED] Unable to activate model $modelName")
            }
        }
    }

    private suspend fun activateModelInternal(id: ModelId, persistSelection: Boolean): Boolean = modelActivationMutex.withLock {
        if (llmEngine.isModelLoaded && llmEngine.currentLoadedModel?.id == id) {
            modelRepository.markModelActive(id)
            if (persistSelection) modelRepository.selectModel(id)
            return@withLock true
        }
        activateModelLocked(id, persistSelection)
    }

    private suspend fun activateModelLocked(id: ModelId, persistSelection: Boolean): Boolean {
        val model = modelRepository.getModel(id) ?: return false
        if (!model.isChatModel) return false

        if (model.state !in setOf(ModelState.READY, ModelState.ACTIVE, ModelState.ERROR)) {
            Log.w(TAG, "Model ${model.name} is not loadable. state=${model.state}")
            return false
        }

        if (persistSelection && !modelRepository.selectModel(id)) {
            return false
        }

        _selectedModelId.value = id
        modelRepository.markModelLoading(id)

        return try {
            withTimeout(MODEL_LOAD_TIMEOUT_MS) {
                llmEngine.loadModel(
                    model = model,
                    threads = recommendedThreadCount(),
                    ctxSize = minOf(DEFAULT_RUNTIME_CONTEXT, model.contextSize)
                )
            }
            modelRepository.markModelActive(id)
            Log.i(TAG, "[MODEL_LOAD_SUCCESS] Activated model ${model.name}")
            true
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            modelRepository.markModelLoadFailed(id, "Model loading timed out. Please try again.")
            false
        } catch (e: CancellationException) {
            modelRepository.markModelReady(id)
            throw e
        } catch (e: Exception) {
            val message = e.localizedMessage ?: "Unable to load model"
            modelRepository.markModelLoadFailed(id, message)
            Log.e(TAG, "[MODEL_LOAD_FAILED] ${model.name}: $message", e)
            false
        } finally {
            if (modelRepository.getModel(id)?.state == ModelState.LOADING) {
                modelRepository.markModelReady(id)
            }
        }
    }

    fun sendMessage(userText: String, isVoice: Boolean = false) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            val conversationId = _currentConversationId.value ?: return@launch
            val previous = activeRequest
            if (previous?.conversationId == conversationId && previous.userText == trimmed &&
                System.currentTimeMillis() - previous.startedAt < DUPLICATE_WINDOW_MS
            ) return@launch

            stopGeneration()
            val request = Request(conversationId, trimmed)
            activeRequest = request
            _composerText.value = ""
            _isGenerating.value = true
            _responsePhase.value = ResponsePhase.GENERATING
            _streamingMessage.value = ""

            activeGenerationJob = viewModelScope.launch {
                var metrics: InferenceMetrics? = null
                try {
                    withTimeout(REQUEST_TIMEOUT_MS) {
                        db.withTransaction {
                            if (conversationDao.getConversationById(conversationId) == null) {
                                conversationDao.insertConversation(ConversationEntity(
                                    id = conversationId,
                                    title = trimmed.take(30),
                                    selectedModelId = _selectedModelId.value.rawValue
                                ))
                            }
                            messageDao.insertMessage(MessageEntity(
                                conversationId = conversationId,
                                role = "user",
                                content = trimmed,
                                timestamp = request.startedAt,
                                isVoiceInput = isVoice
                            ))
                        }
                        val response = responsePipeline.respond(
                            userText = trimmed,
                            generate = { generateResponse(conversationId, trimmed) { metrics = it } },
                            execute = { executeActionSafely(it, "explicit_user_request") },
                            onPhase = { phase ->
                                if (activeRequest === request) {
                                    _responsePhase.value = phase
                                    if (isVoice) {
                                        _voiceState.value = when (phase) {
                                            ResponsePhase.GENERATING -> VoiceState.THINKING
                                            ResponsePhase.EXECUTING_ACTION -> VoiceState.ACTION_EXECUTING
                                            ResponsePhase.IDLE -> VoiceState.IDLE
                                        }
                                    }
                                }
                            },
                            onText = { fullText ->
                                request.partialText = fullText
                                if (activeRequest === request) _streamingMessage.value = fullText
                            }
                        )
                        request.finalText = response.text
                        insertAssistantMessage(conversationId, response.text, request.assistantMessageId, metrics, response.action,
                            timestamp = request.startedAt + 1)
                        val shouldSpeak = isVoice && activeRequest === request
                        finishRequest(request)
                        if (shouldSpeak) {
                            _voiceTranscript.value = response.text
                            speakMessage(request.assistantMessageId, response.text)
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    finishRequest(request)
                    saveInterruptedResponse(request, "The request timed out. Please try again.")
                } catch (e: CancellationException) {
                    finishRequest(request)
                    saveInterruptedResponse(request, "Response stopped.")
                    throw e
                } catch (e: Exception) {
                    finishRequest(request)
                    saveInterruptedResponse(request, "Unable to complete the request: ${e.localizedMessage ?: "unknown error"}")
                } finally {
                    finishRequest(request)
                }
            }
        }
    }

    private fun generateResponse(
        conversationId: String,
        userQuery: String,
        onMetrics: (InferenceMetrics) -> Unit
    ): Flow<String> = flow {
        val model = modelRepository.getModel(_selectedModelId.value)
            ?: error("Select a local model to chat.")
        check(model.state != ModelState.NOT_INSTALLED) { "Download ${model.name} to chat." }
        if (!llmEngine.isModelLoaded || llmEngine.currentLoadedModel?.id != model.id) {
            check(activateModelInternal(model.id, persistSelection = true)) {
                modelRepository.getModel(model.id)?.errorMessage ?: "Unable to load ${model.name}."
            }
        }
        val history = messageDao.getMessagesList(conversationId)
            .dropLastWhile { it.role == "user" && it.content == userQuery }
            .takeLast(4).map { it.role to it.content }
        val prompt = llmEngine.formatPrompt(
            modelId = model.id,
            conversationHistory = history,
            userQuery = userQuery,
            enableThinking = false
        )
        llmEngine.generateStreaming(
            prompt = prompt,
            userQuery = userQuery,
            maxTokens = if (userQuery.length <= 30) 128 else 256,
            onMetricsCalculated = onMetrics
        ).collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    private suspend fun saveInterruptedResponse(request: Request, reason: String) {
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching {
                withTimeout(SAVE_TIMEOUT_MS) {
                    if (conversationDao.getConversationById(request.conversationId) != null) {
                        val text = request.finalText ?: listOf(request.partialText.trim(), reason)
                            .filter { it.isNotBlank() }.joinToString("\n\n")
                        insertAssistantMessage(request.conversationId, text, request.assistantMessageId,
                            timestamp = request.startedAt + 1)
                    }
                }
            }.onFailure { Log.e(TAG, "Unable to save interrupted response", it) }
        }
    }

    private fun finishRequest(request: Request) {
        // An old cancelled job must never clear a newer request's state.
        if (activeRequest === request) {
            activeRequest = null
            activeGenerationJob = null
            _isGenerating.value = false
            _responsePhase.value = ResponsePhase.IDLE
            _streamingMessage.value = ""
            _voiceState.value = VoiceState.IDLE
        }
    }

    fun stopGeneration() {
        try {
            llmEngine.stopGeneration()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to signal native generation cancellation", e)
        } finally {
            activeGenerationJob?.cancel()
            activeGenerationJob = null
            activeRequest = null
            _isGenerating.value = false
            _responsePhase.value = ResponsePhase.IDLE
            _streamingMessage.value = ""
            _voiceState.value = VoiceState.IDLE
        }
    }

    fun startVoiceListening() {
        if (_voiceState.value != VoiceState.IDLE) return
        stopGeneration()
        stopSpeaking()
        val session = ++voiceSession
        _voiceState.value = VoiceState.TRANSCRIBING
        _voiceTranscript.value = "Preparing microphone..."
        voiceJob = viewModelScope.launch {
            try {
                val selectedRuntime = withTimeout(VOICE_TIMEOUT_MS) {
                    backgroundOperation {
                        speechMutex.withLock {
                            val preferredOrder = if (_selectedSttEngine.value == SpeechToTextEngine.MOONSHINE_TINY) {
                                listOf(SpeechToTextEngine.MOONSHINE_TINY, SpeechToTextEngine.WHISPER_BASE)
                            } else {
                                listOf(SpeechToTextEngine.WHISPER_BASE, SpeechToTextEngine.MOONSHINE_TINY)
                            }
                            preferredOrder.firstOrNull { engine ->
                                when (engine) {
                                    SpeechToTextEngine.MOONSHINE_TINY -> prepareMoonshineEngine()
                                    SpeechToTextEngine.WHISPER_BASE -> prepareWhisperEngine()
                                }
                            }
                        }
                    }
                }
                checkNotNull(selectedRuntime) { "Install Moonshine Tiny or Whisper Base.en to use voice input." }
                currentCoroutineContext().ensureActive()
                activeListeningSttEngine = selectedRuntime
                audioRecorder.startRecording(viewModelScope)
                check(audioRecorder.isRecording.value) { "Unable to start the microphone. Check microphone permission." }
                _voiceTranscript.value = ""
                _voiceState.value = VoiceState.LISTENING
            } catch (e: TimeoutCancellationException) {
                if (session == voiceSession) showVoiceError("Voice initialization timed out. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (session == voiceSession) showVoiceError(e.localizedMessage ?: "Unable to start voice input.")
            } finally {
                if (session == voiceSession) {
                    voiceJob = null
                    if (_voiceState.value == VoiceState.TRANSCRIBING) _voiceState.value = VoiceState.IDLE
                }
            }
        }
    }

    fun stopVoiceListening() {
        if (_voiceState.value != VoiceState.LISTENING) return
        val session = ++voiceSession
        _voiceState.value = VoiceState.TRANSCRIBING
        val pcmAudio = audioRecorder.stopRecording()
        voiceJob = viewModelScope.launch {
            try {
                val transcript = withTimeout(VOICE_TIMEOUT_MS) {
                    backgroundOperation {
                        speechMutex.withLock {
                            when (activeListeningSttEngine) {
                                SpeechToTextEngine.MOONSHINE_TINY -> moonshineEngine.transcribe(pcmAudio)
                                SpeechToTextEngine.WHISPER_BASE -> whisperEngine.transcribe(pcmAudio)
                            }
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                check(transcript.isNotBlank()) { "No speech detected." }
                _voiceTranscript.value = transcript
                _voiceState.value = VoiceState.IDLE
                // Voice and keyboard input share exactly one response/action pipeline.
                sendMessage(transcript, isVoice = true)
            } catch (e: TimeoutCancellationException) {
                if (session == voiceSession) showVoiceError("Speech recognition timed out. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (session == voiceSession) showVoiceError(e.localizedMessage ?: "Speech recognition failed.")
            } finally {
                if (session == voiceSession) {
                    voiceJob = null
                    if (_voiceState.value == VoiceState.TRANSCRIBING) _voiceState.value = VoiceState.IDLE
                }
            }
        }
    }

    fun cancelVoice() {
        ++voiceSession
        voiceJob?.cancel()
        voiceJob = null
        audioRecorder.stopRecording()
        stopGeneration()
        stopSpeaking()
        _voiceState.value = VoiceState.IDLE
        _voiceTranscript.value = ""
    }

    private fun showVoiceError(message: String) {
        _voiceTranscript.value = message
        _voiceState.value = VoiceState.ERROR
    }

    private suspend fun <T> backgroundOperation(block: suspend () -> T): T {
        val task = backgroundTasks.async { block() }
        try {
            return task.await()
        } finally {
            task.cancel()
        }
    }

    fun selectSpeechToTextEngine(engine: SpeechToTextEngine) {
        _selectedSttEngine.value = engine
    }

    fun selectTextToSpeechEngine(engine: TextToSpeechEngine) {
        ttsManager.setPreferredEngine(engine)
    }

    fun selectKokoroVoice(voiceId: Int) {
        ttsManager.setKokoroVoice(voiceId)
    }

    private suspend fun prepareMoonshineEngine(): Boolean {
        val moonshineModel = modelRepository.getModel(ModelId.MOONSHINE_TINY_EN)
            ?: return false
        if (moonshineModel.state !in setOf(ModelState.READY, ModelState.ACTIVE)) {
            return false
        }

        if (moonshineEngine.isModelLoaded) {
            return true
        }

        val loaded = moonshineEngine.loadModel(moonshineModel)
        if (!loaded) {
            Log.w(TAG, "[MOONSHINE_INIT] Unable to initialize Moonshine Tiny EN")
        }
        return loaded
    }

    private suspend fun prepareWhisperEngine(): Boolean {
        val whisperModel = modelRepository.getModel(ModelId.WHISPER_BASE)
            ?: return false
        if (whisperModel.state !in setOf(ModelState.READY, ModelState.ACTIVE)) {
            return false
        }

        if (whisperEngine.isModelLoaded) {
            return true
        }

        val loaded = whisperEngine.loadModel(whisperModel)
        if (!loaded) {
            Log.w(TAG, "[WHISPER_INIT] Unable to initialize Whisper Base.en")
        }
        return loaded
    }

    fun executeAction(action: AssistantAction) {
        if (ActionValidator.validate(action) !is ActionValidator.ValidationResult.Valid) return
        val explicitRequest = when (action.type) {
            com.myai.offline.data.model.AssistantActionType.OPEN_YOUTUBE -> "Open YouTube"
            com.myai.offline.data.model.AssistantActionType.OPEN_CHROME -> "Open Chrome"
            com.myai.offline.data.model.AssistantActionType.OPEN_SETTINGS -> "Open Settings"
            com.myai.offline.data.model.AssistantActionType.OPEN_APP -> "Open ${action.appName} app"
            com.myai.offline.data.model.AssistantActionType.SEARCH_YOUTUBE -> "Search YouTube for ${action.query}"
            else -> return
        }
        sendMessage(explicitRequest)
    }

    private suspend fun executeActionSafely(
        action: AssistantAction,
        source: String
    ): AndroidActionHandler.ExecutionOutcome {
        Log.i(
            TAG,
            "[ACTION_EXEC_START] source=$source type=${action.type} query=${action.query} app=${action.appName} url=${action.url}"
        )

        return backgroundOperation {
            actionHandler.execute(action)
        }
    }

    fun speakMessage(messageId: String, content: String) {
        _currentlySpeakingMessageId.value = messageId
        ttsManager.speak(content)
    }

    fun stopSpeaking() {
        ttsManager.stop()
        _currentlySpeakingMessageId.value = null
    }

    fun downloadModel(modelId: ModelId) {
        viewModelScope.launch(Dispatchers.IO) {
            modelRepository.downloadModel(modelId)
        }
    }

    fun retryModelDownload(modelId: ModelId) {
        viewModelScope.launch(Dispatchers.IO) {
            modelRepository.retryDownload(modelId)
        }
    }

    fun pauseModelDownload(modelId: ModelId) {
        viewModelScope.launch(Dispatchers.IO) {
            modelRepository.pauseDownload(modelId)
        }
    }

    fun resumeModelDownload(modelId: ModelId) {
        viewModelScope.launch(Dispatchers.IO) {
            modelRepository.resumeDownload(modelId)
        }
    }

    fun cancelModelDownload(modelId: ModelId) {
        viewModelScope.launch(Dispatchers.IO) {
            modelRepository.cancelDownload(modelId)
        }
    }

    fun deleteModel(modelId: ModelId) {
        cancelVoice()
        viewModelScope.launch(Dispatchers.IO) {
            val modelInfo = modelRepository.getModel(modelId)
            if (llmEngine.currentLoadedModel?.id == modelId) {
                llmEngine.unloadModel()
            }
            speechMutex.withLock {
                if (modelId == ModelId.WHISPER_BASE && whisperEngine.isModelLoaded) {
                    whisperEngine.unloadModel()
                }
                if (modelId == ModelId.MOONSHINE_TINY_EN && moonshineEngine.isModelLoaded) {
                    moonshineEngine.unloadModel()
                }
                if (modelId == ModelId.KOKORO_EN_INT8) {
                    ttsManager.unloadKokoroModel()
                }
            }

            modelRepository.deleteModel(modelId)
            _selectedModelId.value = modelRepository.selectedModelId.value

            if (modelInfo?.isChatModel == true) {
                activateStartupDefaultModel()
            }
        }
    }

    fun refreshModelsFromStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            modelRepository.reconcileInstalledModels(verifyChecksum = false)
        }
    }

    private suspend fun insertAssistantMessage(
        conversationId: String,
        content: String,
        messageId: String = UUID.randomUUID().toString(),
        metrics: InferenceMetrics? = null,
        action: AssistantAction? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val completedAction = action?.takeIf { it.executed }
        val message = MessageEntity(
            id = messageId,
            conversationId = conversationId,
            role = "assistant",
            content = content,
            timestamp = timestamp,
            metricsJson = metrics?.let {
                JSONObject().put("timeToFirstTokenMs", it.timeToFirstTokenMs)
                    .put("tokensPerSec", it.tokensPerSec).put("totalTokens", it.totalTokens)
                    .put("totalGenTimeMs", it.totalGenTimeMs).toString()
            },
            actionType = completedAction?.type?.rawValue,
            actionDataJson = completedAction?.let {
                JSONObject().put("action", it.type.rawValue).apply {
                    it.appName?.let { name -> put("appName", name) }
                    it.query?.let { query -> put("query", query) }
                }.toString()
            }
        )
        db.withTransaction {
            messageDao.insertMessage(message)
            conversationDao.getConversationById(conversationId)?.let { parent ->
                conversationDao.updateConversation(parent.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    private fun recommendedThreadCount(): Int {
        val procs = Runtime.getRuntime().availableProcessors()
        return when {
            procs <= 2 -> 2
            procs <= 4 -> maxOf(2, procs - 1)
            else -> 4 // Pin to 4 threads to run strictly on big performance cores and avoid little-core contention
        }
    }

    private fun updateDeviceMetrics() {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        _deviceRamUsageMb.value = usedMem

        val batteryManager = getApplication<Application>()
            .getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        _batteryLevel.value = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    override fun onCleared() {
        super.onCleared()
        voiceJob?.cancel()
        audioRecorder.stopRecording()
        backgroundTasks.cancel()
        llmEngine.close()
        // viewModelScope is already cancelled in onCleared; use a cleanup owner and wait
        // for speech calls to finish before freeing their native contexts.
        CoroutineScope(Dispatchers.IO).launch {
            viewModelScope.coroutineContext[Job]?.join()
            backgroundTasks.coroutineContext[Job]?.join()
            speechMutex.withLock {
                runCatching { whisperEngine.unloadModel() }
                runCatching { moonshineEngine.unloadModel() }
            }
        }
        ttsManager.shutdown()
    }

    companion object {
        private const val DEFAULT_RUNTIME_CONTEXT = 2048
        private const val DUPLICATE_WINDOW_MS = 800L
        private const val MODEL_LOAD_TIMEOUT_MS = 120_000L
        private const val REQUEST_TIMEOUT_MS = 150_000L
        private const val SAVE_TIMEOUT_MS = 5_000L
        private const val VOICE_TIMEOUT_MS = 60_000L
    }
}
