package com.myai.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myai.offline.data.model.ModelConstants
import com.myai.offline.data.model.ModelId
import com.myai.offline.assistant.AssistantResponsePipeline
import com.myai.offline.assistant.ResponsePhase
import com.myai.offline.llm.LocalLLMEngine
import com.myai.offline.llm.LlamaTokenCallback
import com.myai.offline.llm.NativeLlamaBridge
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

@RunWith(AndroidJUnit4::class)
class NativeInferenceInstrumentationTest {

    @Test
    fun llamaModelLoadAndSingleTokenGeneration_whenLocalModelExists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Native llama backend unavailable", NativeLlamaBridge.isAvailable())

        val availableModelFile = ModelConstants.INITIAL_MODELS
            .filter { it.isChatModel }
            .flatMap { model -> listOf(
                File(context.filesDir, "models/llm/${model.id.rawValue}/${model.filename}"),
                File(context.filesDir, "models/${model.id.rawValue}/${model.filename}")
            ) }
            .firstOrNull { it.exists() && it.length() > 0L }

        assumeTrue("No local GGUF model exists in app storage for instrumentation test", availableModelFile != null)

        val handle = NativeLlamaBridge.nativeLoadModel(availableModelFile!!.absolutePath, 2, 2048)
        try {
            assertTrue("Expected non-zero model handle", handle != 0L)
            assertTrue("Native backend reports model not loaded", NativeLlamaBridge.nativeIsModelLoaded(handle))

            var tokenCount = 0
            val generated = NativeLlamaBridge.nativeGenerate(
                modelHandle = handle,
                prompt = "Hello",
                maxTokens = 1,
                callback = LlamaTokenCallback { token ->
                    if (token.isNotBlank()) {
                        tokenCount++
                    }
                    false
                }
            )

            assertTrue(
                "Expected at least one token from native generation",
                generated > 0 && tokenCount > 0
            )
        } finally {
            if (handle != 0L) {
                NativeLlamaBridge.nativeUnloadModel(handle)
            }
        }
    }

    @Test
    fun qwenConversationRequestsGenerateTextWithoutExecutingActions_whenModelInstalled() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = ModelConstants.INITIAL_MODELS.first { it.id == ModelId.QWEN3_1_7B }
        val file = File(context.filesDir, "models/llm/${model.id.rawValue}/${model.filename}")
        assumeTrue("Install Qwen3 1.7B to run native conversation verification", file.isFile)
        assumeTrue("Native llama backend unavailable", NativeLlamaBridge.isAvailable())
        val engine = LocalLLMEngine(context)
        try {
            withTimeout(120_000) { engine.loadModel(model, threads = 2, ctxSize = 2048) }
            for (query in listOf(
                "Hello", "Hi", "6+9", "What is Android?", "What is Python?",
                "Explain machine learning", "How do I create a website?",
                "How to create a website for wedding invitation website",
                "How do I create a wedding invitation website?",
                "Write HTML for a wedding invitation website", "Write Python code",
                "Explain this code: print('Hello')", "Give me ideas for my project", "What is 7+9?"
            )) {
                val phases = mutableListOf<ResponsePhase>()
                var streamedText = ""
                val response = AssistantResponsePipeline().respond(
                    userText = query,
                    generate = {
                        engine.generateStreaming(
                            prompt = engine.formatPrompt(model.id, userQuery = query, enableThinking = false),
                            userQuery = query,
                            maxTokens = 64
                        )
                    },
                    execute = { throw AssertionError("A normal Qwen conversation attempted a device action") },
                    onPhase = { phases.add(it) },
                    onText = { streamedText = it }
                )
                assertTrue("Expected actual model tokens for: $query", streamedText.isNotBlank())
                assertEquals(query, streamedText.trim(), response.text)
                assertNull(response.action)
                assertEquals(listOf(ResponsePhase.GENERATING, ResponsePhase.IDLE), phases)
            }
        } finally {
            engine.unloadModel()
            engine.close()
        }
    }
}
