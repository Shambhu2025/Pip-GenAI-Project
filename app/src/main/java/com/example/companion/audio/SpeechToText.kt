package com.example.companion.audio

import com.example.companion.models.ModelPaths
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

class SpeechToText(paths: ModelPaths) {
    private val recognizer = OfflineRecognizer(
        assetManager = null,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = paths.whisperEncoder,
                    decoder = paths.whisperDecoder,
                    language = "en",
                    task = "transcribe"
                ),
                tokens = paths.whisperTokens,
                modelType = "whisper",
                numThreads = 2,
                provider = "cpu"
            ),
            decodingMethod = "greedy_search"
        )
    )

    fun transcribe(samples: FloatArray, sampleRate: Int = 16000): String {
        if (samples.isEmpty()) return ""
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, sampleRate)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }
}
