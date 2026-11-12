package com.example.companion.models

import android.content.Context
import java.io.File

class ModelPaths(context: Context) {
    val baseDir: File = File(context.getExternalFilesDir(null), "models")
    val vadModel: String get() = File(baseDir, "silero_vad.onnx").absolutePath
    val whisperDir: String get() = File(baseDir, "sherpa-onnx-whisper-tiny.en").absolutePath
    val whisperEncoder: String get() = "$whisperDir/tiny.en-encoder.int8.onnx"
    val whisperDecoder: String get() = "$whisperDir/tiny.en-decoder.int8.onnx"
    val whisperTokens: String get() = "$whisperDir/tiny.en-tokens.txt"
    val piperDir: String get() = File(baseDir, "vits-piper-en_US-amy-low").absolutePath
    val piperModel: String get() = "$piperDir/en_US-amy-low.onnx"
    val piperTokens: String get() = "$piperDir/tokens.txt"
    val piperDataDir: String get() = "$piperDir/espeak-ng-data"
    // Use the block-32 quantized model that preserves full English fluency
    val llmModel: String get() = File(baseDir, "pip_qwen25_1_5b_qlora.litertlm").absolutePath

    fun allModelsPresent(): Boolean {
        return File(vadModel).exists() &&
            File(whisperEncoder).exists() &&
            File(piperModel).exists() &&
            File(llmModel).exists()
    }
}

