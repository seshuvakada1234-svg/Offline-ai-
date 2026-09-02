package com.myai.offline.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.StatFs
import android.util.Log
import com.myai.offline.data.model.ModelConstants
import com.myai.offline.data.model.ModelId
import com.myai.offline.data.model.ModelInfo
import com.myai.offline.data.model.ModelState
import com.myai.offline.llm.NativeLlamaBridge
import com.myai.offline.voice.NativeWhisperBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.max
import kotlin.coroutines.coroutineContext

class ModelRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val TAG = "ModelRepository"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("myai_models_pref", Context.MODE_PRIVATE)

    private val _models = MutableStateFlow(ModelConstants.INITIAL_MODELS)
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()

    private val _selectedModelId = MutableStateFlow(ModelId.QWEN3_1_7B)
    val selectedModelId: StateFlow<ModelId> = _selectedModelId.asStateFlow()

    private val downloadJobs = mutableMapOf<ModelId, Job>()

    init {
        loadSavedStates()
    }

    fun loadSavedStates() {
        reconcileInstalledModels(verifyChecksum = false)
    }

    fun checkLocalModelFiles() {
        reconcileInstalledModels(verifyChecksum = false)
    }

    fun reconcileInstalledModels(verifyChecksum: Boolean = false) {
        val refreshed = _models.value.map { model ->
            val finalFile = getModelFile(model)
            val partialFile = getDownloadFile(model)

            when {
                finalFile.exists() -> {
                    val validation = validateModelFile(
                        model = model,
                        file = finalFile,
                        verifyChecksum = verifyChecksum,
                        verifyRuntimeLoad = false
                    )
                    if (validation.isValid) {
                        model.copy(
                            state = if (model.state == ModelState.ACTIVE) ModelState.ACTIVE else ModelState.READY,
                            downloadedBytes = finalFile.length(),
                            downloadSpeed = null,
                            errorMessage = null
                        )
                    } else {
                        finalFile.delete()
                        model.copy(
                            state = ModelState.NOT_INSTALLED,
                            downloadedBytes = 0L,
                            downloadSpeed = null,
                            errorMessage = validation.errorMessage,
                            isLoaded = false
                        )
                    }
                }

                partialFile.exists() && partialFile.length() > 0L -> {
                    model.copy(
                        state = ModelState.PAUSED,
                        downloadedBytes = partialFile.length(),
                        downloadSpeed = null,
                        errorMessage = null,
                        isLoaded = false
                    )
                }

                else -> {
                    model.copy(
                        state = ModelState.NOT_INSTALLED,
                        downloadedBytes = 0L,
                        downloadSpeed = null,
                        errorMessage = null,
                        isLoaded = false
                    )
                }
            }
        }

        _models.value = refreshed
        restoreSelectedModel()
    }

    fun getModelsDir(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getModelDirectory(model: ModelInfo): File {
        val dir = File(getModelsDir(), model.id.rawValue)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getModelFile(model: ModelInfo): File {
        return File(getModelDirectory(model), model.filename)
    }

    fun getDownloadFile(model: ModelInfo): File {
        return File(getModelDirectory(model), "${model.filename}.download")
    }

    fun getModelFile(filename: String): File {
        return File(getModelsDir(), filename)
    }

    fun getModel(id: ModelId): ModelInfo? {
        return _models.value.firstOrNull { it.id == id }
    }

    fun getSelectedModel(): ModelInfo {
        return _models.value.firstOrNull { it.id == _selectedModelId.value }
            ?: _models.value.first { it.id == ModelId.QWEN3_1_7B }
    }

    fun selectModel(id: ModelId): Boolean {
        val model = getModel(id) ?: return false
        if (!model.isChatModel) {
            Log.w(TAG, "selectModel ignored for non-chat model id=${id.rawValue}")
            return false
        }

        if (model.state !in setOf(ModelState.READY, ModelState.ACTIVE)) {
            Log.w(TAG, "Cannot select model ${model.name}. State=${model.state}")
            return false
        }

        val modelFile = getModelFile(model)
        val validation = validateModelFile(
            model = model,
            file = modelFile,
            verifyChecksum = false,
            verifyRuntimeLoad = false
        )
        if (!validation.isValid) {
            Log.w(TAG, "Cannot select ${model.name}: ${validation.errorMessage}")
            return false
        }

        _selectedModelId.value = id
        prefs.edit().putString(KEY_SELECTED_MODEL_ID, id.rawValue).apply()
        return true
    }

    fun markModelLoading(id: ModelId) {
        val model = getModel(id) ?: return
        if (!model.isChatModel) return

        updateModel(id) {
            it.copy(
                state = ModelState.LOADING,
                errorMessage = null,
                isLoaded = false
            )
        }
    }

    fun markModelActive(id: ModelId) {
        val model = getModel(id) ?: return
        if (!model.isChatModel) return

        _models.value = _models.value.map { item ->
            when {
                item.id == id -> item.copy(
                    state = ModelState.ACTIVE,
                    isLoaded = true,
                    errorMessage = null
                )

                item.isChatModel && item.state == ModelState.ACTIVE -> item.copy(
                    state = ModelState.READY,
                    isLoaded = false
                )

                item.isChatModel && item.state == ModelState.LOADING -> item.copy(
                    state = ModelState.READY,
                    isLoaded = false
                )

                else -> item
            }
        }
    }

    fun markModelReady(id: ModelId) {
        updateModel(id) {
            if (it.state == ModelState.NOT_INSTALLED || it.state == ModelState.DOWNLOADING || it.state == ModelState.VERIFYING) {
                it
            } else {
                it.copy(
                    state = ModelState.READY,
                    isLoaded = false,
                    errorMessage = null
                )
            }
        }
    }

    fun markModelLoadFailed(id: ModelId, errorMessage: String) {
        val model = getModel(id)
        val hasValidInstalledFile = if (model != null) {
            val file = getModelFile(model)
            file.exists() && verifyModelFile(model, file)
        } else {
            false
        }

        updateModel(id) {
            it.copy(
                state = if (hasValidInstalledFile) ModelState.READY else ModelState.ERROR,
                isLoaded = false,
                errorMessage = errorMessage
            )
        }
    }

    fun downloadModel(id: ModelId) {
        startDownload(id)
    }

    fun retryDownload(id: ModelId) {
        startDownload(id, restartFromScratch = false)
    }

    fun startDownload(id: ModelId, restartFromScratch: Boolean = false) {
        val currentModel = getModel(id) ?: return
        if (currentModel.state == ModelState.DOWNLOADING) return

        if (!currentModel.isDownloadable || currentModel.sourceUrl.isNullOrBlank()) {
            updateModel(id) {
                it.copy(
                    state = ModelState.ERROR,
                    errorMessage = "No direct download URL is available for ${currentModel.name}."
                )
            }
            return
        }

        downloadJobs[id]?.cancel()

        val job = scope.launch(Dispatchers.IO) {
            val model = getModel(id) ?: return@launch
            val targetFile = getModelFile(model)
            val partialFile = getDownloadFile(model)

            if (restartFromScratch && partialFile.exists()) {
                partialFile.delete()
            }

            Log.i(TAG, "MODEL_DOWNLOAD_START id=${model.id.rawValue} name=${model.name}")

            try {
                val freeBytes = getStorageStats().freeBytes
                val existingPartial = if (partialFile.exists()) partialFile.length() else 0L
                val bytesNeeded = (model.sizeBytes - existingPartial).coerceAtLeast(0L)
                if (freeBytes < bytesNeeded) {
                    throw IllegalStateException(
                        "Insufficient storage. Need ${formatBytes(bytesNeeded)} free, but only ${formatBytes(freeBytes)} available."
                    )
                }

                var completed = false
                var lastError: Exception? = null
                repeat(MAX_DOWNLOAD_RETRIES) { attempt ->
                    if (completed) return@repeat
                    try {
                        downloadOnce(model, partialFile)
                        completed = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (downloadError: Exception) {
                        lastError = downloadError
                        Log.w(
                            TAG,
                            "MODEL_DOWNLOAD_RETRY id=${model.id.rawValue} attempt=${attempt + 1} error=${downloadError.message}"
                        )
                        if (attempt < MAX_DOWNLOAD_RETRIES - 1) {
                            delay((attempt + 1) * RETRY_BACKOFF_MS)
                        }
                    }
                }

                if (!completed) {
                    throw lastError ?: IllegalStateException("Download failed")
                }

                updateModel(id) {
                    it.copy(
                        state = ModelState.VERIFYING,
                        downloadSpeed = null,
                        downloadedBytes = partialFile.length(),
                        errorMessage = null
                    )
                }
                Log.i(TAG, "MODEL_VERIFY_START id=${model.id.rawValue} file=${partialFile.absolutePath}")

                val validation = validateModelFile(
                    model = model,
                    file = partialFile,
                    verifyChecksum = true,
                    verifyRuntimeLoad = false
                )
                if (!validation.isValid) {
                    partialFile.delete()
                    throw IllegalStateException(validation.errorMessage ?: "Verification failed")
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }

                val renamed = partialFile.renameTo(targetFile)
                if (!renamed) {
                    partialFile.copyTo(targetFile, overwrite = true)
                    partialFile.delete()
                }

                val finalValidation = validateModelFile(
                    model = model,
                    file = targetFile,
                    verifyChecksum = false,
                    verifyRuntimeLoad = false
                )
                if (!finalValidation.isValid) {
                    targetFile.delete()
                    throw IllegalStateException(finalValidation.errorMessage ?: "Final file validation failed")
                }

                updateModel(id) {
                    it.copy(
                        state = ModelState.READY,
                        downloadedBytes = targetFile.length(),
                        downloadSpeed = null,
                        errorMessage = null
                    )
                }

                Log.i(TAG, "MODEL_DOWNLOAD_COMPLETE id=${model.id.rawValue} bytes=${targetFile.length()}")
                Log.i(TAG, "MODEL_VERIFY_SUCCESS id=${model.id.rawValue} path=${targetFile.absolutePath}")
            } catch (cancelled: CancellationException) {
                val bytes = if (partialFile.exists()) partialFile.length() else model.downloadedBytes
                updateModel(id) {
                    it.copy(
                        state = ModelState.PAUSED,
                        downloadedBytes = bytes,
                        downloadSpeed = null,
                        errorMessage = null
                    )
                }
                Log.i(TAG, "MODEL_DOWNLOAD_PAUSED id=${model.id.rawValue} bytes=$bytes")
            } catch (e: Exception) {
                updateModel(id) {
                    it.copy(
                        state = ModelState.ERROR,
                        downloadSpeed = null,
                        errorMessage = e.localizedMessage ?: "Download failed"
                    )
                }
                Log.e(TAG, "MODEL_VERIFY_FAILED id=${model.id.rawValue} error=${e.message}", e)
            } finally {
                downloadJobs.remove(id)
            }
        }

        downloadJobs[id] = job
    }

    fun pauseDownload(id: ModelId) {
        downloadJobs[id]?.cancel()
        val model = getModel(id) ?: return
        val partialFile = getDownloadFile(model)
        val bytes = if (partialFile.exists()) partialFile.length() else model.downloadedBytes
        updateModel(id) {
            it.copy(
                state = ModelState.PAUSED,
                downloadedBytes = bytes,
                downloadSpeed = null,
                errorMessage = null
            )
        }
    }

    fun resumeDownload(id: ModelId) {
        startDownload(id)
    }

    fun cancelDownload(id: ModelId) {
        downloadJobs[id]?.cancel()
        downloadJobs.remove(id)

        val model = getModel(id) ?: return
        val partialFile = getDownloadFile(model)
        if (partialFile.exists()) {
            partialFile.delete()
        }

        val targetFile = getModelFile(model)
        val nextState = if (targetFile.exists()) ModelState.READY else ModelState.NOT_INSTALLED
        val downloadedBytes = if (targetFile.exists()) targetFile.length() else 0L

        updateModel(id) {
            it.copy(
                state = nextState,
                downloadedBytes = downloadedBytes,
                downloadSpeed = null,
                errorMessage = null
            )
        }
    }

    fun deleteModel(id: ModelId) {
        downloadJobs[id]?.cancel()
        downloadJobs.remove(id)

        val model = getModel(id) ?: return
        val targetFile = getModelFile(model)
        val partialFile = getDownloadFile(model)

        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (partialFile.exists()) {
            partialFile.delete()
        }

        updateModel(id) {
            it.copy(
                state = ModelState.NOT_INSTALLED,
                downloadedBytes = 0L,
                downloadSpeed = null,
                errorMessage = null,
                isLoaded = false
            )
        }

        if (_selectedModelId.value == id) {
            restoreSelectedModel()
        }
    }

    fun verifyModelFile(model: ModelInfo, file: File): Boolean {
        return validateModelFile(
            model = model,
            file = file,
            verifyChecksum = false,
            verifyRuntimeLoad = false
        ).isValid
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun getStorageStats(): StorageStats {
        val statFs = StatFs(context.filesDir.absolutePath)
        val totalBytes = statFs.totalBytes
        val freeBytes = statFs.availableBytes
        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

        val modelBytes = getModelsDir()
            .walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

        return StorageStats(
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            freeBytes = freeBytes,
            modelsSizeBytes = modelBytes
        )
    }

    private suspend fun downloadOnce(model: ModelInfo, outputFile: File): Long {
        val url = model.sourceUrl ?: throw IllegalStateException("Missing download URL for ${model.name}")
        var existingBytes = if (outputFile.exists()) outputFile.length() else 0L

        if (model.sizeBytes > 0L && existingBytes > model.sizeBytes) {
            outputFile.delete()
            existingBytes = 0L
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/octet-stream")
            if (existingBytes > 0L) {
                setRequestProperty("Range", "bytes=$existingBytes-")
            }
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in listOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) {
                val responseBody = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull().orEmpty()
                throw IllegalStateException("HTTP $responseCode while downloading ${model.name}. ${responseBody.take(120)}")
            }

            val contentType = connection.contentType?.lowercase(Locale.US).orEmpty()
            if (contentType.contains("text/html") || contentType.contains("application/json")) {
                throw IllegalStateException("Unexpected content-type '$contentType' for model download.")
            }

            val appendMode = responseCode == HttpURLConnection.HTTP_PARTIAL && existingBytes > 0L
            if (appendMode) {
                val contentRange = connection.getHeaderField("Content-Range").orEmpty()
                if (!contentRange.startsWith("bytes $existingBytes-")) {
                    throw IllegalStateException(
                        "Range mismatch while resuming ${model.name}. Existing=$existingBytes content-range='$contentRange'."
                    )
                }
            }

            val totalExpectedBytes = when {
                model.sizeBytes > 0L -> model.sizeBytes
                connection.contentLengthLong > 0L && appendMode -> existingBytes + connection.contentLengthLong
                connection.contentLengthLong > 0L -> connection.contentLengthLong
                else -> 0L
            }

            var downloadedBytes = if (appendMode) existingBytes else 0L
            if (!appendMode && outputFile.exists()) {
                outputFile.delete()
            }

            updateModel(model.id) {
                it.copy(
                    state = ModelState.DOWNLOADING,
                    downloadedBytes = downloadedBytes,
                    downloadSpeed = null,
                    errorMessage = null
                )
            }

            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L

            connection.inputStream.use { input ->
                FileOutputStream(outputFile, appendMode).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        bytesSinceLastUpdate += read

                        val now = System.currentTimeMillis()
                        val intervalMs = now - lastUpdateTime
                        if (intervalMs >= 250L) {
                            val speedBytesPerSec = bytesSinceLastUpdate / max(1.0, intervalMs / 1000.0)
                            val speedLabel = String.format(Locale.US, "%.1f MB/s", speedBytesPerSec / (1024.0 * 1024.0))

                            updateModel(model.id) {
                                it.copy(
                                    state = ModelState.DOWNLOADING,
                                    downloadedBytes = downloadedBytes,
                                    downloadSpeed = speedLabel,
                                    errorMessage = null
                                )
                            }

                            Log.i(
                                TAG,
                                "MODEL_DOWNLOAD_PROGRESS id=${model.id.rawValue} bytes=$downloadedBytes speed=$speedLabel"
                            )

                            bytesSinceLastUpdate = 0L
                            lastUpdateTime = now
                        }
                    }
                    output.flush()
                }
            }

            val fileBytes = if (outputFile.exists()) outputFile.length() else downloadedBytes
            if (fileBytes != downloadedBytes) {
                throw IllegalStateException(
                    "Download write mismatch. Streamed=$downloadedBytes bytes, file=$fileBytes bytes."
                )
            }

            updateModel(model.id) {
                it.copy(
                    state = ModelState.DOWNLOADING,
                    downloadedBytes = fileBytes,
                    downloadSpeed = null,
                    errorMessage = null
                )
            }

            if (connection.contentLengthLong > 0L) {
                val expectedFromResponse = if (appendMode) {
                    existingBytes + connection.contentLengthLong
                } else {
                    connection.contentLengthLong
                }
                if (fileBytes != expectedFromResponse) {
                    throw IllegalStateException(
                        "Downloaded bytes mismatch. Expected $expectedFromResponse, got $fileBytes."
                    )
                }
            }

            if (!hasExpectedSize(fileBytes, totalExpectedBytes)) {
                throw IllegalStateException(
                    "Downloaded size mismatch for ${model.name}. Expected ${totalExpectedBytes} bytes, got $fileBytes."
                )
            }

            return fileBytes
        } finally {
            connection.disconnect()
        }
    }

    private fun validateModelFile(
        model: ModelInfo,
        file: File,
        verifyChecksum: Boolean,
        verifyRuntimeLoad: Boolean
    ): ValidationResult {
        if (!file.exists()) {
            return ValidationResult(false, "Model file does not exist: ${file.absolutePath}")
        }

        if (!file.canRead()) {
            return ValidationResult(false, "Model file is not readable: ${file.absolutePath}")
        }

        if (file.length() < MIN_VALID_MODEL_BYTES) {
            return ValidationResult(false, "Model file is too small (${file.length()} bytes).")
        }

        if (looksLikeHtml(file)) {
            return ValidationResult(false, "Downloaded file appears to be HTML/error content, not a model file.")
        }

        if (!hasExpectedSize(file.length(), model.sizeBytes)) {
            return ValidationResult(
                false,
                "File size mismatch. Expected ${model.sizeBytes} bytes, got ${file.length()} bytes."
            )
        }

        val headerOk = if (model.isWhisper) {
            hasValidWhisperHeader(file)
        } else {
            hasValidGgufHeader(file)
        }

        if (!headerOk) {
            return ValidationResult(false, "Invalid model header/magic for ${model.filename}.")
        }

        if (verifyChecksum && !model.sha256Expected.isNullOrBlank()) {
            val actual = calculateSha256(file)
            if (!actual.equals(model.sha256Expected, ignoreCase = true)) {
                return ValidationResult(
                    false,
                    "SHA256 mismatch. Expected ${model.sha256Expected}, got $actual."
                )
            }
        }

        if (verifyRuntimeLoad) {
            val runtimeValidation = if (model.isWhisper) {
                verifyWhisperRuntime(file)
            } else {
                verifyLlamaRuntime(model = model, file = file)
            }
            if (!runtimeValidation.isValid) {
                return runtimeValidation
            }
        }

        return ValidationResult(true)
    }

    private fun verifyLlamaRuntime(
        model: ModelInfo,
        file: File
    ): ValidationResult {
        if (!NativeLlamaBridge.isAvailable()) {
            return ValidationResult(false, "Native llama backend is not available.")
        }

        val handle = NativeLlamaBridge.nativeLoadModel(
            file.absolutePath,
            2,
            minOf(2048, model.contextSize)
        )

        if (handle == 0L) {
            return ValidationResult(false, "llama.cpp could not load model: ${file.name}")
        }

        return try {
            if (!NativeLlamaBridge.nativeIsModelLoaded(handle)) {
                return ValidationResult(false, "llama.cpp reported model is not loaded.")
            }

            ValidationResult(true)
        } catch (e: Exception) {
            ValidationResult(false, "llama runtime validation failed: ${e.localizedMessage}")
        } finally {
            NativeLlamaBridge.nativeUnloadModel(handle)
        }
    }

    private fun verifyWhisperRuntime(file: File): ValidationResult {
        if (!NativeWhisperBridge.isAvailable()) {
            return ValidationResult(false, "Native whisper backend is not available.")
        }

        val handle = NativeWhisperBridge.nativeLoadModel(file.absolutePath)
        if (handle == 0L) {
            return ValidationResult(false, "whisper.cpp could not load model: ${file.name}")
        }

        return try {
            NativeWhisperBridge.nativeUnloadModel(handle)
            ValidationResult(true)
        } catch (e: Exception) {
            ValidationResult(false, "whisper runtime validation failed: ${e.localizedMessage}")
        }
    }

    private fun hasValidGgufHeader(file: File): Boolean {
        return try {
            FileInputStream(file).use { input ->
                val magic = ByteArray(4)
                val read = input.read(magic)
                read == 4 &&
                    magic[0] == 'G'.code.toByte() &&
                    magic[1] == 'G'.code.toByte() &&
                    magic[2] == 'U'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun hasValidWhisperHeader(file: File): Boolean {
        return try {
            FileInputStream(file).use { input ->
                val magic = ByteArray(4)
                val read = input.read(magic)
                if (read < 4) return false

                val header = String(magic, Charsets.US_ASCII)
                header == "ggml" || header == "ggmf" || header == "ggjt"
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun looksLikeHtml(file: File): Boolean {
        return try {
            FileInputStream(file).use { input ->
                val sniff = ByteArray(512)
                val read = input.read(sniff)
                if (read <= 0) return false
                val head = String(sniff, 0, read, Charsets.UTF_8).lowercase(Locale.US)
                head.contains("<html") || head.contains("<!doctype html")
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun hasExpectedSize(actual: Long, expected: Long): Boolean {
        if (expected <= 0L) return actual > 0L
        return actual == expected
    }

    private fun restoreSelectedModel() {
        val allModels = _models.value
        val saved = prefs.getString(KEY_SELECTED_MODEL_ID, ModelId.QWEN3_1_7B.rawValue)
        val savedId = ModelId.fromRaw(saved ?: ModelId.QWEN3_1_7B.rawValue)

        val installedChat = allModels.filter {
            it.isChatModel && it.state in setOf(ModelState.READY, ModelState.ACTIVE)
        }

        val selected = when {
            installedChat.any { it.id == savedId } -> savedId
            installedChat.any { it.id == ModelId.QWEN3_1_7B } -> ModelId.QWEN3_1_7B
            installedChat.isNotEmpty() -> installedChat.first().id
            else -> ModelId.QWEN3_1_7B
        }

        _selectedModelId.value = selected
        prefs.edit().putString(KEY_SELECTED_MODEL_ID, selected.rawValue).apply()
    }

    private fun updateModel(id: ModelId, transform: (ModelInfo) -> ModelInfo) {
        _models.value = _models.value.map { model ->
            if (model.id == id) transform(model) else model
        }
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    data class StorageStats(
        val totalBytes: Long,
        val usedBytes: Long,
        val freeBytes: Long,
        val modelsSizeBytes: Long
    ) {
        val totalFormatted: String = String.format(Locale.US, "%.1f GB", totalBytes / (1024.0 * 1024.0 * 1024.0))
        val usedFormatted: String = String.format(Locale.US, "%.1f GB", usedBytes / (1024.0 * 1024.0 * 1024.0))
        val freeFormatted: String = String.format(Locale.US, "%.1f GB", freeBytes / (1024.0 * 1024.0 * 1024.0))
        val modelsFormatted: String = String.format(Locale.US, "%.2f GB", modelsSizeBytes / (1024.0 * 1024.0 * 1024.0))
    }

    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    companion object {
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val USER_AGENT = "MyAI-Android-Offline/2.0"
        private const val MIN_VALID_MODEL_BYTES = 1024L
        private const val MAX_DOWNLOAD_RETRIES = 3
        private const val RETRY_BACKOFF_MS = 1_500L
    }
}
