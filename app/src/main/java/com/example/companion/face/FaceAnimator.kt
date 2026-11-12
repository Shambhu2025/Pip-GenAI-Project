package com.example.companion.face

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlin.random.Random

data class AnimatedFaceState(
    val eyeOpenness: Float,
    val pupilOffsetX: Float,
    val pupilOffsetY: Float,
    val pupilScale: Float,
    val leftBrowAngle: Float,
    val rightBrowAngle: Float,
    val browHeightOffset: Float,
    val mouthCurve: Float,
    val mouthWidthScale: Float,
    val cheekBlushAlpha: Float,
    val mouthOpenness: Float,
    val headBobY: Float,
    val headSwayX: Float,
    val headTiltDeg: Float,
    val effectPhase: Float
)

@Composable
fun rememberAnimatedFaceState(
    emotion: Emotion,
    rawMouthOpenness: Float,
    appState: AppState
): AnimatedFaceState {
    val target = emotion.params
    val spec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2600L, 4800L))
            isBlinking = true
            delay(130L)
            isBlinking = false
        }
    }

    val blinkMultiplier by animateFloatAsState(
        targetValue = if (isBlinking && emotion != Emotion.LAUGH) 0.05f else 1.0f,
        animationSpec = tween(durationMillis = 75, easing = FastOutSlowInEasing),
        label = "blink"
    )

    val baseEyeOpen by animateFloatAsState(target.eyeOpenness, spec, label = "eyeOpen")
    val pupilX by animateFloatAsState(target.pupilOffsetX, spec, label = "pupilX")
    val pupilY by animateFloatAsState(target.pupilOffsetY, spec, label = "pupilY")
    val pupilScale by animateFloatAsState(target.pupilScale, spec, label = "pupilScale")
    val leftBrow by animateFloatAsState(target.leftBrowAngle, spec, label = "leftBrow")
    val rightBrow by animateFloatAsState(target.rightBrowAngle, spec, label = "rightBrow")
    val browHeight by animateFloatAsState(target.browHeightOffset, spec, label = "browHeight")
    val mouthCurve by animateFloatAsState(target.mouthCurve, spec, label = "mouthCurve")
    val mouthWidth by animateFloatAsState(target.mouthWidthScale, spec, label = "mouthWidth")
    val cheekBlush by animateFloatAsState(target.cheekBlushAlpha, tween(350), label = "blush")

    val smoothMouthOpen by animateFloatAsState(
        targetValue = when {
            appState == AppState.SPEAKING -> rawMouthOpenness
            emotion == Emotion.LAUGH -> 0.55f
            emotion == Emotion.SING -> 0.45f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 40, easing = LinearOutSlowInEasing),
        label = "mouthOpen"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "choreography")

    // Fast rhythmic oscillator for DANCE, SING, and LAUGH
    val fastWave by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fastWave"
    )

    val slowWave by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slowWave"
    )

    val effectPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "effectPhase"
    )

    val headBobY = when (emotion) {
        Emotion.DANCE -> fastWave * 22f
        Emotion.LAUGH -> fastWave * 18f
        Emotion.SING -> slowWave * 10f
        else -> slowWave * 5f
    }

    val headSwayX = when (emotion) {
        Emotion.DANCE -> slowWave * 28f
        Emotion.SING -> slowWave * 14f
        else -> 0f
    }

    val headTiltDeg = when (emotion) {
        Emotion.DANCE -> slowWave * 14f
        Emotion.SING -> slowWave * 8f
        Emotion.LAUGH -> fastWave * 4f
        else -> 0f
    }

    return AnimatedFaceState(
        eyeOpenness = baseEyeOpen * blinkMultiplier,
        pupilOffsetX = if (emotion == Emotion.DANCE) slowWave * 0.5f else pupilX,
        pupilOffsetY = pupilY,
        pupilScale = pupilScale,
        leftBrowAngle = if (emotion == Emotion.DANCE) leftBrow + fastWave * 10f else leftBrow,
        rightBrowAngle = if (emotion == Emotion.DANCE) rightBrow - fastWave * 10f else rightBrow,
        browHeightOffset = browHeight,
        mouthCurve = mouthCurve,
        mouthWidthScale = mouthWidth,
        cheekBlushAlpha = cheekBlush,
        mouthOpenness = smoothMouthOpen,
        headBobY = headBobY,
        headSwayX = headSwayX,
        headTiltDeg = headTiltDeg,
        effectPhase = effectPhase
    )
}
