package com.myai.offline.llm

import com.myai.offline.data.model.InferenceMetrics
import com.myai.offline.data.model.ModelId
import com.myai.offline.data.model.ModelInfo
import kotlinx.coroutines.flow.Flow

interface ILocalLLMEngine {
    val isModelLoaded: Boolean
    val currentLoadedModel: ModelInfo?

    suspend fun loadModel(model: ModelInfo, threads: Int = 4, ctxSize: Int = 4096): Long
    suspend fun unloadModel()
    fun stopGeneration()
    fun formatPrompt(
        modelId: ModelId,
        systemPrompt: String = LocalLLMEngine.DEFAULT_SYSTEM_PROMPT,
        conversationHistory: List<Pair<String, String>>,
        userQuery: String
    ): String
    fun generateStreaming(
        prompt: String,
        userQuery: String,
        maxTokens: Int = 1024,
        onMetricsCalculated: (InferenceMetrics) -> Unit = {}
    ): Flow<String>
}
