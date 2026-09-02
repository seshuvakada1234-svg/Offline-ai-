package com.myai.offline.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myai.offline.actions.ActionParser
import com.myai.offline.data.database.MessageEntity
import com.myai.offline.data.model.AssistantAction
import com.myai.offline.data.model.InferenceMetrics
import com.myai.offline.ui.theme.AccentTeal
import com.myai.offline.ui.theme.AssistantBubbleBg
import com.myai.offline.ui.theme.BorderLight
import com.myai.offline.ui.theme.BorderSubtle
import com.myai.offline.ui.theme.PrimaryIndigo
import com.myai.offline.ui.theme.SurfaceCard
import com.myai.offline.ui.theme.TextMuted
import com.myai.offline.ui.theme.TextPrimary
import com.myai.offline.ui.theme.TextSecondary
import com.myai.offline.ui.theme.UserBubbleBg
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun MessageItem(
    message: MessageEntity,
    isSpeakingThis: Boolean,
    modelName: String = "Qwen3 1.7B",
    isThinking: Boolean = false,
    onSpeakClick: (String) -> Unit,
    onStopSpeakClick: () -> Unit,
    onActionConfirm: (AssistantAction) -> Unit,
    onActionCancel: (AssistantAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val parseResult = if (!isUser) ActionParser.parse(message.content) else null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // USER MESSAGE BUBBLE - ALIGNED TO RIGHT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val userBubbleShape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 4.dp
                )

                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(userBubbleShape)
                        .background(UserBubbleBg)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Column {
                        if (message.isVoiceInput) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Input",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Voice Transcript",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Text(
                            text = message.content,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }
                }
            }
        } else {
            // AI ASSISTANT MESSAGE - ALIGNED TO LEFT
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                // Small AI Avatar / Icon
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(PrimaryIndigo.copy(alpha = 0.15f))
                        .border(1.dp, PrimaryIndigo.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "AI Assistant",
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Header with Model Tag
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "MyAI",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceCard)
                                .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = modelName,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Content Container or Thinking indicator
                    if (isThinking) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AssistantBubbleBg)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = PrimaryIndigo,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Thinking...",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    } else {
                        val displayText = if (parseResult != null) {
                            parseResult.cleanText.ifBlank { "Executing assistant command..." }
                        } else {
                            message.content
                        }

                        // Rich Markdown Text
                        MarkdownText(
                            text = displayText,
                            textColor = TextPrimary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                        )

                        // Render Action Card if present
                        if (parseResult != null && parseResult.hasAction && parseResult.action != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ActionCard(
                                action = parseResult.action,
                                onConfirm = onActionConfirm,
                                onCancel = onActionCancel
                            )
                        }

                        // AI Action Bar: [Copy] [Speak] [Performance]
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Copy Action
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceCard.copy(alpha = 0.5f))
                                    .border(1.dp, BorderLight, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("response", displayText)
                                        clipboard.setPrimaryClip(clip)
                                        isCopied = true
                                        Toast.makeText(context, "Copied response", Toast.LENGTH_SHORT).show()
                                        scope.launch {
                                            delay(2000)
                                            isCopied = false
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = "Copy response",
                                        tint = if (isCopied) AccentTeal else TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = if (isCopied) "Copied" else "Copy",
                                    fontSize = 11.sp,
                                    color = if (isCopied) AccentTeal else TextSecondary
                                )
                            }

                            // Speak Action
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceCard.copy(alpha = 0.5f))
                                    .border(1.dp, BorderLight, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isSpeakingThis) onStopSpeakClick() else onSpeakClick(displayText)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSpeakingThis) Icons.Default.Stop else Icons.Default.VolumeUp,
                                        contentDescription = if (isSpeakingThis) "Stop speaking" else "Speak response",
                                        tint = if (isSpeakingThis) AccentTeal else TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = if (isSpeakingThis) "Stop" else "Speak",
                                    fontSize = 11.sp,
                                    color = if (isSpeakingThis) AccentTeal else TextSecondary
                                )
                            }

                            // Performance telemetry badge
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
