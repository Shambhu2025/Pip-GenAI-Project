package com.example.companion.audio

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

class VoiceActivityDetector(modelPath: String) {
    private val vad = Vad(
        assetManager = null,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = modelPath,
                threshold = 0.50f,
                minSilenceDuration = 0.85f, // Wait 0.85s of silence so natural pauses between words don't cut off speech!
                minSpeechDuration = 0.30f,
                windowSize = 512,
                maxSpeechDuration = 15.0f
            ),
            sampleRate = 16000,
            numThreads = 1,
            provider = "cpu"
        )
    )

    fun reset() = vad.reset()

    fun acceptWaveform(samples: FloatArray) = vad.acceptWaveform(samples)

    fun isSpeechDetected(): Boolean = vad.isSpeechDetected()

    fun popCompletedSegment(): FloatArray? {
        if (vad.empty()) return null
        val seg = vad.front()
        vad.pop()
        return seg.samples
    }
}
