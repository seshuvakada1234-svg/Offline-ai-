package com.myai.offline.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecorder(private val context: Context) {
    private val TAG = "AudioRecorder"

    companion object {
        const val SAMPLE_RATE = 16000 // 16kHz standard for Whisper
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val _audioLevel = MutableStateFlow<Float>(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _isRecording = MutableStateFlow<Boolean>(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val audioDataStream = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun startRecording(scope: CoroutineScope) {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = Math.max(minBufferSize, 2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }

            audioDataStream.reset()
            audioRecord?.startRecording()
            _isRecording.value = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(1024)
                val byteBuffer = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)

                while (isActive && _isRecording.value) {
                    val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readCount > 0) {
                        // Calculate RMS amplitude for visualizer
                        var sum = 0.0
                        byteBuffer.clear()
                        for (i in 0 until readCount) {
                            val sample = buffer[i]
                            sum += sample * sample
                            byteBuffer.putShort(sample)
                        }
                        val rms = Math.sqrt(sum / readCount)
                        val normalized = Math.min(1.0f, (rms / 32767.0 * 5.0).toFloat())
                        _audioLevel.value = normalized

                        synchronized(audioDataStream) {
                            audioDataStream.write(byteBuffer.array(), 0, readCount * 2)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord", e)
            stopRecording()
        }
    }

    fun stopRecording(): FloatArray {
        _isRecording.value = false
        _audioLevel.value = 0f
        recordingJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audioRecord: ${e.message}")
        }

        val rawBytes: ByteArray
        synchronized(audioDataStream) {
            rawBytes = audioDataStream.toByteArray()
        }

        // Convert 16-bit PCM bytes to Normalized FloatArray [-1.0f .. 1.0f] for Whisper
        val shortCount = rawBytes.size / 2
        val floatArray = FloatArray(shortCount)
        val shortBuffer = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        for (i in 0 until shortCount) {
            floatArray[i] = shortBuffer.get(i) / 32768.0f
        }

        return floatArray
    }
}
