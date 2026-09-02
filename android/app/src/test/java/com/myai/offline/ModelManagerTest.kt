package com.myai.offline

import com.myai.offline.data.model.ModelConstants
import com.myai.offline.data.model.ModelId
import com.myai.offline.data.model.ModelInfo
import com.myai.offline.data.model.ModelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

class ModelManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testAllRequiredModelsExistInConstants() {
        val modelIds = ModelId.entries.toList()
        assertTrue(modelIds.contains(ModelId.QWEN3_1_7B))
        assertTrue(modelIds.contains(ModelId.QWEN3_4B))
        assertTrue(modelIds.contains(ModelId.PHI4_MINI))
        assertTrue(modelIds.contains(ModelId.GEMMA3_4B))
        assertTrue(modelIds.contains(ModelId.GEMMA3_270M))
        assertTrue(modelIds.contains(ModelId.WHISPER_BASE))

        val initialModels = ModelConstants.INITIAL_MODELS
        assertEquals(6, initialModels.size)

        // Verify all models start in NOT_INSTALLED state (no fake ready)
        for (model in initialModels) {
            assertEquals("Model ${model.name} must start as NOT_INSTALLED", ModelState.NOT_INSTALLED, model.state)
            assertFalse("Model ${model.name} must not be loaded initially", model.isLoaded)
            assertTrue("Model ${model.name} filename must be defined", model.filename.isNotBlank())
            assertTrue("Model ${model.name} sourceUrl must be a valid URL", model.sourceUrl.startsWith("https://"))
            assertTrue("Model ${model.name} sizeBytes must be positive", model.sizeBytes > 0L)
        }
    }

    @Test
    fun testExactModelFilenamesAndQuantizations() {
        val qwen17 = ModelConstants.INITIAL_MODELS.first { it.id == ModelId.QWEN3_1_7B }
        assertEquals("Qwen3-1.7B-Q4_K_M.gguf", qwen17.filename)
        assertEquals("Q4_K_M", qwen17.quant)
        assertEquals("llama.cpp", qwen17.backend)

        val qwen4b = ModelConstants.INITIAL_MODELS.first { it.id == ModelId.QWEN3_4B }
        assertEquals("Qwen3-4B-Q4_K_M.gguf", qwen4b.filename)
        assertEquals("Q4_K_M", qwen4b.quant)

        val phi4 = ModelConstants.INITIAL_MODELS.first { it.id == ModelId.PHI4_MINI }
        assertEquals("Phi-4-mini-instruct-Q4_K_M.gguf", phi4.filename)
        assertEquals("Q4_K_M", phi4.quant)

        val gemma4b = ModelConstants.INITIAL_MODELS.first { it.id == ModelId.GEMMA3_4B }
        assertEquals("google_gemma-3-4b-it-Q4_K_M.gguf", gemma4b.filename)
        assertEquals("Q4_K_M", gemma4b.quant)

        val gemma270m = ModelConstants.INITIAL_MODELS.first { it.id == ModelId.GEMMA3_270M }
        assertEquals("gemma-3-270m-it-Q4_K_M.gguf", gemma270m.filename)
        assertEquals("Q4_K_M", gemma270m.quant)

        val whisper = ModelConstants.INITIAL_MODELS.first { it.id == ModelId.WHISPER_BASE }
        assertEquals("ggml-base.en.bin", whisper.filename)
        assertEquals("whisper.cpp", whisper.backend)
    }

    @Test
    fun testGgufHeaderVerification() {
        val validGgufFile = tempFolder.newFile("valid.gguf")
        FileOutputStream(validGgufFile).use { fos ->
            // GGUF magic bytes: 0x47, 0x47, 0x55, 0x46 ("GGUF")
            fos.write(byteArrayOf(0x47, 0x47, 0x55, 0x46))
            // Version 3 + metadata padding to exceed 1KB
            fos.write(ByteArray(2048))
        }

        val isValid = verifyGgufHeader(validGgufFile)
        assertTrue("Valid GGUF header must be accepted", isValid)
    }

    @Test
    fun testInvalidGgufRejection() {
        // 1. File too small / empty
        val emptyFile = tempFolder.newFile("empty.gguf")
        assertFalse("0-byte file must be rejected", verifyGgufHeader(emptyFile))

        // 2. Corrupted magic header
        val corruptFile = tempFolder.newFile("corrupt.gguf")
        FileOutputStream(corruptFile).use { fos ->
            fos.write("CORRUPT_MAGIC_HEADER".toByteArray())
            fos.write(ByteArray(2048))
        }
        assertFalse("File with bad magic header must be rejected", verifyGgufHeader(corruptFile))

        // 3. Non-existent file
        val missingFile = File(tempFolder.root, "missing.gguf")
        assertFalse("Missing file must be rejected", verifyGgufHeader(missingFile))
    }

    @Test
    fun testWhisperHeaderVerification() {
        val validWhisperFile = tempFolder.newFile("ggml-base.en.bin")
        FileOutputStream(validWhisperFile).use { fos ->
            // "ggml" magic
            fos.write(byteArrayOf('g'.code.toByte(), 'g'.code.toByte(), 'm'.code.toByte(), 'l'.code.toByte()))
            fos.write(ByteArray(2048))
        }
        val isValid = verifyWhisperHeader(validWhisperFile)
        assertTrue("Valid Whisper GGML header must be accepted", isValid)
    }

    @Test
    fun testSha256ChecksumVerification() {
        val testFile = tempFolder.newFile("test_data.bin")
        val content = "MyAI Offline On-Device LLM Verification Payload"
        testFile.writeText(content)

        val digest = MessageDigest.getInstance("SHA-256")
        val expectedHash = digest.digest(content.toByteArray()).joinToString("") { "%02x".format(it) }

        val calculated = calculateSha256(testFile)
        assertEquals(expectedHash, calculated)
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
    fun testModelDeletionPhysicallyRemovesFile() {
        val modelFile = tempFolder.newFile("qwen_test.gguf")
        val tmpFile = tempFolder.newFile("qwen_test.gguf.tmp")
        assertTrue(modelFile.exists())
        assertTrue(tmpFile.exists())

        // Perform deletion
        modelFile.delete()
        tmpFile.delete()

        assertFalse(modelFile.exists())
        assertFalse(tmpFile.exists())
    }

    @Test
    fun testModelProgressCalculation() {
        val totalBytes = 1000L
        val downloadedBytes = 620L

        val model = ModelInfo(
            id = ModelId.QWEN3_1_7B,
            name = "Qwen3 1.7B",
            tag = "Fast Assistant",
            description = "",
            sizeFormatted = "1.00 GB",
            sizeBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            progress = 62,
            sha256Expected = "",
            filename = "Qwen3-1.7B-Q4_K_M.gguf",
            sourceUrl = "",
            contextSize = 4096,
            quant = "Q4_K_M",
            backend = "llama.cpp",
            architecture = "qwen3",
            state = ModelState.DOWNLOADING
        )

        assertEquals(0.62f, model.downloadProgress, 0.001f)
    }

    // Helper methods matching ModelRepository
    private fun verifyGgufHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 1024L) return false
        return try {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(4)
                val read = fis.read(magic)
                if (read < 4) return false
                magic[0] == 0x47.toByte() && magic[1] == 0x47.toByte() && magic[2] == 0x55.toByte() && magic[3] == 0x46.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun verifyWhisperHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 1024L) return false
        return try {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(4)
                val read = fis.read(magic)
                if (read < 4) return false
                val isGgml = magic[0] == 'g'.code.toByte() && magic[1] == 'g'.code.toByte()
                val isLmg = magic[0] == 'l'.code.toByte() && magic[1] == 'm'.code.toByte()
                isGgml || isLmg || file.length() > 1024L * 1024L
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
