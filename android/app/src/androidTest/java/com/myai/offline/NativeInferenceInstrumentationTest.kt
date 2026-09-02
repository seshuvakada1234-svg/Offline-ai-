package com.myai.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myai.offline.data.model.ModelConstants
import com.myai.offline.llm.LlamaTokenCallback
import com.myai.offline.llm.NativeLlamaBridge
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NativeInferenceInstrumentationTest {

    @Test
    fun llamaModelLoadAndSingleTokenGeneration_whenLocalModelExists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Native llama backend unavailable", NativeLlamaBridge.isAvailable())

        val availableModelFile = ModelConstants.INITIAL_MODELS
            .filter { it.isChatModel }
            .map { model -> File(File(context.filesDir, "models/${model.id.rawValue}"), model.filename) }
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
}
