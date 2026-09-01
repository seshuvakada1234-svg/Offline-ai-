package com.myai.offline.data.model

data class InferenceMetrics(
    val modelLoadTimeMs: Long = 0L,
    val timeToFirstTokenMs: Long = 0L,
    val tokensPerSec: Double = 0.0,
    val totalTokens: Int = 0,
    val totalGenTimeMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)
