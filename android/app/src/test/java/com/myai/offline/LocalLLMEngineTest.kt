package com.myai.offline

import com.myai.offline.data.model.InferenceMetrics
import com.myai.offline.data.model.ModelId
import com.myai.offline.llm.LocalLLMEngine
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLLMEngineTest {

    @Test
    fun testQwen3PromptFormatting() {
        val history = listOf("user" to "Hello", "assistant" to "Hi there!")
        val userQuery = "What is an OS?"

        val engine = LocalLLMEngine(context = null)

        val prompt = engine.formatPrompt(
            modelId = ModelId.QWEN3_1_7B,
            systemPrompt = "You are MyAI",
            conversationHistory = history,
            userQuery = userQuery
        )

        assertTrue(prompt.contains("<|im_start|>system\nYou are MyAI<|im_end|>"))
        assertTrue(prompt.contains("<|im_start|>user\nHello<|im_end|>"))
        assertTrue(prompt.contains("<|im_start|>assistant\nHi there!<|im_end|>"))
        assertTrue(prompt.contains("<|im_start|>user\nWhat is an OS?<|im_end|>"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun testPhi4PromptFormatting() {
        val history = listOf("user" to "Hello")
        val userQuery = "Explain algorithms"

        val engine = LocalLLMEngine(context = null)

        val prompt = engine.formatPrompt(
            modelId = ModelId.PHI4_MINI,
            systemPrompt = "You are MyAI",
            conversationHistory = history,
            userQuery = userQuery
        )

        assertTrue(prompt.contains("<|system|>\nYou are MyAI<|end|>"))
        assertTrue(prompt.contains("<|user|>\nHello<|end|>"))
        assertTrue(prompt.contains("<|user|>\nExplain algorithms<|end|>"))
        assertTrue(prompt.endsWith("<|assistant|>\n"))
    }

    @Test
    fun testGemma3PromptFormatting() {
        val history = listOf("user" to "Hi")
        val userQuery = "Write a function"

        val engine = LocalLLMEngine(context = null)

        val prompt = engine.formatPrompt(
            modelId = ModelId.GEMMA3_4B,
            systemPrompt = "You are MyAI",
            conversationHistory = history,
            userQuery = userQuery
        )

        assertTrue(prompt.contains("<start_of_turn>user\nYou are MyAI"))
        assertTrue(prompt.contains("User: Write a function<end_of_turn>"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun testStreamingTokenEmissionAndMetrics() = runBlocking {
        val engine = LocalLLMEngine(context = null)
        var calculatedMetrics: InferenceMetrics? = null

        val tokens = engine.generateStreaming(
            prompt = "",
            userQuery = "What is an operating system?",
            maxTokens = 64,
            onMetricsCalculated = { calculatedMetrics = it }
        ).toList()

        assertTrue(tokens.isNotEmpty())
        val combinedText = tokens.joinToString("")
        assertTrue(combinedText.contains("Operating System"))
        assertNotNull(calculatedMetrics)
        assertTrue(calculatedMetrics!!.totalTokens > 0)
        assertTrue(calculatedMetrics!!.totalGenTimeMs >= 0)
    }

    @Test
    fun testLlmCancellation() = runBlocking {
        val engine = LocalLLMEngine(context = null)

        val firstTokens = engine.generateStreaming(
            prompt = "",
            userQuery = "Explain quantum computing in detail",
            maxTokens = 256
        ).take(2).toList()

        engine.stopGeneration()

        assertEquals(2, firstTokens.size)
        assertFalse(engine.isModelLoaded)
    }
}
