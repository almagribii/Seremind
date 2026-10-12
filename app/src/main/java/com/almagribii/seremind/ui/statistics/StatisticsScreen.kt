package com.almagribii.seremind.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error
    )

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali") }
            Text("Statistik", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        PeriodSelector(state.filterPeriod, viewModel::setFilterPeriod)
        StatisticsCard("Pembukaan per Jam") {
            HourChart(state.hourly)
        }
        StatisticsCard("Alasan Membuka") {
            if (state.reasons.isEmpty()) {
                EmptyChart("Belum ada alasan yang tercatat pada periode ini.")
            } else {
                ReasonChart(state.reasons, colors)
            }
        }
        StatisticsCard("Tren") {
            val trend = state.trendPercent
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        trend == null -> "Belum cukup data untuk membandingkan tren."
                        trend < 0 -> "${-trend}% lebih sedikit dibanding periode sebelumnya."
                        trend > 0 -> "$trend% lebih banyak dibanding periode sebelumnya."
                        else -> "Jumlah pembukaan sama dengan periode sebelumnya."
                    }
                )
            }
        }
        StatisticsCard("Pola Menarik") {
            val peak = state.peakHour
            val reason = state.reasons.firstOrNull()?.reason
            Text(
                when {
                    peak == null -> "Belum ada pola yang dapat dianalisis pada periode ini."
                    reason == null -> "Pembukaan paling sering terjadi sekitar pukul $peak:00."
                    else -> "Pembukaan paling sering terjadi sekitar pukul $peak:00, dengan alasan terbanyak \"$reason\"."
                },
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun PeriodSelector(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp)
    ) {
        listOf("Harian", "Mingguan", "Bulanan").forEach { period ->
            val active = selected == period
            TextButton(
                onClick = { onSelect(period) },
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            ) { Text(period, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun StatisticsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun HourChart(hourly: List<com.almagribii.seremind.data.local.dao.HourCount>) {
    val values = (0..23).map { hour ->
        hourly.firstOrNull { it.hour.toIntOrNull() == hour }?.count ?: 0
    }
    val max = values.maxOrNull() ?: 0
    if (max == 0) {
        EmptyChart("Belum ada pembukaan yang tercatat pada periode ini.")
        return
    }
    Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        values.forEachIndexed { index, value ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom) {
                Box(
                    Modifier.fillMaxWidth().height((value.toFloat() / max * 105f).coerceAtLeast(3f).dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                if (index % 6 == 0) Text("$index", fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun ReasonChart(
    reasons: List<com.almagribii.seremind.data.local.dao.ReasonCount>,
    colors: List<Color>
) {
    val total = reasons.sumOf { it.count }.toFloat()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(120.dp)) {
            var start = -90f
            reasons.forEachIndexed { index, reason ->
                val sweep = reason.count / total * 360f
                drawArc(colors[index % colors.size], start, sweep, false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 28.dp.toPx()))
                start += sweep
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            reasons.forEachIndexed { index, reason ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colors[index % colors.size]))
                    Spacer(Modifier.width(6.dp))
                    Text("${reason.reason}: ${reason.count}", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptyChart(message: String) {
    Text(message, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
}
