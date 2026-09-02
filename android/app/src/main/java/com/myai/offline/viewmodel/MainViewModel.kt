package com.myai.offline.viewmodel

import android.app.Application
import android.content.Context
import android.os.BatteryManager
import android.os.Process
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

    // UI States
    val conversations = conversationDao.getAllConversations()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    val models: StateFlow<List<ModelInfo>> = modelRepository.models

    private val _selectedModelId = MutableStateFlow<ModelId>(ModelId.QWEN3_1_7B)
    val selectedModelId: StateFlow<ModelId> = _selectedModelId.asStateFlow()

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

    // Hardware Telemetry
    private val _deviceRamUsageMb = MutableStateFlow(0L)
    val deviceRamUsageMb: StateFlow<Long> = _deviceRamUsageMb.asStateFlow()

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private var activeGenerationJob: Job? = null

    init {
        // Initialize default session if none exists
        viewModelScope.launch {
            modelRepository.checkLocalModelFiles()
            val initial = modelRepository.getModel(ModelId.QWEN3_1_7B)
            if (initial != null && initial.state == ModelState.READY) {
                try {
                    llmEngine.loadModel(initial)
                } catch (e: Exception) {
                    Log.w(TAG, "Default LLM model not loaded yet: ${e.message}")
                }
            }
            val whisperModel = modelRepository.getModel(ModelId.WHISPER_BASE)
            if (whisperModel != null && whisperModel.state == ModelState.READY) {
                try {
                    whisperEngine.loadModel(whisperModel)
                } catch (e: Exception) {
                    Log.w(TAG, "Whisper model not loaded yet: ${e.message}")
                }
            }

            createNewConversation()
            updateDeviceMetrics()
        }
    }

    private var messageCollectionJob: Job? = null

    fun createNewConversation() {
        val newId = UUID.randomUUID().toString()
        _currentConversationId.value = newId
        _messages.value = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            val conv = ConversationEntity(
                id = newId,
                title = "New Chat",
                selectedModelId = _selectedModelId.value.rawValue
            )
            conversationDao.insertConversation(conv)
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
            messageDao.getMessagesForConversation(id).collect { list: List<MessageEntity> ->
                _messages.value = list
            }
        }
    }

    fun selectModel(id: ModelId) {
        val success = modelRepository.selectModel(id)
        if (success) {
            _selectedModelId.value = id
            viewModelScope.launch {
                val model = modelRepository.getModel(id)
                if (model != null && model.state == ModelState.READY) {
                    try {
                        llmEngine.loadModel(model)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load model $id: ${e.message}")
                    }
                }
            }
        } else {
            Log.w(TAG, "Model $id is not ready or not installed. Download required.")
        }
    }

    fun sendMessage(userText: String, isVoice: Boolean = false) {
        val convId = _currentConversationId.value ?: return
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return

        val userMessageId = UUID.randomUUID().toString()
        val userMessage = MessageEntity(
            id = userMessageId,
            conversationId = convId,
            role = "user",
            content = trimmed,
            isVoiceInput = isVoice
        )

        viewModelScope.launch(Dispatchers.IO) {
            messageDao.insertMessage(userMessage)

            // Update conversation title if first message
            if (_messages.value.isEmpty()) {
                val title = if (trimmed.length > 30) trimmed.take(27) + "..." else trimmed
                conversationDao.updateConversationTitle(convId, title)
            }

            // Trigger Assistant LLM Stream
            generateAssistantResponse(convId, trimmed)
        }
    }

    private fun generateAssistantResponse(convId: String, userQuery: String) {
        _isGenerating.value = true
        _streamingMessage.value = ""

        activeGenerationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure model is ready
                val selectedModel = modelRepository.getModel(_selectedModelId.value)
                if (selectedModel == null || selectedModel.state != ModelState.READY) {
                    val errorText = "Unable to generate a response.\n\nModel ${selectedModel?.name ?: "selected"} is not installed. Please download it from the Model Manager."
                    val errorMsgEntity = MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        role = "assistant",
                        content = errorText
                    )
                    messageDao.insertMessage(errorMsgEntity)
                    _isGenerating.value = false
                    _streamingMessage.value = ""
                    return@launch
                }

                if (!llmEngine.isModelLoaded || llmEngine.currentLoadedModel?.id != _selectedModelId.value) {
                    try {
                        llmEngine.loadModel(selectedModel)
                    } catch (e: Exception) {
                        val errorText = "Failed to load ${selectedModel.name}: ${e.localizedMessage ?: "File error"}.\nPlease verify the model file in Model Manager."
                        val errorMsgEntity = MessageEntity(
                            id = UUID.randomUUID().toString(),
                            conversationId = convId,
                            role = "assistant",
                            content = errorText
                        )
                        messageDao.insertMessage(errorMsgEntity)
                        _isGenerating.value = false
                        _streamingMessage.value = ""
                        return@launch
                    }
                }

                val history = _messages.value.map { it.role to it.content }
                val prompt = llmEngine.formatPrompt(
                    modelId = _selectedModelId.value,
                    conversationHistory = history,
                    userQuery = userQuery
                )

                val stringBuffer = StringBuilder()
                var calculatedMetrics: InferenceMetrics? = null

                llmEngine.generateStreaming(
                    prompt = prompt,
                    userQuery = userQuery,
                    maxTokens = 1024,
                    onMetricsCalculated = { metrics: InferenceMetrics ->
                        calculatedMetrics = metrics
                    }
                ).collect { tokenChunk: String ->
                    stringBuffer.append(tokenChunk)
                    _streamingMessage.value = stringBuffer.toString()
                }

                val finalContent = stringBuffer.toString()
                if (finalContent.isNotBlank()) {
                    val aiMessageId = UUID.randomUUID().toString()

                    val metricsJson = calculatedMetrics?.let {
                        JSONObject().apply {
                            put("timeToFirstTokenMs", it.timeToFirstTokenMs)
                            put("tokensPerSec", it.tokensPerSec)
                            put("totalTokens", it.totalTokens)
                            put("totalGenTimeMs", it.totalGenTimeMs)
                        }.toString()
                    }

                    val aiMessage = MessageEntity(
                        id = aiMessageId,
                        conversationId = convId,
                        role = "assistant",
                        content = finalContent,
                        metricsJson = metricsJson
                    )

                    messageDao.insertMessage(aiMessage)

                    // Check if there is an automatic action to execute immediately
                    val parseResult = ActionParser.parse(finalContent)
                    if (parseResult.hasAction && parseResult.action != null && !parseResult.action.requiresConfirmation) {
                        executeAction(parseResult.action)
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    val errorMsg = "Generation failed: ${e.localizedMessage ?: "Unexpected inference error"}"
                    val errorEntity = MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        role = "assistant",
                        content = errorMsg
                    )
                    messageDao.insertMessage(errorEntity)
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
        val partialContent = _streamingMessage.value
        val convId = _currentConversationId.value
        if (partialContent.isNotBlank() && convId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val aiMessage = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = convId,
                    role = "assistant",
                    content = partialContent
                )
                messageDao.insertMessage(aiMessage)
            }
        }
        _isGenerating.value = false
        _streamingMessage.value = ""
    }

    fun startVoiceListening() {
        _voiceState.value = VoiceState.LISTENING
        _voiceTranscript.value = ""
        audioRecorder.startRecording(viewModelScope)
    }

    fun stopVoiceListening() {
        _voiceState.value = VoiceState.TRANSCRIBING
        val pcmAudio = audioRecorder.stopRecording()

        viewModelScope.launch {
            val transcript = whisperEngine.transcribe(pcmAudio)
            _voiceTranscript.value = transcript

            if (transcript.isNotBlank()) {
                _voiceState.value = VoiceState.THINKING
                sendMessage(transcript, isVoice = true)
                _voiceState.value = VoiceState.IDLE
            } else {
                _voiceState.value = VoiceState.IDLE
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
        viewModelScope.launch {
            modelRepository.downloadModel(modelId)
        }
    }

    fun deleteModel(modelId: ModelId) {
        viewModelScope.launch {
            modelRepository.deleteModel(modelId)
        }
    }

    private fun updateDeviceMetrics() {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        _deviceRamUsageMb.value = usedMem

        val bm = getApplication<Application>().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        _batteryLevel.value = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
