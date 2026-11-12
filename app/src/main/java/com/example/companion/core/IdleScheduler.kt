package com.example.companion.core

import com.example.companion.face.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class IdleScheduler(
    private val scope: CoroutineScope,
    private val minQuietSeconds: Long = 10L,
    private val maxQuietSeconds: Long = 10L,
    private val maxUnanswered: Int = 10
) {
    private val _unansweredCount = MutableStateFlow(0)
    val unansweredCount: StateFlow<Int> = _unansweredCount

    private val _secondsUntilNextInit = MutableStateFlow(10L)
    val secondsUntilNextInit: StateFlow<Long> = _secondsUntilNextInit

    private var timerJob: Job? = null
    private var pausedUntilUserSpeaks = false

    private fun computeNextWaitSeconds(): Long = 10L

    fun onUserSpoke() {
        _unansweredCount.value = 0
        pausedUntilUserSpeaks = false
        _secondsUntilNextInit.value = computeNextWaitSeconds()
    }

    fun onCompanionFinishedSpeaking(wasSelfInitiated: Boolean) {
        if (wasSelfInitiated) {
            val nextCount = _unansweredCount.value + 1
            _unansweredCount.value = nextCount
            if (nextCount >= maxUnanswered) {
                pausedUntilUserSpeaks = true
                _secondsUntilNextInit.value = -1L
                return
            }
        }
        _secondsUntilNextInit.value = computeNextWaitSeconds()
    }

    fun startMonitoring(
        getCurrentState: () -> AppState,
        onTriggerInitiate: suspend () -> Unit
    ) {
        timerJob?.cancel()
        _secondsUntilNextInit.value = computeNextWaitSeconds()

        timerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                if (pausedUntilUserSpeaks) {
                    _secondsUntilNextInit.value = -1L
                    continue
                }

                val state = getCurrentState()
                if (state == AppState.IDLE || state == AppState.LISTENING) {
                    val remaining = _secondsUntilNextInit.value - 1L
                    if (remaining > 0L) {
                        _secondsUntilNextInit.value = remaining
                    } else {
                        _secondsUntilNextInit.value = 10L
                        onTriggerInitiate()
                    }
                }
            }
        }
    }

    fun stop() {
        timerJob?.cancel()
        timerJob = null
    }
}
