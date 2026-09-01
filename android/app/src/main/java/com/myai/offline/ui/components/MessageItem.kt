package com.myai.offline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myai.offline.actions.ActionParser
import com.myai.offline.data.database.MessageEntity
import com.myai.offline.data.model.AssistantAction
import com.myai.offline.data.model.InferenceMetrics
import com.myai.offline.ui.theme.AccentTeal
import com.myai.offline.ui.theme.AssistantBubbleBg
import com.myai.offline.ui.theme.BorderSubtle
import com.myai.offline.ui.theme.PrimaryIndigo
import com.myai.offline.ui.theme.TextMuted
import com.myai.offline.ui.theme.TextPrimary
import com.myai.offline.ui.theme.TextSecondary
import com.myai.offline.ui.theme.UserBubbleBg
import org.json.JSONObject

@Composable
fun MessageItem(
    message: MessageEntity,
    isSpeakingThis: Boolean,
    onSpeakClick: (String) -> Unit,
    onStopSpeakClick: () -> Unit,
    onActionConfirm: (AssistantAction) -> Unit,
    onActionCancel: (AssistantAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val parseResult = if (!isUser) ActionParser.parse(message.content) else null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            if (!isUser) {
                // AI Avatar
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(PrimaryIndigo.copy(alpha = 0.15f))
                        .border(1.dp, PrimaryIndigo.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "AI",
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f, fill = false)) {
                // Bubble Box
                val shape = if (isUser) {
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
                } else {
                    RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                }

                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(if (isUser) UserBubbleBg else AssistantBubbleBg)
                        .border(1.dp, if (isUser) Color.Transparent else BorderSubtle, shape)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column {
                        if (isUser && message.isVoiceInput) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Input",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Voice Transcript",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        val displayText = if (!isUser && parseResult != null) {
                            parseResult.cleanText.ifBlank { "Executing assistant command..." }
                        } else {
                            message.content
                        }

                        Text(
                            text = displayText,
                            color = if (isUser) Color.White else TextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Render Action Card if present
                if (!isUser && parseResult != null && parseResult.hasAction && parseResult.action != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ActionCard(
                        action = parseResult.action,
                        onConfirm = onActionConfirm,
                        onCancel = onActionCancel
                    )
                }

                // AI Footer Controls (TTS & Performance)
                if (!isUser) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (isSpeakingThis) onStopSpeakClick() else onSpeakClick(message.content)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isSpeakingThis) Icons.Default.Stop else Icons.Default.VolumeUp,
                                contentDescription = "TTS",
                                tint = if (isSpeakingThis) AccentTeal else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Parse metrics if available
                        if (message.metricsJson != null) {
                            val metrics = parseMetricsJson(message.metricsJson)
                            if (metrics != null) {
                                PerformanceBadge(metrics = metrics)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseMetricsJson(jsonStr: String): InferenceMetrics? {
    return try {
        val json = JSONObject(jsonStr)
        InferenceMetrics(
            timeToFirstTokenMs = json.optLong("timeToFirstTokenMs", 0L),
            tokensPerSec = json.optDouble("tokensPerSec", 0.0),
            totalTokens = json.optInt("totalTokens", 0),
            totalGenTimeMs = json.optLong("totalGenTimeMs", 0L)
        )
    } catch (e: Exception) {
        null
    }
}
