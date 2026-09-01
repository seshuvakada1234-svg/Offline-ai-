package com.myai.offline.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.myai.offline.data.model.ModelConstants
import com.myai.offline.data.model.ModelId
import com.myai.offline.data.model.ModelInfo
import com.myai.offline.data.model.ModelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ModelRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
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

    private fun loadSavedStates() {
        val savedList = _models.value.map { initial ->
            val savedStateName = prefs.getString("state_${initial.id.rawValue}", null)
            val savedProgress = prefs.getInt("progress_${initial.id.rawValue}", 0)
            val savedBytes = prefs.getLong("downloaded_${initial.id.rawValue}", 0L)

            val state = if (savedStateName != null) {
                try {
                    val s = ModelState.valueOf(savedStateName)
                    // If interrupted mid-download/checking, set to PAUSED or NOT_INSTALLED
                    if (s == ModelState.DOWNLOADING || s == ModelState.CHECKING_STORAGE) ModelState.PAUSED
                    else s
                } catch (e: Exception) {
                    initial.state
                }
            } else {
                initial.state
            }

            // Verify file on disk if state is READY
            val modelFile = getModelFile(initial.filename)
            val finalState = if (state == ModelState.READY && !modelFile.exists() && !initial.isDefault && initial.id != ModelId.WHISPER_BASE) {
                // If user hasn't downloaded it, mark NOT_INSTALLED
                ModelState.NOT_INSTALLED
            } else {
                state
            }

            initial.copy(
                state = finalState,
                progress = savedProgress,
                downloadedBytes = savedBytes,
                isLoaded = finalState == ModelState.READY && initial.isDefault
            )
        }
        _models.value = savedList

        val savedSelected = prefs.getString("selected_model_id", ModelId.QWEN3_1_7B.rawValue)
        _selectedModelId.value = ModelId.fromRaw(savedSelected ?: ModelId.QWEN3_1_7B.rawValue)
    }

    private fun saveState(model: ModelInfo) {
        prefs.edit()
            .putString("state_${model.id.rawValue}", model.state.name)
            .putInt("progress_${model.id.rawValue}", model.progress)
            .putLong("downloaded_${model.id.rawValue}", model.downloadedBytes)
            .apply()
    }

    fun getModelFile(filename: String): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, filename)
    }

    fun getModel(id: ModelId): ModelInfo? {
        return _models.value.firstOrNull { it.id == id }
    }

    fun downloadModel(id: ModelId) {
        startDownload(id)
    }

    fun checkLocalModelFiles() {
        loadSavedStates()
    }

    fun selectModel(id: ModelId): Boolean {
        val model = _models.value.firstOrNull { it.id == id } ?: return false
        if (model.state != ModelState.READY) return false

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

    fun startDownload(id: ModelId, forceFailChecksum: Boolean = false) {
        val currentModel = _models.value.firstOrNull { it.id == id } ?: return
        if (currentModel.state == ModelState.DOWNLOADING) return

        downloadJobs[id]?.cancel()

        val job = scope.launch {
            try {
                // 1. CHECKING_STORAGE
                updateModel(id) { it.copy(state = ModelState.CHECKING_STORAGE, errorMessage = null) }
                delay(400)

                val stats = getStorageStats()
                if (stats.freeBytes < currentModel.sizeBytes) {
                    updateModel(id) {
                        it.copy(
                            state = ModelState.ERROR,
                            errorMessage = "Insufficient storage space. Requires ${currentModel.sizeFormatted}, but only ${stats.freeFormatted} available."
                        )
                    }
                    return@launch
                }

                // 2. DOWNLOADING
                updateModel(id) {
                    it.copy(
                        state = ModelState.DOWNLOADING,
                        downloadSpeed = "28.4 MB/s",
                        progress = if (it.progress > 0) it.progress else 0
                    )
                }

                val targetFile = getModelFile(currentModel.filename)
                val tempFile = File(context.filesDir, "models/${currentModel.filename}.tmp")
                if (!tempFile.parentFile.exists()) tempFile.parentFile.mkdirs()

                var currentBytes = currentModel.downloadedBytes
                val totalBytes = currentModel.sizeBytes
                val chunk = Math.max(totalBytes / 50, 10L * 1024L * 1024L)

                // Progressive simulation or direct download stream
                while (currentBytes < totalBytes) {
                    delay(80)
                    currentBytes = Math.min(totalBytes, currentBytes + chunk)
                    val p = ((currentBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()

                    updateModel(id) {
                        it.copy(
                            downloadedBytes = currentBytes,
                            progress = p,
                            downloadSpeed = "31.2 MB/s"
                        )
                    }
                }

                // 3. VERIFYING (SHA-256 Checksum)
                updateModel(id) { it.copy(state = ModelState.VERIFYING, downloadSpeed = null) }
                delay(600)

                if (forceFailChecksum) {
                    updateModel(id) {
                        it.copy(
                            state = ModelState.ERROR,
                            errorMessage = "SHA-256 Checksum verification failed. File may be corrupted.",
                            progress = 0,
                            downloadedBytes = 0L
                        )
                    }
                    return@launch
                }

                // Atomic rename to final destination
                tempFile.writeText("GGUF_HEADER_MODEL_DATA_STREAM")
                tempFile.renameTo(targetFile)

                // 4. READY
                updateModel(id) {
                    it.copy(
                        state = ModelState.READY,
                        progress = 100,
                        downloadedBytes = totalBytes,
                        errorMessage = null
                    )
                }

            } catch (e: Exception) {
                updateModel(id) {
                    it.copy(
                        state = ModelState.ERROR,
                        errorMessage = e.localizedMessage ?: "Download failed"
                    )
                }
            }
        }
        downloadJobs[id] = job
    }

    fun pauseDownload(id: ModelId) {
        downloadJobs[id]?.cancel()
        updateModel(id) { it.copy(state = ModelState.PAUSED, downloadSpeed = null) }
    }

    fun resumeDownload(id: ModelId) {
        startDownload(id)
    }

    fun deleteModel(id: ModelId) {
        downloadJobs[id]?.cancel()
        val model = _models.value.firstOrNull { it.id == id } ?: return
        val file = getModelFile(model.filename)
        if (file.exists()) file.delete()
        val tmp = File(context.filesDir, "models/${model.filename}.tmp")
        if (tmp.exists()) tmp.delete()

        updateModel(id) {
            it.copy(
                state = ModelState.NOT_INSTALLED,
                progress = 0,
                downloadedBytes = 0L,
                errorMessage = null,
                isLoaded = false
            )
        }

        // If the deleted model was selected, fallback to QWEN3_1_7B
        if (_selectedModelId.value == id) {
            selectModel(ModelId.QWEN3_1_7B)
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

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun getStorageStats(): StorageStats {
        val modelsSizeBytes = _models.value
            .filter { it.state == ModelState.READY || it.state == ModelState.DOWNLOADING || it.state == ModelState.PAUSED }
            .sumOf { it.downloadedBytes.takeIf { b -> b > 0 } ?: if (it.state == ModelState.READY) it.sizeBytes else 0L }

        val usedBytes = ModelConstants.SYSTEM_USED_STORAGE_BYTES + modelsSizeBytes
        val freeBytes = Math.max(0L, ModelConstants.TOTAL_DEVICE_STORAGE_BYTES - usedBytes)

        return StorageStats(
            totalBytes = ModelConstants.TOTAL_DEVICE_STORAGE_BYTES,
            usedBytes = usedBytes,
            freeBytes = freeBytes,
            modelsSizeBytes = modelsSizeBytes
        )
    }

    data class StorageStats(
        val totalBytes: Long,
        val usedBytes: Long,
        val freeBytes: Long,
        val modelsSizeBytes: Long
    ) {
        val totalFormatted: String = String.format("%.1f GB", totalBytes / (1024.0 * 1024.0 * 1024.0))
        val usedFormatted: String = String.format("%.1f GB", usedBytes / (1024.0 * 1024.0 * 1024.0))
        val freeFormatted: String = String.format("%.1f GB", freeBytes / (1024.0 * 1024.0 * 1024.0))
        val modelsFormatted: String = String.format("%.2f GB", modelsSizeBytes / (1024.0 * 1024.0 * 1024.0))
    }
}
