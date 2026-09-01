package com.myai.offline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myai.offline.data.model.AssistantAction
import com.myai.offline.data.model.AssistantActionType
import com.myai.offline.ui.theme.AccentAmber
import com.myai.offline.ui.theme.AccentTeal
import com.myai.offline.ui.theme.BorderSubtle
import com.myai.offline.ui.theme.PrimaryIndigo
import com.myai.offline.ui.theme.SurfaceCard
import com.myai.offline.ui.theme.TextPrimary
import com.myai.offline.ui.theme.TextSecondary

@Composable
fun ActionCard(
    action: AssistantAction,
    onConfirm: (AssistantAction) -> Unit,
    onCancel: (AssistantAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceCard)
            .border(1.dp, if (action.executed) AccentTeal.copy(alpha = 0.4f) else BorderSubtle, shape)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (action.type) {
                    AssistantActionType.OPEN_YOUTUBE, AssistantActionType.SEARCH_YOUTUBE -> Icons.Default.PlayArrow
                    AssistantActionType.OPEN_SETTINGS -> Icons.Default.Settings
                    else -> Icons.Default.Search
                }
                val iconColor = if (action.executed) AccentTeal else PrimaryIndigo

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = action.type.name.replace("_", " "),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            if (action.executed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Executed",
                        tint = AccentTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Executed",
                        color = AccentTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action details
        if (!action.query.isNullOrBlank()) {
            Text(
                text = "Search Query: \"${action.query}\"",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        if (!action.appName.isNullOrBlank()) {
            Text(
                text = "Target App: ${action.appName}",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        if (!action.phoneNumber.isNullOrBlank()) {
            Text(
                text = "Phone Number: ${action.phoneNumber}",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        if (action.resultMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = action.resultMessage,
                color = if (action.executed) AccentTeal else AccentAmber,
                fontSize = 11.sp
            )
        }

        // Confirmation buttons if required and not executed yet
        if (action.requiresConfirmation && !action.executed) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = { onCancel(action) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(action) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Confirm & Launch", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}
