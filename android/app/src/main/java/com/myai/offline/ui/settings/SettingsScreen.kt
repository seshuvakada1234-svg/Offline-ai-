package com.myai.offline.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myai.offline.ui.theme.AccentTeal
import com.myai.offline.ui.theme.BgDark
import com.myai.offline.ui.theme.BorderSubtle
import com.myai.offline.ui.theme.PrimaryIndigo
import com.myai.offline.ui.theme.SurfaceCard
import com.myai.offline.ui.theme.SurfaceDark
import com.myai.offline.ui.theme.TextMuted
import com.myai.offline.ui.theme.TextPrimary
import com.myai.offline.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    deviceRamMb: Long,
    batteryPercent: Int,
    onBack: () -> Unit
) {
    var autoExecuteActions by remember { mutableStateOf(true) }
    var highPrecisionAudio by remember { mutableStateOf(true) }
    var autoReadTts by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings & Privacy", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("On-device Hardware & Controls", color = TextSecondary, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Privacy Card
            item {
                val shape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(AccentTeal.copy(alpha = 0.08f))
                        .border(1.dp, AccentTeal.copy(alpha = 0.3f), shape)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(AccentTeal.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("100% Offline Guarantee", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                            Text(
                                "No internet connection is required or queried. All inference, transcripts, and database storage remain strictly on this device.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // System Hardware
            item {
                val shape = RoundedCornerShape(16.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(SurfaceCard)
                        .border(1.dp, BorderSubtle, shape)
                        .padding(16.dp)
                ) {
                    Text("DEVICE HARDWARE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Active Process RAM", color = TextPrimary, fontSize = 13.sp)
                        }
                        Text("${deviceRamMb} MB Used", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Battery Level", color = TextPrimary, fontSize = 13.sp)
                        }
                        Text("$batteryPercent%", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Language & Telugu Support
            item {
                val shape = RoundedCornerShape(16.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(SurfaceCard)
                        .border(1.dp, BorderSubtle, shape)
                        .padding(16.dp)
                ) {
                    Text("MULTILINGUAL CAPABILITIES", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Telugu Speech & Text", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("Native Whisper STT + Android TTS", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Action Automation Toggles
            item {
                val shape = RoundedCornerShape(16.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(SurfaceCard)
                        .border(1.dp, BorderSubtle, shape)
                        .padding(16.dp)
                ) {
                    Text("ANDROID ACTION AUTOMATION", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
                            Text("Safe Direct Launch", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Auto-launch YouTube/Chrome queries without extra confirmation dialogs", color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = autoExecuteActions,
                            onCheckedChange = { autoExecuteActions = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryIndigo,
                                uncheckedTrackColor = SurfaceDark
                            )
                        )
                    }
                }
            }

            // Version & Engine info
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("MyAI Native Assistant v1.0.0", color = TextMuted, fontSize = 11.sp)
                    Text("Engine: llama.cpp GGUF + whisper.cpp + Jetpack Compose", color = TextMuted.copy(alpha = 0.7f), fontSize = 10.sp)
                }
            }
        }
    }
}
