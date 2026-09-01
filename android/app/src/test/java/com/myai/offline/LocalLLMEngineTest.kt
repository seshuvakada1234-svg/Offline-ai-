package com.myai.offline

import com.myai.offline.data.model.ModelId
import com.myai.offline.llm.LocalLLMEngine
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLLMEngineTest {

    @Test
    fun testQwen3PromptFormatting() {
        // We can test prompt formatter directly
        val history = listOf("user" to "Hello", "assistant" to "Hi there!")
        val userQuery = "What is an OS?"

        val engine = LocalLLMEngine(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        )

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

        val engine = LocalLLMEngine(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        )

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
}
