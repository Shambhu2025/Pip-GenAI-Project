package com.example.companion.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.companion.util.Logger

class MicRecorder(val sampleRate: Int = 16000, val windowSize: Int = 512) {
    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start() {
        stop()
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        // Use clean VOICE_RECOGNITION source (same as Step 1) so Samsung DSP does not distort Whisper audio
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf * 2, windowSize * 8)
        )
        rec.startRecording()
        audioRecord = rec
        Logger.i("MicRecorder started with clean VOICE_RECOGNITION source")
    }

    fun readWindow(buffer: ShortArray): Int {
        val rec = audioRecord ?: return -1
        return rec.read(buffer, 0, windowSize)
    }

    fun stop() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        } finally {
            audioRecord = null
        }
    }
}
