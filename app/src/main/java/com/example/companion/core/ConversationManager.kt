package com.example.companion.core

import android.content.Context
import com.example.companion.audio.MicRecorder
import com.example.companion.audio.SpeechToText
import com.example.companion.audio.TextToSpeech
import com.example.companion.audio.VoiceActivityDetector
import com.example.companion.face.AppState
import com.example.companion.face.Emotion
import com.example.companion.llm.LlmEngine
import com.example.companion.llm.MediaPipeEngine
import com.example.companion.llm.StreamParser
import com.example.companion.models.ModelPaths
import com.example.companion.util.Logger
import com.example.companion.util.Metrics
import com.example.companion.util.StageMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class ConversationManager(
    private val appContext: Context,
    private val scope: CoroutineScope
) {
    private val _appState = MutableStateFlow(AppState.IDLE)
    val appState: StateFlow<AppState> = _appState

    private val _emotion = MutableStateFlow(Emotion.HAPPY)
    val emotion: StateFlow<Emotion> = _emotion

    private val _mouthOpenness = MutableStateFlow(0f)
    val mouthOpenness: StateFlow<Float> = _mouthOpenness

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _lastHeardText = MutableStateFlow("Tap Start or speak to Pip!")
    val lastHeardText: StateFlow<String> = _lastHeardText

    private val _lastReplyText = MutableStateFlow("Warming up Pip's 1.5B brain...")
    val lastReplyText: StateFlow<String> = _lastReplyText

    private val _metrics = MutableStateFlow(StageMetrics())
    val metrics: StateFlow<StageMetrics> = _metrics

    private val idleScheduler = IdleScheduler(scope, minQuietSeconds = 10L, maxQuietSeconds = 10L, maxUnanswered = 10)
    val unansweredInitiations: StateFlow<Int> = idleScheduler.unansweredCount
    val secondsUntilNextInit: StateFlow<Long> = idleScheduler.secondsUntilNextInit

    private var vad: VoiceActivityDetector? = null
    private var stt: SpeechToText? = null
    private var tts: TextToSpeech? = null
    private var llm: LlmEngine? = null
    private val mic = MicRecorder()

    private var listenJob: Job? = null
    private val turnMutex = Mutex()

    fun initializeModels(context: Context, onReady: () -> Unit) {
        if (_isReady.value) {
            onReady()
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val paths = ModelPaths(context)
                if (!paths.allModelsPresent()) {
                    _lastReplyText.value = "Models missing in ${paths.baseDir.absolutePath}"
                    return@launch
                }
                _lastReplyText.value = "Loading Whisper STT & Piper TTS..."
                vad = VoiceActivityDetector(paths.vadModel)
                stt = SpeechToText(paths)
                tts = TextToSpeech(paths)

                _lastReplyText.value = "Loading QLoRA 1.5B Companion..."
                val engine = MediaPipeEngine(context, paths.llmModel)
                engine.initialize(PromptBuilder.SYSTEM_PERSONA)
                llm = engine

                _isReady.value = true
                withContext(Dispatchers.Main) { onReady() }

                // Greet user warmly on app launch, then start continuous listening + idle timer
                runCompanionTurn(
                    userPrompt = PromptBuilder.buildAppOpenPrompt(),
                    sttMs = 0L,
                    wasSelfInitiated = false
                )
                startListeningLoop()
                idleScheduler.startMonitoring(
                    getCurrentState = { _appState.value },
                    onTriggerInitiate = {
                        runCompanionTurn(
                            userPrompt = PromptBuilder.buildIdleTriggerPrompt(),
                            sttMs = 0L,
                            wasSelfInitiated = true
                        )
                    }
                )
            } catch (e: Exception) {
                Logger.e("Init failed", e)
                _lastReplyText.value = "Init error: ${e.message}"
            }
        }
    }

    fun toggleListening() {
        if (!_isReady.value) return
        if (listenJob?.isActive == true) {
            stopListeningLoop()
        } else {
            startListeningLoop()
        }
    }

    fun triggerManualIdleTest() {
        if (!_isReady.value) return
        scope.launch(Dispatchers.IO) {
            runCompanionTurn(
                userPrompt = PromptBuilder.buildIdleTriggerPrompt(),
                sttMs = 0L,
                wasSelfInitiated = true
            )
        }
    }

    fun setEmotionPreview(selected: Emotion) {
        _emotion.value = selected
    }

    private fun startListeningLoop() {
        if (listenJob?.isActive == true) return
        listenJob = scope.launch(Dispatchers.IO) {
            val shortBuf = ShortArray(mic.windowSize)
            val floatBuf = FloatArray(mic.windowSize)
            mic.start()
            _appState.value = AppState.IDLE

            while (isActive) {
                if (_appState.value == AppState.SPEAKING || _appState.value == AppState.THINKING || _appState.value == AppState.INITIATING) {
                    delay(30L)
                    continue
                }
                val read = mic.readWindow(shortBuf)
                if (read <= 0) {
                    delay(10L)
                    continue
                }
                for (i in 0 until read) {
                    floatBuf[i] = shortBuf[i] / 32768.0f
                }
                val activeVad = vad ?: continue
                activeVad.acceptWaveform(if (read == floatBuf.size) floatBuf else floatBuf.copyOf(read))

                if (activeVad.isSpeechDetected() && _appState.value == AppState.IDLE) {
                    _appState.value = AppState.LISTENING
                    _emotion.value = Emotion.LISTENING
                }

                val segment = activeVad.popCompletedSegment()
                if (segment != null) {
                    activeVad.reset()
                    if (segment.size >= 4800) { // at least 0.30s of speech
                        handleUserAudioSegment(segment)
                    } else {
                        _appState.value = AppState.IDLE
                    }
                }
            }
        }
    }

    private fun stopListeningLoop() {
        listenJob?.cancel()
        listenJob = null
        mic.stop()
        _appState.value = AppState.IDLE
    }

    private suspend fun handleUserAudioSegment(samples: FloatArray) {
        val tStt0 = Metrics.nowMs()
        val rawHeard = stt?.transcribe(samples).orEmpty()
        val sttMs = Metrics.nowMs() - tStt0

        val cleanedHeard = PromptBuilder.normalizeSttTranscript(rawHeard)
        if (cleanedHeard.length < 2) {
            Logger.i("Ignored empty/inaudible STT: '$rawHeard'")
            _appState.value = AppState.IDLE
            _emotion.value = Emotion.NEUTRAL
            return
        }

        idleScheduler.onUserSpoke()
        _lastHeardText.value = cleanedHeard
        runCompanionTurn(
            userPrompt = cleanedHeard,
            sttMs = sttMs,
            wasSelfInitiated = false
        )
    }

    private fun resolveEmotion(rawReply: String, userPrompt: String): Emotion {
        // 1. Honor explicit [tag] emitted by QLoRA model
        StreamParser.extractEmotion(rawReply)?.let {
            if (it != Emotion.NEUTRAL || rawReply.lowercase(Locale.US).contains("[neutral]")) {
                return it
            }
        }
        // 2. Guarantee physical animations when user asks to dance, sing, or laugh
        val u = userPrompt.lowercase(Locale.US)
        val r = rawReply.lowercase(Locale.US)
        return when {
            u.contains("dance") || r.contains("boogie") || r.contains("groove") || r.contains("footwork") || r.contains("swaying") -> Emotion.DANCE
            u.contains("sing") || r.contains("la la") || r.contains("twinkle") || r.contains("hakuna matata") || r.contains("do re mi") || r.contains("melody") -> Emotion.SING
            u.contains("laugh") || u.contains("joke") || r.contains("ha ha") || r.contains("hehe") || r.contains("bwahaha") || r.contains("giggle") -> Emotion.LAUGH
            r.contains("sorry") || u.contains("lonely") || u.contains("angry") || u.contains("sad") -> Emotion.SAD
            r.contains("whoa") || r.contains("adrenaline") || r.contains("unreal") -> Emotion.SURPRISED
            u.contains("why ") || u.contains("how ") || u.contains("cs787") || r.contains("remember that") || r.contains("rotates") || r.contains("minerals") -> Emotion.THINKING
            r.contains("?") -> Emotion.CURIOUS
            else -> Emotion.HAPPY
        }
    }

    private suspend fun runCompanionTurn(
        userPrompt: String,
        sttMs: Long,
        wasSelfInitiated: Boolean
    ) = turnMutex.withLock {
        val activeLlm = llm ?: return@withLock
        _appState.value = if (wasSelfInitiated) AppState.INITIATING else AppState.THINKING
        _emotion.value = Emotion.THINKING
        if (wasSelfInitiated) {
            _lastHeardText.value = "(Proactive) $userPrompt"
        }

        val tLlm0 = Metrics.nowMs()
        var ttftMs = 0L
        var tokenChunks = 0
        val sb = StringBuilder()

        suspend fun collectStream(prompt: String) {
            sb.clear()
            tokenChunks = 0
            ttftMs = 0L
            try {
                activeLlm.streamReply(prompt).collect { chunk ->
                    if (ttftMs == 0L && chunk.isNotEmpty()) {
                        ttftMs = Metrics.nowMs() - tLlm0
                    }
                    tokenChunks++
                    sb.append(chunk)
                    val currentRaw = sb.toString()
                    StreamParser.extractEmotion(currentRaw)?.let { tagEmo ->
                        if (tagEmo != Emotion.NEUTRAL) _emotion.value = tagEmo
                    }
                    _lastReplyText.value = StreamParser.cleanForSpeech(currentRaw)
                }
            } catch (e: Exception) {
                Logger.e("LLM stream error, resetting conversation", e)
            }
        }

        collectStream(userPrompt)
        if (sb.toString().isBlank()) {
            Logger.i("Empty LLM reply -> resetting conversation and retrying turn")
            activeLlm.resetConversation(PromptBuilder.SYSTEM_PERSONA)
            collectStream(userPrompt)
        }

        val llmTotalMs = (Metrics.nowMs() - tLlm0).coerceAtLeast(1L)
        val decodeWindowMs = (llmTotalMs - ttftMs).coerceAtLeast(1L)
        val tokPerSec = (tokenChunks * 1000.0) / decodeWindowMs

        val rawReply = sb.toString().trim()
        val finalEmotion = resolveEmotion(rawReply, userPrompt)
        _emotion.value = finalEmotion

        val spokenText = StreamParser.cleanForSpeech(rawReply)
        _lastReplyText.value = if (spokenText.isNotEmpty()) spokenText else "..."

        var firstTtsMs = 0L
        if (spokenText.isNotEmpty()) {
            _appState.value = AppState.SPEAKING
            mic.stop()
            tts?.synthesizeAndPlay(
                sentence = spokenText,
                onFirstAudioReady = { synthMs -> firstTtsMs = synthMs },
                onMouthOpenness = { open -> _mouthOpenness.value = open }
            )
            mic.start()
        }

        val e2eMs = sttMs + ttftMs + firstTtsMs
        _metrics.value = StageMetrics(
            sttMs = sttMs,
            llmTtftMs = ttftMs,
            firstSentenceMs = llmTotalMs,
            firstTtsMs = firstTtsMs,
            endOfSpeechToFirstSoundMs = e2eMs,
            tokensPerSec = tokPerSec,
            ramMb = Metrics.currentRamMb()
        )

        val logLine = String.format(
            Locale.US,
            "TURN | Heard: '%s' | PipRaw: '%s' | Emotion: [%s] | E2E: %dms (STT:%dms, TTFT:%dms, TTS:%dms, Speed:%.1f tok/s)",
            userPrompt,
            rawReply.replace("\n", " "),
            finalEmotion.tag,
            e2eMs,
            sttMs,
            ttftMs,
            firstTtsMs,
            tokPerSec
        )
        Logger.i(logLine)
        try {
            File(appContext.getExternalFilesDir(null), "dialogue_history.txt").appendText(logLine + "\n")
        } catch (_: Exception) {}

        idleScheduler.onCompanionFinishedSpeaking(wasSelfInitiated)
        _appState.value = AppState.IDLE
    }

    fun pauseForBackground() {
        android.util.Log.i("ConversationManager", "App moved to background -> pausing mic, TTS, and IdleScheduler")
        stopListeningLoop()
        tts?.stopSpeaking()
        idleScheduler.stop()
        _mouthOpenness.value = 0f
        _appState.value = AppState.IDLE
        _emotion.value = Emotion.NEUTRAL
    }


    fun close() {
        pauseForBackground()
    }

}
