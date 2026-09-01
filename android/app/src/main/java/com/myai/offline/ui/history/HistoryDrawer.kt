package com.myai.offline.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myai.offline.data.database.ConversationEntity
import com.myai.offline.ui.theme.AccentTeal
import com.myai.offline.ui.theme.BorderSubtle
import com.myai.offline.ui.theme.PrimaryIndigo
import com.myai.offline.ui.theme.SurfaceCard
import com.myai.offline.ui.theme.SurfaceDark
import com.myai.offline.ui.theme.TextMuted
import com.myai.offline.ui.theme.TextPrimary
import com.myai.offline.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryDrawer(
    conversations: List<ConversationEntity>,
    activeConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenModelManager: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)

    Column(
        modifier = modifier
            .width(300.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(SurfaceDark)
            .border(1.dp, BorderSubtle, shape)
            .padding(18.dp)
    ) {
        // App Branding
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PrimaryIndigo),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "MyAI Offline",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
                Text(
                    text = "100% Private On-Device",
                    fontSize = 11.sp,
                    color = AccentTeal
                )
            }
        }

        // New Chat Button
        Button(
            onClick = onNewChat,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Conversation", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "HISTORY",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )

        // Conversation items
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(conversations) { conv ->
                val isSelected = conv.id == activeConversationId
                val itemShape = RoundedCornerShape(10.dp)
                val dateStr = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(conv.updatedAt))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(itemShape)
                        .background(if (isSelected) PrimaryIndigo.copy(alpha = 0.15f) else SurfaceCard.copy(alpha = 0.5f))
                        .border(
                            1.dp,
                            if (isSelected) PrimaryIndigo.copy(alpha = 0.4f) else Color.Transparent,
                            itemShape
                        )
                        .clickable { onSelectConversation(conv.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = if (isSelected) PrimaryIndigo else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = conv.title,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = dateStr,
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            color = BorderSubtle,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // Bottom Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpenModelManager() }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Memory, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Model Manager", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpenSettings() }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Settings & Privacy", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
