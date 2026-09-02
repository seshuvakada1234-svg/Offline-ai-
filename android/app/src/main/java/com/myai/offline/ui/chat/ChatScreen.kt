package com.myai.offline.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DrawerState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.myai.offline.data.model.ModelState
import com.myai.offline.data.model.VoiceState
import com.myai.offline.ui.components.MessageItem
import com.myai.offline.ui.components.ModelSelectorSheet
import com.myai.offline.ui.components.VoiceOverlay
import com.myai.offline.ui.theme.AccentAmber
import com.myai.offline.ui.theme.AccentRose
import com.myai.offline.ui.theme.AccentTeal
import com.myai.offline.ui.theme.BgDark
import com.myai.offline.ui.theme.BorderLight
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
    val isModelReady = currentModel?.state == ModelState.READY

    // Auto-scroll when new message arrives or streaming updates
    val totalCount = messages.size + if (isGenerating) 1 else 0
    LaunchedEffect(messages.size, streamingMessage, isGenerating) {
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex < totalCount - 3 && totalCount > 3
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = BgDark,
            topBar = {
                // ChatGPT-style Top Navigation Header
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
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open navigation menu",
                                tint = TextPrimary
                            )
                        }

                        // Model Selector Pill
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
                                    tint = if (isModelReady) PrimaryIndigo else AccentAmber,
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
                                    contentDescription = "Select model",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // On-device Status Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isModelReady) AccentTeal.copy(alpha = 0.12f) else AccentAmber.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Offline On-Device AI",
                            tint = if (isModelReady) AccentTeal else AccentAmber,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isModelReady) "Offline" else "Download needed",
                            color = if (isModelReady) AccentTeal else AccentAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            bottomBar = {
                // ChatGPT-style Bottom Composer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(SurfaceCard)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(26.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Plus / Prompt Suggestions button
                        IconButton(
                            onClick = { showModelSheet = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Attach or select model",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Multiline Text Field
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(PrimaryIndigo),
                            decorationBox = { innerTextField ->
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = if (isGenerating) "Generating response..." else "Ask anything...",
                                        color = TextMuted,
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        // Voice Mic Button
                        IconButton(
                            onClick = onStartVoice,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice input",
                                tint = if (voiceState != VoiceState.IDLE) AccentTeal else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Dynamic Send / Stop Button
                        if (isGenerating) {
                            IconButton(
                                onClick = onStopGeneration,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AccentRose)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop generation",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            val canSend = inputText.isNotBlank()
                            IconButton(
                                onClick = {
                                    if (canSend) {
                                        onSendMessage(inputText)
                                        inputText = ""
                                    }
                                },
                                enabled = canSend,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (canSend) PrimaryIndigo else Color(0xFF2A2A35))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Send message",
                                    tint = if (canSend) Color.White else TextMuted,
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
                if (messages.isEmpty() && !isGenerating) {
                    // Modern Empty State
                    EmptyChatSuggestions(
                        selectedModelName = currentModel?.name ?: "Qwen3 1.7B",
                        onSelectSuggestion = { suggestion ->
                            onSendMessage(suggestion)
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageItem(
                                message = message,
                                isSpeakingThis = isSpeaking && currentlySpeakingMessageId == message.id,
                                modelName = currentModel?.name ?: "Qwen3 1.7B",
                                isThinking = false,
                                onSpeakClick = { content -> onSpeakMessage(message.id, content) },
                                onStopSpeakClick = onStopSpeaking,
                                onActionConfirm = onActionConfirm,
                                onActionCancel = onActionCancel
                            )
                        }

                        // Real Streaming Active Item
                        if (isGenerating) {
                            item(key = "active_streaming_item") {
                                MessageItem(
                                    message = MessageEntity(
                                        id = "streaming_active_id",
                                        conversationId = "",
                                        role = "assistant",
                                        content = streamingMessage
                                    ),
                                    isSpeakingThis = false,
                                    modelName = currentModel?.name ?: "Qwen3 1.7B",
                                    isThinking = streamingMessage.isEmpty(),
                                    onSpeakClick = {},
                                    onStopSpeakClick = {},
                                    onActionConfirm = {},
                                    onActionCancel = {}
                                )
                            }
                        }
                    }
                }

                // Scroll to Bottom Floating Button
                AnimatedVisibility(
                    visible = showScrollToBottom,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                if (totalCount > 0) {
                                    listState.animateScrollToItem(totalCount - 1)
                                }
                            }
                        },
                        containerColor = SurfaceCard,
                        contentColor = TextPrimary,
                        modifier = Modifier
                            .size(36.dp)
                            .border(1.dp, BorderLight, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Scroll to bottom",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Model Selector Bottom Sheet
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
    selectedModelName: String,
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
                .size(54.dp)
                .clip(CircleShape)
                .background(PrimaryIndigo.copy(alpha = 0.15f))
                .border(1.dp, PrimaryIndigo.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = PrimaryIndigo,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to MyAI",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Private on-device intelligence powered by $selectedModelName",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        val suggestions = listOf(
            "What is an Operating System?",
            "Open YouTube and search Telugu songs",
            "Open Android Settings",
            "Explain quantum computing in simple terms"
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            suggestions.forEach { prompt ->
                val shape = RoundedCornerShape(12.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(SurfaceCard)
                        .border(1.dp, BorderSubtle, shape)
                        .clickable { onSelectSuggestion(prompt) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
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
