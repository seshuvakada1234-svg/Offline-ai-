package com.myai.offline.llm

import android.content.Context
import android.util.Log
import com.myai.offline.data.model.InferenceMetrics
import com.myai.offline.data.model.ModelId
import com.myai.offline.data.model.ModelInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class LocalLLMEngine(
    private val context: Context
) : ILocalLLMEngine {

    private val TAG = "LocalLLMEngine"
    @Volatile private var activeModelHandle: Long = 0L
    @Volatile private var loadedModel: ModelInfo? = null
    private val nativeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    companion object {
        // llama.cpp's active model is process-global, including across ViewModel recreation.
        private val nativeMutex = Mutex()
    }
    private val generationLock = Any()
    private class Generation {
        val stopped = AtomicBoolean(false)
    }
    private val activeGeneration = AtomicReference<Generation?>(null)
    private var runningGeneration: Generation? = null

    // Blocking JNI work has a serialized owner. Cancelling an await releases the UI immediately;
    // native resources remain owned until the native call actually returns.
    private suspend fun <T> nativeOperation(block: suspend () -> T): T {
        val task = nativeScope.async { nativeMutex.withLock { block() } }
        try {
            return task.await()
        } finally {
            if (!currentCoroutineContext().isActive) task.cancel()
        }
    }

    override val isModelLoaded: Boolean
        get() = activeModelHandle != 0L && loadedModel != null

    override val currentLoadedModel: ModelInfo?
        get() = loadedModel

    override suspend fun loadModel(model: ModelInfo, threads: Int, ctxSize: Int): Long =
        nativeOperation {
            val startTime = System.currentTimeMillis()
            Log.i(TAG, "[MODEL_LOAD_START] Loading model: ${model.name} (${model.quant})")

            // If another model is currently loaded, unload it first
            if (activeModelHandle != 0L || loadedModel != null) {
                unloadNativeModel()
            }

            val modelFile = resolveModelFile(model)
            if (modelFile == null || !modelFile.exists() || modelFile.length() == 0L) {
                val expectedPath = File(File(context.filesDir, "models/llm/${model.id.rawValue}"), model.filename).absolutePath
                val errorMsg = "GGUF model file not found at $expectedPath. Please download ${model.name} (${model.sizeFormatted}) first."
                Log.e(TAG, "[MODEL_LOAD_FAILED] $errorMsg")
                throw IllegalStateException(errorMsg)
            }

            if (!NativeLlamaBridge.isAvailable()) {
                val errorMsg = "Native library libmyai_native.so is not available. Please verify NDK build configuration."
                Log.e(TAG, "[MODEL_LOAD_FAILED] $errorMsg")
                throw IllegalStateException(errorMsg)
            }

            Log.i(TAG, "[CONTEXT_CREATE_START] Creating llama context for ${model.name} with ctx=$ctxSize threads=$threads from ${modelFile.absolutePath}")
            val handle = NativeLlamaBridge.nativeLoadModel(
                modelPath = modelFile.absolutePath,
                nThreads = threads,
                nCtx = ctxSize
            )

            if (handle == 0L) {
                val errorMsg = "Failed to load GGUF weights into llama.cpp memory for ${model.name}. File may be corrupted or incompatible."
                Log.e(TAG, "[MODEL_LOAD_FAILED] $errorMsg")
                throw IllegalStateException(errorMsg)
            }

            try {
                currentCoroutineContext().ensureActive()
                check(NativeLlamaBridge.nativeIsModelLoaded(handle)) { "llama.cpp context creation failed for ${model.name}." }
                if (!runInferenceReadinessProbe(handle)) {
                    Log.w(TAG, "[MODEL_LOAD_WARN] Warmup emitted no token for ${model.name}.")
                }
                currentCoroutineContext().ensureActive()
                activeModelHandle = handle
                loadedModel = model
            } catch (t: Throwable) {
                NativeLlamaBridge.nativeUnloadModel(handle)
                throw t
            }
            val loadDuration = System.currentTimeMillis() - startTime
            Log.i(TAG, "[MODEL_LOAD_SUCCESS] Model ${model.name} loaded in ${loadDuration}ms (handle: $activeModelHandle)")
            loadDuration
        }

    override suspend fun unloadModel() {
        stopGeneration()
        nativeOperation { unloadNativeModel() }
    }

    private fun unloadNativeModel() {
        if (activeModelHandle != 0L && NativeLlamaBridge.isAvailable()) {
            NativeLlamaBridge.nativeUnloadModel(activeModelHandle)
        }
        activeModelHandle = 0L
        loadedModel = null
    }

    override fun close() {
        stopGeneration()
        nativeScope.launch {
            try {
                nativeMutex.withLock { unloadNativeModel() }
            } finally {
                nativeScope.cancel()
            }
        }
    }

    private fun resolveModelFile(model: ModelInfo): File? {
        val candidates = listOf(
            File(context.filesDir, "models/llm/${model.id.rawValue}/${model.filename}"),
            File(context.filesDir, "models/${model.id.rawValue}/${model.filename}"),
            File(context.filesDir, "models/${model.filename}"),
            File(context.filesDir, "models/llm/${model.filename}")
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.length() > 0L) {
                return candidate
            }
        }
        val candidateDirs = listOf(
            File(context.filesDir, "models/llm/${model.id.rawValue}"),
            File(context.filesDir, "models/${model.id.rawValue}")
        )
        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                val gguf = dir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".gguf") && it.length() > 0L }
                if (gguf != null) {
                    return gguf
                }
            }
        }
        return null
    }

    private fun runInferenceReadinessProbe(handle: Long): Boolean {
        val tokenCounter = AtomicInteger(0)
        return try {
            val emitted = NativeLlamaBridge.nativeGenerate(
                modelHandle = handle,
                prompt = "Hello",
                maxTokens = 1,
                callback = LlamaTokenCallback { token ->
                    if (token.isNotEmpty()) {
                        tokenCounter.incrementAndGet()
                    }
                    false
                }
            )
            emitted > 0 || tokenCounter.get() > 0
        } catch (e: Exception) {
            Log.e(TAG, "[MODEL_LOAD_FAILED] Warmup generation failed", e)
            false
        }
    }

    override fun stopGeneration() {
        activeGeneration.getAndSet(null)?.let { stopNativeGeneration(it) }
    }

    private fun stopNativeGeneration(generation: Generation) {
        generation.stopped.set(true)
        synchronized(generationLock) {
            if (runningGeneration === generation && NativeLlamaBridge.isAvailable()) {
                NativeLlamaBridge.nativeStopGeneration()
            }
        }
    }

    override fun formatPrompt(
        modelId: ModelId,
        systemPrompt: String,
        conversationHistory: List<Pair<String, String>>,
        userQuery: String,
        enableThinking: Boolean
    ): String {
        return PromptFormatter.format(
            modelId = modelId,
            systemPrompt = systemPrompt,
            conversationHistory = conversationHistory,
            userQuery = userQuery,
            enableThinking = enableThinking
        )
    }

    /**
     * Executes real native LLM inference through llama.cpp and streams tokens via Kotlin Flow.
     * All model execution runs strictly off the Android main thread (Dispatchers.IO).
     */
    override fun generateStreaming(
        prompt: String,
        userQuery: String,
        maxTokens: Int,
        onMetricsCalculated: (InferenceMetrics) -> Unit
    ): Flow<String> = flow {
        val handle = activeModelHandle
        val model = loadedModel

        if (handle == 0L || model == null) {
            val errorMsg = "No local model is loaded. Please download and load a model (e.g., Qwen3 1.7B) from the Model Manager."
            Log.e(TAG, "[INFERENCE_ERROR] $errorMsg")
            emit(errorMsg)
            return@flow
        }

        if (!NativeLlamaBridge.isAvailable()) {
            val errorMsg = "Native inference engine is unavailable. Please verify libmyai_native.so."
            Log.e(TAG, "[INFERENCE_ERROR] $errorMsg")
            emit(errorMsg)
            return@flow
        }

        val generation = Generation()
        check(activeGeneration.compareAndSet(null, generation)) { "Generation is already in progress." }

        val startTime = System.currentTimeMillis()
        var timeToFirstToken = 0L
        var tokenCount = 0
        Log.i(TAG, "[INFERENCE_START] Model: ${model.name}, maxTokens: $maxTokens, promptLength: ${prompt.length}")

        val tokenChannel = Channel<String>(capacity = Channel.UNLIMITED)
        val worker = nativeScope.async {
            nativeMutex.withLock {
                currentCoroutineContext().ensureActive()
                check(activeModelHandle == handle) { "The active model changed before generation started." }
                synchronized(generationLock) {
                    if (generation.stopped.get()) throw CancellationException("Response stopped")
                    runningGeneration = generation
                }
                try {
                    NativeLlamaBridge.nativeGenerate(
                        modelHandle = handle,
                        prompt = prompt,
                        maxTokens = maxTokens,
                        callback = LlamaTokenCallback { token ->
                            !generation.stopped.get() && tokenChannel.trySend(token).isSuccess
                        }
                    )
                } finally {
                    synchronized(generationLock) {
                        if (runningGeneration === generation) runningGeneration = null
                    }
                }
            }
        }
        worker.invokeOnCompletion { cause -> tokenChannel.close(cause) }

        try {
            for (token in tokenChannel) {
                if (generation.stopped.get()) throw CancellationException("Response stopped")
                if (tokenCount == 0) {
                    timeToFirstToken = System.currentTimeMillis() - startTime
                    Log.i(TAG, "[FIRST_TOKEN] TTFT: ${timeToFirstToken}ms")
                }
                tokenCount++
                emit(token)
            }

            val nativeReturnCode = worker.await()
            if (generation.stopped.get()) throw CancellationException("Response stopped")

            if (nativeReturnCode < 0) {
                val error = "Native inference failed (returnCode=$nativeReturnCode)."
                Log.e(TAG, "[INFERENCE_ERROR] $error")
                throw IllegalStateException(error)
            }

            val totalDuration = maxOf(1L, System.currentTimeMillis() - startTime)
            val tokensPerSec = if (totalDuration > 0) {
                (tokenCount.toDouble() / (totalDuration.toDouble() / 1000.0))
            } else 0.0

            Log.i(
                TAG,
                "[INFERENCE_COMPLETE] Model: ${model.name}, Tokens: $tokenCount, Duration: ${totalDuration}ms, Speed: ${String.format("%.1f", tokensPerSec)} t/s"
            )

            val metrics = InferenceMetrics(
                modelLoadTimeMs = 0L,
                timeToFirstTokenMs = timeToFirstToken,
                tokensPerSec = String.format("%.1f", tokensPerSec).toDoubleOrNull() ?: tokensPerSec,
                totalTokens = tokenCount,
                totalGenTimeMs = totalDuration,
                timestamp = System.currentTimeMillis()
            )
            onMetricsCalculated(metrics)

        } catch (e: CancellationException) {
            Log.i(TAG, "[GENERATION_CANCELLED] Inference job was cancelled.")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[INFERENCE_ERROR] Error during native LLM inference", e)
            throw e
        } finally {
            stopNativeGeneration(generation)
            worker.cancel()
            tokenChannel.cancel()
            activeGeneration.compareAndSet(generation, null)
        }
    }.flowOn(Dispatchers.IO)
}
