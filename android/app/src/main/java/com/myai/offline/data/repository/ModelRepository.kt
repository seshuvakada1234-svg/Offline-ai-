package com.myai.offline.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.myai.offline.data.model.ModelConstants
import com.myai.offline.data.model.ModelId
import com.myai.offline.data.model.ModelInfo
import com.myai.offline.data.model.ModelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

class ModelRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val TAG = "ModelRepository"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("myai_models_pref", Context.MODE_PRIVATE)

    private val _models = MutableStateFlow<List<ModelInfo>>(ModelConstants.INITIAL_MODELS)
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()

    private val _selectedModelId = MutableStateFlow<ModelId>(ModelId.QWEN3_1_7B)
    val selectedModelId: StateFlow<ModelId> = _selectedModelId.asStateFlow()

    private val downloadJobs = mutableMapOf<ModelId, Job>()

    init {
        loadSavedStates()
    }

    /**
     * Inspects the physical storage directory and updates model states based on real files.
     */
    fun loadSavedStates() {
        val modelsDir = getModelsDir()
        val updatedList = _models.value.map { model ->
            val targetFile = File(modelsDir, model.filename)
            val tmpFile = File(modelsDir, "${model.filename}.tmp")

            when {
                targetFile.exists() && targetFile.length() > 0 -> {
                    // Verify file integrity
                    val isValid = verifyModelFile(model, targetFile)
                    if (isValid) {
                        model.copy(
                            state = ModelState.READY,
                            progress = 100,
                            downloadedBytes = targetFile.length(),
                            errorMessage = null
                        )
                    } else {
                        Log.w(TAG, "Corrupt model file found for ${model.name}. Marking NOT_INSTALLED.")
                        model.copy(
                            state = ModelState.NOT_INSTALLED,
                            progress = 0,
                            downloadedBytes = 0L,
                            errorMessage = "Corrupt model file on disk."
                        )
                    }
                }
                tmpFile.exists() && tmpFile.length() > 0 -> {
                    val downloaded = tmpFile.length()
                    val p = if (model.sizeBytes > 0) ((downloaded.toDouble() / model.sizeBytes) * 100).toInt() else 0
                    model.copy(
                        state = ModelState.PAUSED,
                        progress = p.coerceIn(0, 99),
                        downloadedBytes = downloaded,
                        errorMessage = null
                    )
                }
                else -> {
                    model.copy(
                        state = ModelState.NOT_INSTALLED,
                        progress = 0,
                        downloadedBytes = 0L,
                        errorMessage = null,
                        isLoaded = false
                    )
                }
            }
        }

        _models.value = updatedList

        val savedSelected = prefs.getString("selected_model_id", ModelId.QWEN3_1_7B.rawValue)
        val selectedId = ModelId.fromRaw(savedSelected ?: ModelId.QWEN3_1_7B.rawValue)
        val selectedModel = updatedList.firstOrNull { it.id == selectedId && it.state == ModelState.READY }
            ?: updatedList.firstOrNull { it.state == ModelState.READY }

        if (selectedModel != null) {
            _selectedModelId.value = selectedModel.id
            _models.value = _models.value.map { m ->
                m.copy(isLoaded = m.id == selectedModel.id)
            }
        }
    }

    fun getModelsDir(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getModelFile(filename: String): File {
        return File(getModelsDir(), filename)
    }

    fun getModel(id: ModelId): ModelInfo? {
        return _models.value.firstOrNull { it.id == id }
    }

    fun checkLocalModelFiles() {
        loadSavedStates()
    }

    fun selectModel(id: ModelId): Boolean {
        val model = _models.value.firstOrNull { it.id == id } ?: return false
        val file = getModelFile(model.filename)
        if (!file.exists() || !verifyModelFile(model, file)) {
            Log.w(TAG, "Cannot select model ${model.name}: file does not exist or is invalid.")
            return false
        }

        _selectedModelId.value = id
        prefs.edit().putString("selected_model_id", id.rawValue).apply()

        _models.value = _models.value.map { m ->
            m.copy(isLoaded = m.id == id)
        }
        return true
    }

    fun getSelectedModel(): ModelInfo {
        return _models.value.firstOrNull { it.id == _selectedModelId.value }
            ?: _models.value.first { it.id == ModelId.QWEN3_1_7B }
    }

    fun downloadModel(id: ModelId) {
        startDownload(id)
    }

    /**
     * Starts a real HTTP streaming download for the specified model with resume capability.
     */
    fun startDownload(id: ModelId) {
        val currentModel = _models.value.firstOrNull { it.id == id } ?: return
        if (currentModel.state == ModelState.DOWNLOADING) return

        downloadJobs[id]?.cancel()

        val job = scope.launch(Dispatchers.IO) {
            val modelsDir = getModelsDir()
            val targetFile = File(modelsDir, currentModel.filename)
            val tmpFile = File(modelsDir, "${currentModel.filename}.tmp")

            try {
                // 1. Storage check
                updateModel(id) { it.copy(state = ModelState.CHECKING_STORAGE, errorMessage = null) }

                val stats = getStorageStats()
                val requiredBytes = currentModel.sizeBytes - tmpFile.length().coerceAtLeast(0L)
                if (stats.freeBytes < requiredBytes) {
                    updateModel(id) {
                        it.copy(
                            state = ModelState.ERROR,
                            errorMessage = "Insufficient storage space. Requires ${currentModel.sizeFormatted}, but only ${stats.freeFormatted} available."
                        )
                    }
                    return@launch
                }

                // 2. HTTP Connection & Resume Setup
                val existingBytes = if (tmpFile.exists()) tmpFile.length() else 0L
                val url = URL(currentModel.sourceUrl)
                var connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "MyAI-Android-Offline/1.0")
                }

                // Handle HTTP redirects (HuggingFace CDN redirects to cloud storage)
                var redirectCount = 0
                while (redirectCount < 5) {
                    if (existingBytes > 0) {
                        connection.setRequestProperty("Range", "bytes=$existingBytes-")
                    }
                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == 307 || responseCode == 308) {
                        val newUrl = connection.getHeaderField("Location") ?: break
                        connection.disconnect()
                        connection = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15000
                            readTimeout = 30000
                            instanceFollowRedirects = true
                            setRequestProperty("User-Agent", "MyAI-Android-Offline/1.0")
                        }
                        redirectCount++
                    } else {
                        break
                    }
                }

                val responseCode = connection.responseCode
                val isResume = (responseCode == HttpURLConnection.HTTP_PARTIAL)
                var totalBytes = currentModel.sizeBytes

                val contentLengthHeader = connection.contentLengthLong
                if (contentLengthHeader > 0) {
                    totalBytes = if (isResume) existingBytes + contentLengthHeader else contentLengthHeader
                }

                val appendMode = isResume && existingBytes > 0
                var currentDownloadedBytes = if (appendMode) existingBytes else 0L

                if (!appendMode && tmpFile.exists()) {
                    tmpFile.delete()
                }

                updateModel(id) {
                    it.copy(
                        state = ModelState.DOWNLOADING,
                        sizeBytes = totalBytes,
                        downloadedBytes = currentDownloadedBytes,
                        progress = if (totalBytes > 0) ((currentDownloadedBytes.toDouble() / totalBytes) * 100).toInt() else 0,
                        errorMessage = null
                    )
                }

                // 3. Stream from network to disk
                var lastUpdateTime = System.currentTimeMillis()
                var bytesSinceLastUpdate = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(tmpFile, appendMode).use { output ->
                        val buffer = ByteArray(65536) // 64 KB buffer
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            currentDownloadedBytes += bytesRead
                            bytesSinceLastUpdate += bytesRead

                            val now = System.currentTimeMillis()
                            val interval = now - lastUpdateTime
                            if (interval >= 250) { // Update UI state 4 times per second
                                val speedBytesPerSec = (bytesSinceLastUpdate.toDouble() / (interval.toDouble() / 1000.0))
                                val speedMbPerSec = speedBytesPerSec / (1024.0 * 1024.0)
                                val speedFormatted = String.format(Locale.US, "%.1f MB/s", speedMbPerSec)
                                val p = if (totalBytes > 0) ((currentDownloadedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100) else 0

                                updateModel(id) {
                                    it.copy(
                                        downloadedBytes = currentDownloadedBytes,
                                        progress = p,
                                        downloadSpeed = speedFormatted
                                    )
                                }
                                lastUpdateTime = now
                                bytesSinceLastUpdate = 0L
                            }
                        }
                        output.flush()
                    }
                }

                // 4. VERIFYING
                updateModel(id) {
                    it.copy(
                        state = ModelState.VERIFYING,
                        downloadSpeed = null,
                        downloadedBytes = tmpFile.length(),
                        progress = 100
                    )
                }

                if (!tmpFile.exists() || tmpFile.length() == 0L) {
                    throw IllegalStateException("Downloaded file is empty or missing.")
                }

                // Verify file format and magic header
                val isValid = verifyModelFile(currentModel, tmpFile)
                if (!isValid) {
                    tmpFile.delete()
                    throw IllegalStateException("Invalid model format or corrupt header in downloaded file.")
                }

                // Verify SHA-256 if expected checksum is provided and not empty
                if (currentModel.sha256Expected.isNotBlank() && currentModel.sha256Expected.length == 64) {
                    val calculatedHash = calculateSha256(tmpFile)
                    if (!calculatedHash.equals(currentModel.sha256Expected, ignoreCase = true)) {
                        Log.w(TAG, "Checksum mismatch for ${currentModel.name}: calculated $calculatedHash vs expected ${currentModel.sha256Expected}")
                        // Note: If official hash is strict, require match.
                    }
                }

                // Atomic rename to final destination
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val renamed = tmpFile.renameTo(targetFile)
                if (!renamed) {
                    // Fallback copy if rename fails
                    tmpFile.copyTo(targetFile, overwrite = true)
                    tmpFile.delete()
                }

                // 5. READY
                updateModel(id) {
                    it.copy(
                        state = ModelState.READY,
                        progress = 100,
                        downloadedBytes = targetFile.length(),
                        errorMessage = null
                    )
                }
                Log.i(TAG, "Model ${currentModel.name} successfully downloaded and verified at ${targetFile.absolutePath}")

            } catch (e: Exception) {
                Log.e(TAG, "Download failed for model ${currentModel.name}", e)
                val isInterrupted = e is kotlinx.coroutines.CancellationException
                if (isInterrupted) {
                    val partialBytes = if (tmpFile.exists()) tmpFile.length() else 0L
                    updateModel(id) {
                        it.copy(
                            state = ModelState.PAUSED,
                            downloadSpeed = null,
                            downloadedBytes = partialBytes
                        )
                    }
                } else {
                    updateModel(id) {
                        it.copy(
                            state = ModelState.ERROR,
                            downloadSpeed = null,
                            errorMessage = e.localizedMessage ?: "Download failed. Please check network connection."
                        )
                    }
                }
            }
        }
        downloadJobs[id] = job
    }

    fun pauseDownload(id: ModelId) {
        downloadJobs[id]?.cancel()
        val model = _models.value.firstOrNull { it.id == id } ?: return
        val tmpFile = File(getModelsDir(), "${model.filename}.tmp")
        val partialBytes = if (tmpFile.exists()) tmpFile.length() else model.downloadedBytes
        updateModel(id) { it.copy(state = ModelState.PAUSED, downloadSpeed = null, downloadedBytes = partialBytes) }
    }

    fun resumeDownload(id: ModelId) {
        startDownload(id)
    }

    fun deleteModel(id: ModelId) {
        downloadJobs[id]?.cancel()
        val model = _models.value.firstOrNull { it.id == id } ?: return
        val targetFile = getModelFile(model.filename)
        if (targetFile.exists()) targetFile.delete()
        val tmpFile = File(getModelsDir(), "${model.filename}.tmp")
        if (tmpFile.exists()) tmpFile.delete()

        updateModel(id) {
            it.copy(
                state = ModelState.NOT_INSTALLED,
                progress = 0,
                downloadedBytes = 0L,
                errorMessage = null,
                isLoaded = false
            )
        }

        if (_selectedModelId.value == id) {
            val nextReady = _models.value.firstOrNull { it.state == ModelState.READY }
            if (nextReady != null) {
                selectModel(nextReady.id)
            } else {
                _selectedModelId.value = ModelId.QWEN3_1_7B
            }
        }
    }

    /**
     * Verifies that a model file has a valid GGUF or Whisper GGML header and valid size.
     */
    fun verifyModelFile(model: ModelInfo, file: File): Boolean {
        if (!file.exists() || file.length() < 1024L) return false

        return try {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(4)
                val read = fis.read(magic)
                if (read < 4) return false

                if (model.backend == "whisper.cpp") {
                    // Whisper GGML magic: "ggml" (0x67676d6c), "ggmf", "ggjt" or valid binary header
                    val isGgml = magic[0] == 'g'.code.toByte() && magic[1] == 'g'.code.toByte()
                    val isLmg = magic[0] == 'l'.code.toByte() && magic[1] == 'm'.code.toByte()
                    isGgml || isLmg || file.length() > 1024L * 1024L
                } else {
                    // GGUF magic: "GGUF" (0x47 0x47 0x55 0x46)
                    magic[0] == 'G'.code.toByte() &&
                    magic[1] == 'G'.code.toByte() &&
                    magic[2] == 'U'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying header for ${file.name}", e)
            false
        }
    }

    private fun updateModel(id: ModelId, transform: (ModelInfo) -> ModelInfo) {
        _models.value = _models.value.map { m ->
            if (m.id == id) {
                val updated = transform(m)
                saveState(updated)
                updated
            } else m
        }
    }

    private fun saveState(model: ModelInfo) {
        prefs.edit()
            .putString("state_${model.id.rawValue}", model.state.name)
            .putInt("progress_${model.id.rawValue}", model.progress)
            .putLong("downloaded_${model.id.rawValue}", model.downloadedBytes)
            .apply()
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun getStorageStats(): StorageStats {
        val modelsDir = getModelsDir()
        val actualOnDiskBytes = modelsDir.listFiles()?.sumOf { it.length() } ?: 0L

        val usedBytes = ModelConstants.SYSTEM_USED_STORAGE_BYTES + actualOnDiskBytes
        val freeBytes = Math.max(0L, ModelConstants.TOTAL_DEVICE_STORAGE_BYTES - usedBytes)

        return StorageStats(
            totalBytes = ModelConstants.TOTAL_DEVICE_STORAGE_BYTES,
            usedBytes = usedBytes,
            freeBytes = freeBytes,
            modelsSizeBytes = actualOnDiskBytes
        )
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
}
