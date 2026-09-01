package com.myai.offline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myai.offline.data.model.InferenceMetrics
import com.myai.offline.ui.theme.AccentTeal
import com.myai.offline.ui.theme.BorderLight
import com.myai.offline.ui.theme.SurfaceCard
import com.myai.offline.ui.theme.TextMuted
import com.myai.offline.ui.theme.TextSecondary

@Composable
fun PerformanceBadge(
    metrics: InferenceMetrics,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(shape)
            .background(SurfaceCard.copy(alpha = 0.6f))
            .border(1.dp, BorderLight, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "${metrics.tokensPerSec} t/s",
            color = AccentTeal,
            fontSize = 10.sp
        )
        Text(
            text = "•",
            color = TextMuted,
            fontSize = 10.sp
        )
        Text(
            text = "TTFT: ${metrics.timeToFirstTokenMs}ms",
            color = TextSecondary,
            fontSize = 10.sp
        )
        Text(
            text = "•",
            color = TextMuted,
            fontSize = 10.sp
        )
        Text(
            text = "${metrics.totalGenTimeMs}ms total (${metrics.totalTokens} tokens)",
            color = TextMuted,
            fontSize = 10.sp
        )
    }
}
