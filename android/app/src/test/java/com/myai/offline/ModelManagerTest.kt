package com.myai.offline

import com.myai.offline.data.model.ModelConstants
import com.myai.offline.data.model.ModelId
import com.myai.offline.data.model.ModelInfo
import com.myai.offline.data.model.ModelManifest
import com.myai.offline.data.model.ModelState
import com.myai.offline.data.model.ModelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun testManifestHasRequiredModelsAndNoFakeReadyState() {
        val initialModels = ModelConstants.INITIAL_MODELS
        assertEquals(5, initialModels.size)

        initialModels.forEach { model ->
            assertEquals(ModelState.NOT_INSTALLED, model.state)
            assertFalse(model.isLoaded)
            assertTrue(model.filename.isNotBlank())
            assertTrue(model.sizeBytes > 0L)
        }
    }

    @Test
    fun testExactRepositoriesAndFilenames() {
        val qwen17 = ModelManifest.entries.first { it.id == ModelId.QWEN3_1_7B }
        assertEquals("ggml-org/Qwen3-1.7B-GGUF", qwen17.repository)
        assertEquals("Qwen3-1.7B-Q4_K_M.gguf", qwen17.filename)

        val qwen4 = ModelManifest.entries.first { it.id == ModelId.QWEN3_4B }
        assertEquals("ggml-org/Qwen3-4B-GGUF", qwen4.repository)
        assertEquals("Qwen3-4B-Q4_K_M.gguf", qwen4.filename)

        val phi4 = ModelManifest.entries.first { it.id == ModelId.PHI4_MINI }
        assertEquals("second-state/Phi-4-mini-instruct-GGUF", phi4.repository)
        assertEquals("Phi-4-mini-instruct-Q4_K_M.gguf", phi4.filename)

        val gemma4 = ModelManifest.entries.first { it.id == ModelId.GEMMA3_4B }
        assertEquals("bartowski/google_gemma-3-4b-it-GGUF", gemma4.repository)
        assertEquals("google_gemma-3-4b-it-Q4_K_M.gguf", gemma4.filename)

        val whisper = ModelManifest.entries.first { it.id == ModelId.WHISPER_BASE }
        assertEquals("ggerganov/whisper.cpp", whisper.repository)
        assertEquals("ggml-base.en.bin", whisper.filename)
    }

    @Test
    fun testDownloadUrlConstructionUsesRepositoryAndFilename() {
        val expectedUrls = mapOf(
            ModelId.QWEN3_1_7B to "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
            ModelId.QWEN3_4B to "https://huggingface.co/ggml-org/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf",
            ModelId.PHI4_MINI to "https://huggingface.co/second-state/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q4_K_M.gguf",
            ModelId.GEMMA3_4B to "https://huggingface.co/bartowski/google_gemma-3-4b-it-GGUF/resolve/main/google_gemma-3-4b-it-Q4_K_M.gguf",
            ModelId.WHISPER_BASE to "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin"
        )

        expectedUrls.forEach { (id, expectedUrl) ->
            val model = ModelConstants.INITIAL_MODELS.first { it.id == id }
            assertEquals(expectedUrl, model.sourceUrl)
        }
    }

    @Test
    fun testUrlBuilderEncodesPathSegments() {
        val url = ModelManifest.buildHuggingFaceResolveUrl(
            repository = "owner with space/repo name",
            filename = "folder/my model.gguf"
        )

        assertEquals(
            "https://huggingface.co/owner%20with%20space/repo%20name/resolve/main/folder/my%20model.gguf",
            url
        )
    }

    @Test
    fun testLifecycleStatesIncludeRequiredSet() {
        assertTrue(ModelState.entries.contains(ModelState.NOT_INSTALLED))
        assertTrue(ModelState.entries.contains(ModelState.DOWNLOADING))
        assertTrue(ModelState.entries.contains(ModelState.PAUSED))
        assertTrue(ModelState.entries.contains(ModelState.VERIFYING))
        assertTrue(ModelState.entries.contains(ModelState.READY))
        assertTrue(ModelState.entries.contains(ModelState.LOADING))
        assertTrue(ModelState.entries.contains(ModelState.ACTIVE))
        assertTrue(ModelState.entries.contains(ModelState.ERROR))
    }

    @Test
    fun testGgufHeaderVerification() {
        val validGgufFile = tempFolder.newFile("valid.gguf")
        FileOutputStream(validGgufFile).use { fos ->
            fos.write(byteArrayOf(0x47, 0x47, 0x55, 0x46))
            fos.write(ByteArray(2048))
        }
        assertTrue(verifyGgufHeader(validGgufFile))
    }

    @Test
    fun testCorruptedGgufIsRejected() {
        val corruptFile = tempFolder.newFile("corrupt.gguf")
        FileOutputStream(corruptFile).use { fos ->
            fos.write("<!doctype html><html>".toByteArray())
            fos.write(ByteArray(2048))
        }
        assertFalse(verifyGgufHeader(corruptFile))
    }

    @Test
    fun testMissingModelFileRejected() {
        val missing = File(tempFolder.root, "missing.gguf")
        assertFalse(verifyGgufHeader(missing))
    }

    @Test
    fun testWhisperHeaderVerification() {
        val validWhisperFile = tempFolder.newFile("ggml-base.en.bin")
        FileOutputStream(validWhisperFile).use { fos ->
            fos.write(byteArrayOf('g'.code.toByte(), 'g'.code.toByte(), 'm'.code.toByte(), 'l'.code.toByte()))
            fos.write(ByteArray(2048))
        }
        assertTrue(verifyWhisperHeader(validWhisperFile))
    }

    @Test
    fun testSha256ChecksumVerification() {
        val testFile = tempFolder.newFile("test_data.bin")
        val content = "MyAI Offline Validation Payload"
        testFile.writeText(content)

        val expected = MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val actual = calculateSha256(testFile)
        assertEquals(expected, actual)
    }

    @Test
    fun testSha256ChecksumFailure() {
        val testFile = tempFolder.newFile("checksum_fail.bin")
        testFile.writeText("abc")
        val actual = calculateSha256(testFile)
        val incorrect = "0000000000000000000000000000000000000000000000000000000000000000"
        assertFalse(actual.equals(incorrect, ignoreCase = true))
    }

    @Test
    fun testDownloadProgressCalculation() {
        val model = ModelInfo(
            id = ModelId.QWEN3_1_7B,
            name = "Qwen3 1.7B",
            tag = "Fast Assistant",
            description = "",
            sizeFormatted = "1.00 GB",
            sizeBytes = 1000L,
            downloadedBytes = 620L,
            sha256Expected = null,
            repository = "ggml-org/Qwen3-1.7B-GGUF",
            filename = "Qwen3-1.7B-Q4_K_M.gguf",
            sourceUrl = "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
            contextSize = 4096,
            quant = "Q4_K_M",
            modelType = ModelType.CHAT_LLM,
            capabilities = listOf("chat"),
            architecture = "qwen3",
            state = ModelState.DOWNLOADING
        )

        assertEquals(0.62f, model.downloadProgress, 0.001f)
    }

    private fun verifyGgufHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 1024L) return false
        return try {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(4)
                if (fis.read(magic) < 4) return false
                magic[0] == 0x47.toByte() &&
                    magic[1] == 0x47.toByte() &&
                    magic[2] == 0x55.toByte() &&
                    magic[3] == 0x46.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun verifyWhisperHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 1024L) return false
        return try {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(4)
                if (fis.read(magic) < 4) return false
                val header = String(magic, Charsets.US_ASCII)
                header == "ggml" || header == "ggmf" || header == "ggjt"
            }
        } catch (_: Exception) {
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
