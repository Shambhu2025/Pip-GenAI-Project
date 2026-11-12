package com.example.companion.llm

import com.example.companion.face.Emotion

object StreamParser {
    private val tagRegex = Regex("\\[([a-zA-Z_]+)\\]")
    private val knownTagCleanup = Regex(
        "\\[(happy|curious|surprised|thinking|sad|sleepy|neutral|dance|sing|laugh|yes|no|excited| chuckle|giggle)\\]",
        RegexOption.IGNORE_CASE
    )

    fun extractEmotion(text: String): Emotion? {
        val m = tagRegex.find(text) ?: return null
        return Emotion.fromTag(m.groupValues[1])
    }

    fun cleanForSpeech(text: String): String {
        return text
            .replace(knownTagCleanup, " ")
            .replace("[", " ")
            .replace("]", " ")
            .replace(Regex("[^a-zA-Z0-9.,!?'\"\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
