package com.example.companion.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.companion.util.StageMetrics
import java.util.Locale

@Composable
fun FaceScreen(
    appState: AppState,
    emotion: Emotion,
    mouthOpenness: Float,
    isReady: Boolean,
    lastHeardText: String,
    lastReplyText: String,
    metrics: StageMetrics,
    unansweredCount: Int,
    secondsUntilNextInit: Long,
    onToggleTalk: () -> Unit,
    onTriggerIdleNow: () -> Unit = {},
    onSelectEmotionPreview: (Emotion) -> Unit = {}
) {
    val anim = rememberAnimatedFaceState(
        emotion = emotion,
        rawMouthOpenness = mouthOpenness,
        appState = appState
    )

    val bgBrush = Brush.verticalGradient(
        colors = when {
            emotion == Emotion.DANCE -> listOf(Color(0xFF701A75), Color(0xFF1E1B4B), Color(0xFF0F172A))
            emotion == Emotion.SING -> listOf(Color(0xFF0F766E), Color(0xFF1E1B4B), Color(0xFF0F172A))
            emotion == Emotion.LAUGH -> listOf(Color(0xFF854D0E), Color(0xFF1E293B), Color(0xFF0F172A))
            appState == AppState.LISTENING -> listOf(Color(0xFF064E3B), Color(0xFF0F172A))
            appState == AppState.THINKING -> listOf(Color(0xFF3B0764), Color(0xFF0F172A))
            appState == AppState.SPEAKING -> listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
            appState == AppState.INITIATING -> listOf(Color(0xFF4C1D95), Color(0xFF0F172A))
            else -> listOf(Color(0xFF1E293B), Color(0xFF0F172A))
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .clickable(enabled = isReady) { onToggleTalk() }
            .padding(16.dp)
    ) {
        // Top Header + Action/Emotion Preview Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val idleStatus = if (secondsUntilNextInit < 0L) {
                "Idle Chat: Paused (3/3 Ignored)"
            } else {
                "Next Idle Chat: ${secondsUntilNextInit}s (Ignored: $unansweredCount/3)"
            }
            Surface(
                color = Color(0xFF0F172A).copy(alpha = 0.85f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "${appState.name} • [${emotion.tag}] • $idleStatus",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Emotion.entries.forEach { item ->
                    FilterChip(
                        selected = (emotion == item),
                        onClick = { onSelectEmotionPreview(item) },
                        label = { Text(item.tag, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFACC15),
                            selectedLabelColor = Color(0xFF0F172A),
                            labelColor = Color.White
                        )
                    )
                }
            }
        }

        // Center Full Cartoon Face Canvas with Dance, Sing & Laugh Choreography
        Canvas(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.Center)
        ) {
            val r = size.minDimension * 0.43f
            val center = Offset(
                (size.width / 2f) + anim.headSwayX,
                (size.height / 2f) + anim.headBobY
            )

            // Floor shadow (moves with dance sway)
            drawOval(
                color = Color.Black.copy(alpha = 0.30f),
                topLeft = Offset(center.x - r * 0.65f, (size.height / 2f) + r * 0.98f),
                size = Size(r * 1.3f, r * 0.18f)
            )

            // Party rings / musical notes around head during DANCE or SING
            if (emotion == Emotion.DANCE || emotion == Emotion.SING) {
                val noteYOffset = anim.effectPhase * r * 0.8f
                val alpha = (1f - anim.effectPhase).coerceIn(0f, 1f)
                val ringColor = if (emotion == Emotion.DANCE) Color(0xFFF472B6) else Color(0xFF38BDF8)
                drawCircle(
                    color = ringColor.copy(alpha = alpha * 0.55f),
                    radius = r * (1.05f + anim.effectPhase * 0.25f),
                    center = center,
                    style = Stroke(width = 6f)
                )
                // Floating musical note orbs on left and right
                drawCircle(
                    color = Color(0xFFFDE047).copy(alpha = alpha),
                    radius = r * 0.08f,
                    center = Offset(center.x - r * 1.05f, center.y - noteYOffset)
                )
                drawCircle(
                    color = Color(0xFF34D399).copy(alpha = alpha),
                    radius = r * 0.08f,
                    center = Offset(center.x + r * 1.05f, center.y - r * 0.2f - noteYOffset)
                )
            }

            rotate(degrees = anim.headTiltDeg, pivot = center) {
                // Main Cartoon Head
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFDE047), Color(0xFFFACC15), Color(0xFFEAB308)),
                        center = Offset(center.x - r * 0.25f, center.y - r * 0.25f),
                        radius = r * 1.35f
                    ),
                    radius = r,
                    center = center
                )
                drawCircle(
                    color = Color(0xFF713F12),
                    radius = r,
                    center = center,
                    style = Stroke(width = 7f)
                )

                // Rosy Cheeks
                val cheekY = center.y + r * 0.12f
                val cheekOffsetX = r * 0.54f
                drawOval(
                    color = Color(0xFFF43F5E).copy(alpha = anim.cheekBlushAlpha),
                    topLeft = Offset(center.x - cheekOffsetX - r * 0.16f, cheekY - r * 0.09f),
                    size = Size(r * 0.32f, r * 0.18f)
                )
                drawOval(
                    color = Color(0xFFF43F5E).copy(alpha = anim.cheekBlushAlpha),
                    topLeft = Offset(center.x + cheekOffsetX - r * 0.16f, cheekY - r * 0.09f),
                    size = Size(r * 0.32f, r * 0.18f)
                )

                // Eyes
                val eyeSpacingX = r * 0.36f
                val eyeCenterY = center.y - r * 0.14f
                val eyeRadiusX = r * 0.17f
                val eyeRadiusY = (r * 0.21f * anim.eyeOpenness).coerceAtLeast(r * 0.015f)

                for (side in listOf(-1f, 1f)) {
                    val ex = center.x + side * eyeSpacingX
                    val ey = eyeCenterY

                    if (emotion == Emotion.LAUGH) {
                        // Happy squinting crescent eyes for LAUGH!
                        val laughEyePath = Path().apply {
                            moveTo(ex - eyeRadiusX, ey + r * 0.04f)
                            quadraticTo(ex, ey - r * 0.14f, ex + eyeRadiusX, ey + r * 0.04f)
                        }
                        drawPath(
                            laughEyePath,
                            color = Color(0xFF1E293B),
                            style = Stroke(width = 10f, cap = StrokeCap.Round)
                        )
                    } else if (anim.eyeOpenness <= 0.12f) {
                        drawLine(
                            color = Color(0xFF1E293B),
                            start = Offset(ex - eyeRadiusX, ey),
                            end = Offset(ex + eyeRadiusX, ey),
                            strokeWidth = 9f,
                            cap = StrokeCap.Round
                        )
                    } else {
                        drawOval(
                            color = Color.White,
                            topLeft = Offset(ex - eyeRadiusX, ey - eyeRadiusY),
                            size = Size(eyeRadiusX * 2f, eyeRadiusY * 2f)
                        )
                        drawOval(
                            color = Color(0xFF1E293B),
                            topLeft = Offset(ex - eyeRadiusX, ey - eyeRadiusY),
                            size = Size(eyeRadiusX * 2f, eyeRadiusY * 2f),
                            style = Stroke(width = 5f)
                        )

                        val maxShiftX = eyeRadiusX * 0.38f
                        val maxShiftY = eyeRadiusY * 0.38f
                        val px = ex + anim.pupilOffsetX * maxShiftX
                        val py = ey + anim.pupilOffsetY * maxShiftY
                        val pRad = (r * 0.105f * anim.pupilScale).coerceAtMost(eyeRadiusY * 0.92f)

                        drawCircle(color = Color(0xFF0F172A), radius = pRad, center = Offset(px, py))
                        drawCircle(
                            color = Color.White,
                            radius = pRad * 0.34f,
                            center = Offset(px - pRad * 0.30f, py - pRad * 0.30f)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.8f),
                            radius = pRad * 0.16f,
                            center = Offset(px + pRad * 0.28f, py + pRad * 0.28f)
                        )
                    }
                }

                // Eyebrows
                val browBaseY = eyeCenterY - r * 0.28f - (anim.browHeightOffset * r * 0.09f)
                val browHalfWidth = r * 0.18f

                val leftBrowCenter = Offset(center.x - eyeSpacingX, browBaseY)
                rotate(degrees = anim.leftBrowAngle, pivot = leftBrowCenter) {
                    val p = Path().apply {
                        moveTo(leftBrowCenter.x - browHalfWidth, leftBrowCenter.y + r * 0.02f)
                        quadraticTo(
                            leftBrowCenter.x,
                            leftBrowCenter.y - r * 0.06f,
                            leftBrowCenter.x + browHalfWidth,
                            leftBrowCenter.y + r * 0.02f
                        )
                    }
                    drawPath(p, color = Color(0xFF451A03), style = Stroke(width = 11f, cap = StrokeCap.Round))
                }

                val rightBrowCenter = Offset(center.x + eyeSpacingX, browBaseY)
                rotate(degrees = anim.rightBrowAngle, pivot = rightBrowCenter) {
                    val p = Path().apply {
                        moveTo(rightBrowCenter.x - browHalfWidth, rightBrowCenter.y + r * 0.02f)
                        quadraticTo(
                            rightBrowCenter.x,
                            rightBrowCenter.y - r * 0.06f,
                            rightBrowCenter.x + browHalfWidth,
                            rightBrowCenter.y + r * 0.02f
                        )
                    }
                    drawPath(p, color = Color(0xFF451A03), style = Stroke(width = 11f, cap = StrokeCap.Round))
                }

                // Mouth
                val mouthY = center.y + r * 0.34f
                val mHalfW = r * 0.36f * anim.mouthWidthScale
                val curveOffset = anim.mouthCurve * r * 0.24f
                val openPixels = (anim.mouthOpenness * r * 0.42f) +
                    (if (emotion == Emotion.SURPRISED) r * 0.18f else 0f)

                if (openPixels < r * 0.04f) {
                    val mouthPath = Path().apply {
                        moveTo(center.x - mHalfW, mouthY - curveOffset * 0.25f)
                        quadraticTo(center.x, mouthY + curveOffset, center.x + mHalfW, mouthY - curveOffset * 0.25f)
                    }
                    drawPath(mouthPath, color = Color(0xFF1E293B), style = Stroke(width = 10f, cap = StrokeCap.Round))
                } else {
                    val topCurveY = mouthY + (curveOffset * 0.2f) - openPixels * 0.30f
                    val bottomCurveY = mouthY + (curveOffset * 0.6f) + openPixels * 0.85f
                    val cornerY = mouthY - curveOffset * 0.15f

                    val cavityPath = Path().apply {
                        moveTo(center.x - mHalfW, cornerY)
                        quadraticTo(center.x, topCurveY, center.x + mHalfW, cornerY)
                        quadraticTo(center.x, bottomCurveY, center.x - mHalfW, cornerY)
                        close()
                    }
                    drawPath(path = cavityPath, color = Color(0xFF450A0A))
                    clipPath(cavityPath) {
                        drawOval(
                            color = Color(0xFFFB7185),
                            topLeft = Offset(center.x - mHalfW * 0.65f, mouthY + openPixels * 0.10f),
                            size = Size(mHalfW * 1.3f, openPixels * 0.95f)
                        )
                    }
                    drawPath(cavityPath, color = Color(0xFF1E293B), style = Stroke(width = 8f, cap = StrokeCap.Round))
                }
            }
        }

        // Bottom Controls Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.94f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "You said: '$lastHeardText'",
                    color = Color(0xFF4ADE80),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (lastReplyText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pip: '$lastReplyText'",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                if (metrics.endOfSpeechToFirstSoundMs > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(
                            Locale.US,
                            "E2E: %d ms (STT %d | TTFT %d | TTS %d) • %.1f tok/s • %d MB",
                            metrics.endOfSpeechToFirstSoundMs,
                            metrics.sttMs,
                            metrics.llmTtftMs,
                            metrics.firstTtsMs,
                            metrics.tokensPerSec,
                            metrics.ramMb
                        ),
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onToggleTalk,
                        enabled = isReady,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (appState == AppState.LISTENING) Color(0xFFEF4444) else Color(0xFF3B82F6)
                        ),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Text(
                            when (appState) {
                                AppState.LISTENING -> "Finish Speaking"
                                AppState.THINKING -> "Thinking..."
                                AppState.SPEAKING, AppState.INITIATING -> "Interrupt & Talk"
                                else -> if (isReady) "Talk to Pip" else "Loading..."
                            },
                            fontSize = 13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onTriggerIdleNow,
                        enabled = isReady && appState == AppState.IDLE,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Test Idle Trigger", color = Color(0xFFFACC15), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
