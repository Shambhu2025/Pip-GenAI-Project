package com.example.companion.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.example.companion.models.ModelPaths
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.delay

class TextToSpeech(paths: ModelPaths, private val voiceSpeed: Float = 1.20f) {
    private val tts = OfflineTts(
        assetManager = null,
        config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = paths.piperModel,
                    tokens = paths.piperTokens,
                    dataDir = paths.piperDataDir
                ),
                numThreads = 2,
                provider = "cpu"
            )
        )
    )

    @Volatile
    private var stopRequested = false

    fun stopSpeaking() {
        stopRequested = true
    }

    suspend fun synthesizeAndPlay(
        sentence: String,
        onFirstAudioReady: (Long) -> Unit,
        onMouthOpenness: (Float) -> Unit
    ) {
        stopRequested = false
        val t0 = SystemClock.elapsedRealtime()
        val audio = tts.generate(text = sentence, sid = 0, speed = voiceSpeed)
        val synthMs = SystemClock.elapsedRealtime() - t0
        onFirstAudioReady(synthMs)

        if (audio.samples.isEmpty() || audio.sampleRate <= 0 || stopRequested) {
            onMouthOpenness(0f)
            return
        }

        val sr = audio.sampleRate
        val pcm16 = ShortArray(audio.samples.size) { i ->
            (audio.samples[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
        }

        val minBuf = AudioTrack.getMinBufferSize(
            sr,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            track.play()
            // Write in ~45 ms chunks so LoudnessMeter updates mouth openness smoothly
            val chunkSamples = (sr * 0.045f).toInt().coerceAtLeast(256)
            var offset = 0
            while (offset < pcm16.size && !stopRequested) {
                val len = minOf(chunkSamples, pcm16.size - offset)
                val openness = LoudnessMeter.computeMouthOpenness(pcm16, offset, len)
                onMouthOpenness(openness)
                track.write(pcm16, offset, len, AudioTrack.WRITE_BLOCKING)
                offset += len
            }

            // Wait for remaining hardware buffer to finish playing
            val waitLimit = 350L
            val w0 = SystemClock.elapsedRealtime()
            while (
                !stopRequested &&
                track.playbackHeadPosition < pcm16.size &&
                (SystemClock.elapsedRealtime() - w0) < waitLimit
            ) {
                delay(20)
            }
        } finally {
            onMouthOpenness(0f)
            try {
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }
    }
}
