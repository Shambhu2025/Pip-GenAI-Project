package com.example.companion.audio

import kotlin.math.sqrt

object LoudnessMeter {
    fun computeMouthOpenness(samples: ShortArray, offset: Int, length: Int): Float {
        if (length <= 0) return 0f
        var sumSq = 0.0
        val end = (offset + length).coerceAtMost(samples.size)
        for (i in offset until end) {
            val norm = samples[i] / 32768.0
            sumSq += norm * norm
        }
        val rms = sqrt(sumSq / length).toFloat()
        // Map typical speech RMS (0.02..0.25) to mouth openness (0.0..1.0)
        return ((rms - 0.015f) * 5.0f).coerceIn(0f, 1f)
    }
}
