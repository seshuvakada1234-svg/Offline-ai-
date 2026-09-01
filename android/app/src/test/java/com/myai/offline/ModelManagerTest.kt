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
            name = "Qwen3 1.7B",
            tag = "Fast Assistant (Default)",
            description = "Fast, lightweight local on-device LLM with Telugu & English capabilities.",
            sizeFormatted = "1.18 GB",
            sizeBytes = 1266679808L,
            isDefault = true,
            sha256Expected = "9a4f218c347b0e3568c09a842183e29f032e1858a74e9b98031d234ea576ef12",
            filename = "qwen3-1.7b-instruct-q4_k_m.gguf",
            sourceUrl = "https://huggingface.co/Qwen/Qwen3-1.7B-Instruct-GGUF/resolve/main/qwen3-1.7b-instruct-q4_k_m.gguf",
            contextSize = 4096,
            quant = "Q4_K_M",
            backend = "llama.cpp",
            architecture = "qwen3",
            state = ModelState.READY,
            progress = 100
        )

        assertEquals("Qwen3 1.7B", qwen.name)
        assertEquals("Q4_K_M", qwen.quant)
        assertEquals(ModelState.READY, qwen.state)
        assertEquals(1.0f, qwen.downloadProgress, 0.001f)
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
                tag = "Fast Assistant",
                description = "",
                sizeFormatted = "1.18 GB",
                sizeBytes = 1150000000L,
                sha256Expected = "",
                filename = "qwen3-1.7b.gguf",
                sourceUrl = "",
                contextSize = 4096,
                quant = "Q4_K_M",
                backend = "llama.cpp",
                architecture = "qwen3",
                state = ModelState.READY
            ),
            ModelInfo(
                id = ModelId.PHI4_MINI,
                name = "Phi-4 Mini",
                tag = "Coding",
                description = "",
                sizeFormatted = "2.31 GB",
                sizeBytes = 2400000000L,
                sha256Expected = "",
                filename = "phi-4-mini.gguf",
                sourceUrl = "",
                contextSize = 4096,
                quant = "Q4_K_M",
                backend = "llama.cpp",
                architecture = "phi4",
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
