package com.myai.offline.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myai.offline.data.model.VoiceState
import com.myai.offline.ui.theme.AccentTeal
import com.myai.offline.ui.theme.BgDark
import com.myai.offline.ui.theme.BorderSubtle
import com.myai.offline.ui.theme.PrimaryIndigo
import com.myai.offline.ui.theme.SurfaceCard
import com.myai.offline.ui.theme.TextPrimary
import com.myai.offline.ui.theme.TextSecondary

@Composable
fun VoiceOverlay(
    voiceState: VoiceState,
    transcript: String,
    audioLevel: Float,
    onStopListening: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark.copy(alpha = 0.94f))
            .padding(24.dp)
    ) {
        // Top close button
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .clip(CircleShape)
                .background(SurfaceCard)
                .border(1.dp, BorderSubtle, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Voice Stage",
                tint = TextSecondary
            )
        }

        // Center Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
        ) {
            // Pulse rings
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                // Outer ambient ring
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .scale(if (voiceState == VoiceState.LISTENING) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(PrimaryIndigo.copy(alpha = 0.12f))
                )

                // Middle ring
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(1f + (audioLevel * 0.4f))
                        .clip(CircleShape)
                        .background(AccentTeal.copy(alpha = 0.2f))
                )

                // Inner core button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (voiceState == VoiceState.LISTENING) AccentTeal else PrimaryIndigo)
                        .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (voiceState == VoiceState.SPEAKING) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = "Microphone",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // State title
            val statusText = when (voiceState) {
                VoiceState.LISTENING -> "🎤 Listening..."
                VoiceState.TRANSCRIBING -> "Transcribing speech (Whisper)..."
                VoiceState.THINKING -> "Thinking with Qwen3..."
                VoiceState.SPEAKING -> "Speaking response..."
                VoiceState.ACTION_EXECUTING -> "Executing Android Action..."
                VoiceState.ERROR -> "Voice processing error"
                else -> "Voice Assistant Ready"
            }

            Text(
                text = statusText,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Transcript text preview
            if (transcript.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceCard)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "\"$transcript\"",
                        color = AccentTeal,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Text(
                    text = "Try: \"Open YouTube and search Telugu songs\"",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Action stop button if listening
            if (voiceState == VoiceState.LISTENING) {
                IconButton(
                    onClick = onStopListening,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(PrimaryIndigo)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
