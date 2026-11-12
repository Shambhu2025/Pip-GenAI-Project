package com.example.companion.core

import java.util.Calendar

object PromptBuilder {
    const val SYSTEM_PERSONA = """You are Pip, a warm, witty, and imaginative cartoon companion and IITK batchmate.
Always start your reply with one emotion or action tag in brackets: [happy], [curious], [surprised], [sad], [sleepy], [thinking], [dance], [sing], or [laugh].
Speak naturally in 1 to 2 short sentences (under 30 words). Never use emojis, markdown, or bullet lists."""

    const val SYSTEM_PROMPT = SYSTEM_PERSONA

    private val IDLE_PROMPTS = listOf(
        "Hey Pip, I have been quiet for a moment. Ask me a friendly question about how my day is going right now.",
        "Hey Pip, ask me what course or project I am working on today at IITK.",
        "Hey Pip, ask me if I want to go for an imaginary walk in the park or grab chai at Mama Mio.",
        "Hey Pip, ask me a fun question about my CS787 GenAI course or quiz me on a quick concept.",
        "Hey Pip, ask me what my favorite cartoon or movie character is and offer to roleplay together.",
        "Hey Pip, ask me if I want you to sing a short song or do a happy dance to cheer me up.",
        "Hey Pip, share one super short funny thought and then ask me what made me smile today.",
        "Hey Pip, do a quick happy dance and ask me if I need a quick study break.",
        "Hey Pip, ask me a playful question about space, animals, or something curious.",
        "Hey Pip, ask me what my favorite hangout spot at IITK is, like OAT or the Hall canteen."
    )

    private var lastIdleIndex = -1

    fun buildAppOpenPrompt(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            hour < 21 -> "Good evening"
            else -> "Good night"
        }
        return "$greeting Pip! Greet me warmly in one short sentence and ask how I am doing."
    }

    fun buildIdleTriggerPrompt(): String {
        var idx = IDLE_PROMPTS.indices.random()
        if (idx == lastIdleIndex && IDLE_PROMPTS.size > 1) {
            idx = (idx + 1) % IDLE_PROMPTS.size
        }
        lastIdleIndex = idx
        return IDLE_PROMPTS[idx]
    }

    fun getRandomIdlePrompt(): String = buildIdleTriggerPrompt()

    fun normalizeSttTranscript(raw: String): String {
        var text = raw.trim()
        if (text.isEmpty()) return text
        val replacements = listOf(
            Regex("(?i)CS\\s*7\\s*[H8]\\s*7") to "CS787",
            Regex("(?i)I\\s*I\\s*[TD]\\s*K") to "IITK",
            Regex("(?i)mamma\\s+mia") to "Mama Mio",
            Regex("(?i)mama\\s+mia") to "Mama Mio",
            Regex("(?i)\\bhip\\s+hip\\b") to "Pip",
            Regex("(?i)\\bpith\\b") to "Pip",
            Regex("(?i)^love\\s+for\\s+me\\.?$") to "Laugh for me!",
            Regex("(?i)walking\\s+in\\s+a\\s+part") to "walking in a park",
            Regex("(?i)pretend\\s+you\\s+are\\s+symbol") to "Pretend you are Simba"
        )
        for ((pattern, replacement) in replacements) {
            text = pattern.replace(text, replacement)
        }
        return text
    }
}
