package com.myai.offline.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myai.offline.data.database.MessageEntity
import com.myai.offline.data.model.AssistantAction
import com.myai.offline.data.model.ModelId
import com.myai.offline.data.model.ModelInfo
import com.myai.offline.data.model.VoiceState
import com.myai.offline.ui.components.MessageItem
import com.myai.offline.ui.components.ModelSelectorSheet
import com.myai.offline.ui.components.VoiceOverlay
import com.myai.offline.ui.theme.AccentAmber
import com.myai.offline.ui.theme.AccentTeal
import com.myai.offline.ui.theme.BgDark
import com.myai.offline.ui.theme.BorderSubtle
import com.myai.offline.ui.theme.PrimaryIndigo
import com.myai.offline.ui.theme.SurfaceCard
import com.myai.offline.ui.theme.SurfaceDark
import com.myai.offline.ui.theme.TextMuted
import com.myai.offline.ui.theme.TextPrimary
import com.myai.offline.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    messages: List<MessageEntity>,
    streamingMessage: String,
    isGenerating: Boolean,
    models: List<ModelInfo>,
    selectedModelId: ModelId,
    voiceState: VoiceState,
    voiceTranscript: String,
    audioLevel: Float,
    isSpeaking: Boolean,
    currentlySpeakingMessageId: String?,
    drawerState: DrawerState,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onSelectModel: (ModelId) -> Unit,
    onOpenModelManager: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onSpeakMessage: (String, String) -> Unit,
    onStopSpeaking: () -> Unit,
    onActionConfirm: (AssistantAction) -> Unit,
    onActionCancel: (AssistantAction) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var showModelSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val currentModel = models.firstOrNull { it.id == selectedModelId }

    // Scroll to bottom when new messages arrive or while streaming
    LaunchedEffect(messages.size, streamingMessage) {
        if (messages.isNotEmpty() || streamingMessage.isNotEmpty()) {
            val totalItems = messages.size + if (streamingMessage.isNotEmpty()) 1 else 0
            if (totalItems > 0) {
                listState.animateScrollToItem(totalItems - 1)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = BgDark,
            topBar = {
                // Custom Sleek App Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextPrimary)
                        }

                        // Model Chip Selector
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(SurfaceCard)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                                .clickable { showModelSheet = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ElectricBolt,
                                    contentDescription = null,
                                    tint = PrimaryIndigo,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentModel?.name ?: "Qwen3 1.7B",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Offline Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentTeal.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Offline Guarantee",
                            tint = AccentTeal,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Offline",
                            color = AccentTeal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            bottomBar = {
                // Bottom Input Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(SurfaceCard)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(24.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mic Button
                        IconButton(
                            onClick = onStartVoice,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = AccentTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Text Field
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(PrimaryIndigo),
                            decorationBox = { innerTextField ->
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = "Ask anything or command actions...",
                                        color = TextMuted,
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        // Send / Stop button
                        if (isGenerating) {
                            IconButton(
                                onClick = onStopGeneration,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(AccentAmber)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        onSendMessage(inputText)
                                        inputText = ""
                                    }
                                },
                                enabled = inputText.isNotBlank(),
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (inputText.isNotBlank()) PrimaryIndigo else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Send",
                                    tint = if (inputText.isNotBlank()) Color.White else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (messages.isEmpty() && streamingMessage.isEmpty()) {
                    // Empty State with Starter Prompts
                    EmptyChatSuggestions(
                        onSelectSuggestion = { suggestion ->
                            onSendMessage(suggestion)
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageItem(
                                message = message,
                                isSpeakingThis = isSpeaking && currentlySpeakingMessageId == message.id,
                                onSpeakClick = { content -> onSpeakMessage(message.id, content) },
                                onStopSpeakClick = onStopSpeaking,
                                onActionConfirm = onActionConfirm,
                                onActionCancel = onActionCancel
                            )
                        }

                        // Active streaming token chunk
                        if (streamingMessage.isNotEmpty()) {
                            item {
                                MessageItem(
                                    message = MessageEntity(
                                        id = "streaming_temp",
                                        conversationId = "",
                                        role = "assistant",
                                        content = streamingMessage
                                    ),
                                    isSpeakingThis = false,
                                    onSpeakClick = {},
                                    onStopSpeakClick = {},
                                    onActionConfirm = {},
                                    onActionCancel = {}
                                )
                            }
                        }
                    }
                }
            }
        }

        // Model Selector Modal Bottom Sheet
        if (showModelSheet) {
            ModelSelectorSheet(
                models = models,
                selectedModelId = selectedModelId,
                onSelectModel = onSelectModel,
                onOpenModelManager = onOpenModelManager,
                onDismiss = { showModelSheet = false }
            )
        }

        // Voice Overlay Stage
        if (voiceState != VoiceState.IDLE) {
            VoiceOverlay(
                voiceState = voiceState,
                transcript = voiceTranscript,
                audioLevel = audioLevel,
                onStopListening = onStopVoice,
                onCancel = onCancelVoice
            )
        }
    }
}

@Composable
private fun EmptyChatSuggestions(
    onSelectSuggestion: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(PrimaryIndigo.copy(alpha = 0.15f))
                .border(1.dp, PrimaryIndigo.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = PrimaryIndigo,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to MyAI",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Private on-device intelligence powered by local GGUF models",
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        val suggestions = listOf(
            "What is an Operating System?",
            "Open YouTube and search Telugu songs",
            "Open Android Settings",
            "నమస్కారం! Telugu offline chat"
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            suggestions.forEach { prompt ->
                val shape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(SurfaceCard)
                        .border(1.dp, BorderSubtle, shape)
                        .clickable { onSelectSuggestion(prompt) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = prompt,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
