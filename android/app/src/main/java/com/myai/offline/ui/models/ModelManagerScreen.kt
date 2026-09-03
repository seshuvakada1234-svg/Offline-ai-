package com.myai.offline.ui.models

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myai.offline.data.model.ModelId
import com.myai.offline.data.model.ModelInfo
import com.myai.offline.data.model.ModelState
import com.myai.offline.ui.theme.AccentAmber
import com.myai.offline.ui.theme.AccentRose
import com.myai.offline.ui.theme.AccentTeal
import com.myai.offline.ui.theme.BgDark
import com.myai.offline.ui.theme.BorderSubtle
import com.myai.offline.ui.theme.PrimaryIndigo
import com.myai.offline.ui.theme.SurfaceCard
import com.myai.offline.ui.theme.SurfaceDark
import com.myai.offline.ui.theme.TextMuted
import com.myai.offline.ui.theme.TextPrimary
import com.myai.offline.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    models: List<ModelInfo>,
    selectedModelId: ModelId,
    onSelectModel: (ModelId) -> Unit,
    onDownloadModel: (ModelId) -> Unit,
    onPauseDownload: (ModelId) -> Unit,
    onResumeDownload: (ModelId) -> Unit,
    onCancelDownload: (ModelId) -> Unit,
    onRetryDownload: (ModelId) -> Unit,
    onDeleteModel: (ModelId) -> Unit,
    onRefreshModels: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GGUF Model Hub", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Real Offline Model Downloads", color = TextSecondary, fontSize = 12.sp)
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshModels) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextPrimary)
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
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text("Hardware Acceleration & Offline Storage", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Models are stored in app-private storage and executed completely offline via ARM NEON & llama.cpp. No cloud APIs, OpenAI, or remote telemetry are ever used for inference.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            items(models, key = { it.id.rawValue }) { model ->
                val isSelected = model.id == selectedModelId
                val isReady = model.state == ModelState.READY
                val isActive = model.state == ModelState.ACTIVE
                val isDownloading = model.state == ModelState.DOWNLOADING
                val isVerifying = model.state == ModelState.VERIFYING
                val isPaused = model.state == ModelState.PAUSED
                val isLoading = model.state == ModelState.LOADING
                val isError = model.state == ModelState.ERROR
                val isInstalled = isReady || isActive || isLoading
                val shape = RoundedCornerShape(16.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(if (isSelected && isInstalled) PrimaryIndigo.copy(alpha = 0.08f) else SurfaceCard)
                        .border(
                            1.dp,
                            if (isSelected && isInstalled) PrimaryIndigo else BorderSubtle,
                            shape
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected && isInstalled) PrimaryIndigo.copy(alpha = 0.2f) else SurfaceDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (model.isDefault) Icons.Default.ElectricBolt else Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = if (isSelected && isInstalled) PrimaryIndigo else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = model.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextPrimary
                                    )
                                    if (model.isDefault) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(PrimaryIndigo.copy(alpha = 0.2f))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Default",
                                                color = PrimaryIndigo,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${model.tag} • ${model.quant} • RAM: ${model.ramRequired}",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        // Size pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceDark)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(model.sizeFormatted, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when (model.state) {
                            ModelState.NOT_INSTALLED -> "Not installed"
                            ModelState.DOWNLOADING -> "Downloading"
                            ModelState.PAUSED -> "Paused"
                            ModelState.VERIFYING -> "Verifying"
                            ModelState.READY -> if (model.isWhisper) "Whisper ready for voice" else "Installed"
                            ModelState.LOADING -> "Loading into llama.cpp"
                            ModelState.ACTIVE -> if (model.isWhisper) "Whisper ready for voice" else "Active model"
                            ModelState.ERROR -> "Error"
                        },
                        color = when (model.state) {
                            ModelState.ERROR -> AccentRose
                            ModelState.ACTIVE -> AccentTeal
                            ModelState.READY -> AccentTeal
                            ModelState.VERIFYING -> AccentTeal
                            ModelState.DOWNLOADING, ModelState.PAUSED -> AccentAmber
                            ModelState.LOADING -> PrimaryIndigo
                            else -> TextMuted
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = model.description,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    // Download Progress Bar & Speed if downloading
                    if (isDownloading || isPaused) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val downloadedMb = model.downloadedBytes / (1024.0 * 1024.0)
                        val totalMb = model.sizeBytes / (1024.0 * 1024.0)
                        val sizeText = if (totalMb > 1024) {
                            String.format(Locale.US, "%.2f GB / %.2f GB", downloadedMb / 1024.0, totalMb / 1024.0)
                        } else {
                            String.format(Locale.US, "%.1f MB / %.1f MB", downloadedMb, totalMb)
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val safeProgress = model.downloadProgress.takeIf { !it.isNaN() && !it.isInfinite() }?.coerceIn(0f, 1f) ?: 0f
                                val progressPercent = (safeProgress * 100).toInt().coerceIn(0, 100)
                                Text(
                                    text = "$sizeText ${model.downloadSpeed?.let { "• $it" } ?: ""}",
                                    fontSize = 11.sp,
                                    color = AccentAmber
                                )
                                Text(
                                    "$progressPercent%",
                                    fontSize = 11.sp,
                                    color = AccentAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = safeProgress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = AccentAmber,
                                trackColor = SurfaceDark
                            )
                            if (isPaused) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Paused", fontSize = 11.sp, color = AccentAmber)
                            }
                        }
                    } else if (isVerifying) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AccentTeal, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verifying GGUF header & checksum...", fontSize = 11.sp, color = AccentTeal)
                        }
                    } else if (isLoading) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = PrimaryIndigo, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Loading model into llama.cpp...", fontSize = 11.sp, color = PrimaryIndigo)
                        }
                    } else if (isError && model.errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRose, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(model.errorMessage, fontSize = 11.sp, color = AccentRose)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            isDownloading -> {
                                OutlinedButton(
                                    onClick = { onPauseDownload(model.id) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pause", fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = { onCancelDownload(model.id) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Cancel", fontSize = 12.sp)
                                }
                            }
                            isPaused -> {
                                Button(
                                    onClick = { onResumeDownload(model.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Resume", fontSize = 12.sp, color = Color.White)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = { onCancelDownload(model.id) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Cancel", fontSize = 12.sp)
                                }
                            }
                            isVerifying || isLoading -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    colors = ButtonDefaults.buttonColors(disabledContainerColor = SurfaceDark),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentAmber, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isVerifying) "Verifying" else "Loading", fontSize = 12.sp, color = TextMuted)
                                }
                            }
                            isInstalled -> {
                                OutlinedButton(
                                    onClick = { onDeleteModel(model.id) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))

                                if (!model.isChatModel) {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        colors = ButtonDefaults.buttonColors(disabledContainerColor = AccentTeal.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Ready for voice", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                } else if (isActive) {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Active Model", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = { onSelectModel(model.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Load & Activate", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            isError -> {
                                if (model.isDownloadable) {
                                    Button(
                                        onClick = { onRetryDownload(model.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry Download", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onDeleteModel(model.id) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete", fontSize = 12.sp)
                                    }
                                }
                            }
                            else -> {
                                if (model.isDownloadable) {
                                    Button(
                                        onClick = { onDownloadModel(model.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Download (${model.sizeFormatted})", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        colors = ButtonDefaults.buttonColors(disabledContainerColor = SurfaceDark),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Sideload required", fontSize = 12.sp, color = TextMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
