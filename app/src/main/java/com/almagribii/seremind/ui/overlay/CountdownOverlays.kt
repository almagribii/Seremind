package com.almagribii.seremind.ui.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun CountdownBadge(
    remainingSeconds: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier
) {
    val pulse by rememberInfiniteTransition(label = "countdown-pulse").animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "countdown-alpha"
    )
    val progress by animateFloatAsState(
        targetValue = (remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1))
            .coerceIn(0f, 1f),
        animationSpec = tween(500),
        label = "countdown-progress"
    )

    Surface(
        modifier = modifier.alpha(pulse),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(22.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
            Text(
                text = formatCountdown(remainingSeconds),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

fun formatCountdown(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    return if (safeSeconds >= 60) {
        "%02d:%02d".format(safeSeconds / 60, safeSeconds % 60)
    } else {
        "%02d dtk".format(safeSeconds)
    }
}

@Composable
fun SessionTimerOverlay(
    appName: String,
    durationSeconds: Int
) {
    var remainingSeconds by remember { mutableStateOf(durationSeconds) }

    LaunchedEffect(durationSeconds) {
        var remaining = durationSeconds
        remainingSeconds = remaining
        while (remaining > 0) {
            delay(1000)
            remaining--
            remainingSeconds = remaining
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 42.dp, end = 16.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Sesi $appName",
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            CountdownBadge(
                remainingSeconds = remainingSeconds,
                totalSeconds = durationSeconds.coerceAtLeast(1)
            )
        }
    }
}
