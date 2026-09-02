package com.myai.offline

import com.myai.offline.actions.ActionParser
import com.myai.offline.data.model.AssistantActionType
import com.myai.offline.data.model.ModelId
import com.myai.offline.data.model.ModelInfo
import com.myai.offline.data.model.ModelState
import com.myai.offline.data.model.ModelType
import com.myai.offline.llm.PromptFormatter
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

        val prompt = PromptFormatter.format(
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

        val prompt = PromptFormatter.format(
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

        val prompt = PromptFormatter.format(
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
    fun testPromptFormatterWithConversationHistory() {
        val history = listOf(
            "user" to "Search Telugu songs on YouTube",
            "assistant" to "```json\n{\"action\": \"SEARCH_YOUTUBE\", \"query\": \"Telugu songs\"}\n```"
        )
        val userQuery = "Open settings"

        val prompt = PromptFormatter.format(
            modelId = ModelId.QWEN3_1_7B,
            conversationHistory = history,
            userQuery = userQuery
        )

        assertTrue(prompt.contains("SEARCH_YOUTUBE"))
        assertTrue(prompt.contains("Open settings"))
        assertTrue(prompt.startsWith("<|im_start|>system\n"))
    }

    @Test
    fun testPromptFormatterDefaultSystemPromptContainsActionInstructions() {
        val systemPrompt = PromptFormatter.DEFAULT_SYSTEM_PROMPT
        assertTrue(systemPrompt.contains("OPEN_YOUTUBE"))
        assertTrue(systemPrompt.contains("SEARCH_YOUTUBE"))
        assertTrue(systemPrompt.contains("OPEN_CHROME"))
        assertTrue(systemPrompt.contains("OPEN_SETTINGS"))
    }

    @Test
    fun testActionParsingFromLLMOutput() {
        val sampleLLMResponse = """
            I am opening the YouTube app for you.
            ```json
            {
              "action": "OPEN_YOUTUBE"
            }
            ```
        """.trimIndent()

        val parsed = ActionParser.parse(sampleLLMResponse)
        assertTrue(parsed.hasAction)
        assertNotNull(parsed.action)
        assertEquals(AssistantActionType.OPEN_YOUTUBE, parsed.action?.type)
        assertEquals("I am opening the YouTube app for you.", parsed.cleanText)
        assertFalse(parsed.isMalformed)
    }

    @Test
    fun testModelReadinessValidation() {
        val readyModel = ModelInfo(
            id = ModelId.QWEN3_1_7B,
            name = "Qwen3 1.7B",
            tag = "Fast Assistant",
            description = "On-device LLM",
            sizeFormatted = "1.19 GB",
            sizeBytes = 1282439264L,
            sha256Expected = "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5",
            repository = "ggml-org/Qwen3-1.7B-GGUF",
            filename = "Qwen3-1.7B-Q4_K_M.gguf",
            sourceUrl = "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
            contextSize = 4096,
            quant = "Q4_K_M",
            modelType = ModelType.CHAT_LLM,
            capabilities = listOf("chat"),
            architecture = "qwen3",
            state = ModelState.READY,
            downloadedBytes = 1282439264L
        )

        assertEquals(ModelState.READY, readyModel.state)
        assertTrue(readyModel.downloadProgress == 1.0f)
    }
}
