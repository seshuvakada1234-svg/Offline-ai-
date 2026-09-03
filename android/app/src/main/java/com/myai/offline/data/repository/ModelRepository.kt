package com.myai.offline.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.StatFs
import android.util.Log
import com.myai.offline.data.model.ModelConstants
import com.myai.offline.data.model.ModelId
import com.myai.offline.data.model.ModelInfo
import com.myai.offline.data.model.ModelPackageType
import com.myai.offline.data.model.ModelState
import com.myai.offline.data.model.ModelType
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
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.math.max
import kotlin.math.abs
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

    private val downloadJobs = ConcurrentHashMap<ModelId, Job>()
    private val activeConnections = ConcurrentHashMap<ModelId, HttpURLConnection>()
    private val suppressPauseOnCancellation = ConcurrentHashMap.newKeySet<ModelId>()

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
                    var validation = validateInstalledModelArtifacts(
                        model = model,
                        file = finalFile,
                        verifyChecksum = verifyChecksum,
                        verifyRuntimeLoad = false
                    )

                    if (!validation.isValid && model.packageType == ModelPackageType.TAR_BZ2_ARCHIVE) {
                        val archiveValidation = validateModelFile(
                            model = model,
                            file = finalFile,
                            verifyChecksum = verifyChecksum,
                            verifyRuntimeLoad = false
                        )

                        if (archiveValidation.isValid) {
                            val installValidation = installArchiveModel(model, finalFile)
                            validation = if (installValidation.isValid) {
                                validateInstalledModelArtifacts(
                                    model = model,
                                    file = finalFile,
                                    verifyChecksum = verifyChecksum,
                                    verifyRuntimeLoad = false
                                )
                            } else {
                                installValidation
                            }
                        }
                    }

                    if (validation.isValid) {
                        model.copy(
                            state = if (model.state == ModelState.ACTIVE) ModelState.ACTIVE else ModelState.READY,
                            downloadedBytes = finalFile.length(),
                            downloadSpeed = null,
                            errorMessage = null
                        )
                    } else {
                        finalFile.delete()
                        clearExtractedResources(model)
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
        val groupedRoot = File(getModelsDir(), modelStorageBucket(model.modelType))
        if (!groupedRoot.exists()) {
            groupedRoot.mkdirs()
        }

        val dir = File(groupedRoot, model.id.rawValue)
        migrateLegacyModelDirectoryIfNeeded(model, dir)

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

    fun findInstalledResource(model: ModelInfo, requiredName: String): File? {
        val modelDir = getModelDirectory(model)
        return findRequiredEntry(modelDir, requiredName)
    }

    private fun modelStorageBucket(modelType: ModelType): String {
        return when (modelType) {
            ModelType.CHAT_LLM -> MODELS_DIR_LLM
            ModelType.SPEECH_TO_TEXT -> MODELS_DIR_STT
            ModelType.TEXT_TO_SPEECH -> MODELS_DIR_TTS
        }
    }

    private fun migrateLegacyModelDirectoryIfNeeded(model: ModelInfo, destinationDir: File) {
        val legacyDir = File(getModelsDir(), model.id.rawValue)
        if (!legacyDir.exists() || legacyDir.absolutePath == destinationDir.absolutePath) {
            return
        }

        val legacyFiles = legacyDir.listFiles().orEmpty()
        if (legacyFiles.isEmpty()) {
            runCatching { legacyDir.delete() }
            return
        }

        val destinationFiles = destinationDir.listFiles().orEmpty()
        if (destinationFiles.isNotEmpty()) {
            return
        }

        val migrated = runCatching {
            legacyDir.copyRecursively(destinationDir, overwrite = true)
            legacyDir.deleteRecursively()
        }.isSuccess

        if (migrated) {
            Log.i(TAG, "MODEL_DIR_MIGRATED id=${model.id.rawValue} from=${legacyDir.absolutePath} to=${destinationDir.absolutePath}")
        }
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
        val existingJob = downloadJobs[id]
        if (existingJob?.isActive == true) {
            Log.i(TAG, "MODEL_DOWNLOAD_ALREADY_ACTIVE id=${id.rawValue}")
            return
        }

        if (!currentModel.isDownloadable || currentModel.sourceUrl.isNullOrBlank()) {
            updateModel(id) {
                it.copy(
                    state = ModelState.ERROR,
                    errorMessage = "No direct download URL is available for ${currentModel.name}."
                )
            }
            return
        }

        existingJob?.cancel()
        disconnectActiveConnection(id)
        suppressPauseOnCancellation.remove(id)

        val job = scope.launch(Dispatchers.IO) {
            val model = getModel(id) ?: return@launch
            val targetFile = getModelFile(model)
            val partialFile = getDownloadFile(model)
            val initialUrl = model.sourceUrl ?: throw IllegalStateException("Missing download URL for ${model.name}")

            if (restartFromScratch && partialFile.exists()) {
                partialFile.delete()
            }

            Log.i(
                TAG,
                "MODEL_DOWNLOAD_START id=${model.id.rawValue} name=${model.name} initialUrl=$initialUrl existingPartialBytes=${if (partialFile.exists()) partialFile.length() else 0L}"
            )

            try {
                val freeBytes = getStorageStats().freeBytes
                val existingPartial = if (partialFile.exists()) partialFile.length() else 0L
                val bytesNeeded = if (model.sizeBytes > 0L) {
                    (model.sizeBytes - existingPartial).coerceAtLeast(0L)
                } else {
                    0L
                }

                if (model.sizeBytes > 0L && freeBytes < bytesNeeded) {
                    throw IllegalStateException(
                        "Insufficient storage. Need ${formatBytes(bytesNeeded)} free, but only ${formatBytes(freeBytes)} available."
                    )
                }

                updateModel(id) {
                    it.copy(
                        state = ModelState.DOWNLOADING,
                        downloadedBytes = existingPartial,
                        downloadSpeed = STATUS_CONNECTING,
                        errorMessage = null,
                        isLoaded = false
                    )
                }

                var completed = false
                var lastError: Exception? = null

                for (attempt in 0 until MAX_DOWNLOAD_ATTEMPTS) {
                    coroutineContext.ensureActive()
                    val currentBytes = if (partialFile.exists()) partialFile.length() else 0L

                    updateModel(id) {
                        it.copy(
                            state = ModelState.DOWNLOADING,
                            downloadedBytes = currentBytes,
                            downloadSpeed = STATUS_CONNECTING,
                            errorMessage = null,
                            isLoaded = false
                        )
                    }

                    try {
                        performConnectionDiagnostic(model, currentBytes)
                        downloadOnce(model, partialFile)
                        completed = true
                        break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (downloadError: Exception) {
                        lastError = downloadError
                        val retryable = shouldRetryDownload(downloadError)
                        val isLastAttempt = attempt == MAX_DOWNLOAD_ATTEMPTS - 1

                        Log.w(
                            TAG,
                            "MODEL_DOWNLOAD_RETRY id=${model.id.rawValue} attempt=${attempt + 1}/$MAX_DOWNLOAD_ATTEMPTS retryable=$retryable error=${downloadError.message}"
                        )

                        if (!retryable || isLastAttempt) {
                            throw downloadError
                        }

                        val backoffMs = computeRetryDelayMs(attempt, downloadError)
                        val partialBytes = if (partialFile.exists()) partialFile.length() else currentBytes
                        updateModel(id) {
                            it.copy(
                                state = ModelState.DOWNLOADING,
                                downloadedBytes = partialBytes,
                                downloadSpeed = STATUS_STALLED_RETRYING,
                                errorMessage = null,
                                isLoaded = false
                            )
                        }
                        delay(backoffMs)
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
                        errorMessage = null,
                        isLoaded = false
                    )
                }
                Log.i(TAG, "MODEL_VERIFY_START id=${model.id.rawValue} file=${partialFile.absolutePath}")

                val validation = validateModelFile(
                    model = model,
                    file = partialFile,
                    verifyChecksum = false,
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
                    verifyChecksum = !model.sha256Expected.isNullOrBlank(),
                    verifyRuntimeLoad = false
                )
                if (!finalValidation.isValid) {
                    targetFile.delete()
                    throw IllegalStateException(finalValidation.errorMessage ?: "Final file validation failed")
                }

                if (model.packageType == ModelPackageType.TAR_BZ2_ARCHIVE) {
                    updateModel(id) {
                        it.copy(
                            state = ModelState.VERIFYING,
                            downloadSpeed = STATUS_EXTRACTING,
                            downloadedBytes = targetFile.length(),
                            errorMessage = null,
                            isLoaded = false
                        )
                    }

                    val installValidation = installArchiveModel(model, targetFile)
                    if (!installValidation.isValid) {
                        clearExtractedResources(model)
                        targetFile.delete()
                        throw IllegalStateException(installValidation.errorMessage ?: "Archive extraction failed")
                    }
                }

                val installedValidation = validateInstalledModelArtifacts(
                    model = model,
                    file = targetFile,
                    verifyChecksum = !model.sha256Expected.isNullOrBlank(),
                    verifyRuntimeLoad = false
                )
                if (!installedValidation.isValid) {
                    clearExtractedResources(model)
                    targetFile.delete()
                    throw IllegalStateException(installedValidation.errorMessage ?: "Final installation validation failed")
                }

                updateModel(id) {
                    it.copy(
                        state = ModelState.READY,
                        downloadedBytes = targetFile.length(),
                        downloadSpeed = null,
                        errorMessage = null,
                        isLoaded = false
                    )
                }

                Log.i(TAG, "MODEL_DOWNLOAD_COMPLETE id=${model.id.rawValue} bytes=${targetFile.length()}")
                Log.i(TAG, "MODEL_VERIFY_SUCCESS id=${model.id.rawValue} path=${targetFile.absolutePath}")
            } catch (cancelled: CancellationException) {
                disconnectActiveConnection(id)
                if (suppressPauseOnCancellation.remove(id)) {
                    Log.i(TAG, "MODEL_DOWNLOAD_CANCELLED id=${model.id.rawValue}")
                    return@launch
                }

                val bytes = when {
                    partialFile.exists() -> partialFile.length()
                    targetFile.exists() -> targetFile.length()
                    else -> model.downloadedBytes
                }
                updateModel(id) {
                    it.copy(
                        state = ModelState.PAUSED,
                        downloadedBytes = bytes,
                        downloadSpeed = null,
                        errorMessage = null,
                        isLoaded = false
                    )
                }
                Log.i(TAG, "MODEL_DOWNLOAD_PAUSED id=${model.id.rawValue} bytes=$bytes")
            } catch (e: Exception) {
                val bytes = when {
                    partialFile.exists() -> partialFile.length()
                    targetFile.exists() -> targetFile.length()
                    else -> 0L
                }
                val userMessage = toUserFacingDownloadError(model, e)
                updateModel(id) {
                    it.copy(
                        state = ModelState.ERROR,
                        downloadedBytes = bytes,
                        downloadSpeed = null,
                        errorMessage = userMessage,
                        isLoaded = false
                    )
                }
                Log.e(TAG, "MODEL_DOWNLOAD_FAILED id=${model.id.rawValue} error=${e.message}", e)
            } finally {
                disconnectActiveConnection(id)
                suppressPauseOnCancellation.remove(id)
                val runningJob = coroutineContext[Job]
                if (runningJob != null) {
                    downloadJobs.remove(id, runningJob)
                } else {
                    downloadJobs.remove(id)
                }
            }
        }

        downloadJobs[id] = job
    }

    fun pauseDownload(id: ModelId) {
        suppressPauseOnCancellation.remove(id)
        disconnectActiveConnection(id)
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
        if (downloadJobs[id]?.isActive == true) {
            suppressPauseOnCancellation.add(id)
        }
        disconnectActiveConnection(id)
        downloadJobs[id]?.cancel()
        downloadJobs.remove(id)

        val model = getModel(id) ?: return
        val partialFile = getDownloadFile(model)
        if (partialFile.exists()) {
            partialFile.delete()
        }

        val targetFile = getModelFile(model)
        val isInstalled = targetFile.exists() && validateInstalledModelArtifacts(
            model = model,
            file = targetFile,
            verifyChecksum = false,
            verifyRuntimeLoad = false
        ).isValid
        val nextState = if (isInstalled) ModelState.READY else ModelState.NOT_INSTALLED
        val downloadedBytes = if (isInstalled) targetFile.length() else 0L

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
        if (downloadJobs[id]?.isActive == true) {
            suppressPauseOnCancellation.add(id)
        }
        disconnectActiveConnection(id)
        downloadJobs[id]?.cancel()
        downloadJobs.remove(id)

        val model = getModel(id) ?: return
        val modelDir = getModelDirectory(model)
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
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
        return validateInstalledModelArtifacts(
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
        var existingBytes = if (outputFile.exists()) outputFile.length() else 0L

        if (model.sizeBytes > 0L && existingBytes > model.sizeBytes) {
            outputFile.delete()
            existingBytes = 0L
        }

        val response = openConnectionWithRedirects(
            model = model,
            requestMethod = "GET",
            rangeStart = if (existingBytes > 0L) existingBytes else null,
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS
        )
        val connection = response.connection

        try {
            val responseCode = connection.responseCode
            val contentType = connection.contentType?.lowercase(Locale.US).orEmpty()
            val contentLength = connection.contentLengthLong
            val contentRange = connection.getHeaderField("Content-Range")

            Log.i(
                TAG,
                "MODEL_DOWNLOAD_HTTP id=${model.id.rawValue} code=$responseCode initialUrl=${response.initialUrl} finalUrl=${response.finalUrl} contentType=${connection.contentType.orEmpty()} contentLength=$contentLength contentRange=${contentRange.orEmpty()} existingPartialBytes=$existingBytes"
            )

            if (responseCode == HTTP_RANGE_NOT_SATISFIABLE && existingBytes > 0L) {
                if (model.sizeBytes > 0L && existingBytes >= model.sizeBytes) {
                    Log.i(TAG, "MODEL_DOWNLOAD_RANGE_COMPLETE id=${model.id.rawValue} bytes=$existingBytes")
                    return existingBytes
                }

                outputFile.delete()
                throw RetryableDownloadException(
                    "Server rejected resume range (HTTP 416). Restarting from byte 0."
                )
            }

            if (responseCode !in HTTP_SUCCESS_CODES) {
                throw buildHttpStatusException(
                    model = model,
                    connection = connection,
                    responseCode = responseCode,
                    finalUrl = response.finalUrl,
                    context = "Download request failed"
                )
            }

            if (contentType.contains("text/html") || contentType.contains("application/json")) {
                throw NonRetryableDownloadException(
                    "Unexpected content-type '$contentType' for model download."
                )
            }

            val rangeRequested = existingBytes > 0L
            var appendMode = false

            if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                val parsedRange = parseContentRange(contentRange)
                    ?: throw NonRetryableDownloadException(
                        "Invalid Content-Range while downloading ${model.name}: '${contentRange.orEmpty()}'."
                    )

                if (rangeRequested && parsedRange.start != existingBytes) {
                    outputFile.delete()
                    throw RetryableDownloadException(
                        "Resume mismatch for ${model.name}. Existing=$existingBytes, serverStart=${parsedRange.start}. Restarting from byte 0."
                    )
                }

                if (!rangeRequested && parsedRange.start != 0L) {
                    throw NonRetryableDownloadException(
                        "Unexpected partial range start ${parsedRange.start} for fresh download."
                    )
                }

                appendMode = rangeRequested && parsedRange.start == existingBytes
            } else if (rangeRequested && responseCode == HttpURLConnection.HTTP_OK) {
                Log.w(
                    TAG,
                    "MODEL_DOWNLOAD_RANGE_RESTART id=${model.id.rawValue} existingBytes=$existingBytes reason=range_ignored_http_200"
                )
                if (outputFile.exists()) {
                    outputFile.delete()
                }
                existingBytes = 0L
            }

            var downloadedBytes = if (appendMode) existingBytes else 0L
            if (!appendMode && outputFile.exists()) {
                outputFile.delete()
            }

            updateModel(model.id) {
                it.copy(
                    state = ModelState.DOWNLOADING,
                    downloadedBytes = downloadedBytes,
                    downloadSpeed = STATUS_CONNECTING,
                    errorMessage = null,
                    isLoaded = false
                )
            }

            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L
            var lastFlushTime = lastUpdateTime
            var bytesSinceFlush = 0L

            connection.inputStream.use { input ->
                FileOutputStream(outputFile, appendMode).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        bytesSinceLastUpdate += read
                        bytesSinceFlush += read

                        val now = System.currentTimeMillis()
                        if (bytesSinceFlush >= FLUSH_EVERY_BYTES || now - lastFlushTime >= FLUSH_INTERVAL_MS) {
                            output.flush()
                            bytesSinceFlush = 0L
                            lastFlushTime = now
                        }

                        val intervalMs = now - lastUpdateTime
                        if (intervalMs >= SPEED_UPDATE_INTERVAL_MS) {
                            val speedBytesPerSec = bytesSinceLastUpdate * 1000.0 / max(1.0, intervalMs.toDouble())
                            val speedLabel = if (speedBytesPerSec > 0.0) {
                                String.format(Locale.US, "%.1f MB/s", speedBytesPerSec / (1024.0 * 1024.0))
                            } else {
                                STATUS_CONNECTING
                            }

                            updateModel(model.id) {
                                it.copy(
                                    state = ModelState.DOWNLOADING,
                                    downloadedBytes = downloadedBytes,
                                    downloadSpeed = speedLabel,
                                    errorMessage = null,
                                    isLoaded = false
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
            if (fileBytes <= 0L) {
                throw IOException("Download produced an empty file for ${model.name}.")
            }

            updateModel(model.id) {
                it.copy(
                    state = ModelState.DOWNLOADING,
                    downloadedBytes = fileBytes,
                    downloadSpeed = null,
                    errorMessage = null,
                    isLoaded = false
                )
            }

            return fileBytes
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (io: IOException) {
            coroutineContext.ensureActive()
            throw io
        } finally {
            clearActiveConnection(model.id, connection)
            connection.disconnect()
        }
    }

    private suspend fun performConnectionDiagnostic(model: ModelInfo, existingPartialBytes: Long) {
        var response: RedirectedConnection? = null

        try {
            response = openConnectionWithRedirects(
                model = model,
                requestMethod = "HEAD",
                rangeStart = null,
                connectTimeoutMs = CONNECT_TIMEOUT_MS,
                readTimeoutMs = DIAGNOSTIC_READ_TIMEOUT_MS
            )

            var responseCode = response.responseCode
            if (responseCode !in HTTP_SUCCESS_CODES) {
                clearActiveConnection(model.id, response.connection)
                response.connection.disconnect()

                response = openConnectionWithRedirects(
                    model = model,
                    requestMethod = "GET",
                    rangeStart = 0L,
                    connectTimeoutMs = CONNECT_TIMEOUT_MS,
                    readTimeoutMs = DIAGNOSTIC_READ_TIMEOUT_MS
                )
                responseCode = response.responseCode
            }

            val connection = response.connection
            val contentLength = connection.contentLengthLong
            val contentType = connection.contentType.orEmpty()
            val contentRange = connection.getHeaderField("Content-Range").orEmpty()

            Log.i(
                TAG,
                "MODEL_DOWNLOAD_HTTP id=${model.id.rawValue} code=$responseCode initialUrl=${response.initialUrl} finalUrl=${response.finalUrl} contentType=$contentType contentLength=$contentLength contentRange=$contentRange existingPartialBytes=$existingPartialBytes"
            )

            if (responseCode !in HTTP_SUCCESS_CODES) {
                throw buildHttpStatusException(
                    model = model,
                    connection = connection,
                    responseCode = responseCode,
                    finalUrl = response.finalUrl,
                    context = "Connection diagnostic failed"
                )
            }
        } finally {
            response?.let {
                clearActiveConnection(model.id, it.connection)
                it.connection.disconnect()
            }
        }
    }

    private suspend fun openConnectionWithRedirects(
        model: ModelInfo,
        requestMethod: String,
        rangeStart: Long?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): RedirectedConnection {
        val initialUrl = model.sourceUrl ?: throw IllegalStateException("Missing download URL for ${model.name}")
        var currentUrl = initialUrl
        var currentMethod = requestMethod
        var redirectCount = 0

        while (true) {
            coroutineContext.ensureActive()

            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                this.requestMethod = currentMethod
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("Accept-Encoding", "identity")
                if (rangeStart != null) {
                    setRequestProperty("Range", "bytes=$rangeStart-")
                }
            }

            registerActiveConnection(model.id, connection)

            val responseCode = try {
                connection.responseCode
            } catch (e: IOException) {
                clearActiveConnection(model.id, connection)
                connection.disconnect()
                throw e
            }

            Log.i(
                TAG,
                "MODEL_DOWNLOAD_HTTP id=${model.id.rawValue} code=$responseCode initialUrl=$initialUrl requestUrl=$currentUrl"
            )

            if (responseCode in HTTP_REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                if (location.isNullOrBlank()) {
                    clearActiveConnection(model.id, connection)
                    connection.disconnect()
                    throw NonRetryableDownloadException(
                        "Redirect failed for ${model.name}: missing Location header (HTTP $responseCode)."
                    )
                }

                if (redirectCount >= MAX_REDIRECTS) {
                    clearActiveConnection(model.id, connection)
                    connection.disconnect()
                    throw NonRetryableDownloadException(
                        "Redirect failed for ${model.name}: exceeded $MAX_REDIRECTS redirects."
                    )
                }

                val redirectedUrl = URL(URL(currentUrl), location).toString()
                Log.i(TAG, "MODEL_DOWNLOAD_REDIRECT id=${model.id.rawValue} finalUrl=$redirectedUrl")

                clearActiveConnection(model.id, connection)
                connection.disconnect()

                currentUrl = redirectedUrl
                if (responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    currentMethod = "GET"
                }
                redirectCount += 1
                continue
            }

            if (currentUrl != initialUrl) {
                Log.i(TAG, "MODEL_DOWNLOAD_REDIRECT id=${model.id.rawValue} finalUrl=$currentUrl")
            }

            return RedirectedConnection(
                connection = connection,
                initialUrl = initialUrl,
                finalUrl = currentUrl,
                responseCode = responseCode
            )
        }
    }

    private fun buildHttpStatusException(
        model: ModelInfo,
        connection: HttpURLConnection,
        responseCode: Int,
        finalUrl: String,
        context: String
    ): HttpStatusException {
        val snippet = readErrorSnippet(connection)
        val retryAfterMs = parseRetryAfterMs(connection.getHeaderField("Retry-After"))
        val message = buildString {
            append(context)
            append(". HTTP ")
            append(responseCode)
            append(" for ")
            append(model.name)
            append(" at ")
            append(finalUrl)
            if (snippet.isNotBlank()) {
                append(". ")
                append(snippet)
            }
        }
        return HttpStatusException(responseCode = responseCode, retryAfterMs = retryAfterMs, message = message)
    }

    private fun readErrorSnippet(connection: HttpURLConnection): String {
        return runCatching {
            connection.errorStream?.bufferedReader()?.use { it.readText() }
                ?.replace("\n", " ")
                ?.replace("\r", " ")
                ?.trim()
                ?.take(240)
                .orEmpty()
        }.getOrDefault("")
    }

    private fun parseRetryAfterMs(headerValue: String?): Long? {
        if (headerValue.isNullOrBlank()) return null
        val seconds = headerValue.trim().toLongOrNull() ?: return null
        return (seconds * 1000L).coerceAtLeast(1_000L)
    }

    private fun parseContentRange(headerValue: String?): ParsedContentRange? {
        if (headerValue.isNullOrBlank()) return null
        val match = CONTENT_RANGE_REGEX.matchEntire(headerValue.trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].takeIf { it != "*" }?.toLongOrNull()

        if (end < start) return null

        return ParsedContentRange(start = start, end = end, total = total)
    }

    private fun shouldRetryDownload(error: Exception): Boolean {
        return when (error) {
            is CancellationException -> false
            is NonRetryableDownloadException -> false
            is HttpStatusException -> error.responseCode in RETRYABLE_HTTP_CODES
            is RetryableDownloadException -> true
            is SocketTimeoutException -> true
            is IOException -> true
            else -> false
        }
    }

    private fun computeRetryDelayMs(attemptIndex: Int, error: Exception): Long {
        val exponentialBackoff = (BASE_RETRY_BACKOFF_MS * (1L shl attemptIndex.coerceAtMost(6)))
            .coerceAtMost(MAX_RETRY_BACKOFF_MS)
        val retryAfterMs = (error as? HttpStatusException)?.retryAfterMs ?: 0L
        return max(exponentialBackoff, retryAfterMs)
    }

    private fun toUserFacingDownloadError(model: ModelInfo, throwable: Throwable): String {
        val root = rootCause(throwable)
        val sourceHost = runCatching { model.sourceUrl?.let { URL(it).host } }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "huggingface.co"

        return when (root) {
            is HttpStatusException -> when (root.responseCode) {
                HttpURLConnection.HTTP_UNAUTHORIZED -> "Access denied by remote host (HTTP 401). ${model.name} may require authentication before download."
                HttpURLConnection.HTTP_FORBIDDEN -> "Access denied by remote host (HTTP 403). Check repository/file permissions for ${model.name}."
                HttpURLConnection.HTTP_NOT_FOUND -> "Model file not found (HTTP 404) for ${model.name}. Verify repository and filename."
                429 -> "Remote host is rate limiting requests (HTTP 429). Please retry in a moment."
                500, 502, 503, 504 -> "Remote server is temporarily unavailable (HTTP ${root.responseCode}). Please retry."
                else -> root.localizedMessage ?: "HTTP ${root.responseCode} while downloading ${model.name}."
            }

            is UnknownHostException -> "DNS lookup failed for $sourceHost. Check your network connection."
            is SocketTimeoutException -> "Connection timed out while downloading ${model.name}."
            is SSLHandshakeException,
            is SSLPeerUnverifiedException,
            is SSLException -> "TLS handshake failed while connecting to $sourceHost."

            is ConnectException,
            is NoRouteToHostException -> "Unable to connect to $sourceHost. Check network access."

            is NonRetryableDownloadException -> root.localizedMessage ?: "Download failed for ${model.name}."
            else -> root.localizedMessage ?: throwable.localizedMessage ?: "Download failed for ${model.name}."
        }
    }

    private fun rootCause(throwable: Throwable): Throwable {
        var current = throwable
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    private fun registerActiveConnection(modelId: ModelId, connection: HttpURLConnection) {
        activeConnections.put(modelId, connection)?.let { previous ->
            if (previous !== connection) {
                runCatching { previous.disconnect() }
            }
        }
    }

    private fun clearActiveConnection(modelId: ModelId, connection: HttpURLConnection) {
        activeConnections.remove(modelId, connection)
    }

    private fun disconnectActiveConnection(modelId: ModelId) {
        activeConnections.remove(modelId)?.let { connection ->
            runCatching { connection.disconnect() }
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

        val actualSize = file.length()

        if (actualSize < MIN_VALID_MODEL_BYTES) {
            return ValidationResult(false, "Model file is too small ($actualSize bytes).")
        }

        if (looksLikeHtml(file)) {
            return ValidationResult(false, "Downloaded file appears to be HTML/error content, not a model file.")
        }

        val headerOk = when (model.packageType) {
            ModelPackageType.SINGLE_FILE -> {
                if (model.isWhisper) {
                    hasValidWhisperHeader(file)
                } else {
                    hasValidGgufHeader(file)
                }
            }

            ModelPackageType.TAR_BZ2_ARCHIVE -> hasValidTarBz2Header(file)
        }

        if (!headerOk) {
            return ValidationResult(
                false,
                when (model.packageType) {
                    ModelPackageType.SINGLE_FILE -> "Invalid model header/magic for ${model.filename}."
                    ModelPackageType.TAR_BZ2_ARCHIVE -> "Invalid archive header/magic for ${model.filename}."
                }
            )
        }

        var checksumVerified = false
        if (verifyChecksum && !model.sha256Expected.isNullOrBlank()) {
            val actual = calculateSha256(file)
            if (!actual.equals(model.sha256Expected, ignoreCase = true)) {
                return ValidationResult(
                    false,
                    "SHA256 mismatch. Expected ${model.sha256Expected}, got $actual."
                )
            }
            checksumVerified = true
        }

        if (!hasExpectedSize(actualSize, model.sizeBytes)) {
            val allowTinyMetadataMismatch = checksumVerified && hasAcceptableMetadataSizeDelta(
                actual = actualSize,
                expected = model.sizeBytes
            )

            if (!allowTinyMetadataMismatch) {
                return ValidationResult(
                    false,
                    "File size mismatch. Expected ${model.sizeBytes} bytes, got $actualSize bytes."
                )
            }

            Log.w(
                TAG,
                "MODEL_VERIFY_SIZE_TOLERANCE id=${model.id.rawValue} expected=${model.sizeBytes} actual=$actualSize"
            )
        }

        if (verifyRuntimeLoad && model.packageType == ModelPackageType.SINGLE_FILE) {
            val runtimeValidation = when {
                model.isWhisper -> verifyWhisperRuntime(file)
                model.isChatModel -> verifyLlamaRuntime(model = model, file = file)
                else -> ValidationResult(true)
            }

            if (!runtimeValidation.isValid) {
                return runtimeValidation
            }
        }

        return ValidationResult(true)
    }

    private fun validateInstalledModelArtifacts(
        model: ModelInfo,
        file: File,
        verifyChecksum: Boolean,
        verifyRuntimeLoad: Boolean
    ): ValidationResult {
        val fileValidation = validateModelFile(
            model = model,
            file = file,
            verifyChecksum = verifyChecksum,
            verifyRuntimeLoad = verifyRuntimeLoad
        )
        if (!fileValidation.isValid) {
            return fileValidation
        }

        return when (model.packageType) {
            ModelPackageType.SINGLE_FILE -> ValidationResult(true)
            ModelPackageType.TAR_BZ2_ARCHIVE -> validateRequiredResourcesPresent(model)
        }
    }

    private fun installArchiveModel(model: ModelInfo, archiveFile: File): ValidationResult {
        if (model.packageType != ModelPackageType.TAR_BZ2_ARCHIVE) {
            return ValidationResult(true)
        }

        val archiveValidation = validateModelFile(
            model = model,
            file = archiveFile,
            verifyChecksum = !model.sha256Expected.isNullOrBlank(),
            verifyRuntimeLoad = false
        )
        if (!archiveValidation.isValid) {
            return archiveValidation
        }

        clearExtractedResources(model, preserveArchive = true)

        val modelDir = getModelDirectory(model)
        val extractionValidation = extractTarBz2Archive(
            archiveFile = archiveFile,
            destinationDir = modelDir
        )
        if (!extractionValidation.isValid) {
            return extractionValidation
        }

        return validateRequiredResourcesPresent(model)
    }

    private fun clearExtractedResources(model: ModelInfo, preserveArchive: Boolean = false) {
        val modelDir = getModelDirectory(model)
        if (!modelDir.exists()) return

        val keepNames = if (preserveArchive) {
            setOf(model.filename, "${model.filename}.download")
        } else {
            emptySet()
        }

        modelDir.listFiles().orEmpty().forEach { child ->
            if (child.name !in keepNames) {
                runCatching { child.deleteRecursively() }
            }
        }
    }

    private fun extractTarBz2Archive(archiveFile: File, destinationDir: File): ValidationResult {
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            return ValidationResult(false, "Unable to create model directory: ${destinationDir.absolutePath}")
        }

        val destinationCanonical = destinationDir.canonicalFile
        val destinationPrefix = destinationCanonical.path + File.separator

        return try {
            FileInputStream(archiveFile).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    BZip2CompressorInputStream(bis, true).use { bzipInput ->
                        TarArchiveInputStream(bzipInput).use { tarInput ->
                            var entry = tarInput.nextTarEntry
                            while (entry != null) {
                                val output = File(destinationDir, entry.name).canonicalFile
                                val outputPath = output.path
                                if (outputPath != destinationCanonical.path && !outputPath.startsWith(destinationPrefix)) {
                                    return ValidationResult(false, "Archive contains invalid path outside destination: ${entry.name}")
                                }

                                when {
                                    entry.isLink || entry.isSymbolicLink -> {
                                        // Skip links for safety.
                                    }

                                    entry.isDirectory -> {
                                        if (!output.exists()) {
                                            output.mkdirs()
                                        }
                                    }

                                    else -> {
                                        output.parentFile?.mkdirs()
                                        BufferedOutputStream(FileOutputStream(output)).use { out ->
                                            tarInput.copyTo(out)
                                            out.flush()
                                        }
                                    }
                                }

                                entry = tarInput.nextTarEntry
                            }
                        }
                    }
                }
            }

            ValidationResult(true)
        } catch (e: Exception) {
            ValidationResult(false, "Failed to extract archive ${archiveFile.name}: ${e.localizedMessage}")
        }
    }

    private fun validateRequiredResourcesPresent(model: ModelInfo): ValidationResult {
        if (model.requiredFiles.isEmpty()) {
            return ValidationResult(true)
        }

        val modelDir = getModelDirectory(model)
        if (!modelDir.exists()) {
            return ValidationResult(false, "Model directory not found: ${modelDir.absolutePath}")
        }

        for (required in model.requiredFiles) {
            val entry = findRequiredEntry(modelDir, required)
            if (entry == null) {
                return ValidationResult(false, "Missing required extracted file for ${model.name}: $required")
            }
        }

        return ValidationResult(true)
    }

    private fun findRequiredEntry(modelDir: File, requiredName: String): File? {
        val expectsDirectory = requiredName.trim().endsWith("/")
        val normalized = requiredName.trim().trimEnd('/')
        if (normalized.isBlank()) {
            return null
        }

        val direct = File(modelDir, normalized)
        if (expectsDirectory && direct.isDirectory) {
            return direct
        }

        if (!expectsDirectory && direct.isFile) {
            return direct
        }

        val leafName = normalized.substringAfterLast('/')
        return modelDir.walkTopDown().firstOrNull { file ->
            if (expectsDirectory) {
                file.isDirectory && file.name == leafName
            } else {
                file.isFile && file.name == leafName
            }
        }
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
                header == "lmgg" || header == "fmgg" || header == "tjgg"
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun hasValidTarBz2Header(file: File): Boolean {
        return try {
            FileInputStream(file).use { input ->
                val magic = ByteArray(3)
                val read = input.read(magic)
                read == 3 &&
                    magic[0] == 'B'.code.toByte() &&
                    magic[1] == 'Z'.code.toByte() &&
                    magic[2] == 'h'.code.toByte()
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

    private fun hasAcceptableMetadataSizeDelta(actual: Long, expected: Long): Boolean {
        if (expected <= 0L) return false
        return abs(actual - expected) <= SIZE_TOLERANCE_BYTES
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

    private data class RedirectedConnection(
        val connection: HttpURLConnection,
        val initialUrl: String,
        val finalUrl: String,
        val responseCode: Int
    )

    private data class ParsedContentRange(
        val start: Long,
        val end: Long,
        val total: Long?
    )

    private class HttpStatusException(
        val responseCode: Int,
        val retryAfterMs: Long?,
        message: String
    ) : IOException(message)

    private class RetryableDownloadException(message: String) : IOException(message)

    private class NonRetryableDownloadException(message: String) : IOException(message)

    companion object {
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 180_000
        private const val DIAGNOSTIC_READ_TIMEOUT_MS = 20_000
        private const val USER_AGENT = "MyAI-Android-Offline/2.0"
        private const val MIN_VALID_MODEL_BYTES = 1L
        private const val DOWNLOAD_BUFFER_BYTES = 128 * 1024
        private const val SPEED_UPDATE_INTERVAL_MS = 350L
        private const val FLUSH_INTERVAL_MS = 1_000L
        private const val FLUSH_EVERY_BYTES = 2L * 1024L * 1024L
        private const val MAX_DOWNLOAD_ATTEMPTS = 6
        private const val BASE_RETRY_BACKOFF_MS = 1_000L
        private const val MAX_RETRY_BACKOFF_MS = 20_000L
        private const val MAX_REDIRECTS = 8
        private const val SIZE_TOLERANCE_BYTES = 4L * 1024L
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val STATUS_CONNECTING = "Connecting..."
        private const val STATUS_EXTRACTING = "Extracting archive..."
        private const val STATUS_STALLED_RETRYING = "Network stalled — retrying..."
        private const val MODELS_DIR_LLM = "llm"
        private const val MODELS_DIR_STT = "stt"
        private const val MODELS_DIR_TTS = "tts"

        private val HTTP_SUCCESS_CODES = setOf(
            HttpURLConnection.HTTP_OK,
            HttpURLConnection.HTTP_PARTIAL
        )

        private val HTTP_REDIRECT_CODES = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER,
            307,
            308
        )

        private val RETRYABLE_HTTP_CODES = setOf(429, 500, 502, 503, 504)
        private val CONTENT_RANGE_REGEX = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""")
    }
}
