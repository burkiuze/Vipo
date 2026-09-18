package com.example.data.model

data class PerformanceStats(
    val tokensPerSecond: Float = 0f,
    val timeToFirstTokenMs: Long = 0L,
    val promptTokens: Int = 0,
    val generatedTokens: Int = 0,
    val contextUsagePercentage: Float = 0f,
    val contextUsedTokens: Int = 0,
    val contextMaxTokens: Int = 2048,
    val modelLoadTimeMs: Long = 0L,
    val ramEstimateMb: Int = 0
)
