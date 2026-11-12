package com.example.companion.models

import android.content.Context
import android.os.StatFs
import android.util.Log
import java.io.File

object ModelDownloader {
    private const val TAG = "ModelDownloader"

    data class VerificationStatus(
        val allPresent: Boolean,
        val missingFiles: List<String>,
        val totalModelBytes: Long,
        val freeSpaceMb: Long
    )

    fun verifyOfflineBundle(context: Context): VerificationStatus {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val modelsDir = File(baseDir, "models")
        val stat = StatFs(baseDir.absolutePath)
        val freeMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024L * 1024L)

        val required = listOf(
            "Silero VAD" to File(modelsDir, "silero_vad.onnx"),
            "Whisper STT" to File(modelsDir, "sherpa-onnx-whisper-tiny.en"),
            "Piper VITS TTS" to File(modelsDir, "vits-piper-en_US-amy-low"),
            "Pip QLoRA 1.5B LLM" to File(modelsDir, "pip_qwen25_1_5b_qlora.litertlm")
        )

        val missing = mutableListOf<String>()
        var totalBytes = 0L
        for ((label, file) in required) {
            if (!file.exists()) {
                Log.w(TAG, "Missing model: ${file.absolutePath}")
                missing.add("$label (${file.name})")
            } else {
                totalBytes += if (file.isDirectory) {
                    file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                } else {
                    file.length()
                }
            }
        }

        val totalMb = totalBytes / (1024L * 1024L)
        Log.i(TAG, "Offline Bundle Verified: allPresent=${missing.isEmpty()}, totalModelSize=${totalMb}MB, freeSpace=${freeMb}MB")
        return VerificationStatus(
            allPresent = missing.isEmpty(),
            missingFiles = missing,
            totalModelBytes = totalBytes,
            freeSpaceMb = freeMb
        )
    }
}
