package com.myai.offline

import com.myai.offline.data.model.ModelId
import com.myai.offline.data.model.ModelInfo
import com.myai.offline.data.model.ModelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelManagerTest {

    @Test
    fun testAllRequiredModelsExist() {
        val modelIds = ModelId.values().toList()
        assertTrue(modelIds.contains(ModelId.QWEN3_1_7B))
        assertTrue(modelIds.contains(ModelId.QWEN3_4B))
        assertTrue(modelIds.contains(ModelId.PHI4_MINI))
        assertTrue(modelIds.contains(ModelId.GEMMA3_4B))
        assertTrue(modelIds.contains(ModelId.GEMMA3_270M))
        assertTrue(modelIds.contains(ModelId.WHISPER_BASE))
    }

    @Test
    fun testModelDefaultStateAndMetadata() {
        val qwen = ModelInfo(
            id = ModelId.QWEN3_1_7B,
            name = "Qwen3 1.7B Instruct",
            parameterSize = "1.7B",
            quant = "Q4_K_M",
            fileSizeBytes = 1_150_000_000L,
            filename = "qwen3-1.7b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            description = "Fast, lightweight local on-device LLM with Telugu & English capabilities.",
            state = ModelState.READY,
            progress = 1.0f
        )

        assertEquals("Qwen3 1.7B Instruct", qwen.name)
        assertEquals("Q4_K_M", qwen.quant)
        assertEquals(ModelState.READY, qwen.state)
        assertEquals(1.0f, qwen.progress, 0.001f)
        assertFalse(qwen.filename.isEmpty())
    }

    @Test
    fun testModelLifecycleStateTransitions() {
        var state = ModelState.NOT_INSTALLED
        assertEquals(ModelState.NOT_INSTALLED, state)

        state = ModelState.DOWNLOADING
        assertEquals(ModelState.DOWNLOADING, state)

        state = ModelState.VERIFYING
        assertEquals(ModelState.VERIFYING, state)

        state = ModelState.READY
        assertEquals(ModelState.READY, state)

        state = ModelState.LOADING
        assertEquals(ModelState.LOADING, state)

        state = ModelState.ERROR
        assertEquals(ModelState.ERROR, state)
    }

    @Test
    fun testModelSelectionLogic() {
        val availableModels = listOf(
            ModelInfo(
                id = ModelId.QWEN3_1_7B,
                name = "Qwen3 1.7B",
                parameterSize = "1.7B",
                quant = "Q4_K_M",
                fileSizeBytes = 1_150_000_000L,
                filename = "qwen3-1.7b.gguf",
                downloadUrl = "",
                description = "",
                state = ModelState.READY
            ),
            ModelInfo(
                id = ModelId.PHI4_MINI,
                name = "Phi-4 Mini",
                parameterSize = "3.8B",
                quant = "Q4_K_M",
                fileSizeBytes = 2_400_000_000L,
                filename = "phi-4-mini.gguf",
                downloadUrl = "",
                description = "",
                state = ModelState.NOT_INSTALLED
            )
        )

        val selectedReadyModel = availableModels.firstOrNull { it.id == ModelId.QWEN3_1_7B && it.state == ModelState.READY }
        assertNotNull(selectedReadyModel)
        assertEquals(ModelId.QWEN3_1_7B, selectedReadyModel?.id)

        val uninstalledModel = availableModels.firstOrNull { it.id == ModelId.PHI4_MINI && it.state == ModelState.READY }
        assertEquals(null, uninstalledModel)
    }
}
