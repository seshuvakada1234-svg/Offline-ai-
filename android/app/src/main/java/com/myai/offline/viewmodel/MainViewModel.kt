package com.myai.offline.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.BatteryManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myai.offline.actions.ActionParser
import com.myai.offline.actions.AndroidActionHandler
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    private val sendMutex = Mutex()
    private var messageCollectionJob: Job? = null
    private var activeGenerationJob: Job? = null
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
            modelRepository.checkLocalModelFiles()
            activateStartupDefaultModel()
            createNewConversation()
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

    private suspend fun autoInitializeSpeechModels(modelList: List<ModelInfo>) {
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
    } catch (t: Throwable) {
        Log.e(TAG, "[SPEECH_AUTO_INIT] Failed during speech auto-init: ${t.message}", t)
    }
}

    fun updateComposerText(text: String) {
        _composerText.value = text
    }

    fun createNewConversation() {
        val newId = UUID.randomUUID().toString()
        _currentConversationId.value = newId
        _messages.value = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            val conversation = ConversationEntity(
                id = newId,
                title = "New Chat",
                selectedModelId = _selectedModelId.value.rawValue
            )
            conversationDao.insertConversation(conversation)
        }
        observeConversation(newId)
    }

    fun selectConversation(id: String) {
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
        viewModelScope.launch(Dispatchers.IO) {
            val activated = activateModelInternal(id, persistSelection = true)
            if (!activated) {
                val modelName = modelRepository.getModel(id)?.name ?: id.rawValue
                Log.e(TAG, "[MODEL_LOAD_FAILED] Unable to activate model $modelName")
            }
        }
    }

    private suspend fun activateModelInternal(id: ModelId, persistSelection: Boolean): Boolean {
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
            llmEngine.loadModel(
                model = model,
                threads = recommendedThreadCount(),
                ctxSize = minOf(DEFAULT_RUNTIME_CONTEXT, model.contextSize)
            )
            modelRepository.markModelActive(id)
            Log.i(TAG, "[MODEL_LOAD_SUCCESS] Activated model ${model.name}")
            true
        } catch (e: Exception) {
            val message = e.localizedMessage ?: "Unable to load model"
            modelRepository.markModelLoadFailed(id, message)
            Log.e(TAG, "[MODEL_LOAD_FAILED] ${model.name}: $message", e)
            false
        }
    }

    fun sendMessage(userText: String, isVoice: Boolean = false) {
        val conversationId = _currentConversationId.value ?: return
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            sendMutex.withLock {
                val existing = messageDao.getMessagesList(conversationId)
                val last = existing.lastOrNull()
                if (last != null &&
                    last.role == "user" &&
                    last.content == trimmed &&
                    (System.currentTimeMillis() - last.timestamp) < DUPLICATE_WINDOW_MS
                ) {
                    Log.w(TAG, "Skipped duplicate user submission within debounce window")
                    return@withLock
                }

                val userMessage = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    role = "user",
                    content = trimmed,
                    isVoiceInput = isVoice
                )

                messageDao.insertMessage(userMessage)

                if (existing.isEmpty()) {
                    val title = if (trimmed.length > 30) trimmed.take(27) + "..." else trimmed
                    conversationDao.updateConversationTitle(conversationId, title)
                }
            }

            withContext(Dispatchers.Main) {
                _composerText.value = ""
            }

            // 1. Instant App Launching & Fast Actions (< 20ms)
            val fastAction = detectFastCommand(trimmed)
            if (fastAction != null) {
                _voiceState.value = VoiceState.ACTION_EXECUTING
                val outcome = withContext(Dispatchers.Main) {
                    actionHandler.execute(fastAction)
                }
                insertAssistantMessage(conversationId, outcome.message)
                if (isVoice) {
                    _voiceTranscript.value = outcome.message
                    _voiceState.value = VoiceState.SPEAKING
                    ttsManager.speak(outcome.message)
                }
                return@launch
            }

            // 2. Instant Greetings (< 10ms)
            val fastGreeting = detectFastGreeting(trimmed)
            if (fastGreeting != null) {
                insertAssistantMessage(conversationId, fastGreeting)
                if (isVoice) {
                    _voiceTranscript.value = fastGreeting
                    _voiceState.value = VoiceState.SPEAKING
                    ttsManager.speak(fastGreeting)
                }
                return@launch
            }

            // 3. Ultra-Fast On-Device LLM Inference (1-2s target)
            generateAssistantResponse(
                conversationId = conversationId,
                userQuery = trimmed,
                shouldSpeakResponse = isVoice
            )
        }
    }

    private fun generateAssistantResponse(
        conversationId: String,
        userQuery: String,
        shouldSpeakResponse: Boolean
    ) {
        _isGenerating.value = true
        _streamingMessage.value = ""

        activeGenerationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val selectedModel = modelRepository.getModel(_selectedModelId.value)
                if (selectedModel == null || selectedModel.state == ModelState.NOT_INSTALLED) {
                    insertAssistantMessage(
                        conversationId,
                        "Download ${selectedModel?.name ?: "a model"} to chat."
                    )
                    return@launch
                }

                if (selectedModel.state != ModelState.ACTIVE ||
                    !llmEngine.isModelLoaded ||
                    llmEngine.currentLoadedModel?.id != selectedModel.id
                ) {
                    val activated = activateModelInternal(selectedModel.id, persistSelection = true)
                    if (!activated) {
                        val errorDetail = modelRepository.getModel(selectedModel.id)?.errorMessage
                        val detailMsg = if (!errorDetail.isNullOrBlank()) ": $errorDetail" else ". Check Engine Logs for details."
                        insertAssistantMessage(
                            conversationId,
                            "Unable to load model ${selectedModel.name}$detailMsg"
                        )
                        return@launch
                    }
                }

                // Keep context short and relevant (last 4 messages, excluding current query) to guarantee fast inference
                val history = messageDao.getMessagesList(conversationId)
                    .dropLastWhile { it.role == "user" && it.content == userQuery }
                    .takeLast(4)
                    .map { it.role to it.content }

                val prompt = llmEngine.formatPrompt(
                    modelId = _selectedModelId.value,
                    conversationHistory = history,
                    userQuery = userQuery
                )

                val streamedText = StringBuilder()
                var metrics: InferenceMetrics? = null

                // For on-device chat, 128-256 tokens generates in 1-2 seconds
                val maxGenTokens = if (userQuery.length <= 30) 128 else 256

                llmEngine.generateStreaming(
                    prompt = prompt,
                    userQuery = userQuery,
                    maxTokens = maxGenTokens,
                    onMetricsCalculated = { calculated ->
                        metrics = calculated
                    }
                ).collect { tokenChunk ->
                    streamedText.append(tokenChunk)
                    _streamingMessage.value = streamedText.toString()
                }

                val finalText = streamedText.toString().trim()
                if (finalText.isNotBlank()) {
                    val metricsJson = metrics?.let {
                        JSONObject().apply {
                            put("timeToFirstTokenMs", it.timeToFirstTokenMs)
                            put("tokensPerSec", it.tokensPerSec)
                            put("totalTokens", it.totalTokens)
                            put("totalGenTimeMs", it.totalGenTimeMs)
                        }.toString()
                    }

                    val aiMessage = MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        role = "assistant",
                        content = finalText,
                        metricsJson = metricsJson
                    )
                    messageDao.insertMessage(aiMessage)

                    val parseResult = ActionParser.parse(finalText)
                    if (parseResult.hasAction && parseResult.action != null && !parseResult.action.requiresConfirmation) {
                        executeAction(parseResult.action)
                    }

                    if (shouldSpeakResponse) {
                        val speakText = parseResult.cleanText.ifBlank { finalText }
                        _voiceState.value = VoiceState.SPEAKING
                        ttsManager.speak(speakText)
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    insertAssistantMessage(
                        conversationId,
                        "Unable to generate a response: ${e.localizedMessage ?: "unknown inference error"}"
                    )
                }
            } finally {
                _isGenerating.value = false
                _streamingMessage.value = ""
            }
        }
    }

    fun stopGeneration() {
        llmEngine.stopGeneration()
        activeGenerationJob?.cancel()

        val partial = _streamingMessage.value
        val conversationId = _currentConversationId.value
        if (partial.isNotBlank() && conversationId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                insertAssistantMessage(conversationId, partial)
            }
        }

        _isGenerating.value = false
        _streamingMessage.value = ""
    }

    fun startVoiceListening() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_voiceState.value != VoiceState.IDLE) {
                return@launch
            }

            val preferredOrder = if (_selectedSttEngine.value == SpeechToTextEngine.MOONSHINE_TINY) {
                listOf(SpeechToTextEngine.MOONSHINE_TINY, SpeechToTextEngine.WHISPER_BASE)
            } else {
                listOf(SpeechToTextEngine.WHISPER_BASE, SpeechToTextEngine.MOONSHINE_TINY)
            }

            var selectedRuntime: SpeechToTextEngine? = null
            for (engine in preferredOrder) {
                val ready = when (engine) {
                    SpeechToTextEngine.MOONSHINE_TINY -> prepareMoonshineEngine()
                    SpeechToTextEngine.WHISPER_BASE -> prepareWhisperEngine()
                }
                if (ready) {
                    selectedRuntime = engine
                    break
                }
            }

            if (selectedRuntime == null) {
                _voiceTranscript.value = "Install Moonshine Tiny or Whisper Base.en to use voice input."
                _voiceState.value = VoiceState.ERROR
                return@launch
            }

            if (selectedRuntime != _selectedSttEngine.value) {
                _selectedSttEngine.value = selectedRuntime
            }

            activeListeningSttEngine = selectedRuntime

            _voiceTranscript.value = ""
            _voiceState.value = VoiceState.LISTENING
            audioRecorder.startRecording(viewModelScope)
        }
    }

    fun stopVoiceListening() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_voiceState.value != VoiceState.LISTENING) {
                return@launch
            }

            _voiceState.value = VoiceState.TRANSCRIBING
            val pcmAudio = audioRecorder.stopRecording()
            val transcript = when (activeListeningSttEngine) {
                SpeechToTextEngine.MOONSHINE_TINY -> moonshineEngine.transcribe(pcmAudio)
                SpeechToTextEngine.WHISPER_BASE -> whisperEngine.transcribe(pcmAudio)
            }

            _voiceTranscript.value = transcript
            if (transcript.isNotBlank()) {
                val quickAction = detectFastCommand(transcript)
                if (quickAction != null) {
                    _voiceState.value = VoiceState.ACTION_EXECUTING
                    val result = withContext(Dispatchers.Main) {
                        actionHandler.execute(quickAction)
                    }
                    _voiceTranscript.value = result.message
                    _voiceState.value = VoiceState.SPEAKING
                    ttsManager.speak(result.message)
                } else {
                    _voiceState.value = VoiceState.THINKING
                    sendMessage(transcript, isVoice = true)
                }
            } else {
                _voiceState.value = VoiceState.ERROR
                _voiceTranscript.value = "No speech detected."
            }
        }
    }

    fun cancelVoice() {
        audioRecorder.stopRecording()
        _voiceState.value = VoiceState.IDLE
        _voiceTranscript.value = ""
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

    private fun detectFastGreeting(text: String): String? {
        val clean = text.trim().lowercase()
            .removeSuffix("!").removeSuffix(".").removeSuffix("?").trim()
        return when (clean) {
            "hi", "hello", "hey", "hiya", "howdy", "good morning", "good afternoon", "good evening" -> {
                "Hello! How can I help you today?"
            }
            "how are you", "how are you doing", "how's it going" -> {
                "I am doing great! Ready to help you with anything you need."
            }
            "who are you", "what are you" -> {
                "I am MyAI, your private, high-performance offline AI assistant running locally on your device."
            }
            else -> null
        }
    }

    private fun detectFastCommand(transcript: String): AssistantAction? {
        val normalized = transcript.trim().lowercase()
        if (normalized.isBlank()) return null

        // 1. YouTube Search: "search youtube for <query>", "play <query> on youtube"
        val youtubeSearch = Regex("^(?:search\\s+(?:on\\s+)?youtube\\s+(?:for\\s+)?|youtube\\s+search\\s+(?:for\\s+)?|play\\s+(.+?)\\s+on\\s+youtube|watch\\s+(.+?)\\s+on\\s+youtube)(.*)$", RegexOption.IGNORE_CASE)
            .find(normalized)
        if (youtubeSearch != null) {
            val q = (youtubeSearch.groupValues[1].ifBlank { youtubeSearch.groupValues[2] }.ifBlank { youtubeSearch.groupValues[3] }).trim()
            if (q.isNotBlank()) {
                return AssistantAction(
                    type = com.myai.offline.data.model.AssistantActionType.SEARCH_YOUTUBE,
                    query = q
                )
            }
        }

        // 2. Web Search: "search web for <query>", "google <query>"
        val webSearch = Regex("^(?:web\\s+search\\s+for|search\\s+(?:the\\s+)?web\\s+for|google\\s+for|google)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!webSearch.isNullOrBlank()) {
            val encodedQuery = Uri.encode(webSearch)
            return AssistantAction(
                type = com.myai.offline.data.model.AssistantActionType.OPEN_URL,
                url = "https://www.google.com/search?q=$encodedQuery"
            )
        }

        // 3. Generic App Launch: "open <app>", "launch <app>", "start <app>", "run <app>", "go to <app>"
        val openAppRegex = Regex("^(?:please\\s+)?(?:open|launch|start|run|go\\s+to)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$", RegexOption.IGNORE_CASE)
        val openMatch = openAppRegex.find(normalized)?.groupValues?.getOrNull(1)?.trim()
        val targetApp = openMatch ?: if (normalized.startsWith("open ")) normalized.removePrefix("open ").trim() else null

        if (!targetApp.isNullOrBlank()) {
            val app = targetApp.lowercase()
            return when {
                app == "youtube" || app == "yt" -> AssistantAction(type = com.myai.offline.data.model.AssistantActionType.OPEN_YOUTUBE)
                app == "chrome" || app == "browser" || app == "google chrome" || app == "internet" -> AssistantAction(type = com.myai.offline.data.model.AssistantActionType.OPEN_CHROME)
                app == "settings" || app == "phone settings" || app == "system settings" -> AssistantAction(type = com.myai.offline.data.model.AssistantActionType.OPEN_SETTINGS)
                else -> AssistantAction(type = com.myai.offline.data.model.AssistantActionType.OPEN_APP, appName = targetApp)
            }
        }

        // Direct app names without "open"
        if (normalized == "youtube") return AssistantAction(type = com.myai.offline.data.model.AssistantActionType.OPEN_YOUTUBE)
        if (normalized == "settings") return AssistantAction(type = com.myai.offline.data.model.AssistantActionType.OPEN_SETTINGS)
        if (normalized == "chrome" || normalized == "browser") return AssistantAction(type = com.myai.offline.data.model.AssistantActionType.OPEN_CHROME)

        return null
    }

    fun executeAction(action: AssistantAction) {
        viewModelScope.launch(Dispatchers.Main) {
            val result = actionHandler.execute(action)
            Log.i(TAG, "Action result: ${result.message}")
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
        viewModelScope.launch(Dispatchers.IO) {
            val modelInfo = modelRepository.getModel(modelId)
            if (llmEngine.currentLoadedModel?.id == modelId) {
                llmEngine.unloadModel()
            }
            if (modelId == ModelId.WHISPER_BASE && whisperEngine.isModelLoaded) {
                whisperEngine.unloadModel()
            }
            if (modelId == ModelId.MOONSHINE_TINY_EN && moonshineEngine.isModelLoaded) {
                moonshineEngine.unloadModel()
            }
            if (modelId == ModelId.KOKORO_EN_INT8) {
                ttsManager.unloadKokoroModel()
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

    private suspend fun insertAssistantMessage(conversationId: String, content: String) {
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = "assistant",
            content = content
        )
        messageDao.insertMessage(message)
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
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { llmEngine.unloadModel() }
            runCatching { whisperEngine.unloadModel() }
            runCatching { moonshineEngine.unloadModel() }
        }
        ttsManager.shutdown()
    }

    companion object {
        private const val DEFAULT_RUNTIME_CONTEXT = 2048 // 2048 context size guarantees fast 1-2s response latency on mobile
        private const val DUPLICATE_WINDOW_MS = 800L
    }
}
