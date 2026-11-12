package com.example.companion.face

enum class AppState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    INITIATING
}

data class EmotionParams(
    val eyeOpenness: Float,       // 0.2 (sleepy/laugh squint) .. 1.3 (surprised)
    val pupilOffsetX: Float,      // -1.0 (left) .. +1.0 (right)
    val pupilOffsetY: Float,      // -1.0 (up) .. +1.0 (down)
    val pupilScale: Float,        // 0.8 .. 1.25
    val leftBrowAngle: Float,     // degrees (-25 .. +25)
    val rightBrowAngle: Float,    // degrees (-25 .. +25)
    val browHeightOffset: Float,  // -1.0 (low) .. +1.0 (high)
    val mouthCurve: Float,        // -1.0 (sad frown) .. +1.0 (big smile)
    val mouthWidthScale: Float,   // 0.45 ("O" mouth) .. 1.20 (wide laugh grin)
    val cheekBlushAlpha: Float    // 0.0 .. 0.85
)

enum class Emotion(val tag: String, val params: EmotionParams) {
    HAPPY(
        "happy",
        EmotionParams(0.88f, 0f, -0.05f, 1.12f, -8f, 8f, 0.35f, 1.0f, 1.10f, 0.65f)
    ),
    DANCE(
        "dance",
        EmotionParams(0.95f, 0.15f, -0.10f, 1.18f, -14f, 14f, 0.55f, 1.0f, 1.12f, 0.75f)
    ),
    SING(
        "sing",
        EmotionParams(0.78f, 0f, -0.20f, 1.10f, -10f, 10f, 0.45f, 0.65f, 0.62f, 0.70f)
    ),
    LAUGH(
        "laugh",
        EmotionParams(0.22f, 0f, -0.10f, 1.05f, -12f, 12f, 0.50f, 1.0f, 1.22f, 0.85f)
    ),
    CURIOUS(
        "curious",
        EmotionParams(1.08f, 0.25f, -0.15f, 1.08f, -16f, 6f, 0.45f, 0.45f, 0.78f, 0.40f)
    ),
    SURPRISED(
        "surprised",
        EmotionParams(1.28f, 0f, 0f, 0.82f, -14f, 14f, 0.85f, 0.05f, 0.48f, 0.35f)
    ),
    SAD(
        "sad",
        EmotionParams(0.72f, 0f, 0.35f, 0.95f, 18f, -18f, -0.25f, -0.85f, 0.82f, 0.10f)
    ),
    SLEEPY(
        "sleepy",
        EmotionParams(0.28f, 0f, 0.25f, 0.90f, 4f, -4f, -0.45f, -0.10f, 0.65f, 0.25f)
    ),
    THINKING(
        "thinking",
        EmotionParams(0.82f, -0.45f, -0.45f, 0.95f, 12f, -16f, 0.20f, -0.15f, 0.68f, 0.15f)
    ),
    LISTENING(
        "listening",
        EmotionParams(1.16f, 0f, 0f, 1.18f, -10f, 10f, 0.50f, 0.55f, 0.90f, 0.50f)
    ),
    NEUTRAL(
        "neutral",
        EmotionParams(1.0f, 0f, 0f, 1.0f, -3f, 3f, 0.0f, 0.35f, 0.92f, 0.30f)
    );

    companion object {
        fun fromTag(raw: String?): Emotion {
            if (raw.isNullOrBlank()) return NEUTRAL
            val clean = raw.trim().lowercase()
            return entries.firstOrNull { it.tag == clean } ?: NEUTRAL
        }
    }
}
