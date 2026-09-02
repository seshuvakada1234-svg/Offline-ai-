package com.myai.offline.viewmodel

import android.app.Application
import android.content.Context
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
import com.myai.offline.data.model.VoiceState
import com.myai.offline.data.repository.ModelRepository
import com.myai.offline.llm.ILocalLLMEngine
import com.myai.offline.llm.LocalLLMEngine
import com.myai.offline.voice.AudioRecorder
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
    private var whisperAutoInitAttempted = false

    init {
        viewModelScope.launch {
            modelRepository.selectedModelId.collect { selected ->
                _selectedModelId.value = selected
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            modelRepository.models.collect { modelList ->
                val whisperModel = modelList.firstOrNull { it.id == ModelId.WHISPER_BASE } ?: return@collect
                val shouldAutoInit = whisperModel.state in setOf(ModelState.READY, ModelState.ACTIVE)
                if (!shouldAutoInit) {
                    whisperAutoInitAttempted = false
                    return@collect
                }

                if (whisperEngine.isModelLoaded) {
                    whisperAutoInitAttempted = true
                    return@collect
                }

                if (!whisperAutoInitAttempted) {
                    whisperAutoInitAttempted = true
                    val loaded = whisperEngine.loadModel(whisperModel)
                    if (loaded) {
                        Log.i(TAG, "[WHISPER_AUTO_INIT] Whisper initialized automatically")
                    } else {
                        Log.w(TAG, "[WHISPER_AUTO_INIT] Whisper model present but initialization failed")
                    }
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
            generateAssistantResponse(conversationId, trimmed)
        }
    }

    private fun generateAssistantResponse(conversationId: String, userQuery: String) {
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
                        insertAssistantMessage(
                            conversationId,
                            "Unable to load model ${selectedModel.name}. Check Engine Logs for details."
                        )
                        return@launch
                    }
                }

                val history = messageDao.getMessagesList(conversationId).map { it.role to it.content }
                val prompt = llmEngine.formatPrompt(
                    modelId = _selectedModelId.value,
                    conversationHistory = history,
                    userQuery = userQuery
                )

                val streamedText = StringBuilder()
                var metrics: InferenceMetrics? = null

                llmEngine.generateStreaming(
                    prompt = prompt,
                    userQuery = userQuery,
                    maxTokens = 1024,
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

            val whisperModel = modelRepository.getModel(ModelId.WHISPER_BASE)
            if (whisperModel == null || whisperModel.state !in setOf(ModelState.READY, ModelState.ACTIVE)) {
                _voiceTranscript.value = "Download Whisper to use voice input."
                _voiceState.value = VoiceState.ERROR
                return@launch
            }

            if (!whisperEngine.isModelLoaded) {
                val loaded = whisperEngine.loadModel(whisperModel)
                if (!loaded) {
                    _voiceTranscript.value = "Unable to initialize Whisper model."
                    _voiceState.value = VoiceState.ERROR
                    return@launch
                }
            }

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
            val transcript = whisperEngine.transcribe(pcmAudio)

            _voiceTranscript.value = transcript
            if (transcript.isNotBlank()) {
                _composerText.value = transcript
                _voiceState.value = VoiceState.IDLE
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
            if (llmEngine.currentLoadedModel?.id == modelId) {
                llmEngine.unloadModel()
            }
            if (modelId == ModelId.WHISPER_BASE && whisperEngine.isModelLoaded) {
                whisperEngine.unloadModel()
            }

            modelRepository.deleteModel(modelId)
            _selectedModelId.value = modelRepository.selectedModelId.value

            if (modelId != ModelId.WHISPER_BASE) {
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
        return Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
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
        }
        ttsManager.shutdown()
    }

    companion object {
        private const val DEFAULT_RUNTIME_CONTEXT = 8192
        private const val DUPLICATE_WINDOW_MS = 800L
    }
}
