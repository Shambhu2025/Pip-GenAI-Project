package com.example.companion.util

import android.os.Debug
import android.os.SystemClock

data class StageMetrics(
    val sttMs: Long = 0L,
    val llmTtftMs: Long = 0L,
    val firstSentenceMs: Long = 0L,
    val firstTtsMs: Long = 0L,
    val endOfSpeechToFirstSoundMs: Long = 0L,
    val tokensPerSec: Double = 0.0,
    val ramMb: Int = 0
)

object Metrics {
    fun nowMs(): Long = SystemClock.elapsedRealtime()

    fun currentRamMb(): Int {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss / 1024
    }
}
