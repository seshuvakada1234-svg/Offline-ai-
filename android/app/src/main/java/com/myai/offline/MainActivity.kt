package com.myai.offline

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.myai.offline.ui.chat.ChatScreen
import com.myai.offline.ui.history.HistoryDrawer
import com.myai.offline.ui.models.ModelManagerScreen
import com.myai.offline.ui.settings.SettingsScreen
import com.myai.offline.ui.theme.MyAITheme
import com.myai.offline.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle audio & notification permissions
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            MyAITheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                val conversations by viewModel.conversations.collectAsState()
                val currentConvId by viewModel.currentConversationId.collectAsState()
                val messages by viewModel.messages.collectAsState()
                val streamingMessage by viewModel.streamingMessage.collectAsState()
                val isGenerating by viewModel.isGenerating.collectAsState()
                val models by viewModel.models.collectAsState()
                val selectedModelId by viewModel.selectedModelId.collectAsState()
                val voiceState by viewModel.voiceState.collectAsState()
                val voiceTranscript by viewModel.voiceTranscript.collectAsState()
                val audioLevel by viewModel.audioLevel.collectAsState()
                val isSpeaking by viewModel.isTtsSpeaking.collectAsState()
                val currentlySpeakingMessageId by viewModel.currentlySpeakingMessageId.collectAsState()
                val deviceRamMb by viewModel.deviceRamUsageMb.collectAsState()
                val batteryPercent by viewModel.batteryLevel.collectAsState()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        HistoryDrawer(
                            conversations = conversations,
                            activeConversationId = currentConvId,
                            onSelectConversation = { id ->
                                viewModel.selectConversation(id)
                                scope.launch { drawerState.close() }
                            },
                            onNewChat = {
                                viewModel.createNewConversation()
                                scope.launch { drawerState.close() }
                            },
                            onOpenModelManager = {
                                scope.launch { drawerState.close() }
                                navController.navigate("models")
                            },
                            onOpenSettings = {
                                scope.launch { drawerState.close() }
                                navController.navigate("settings")
                            }
                        )
                    }
                ) {
                    NavHost(navController = navController, startDestination = "chat") {
                        composable("chat") {
                            ChatScreen(
                                messages = messages,
                                streamingMessage = streamingMessage,
                                isGenerating = isGenerating,
                                models = models,
                                selectedModelId = selectedModelId,
                                voiceState = voiceState,
                                voiceTranscript = voiceTranscript,
                                audioLevel = audioLevel,
                                isSpeaking = isSpeaking,
                                currentlySpeakingMessageId = currentlySpeakingMessageId,
                                drawerState = drawerState,
                                onSendMessage = { text -> viewModel.sendMessage(text) },
                                onStopGeneration = { viewModel.stopGeneration() },
                                onSelectModel = { id -> viewModel.selectModel(id) },
                                onOpenModelManager = { navController.navigate("models") },
                                onStartVoice = {
                                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                                        == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        viewModel.startVoiceListening()
                                    } else {
                                        requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                    }
                                },
                                onStopVoice = { viewModel.stopVoiceListening() },
                                onCancelVoice = { viewModel.cancelVoice() },
                                onSpeakMessage = { id, text -> viewModel.speakMessage(id, text) },
                                onStopSpeaking = { viewModel.stopSpeaking() },
                                onActionConfirm = { action -> viewModel.executeAction(action) },
                                onActionCancel = { /* No-op */ }
                            )
                        }

                        composable("models") {
                            ModelManagerScreen(
                                models = models,
                                selectedModelId = selectedModelId,
                                onSelectModel = { id -> viewModel.selectModel(id) },
                                onDownloadModel = { id -> viewModel.downloadModel(id) },
                                onDeleteModel = { id -> viewModel.deleteModel(id) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                deviceRamMb = deviceRamMb,
                                batteryPercent = batteryPercent,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            requestPermissionLauncher.launch(needed.toTypedArray())
        }
    }
}
