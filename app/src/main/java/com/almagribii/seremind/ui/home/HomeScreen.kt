package com.almagribii.seremind.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.almagribii.seremind.util.AppIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToJournal: (String?) -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToStatistics: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val view = LocalView.current
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) { viewModel.loadHomeData() }
    DisposableEffect(view) {
        val owner = view.findViewTreeLifecycleOwner()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.loadHomeData()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    val total = state.openedCount + state.cancelledCount
    val date = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(Date()) }
    val greeting = when {
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) < 12 -> "Selamat pagi,"
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) < 18 -> "Selamat siang,"
        else -> "Selamat malam,"
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Header(greeting, date)

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SummaryCard(total, state.cancelledCount, state.openedCount)

            ShortcutRow(
                onJournal = { onNavigateToJournal(state.aiPrompt) },
                onStatistics = onNavigateToStatistics,
                onApps = onNavigateToApps,
                onArticles = { onNavigateToJournal(null) }
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
                )
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.onPrimary)
                    Text("Refleksi Hari Ini", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    Text(state.aiPrompt, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .9f), lineHeight = 23.sp)
                    OutlinedButton(
                        onClick = { onNavigateToJournal(state.aiPrompt) },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                        border = ButtonDefaults.outlinedButtonBorder
                    ) { Text("Jawab", fontWeight = FontWeight.Bold) }
                }
            }

            Text("Momen Paling Rawan", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(if (state.peakHour == "-") "Belum ada data" else state.peakHour,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (state.peakReason == "-") "Belum ada alasan tercatat"
                            else "Alasan terbanyak: ${state.peakReason}",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)
                        )
                    }
                }
            }

            Text("Aplikasi Target Aktif", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            if (state.activeTargets.isEmpty()) {
                Text("Belum ada aplikasi target. Tambahkan dari menu Aplikasi.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
            } else {
                state.activeTargets.take(3).forEach { target ->
                    AppRankItem(target.packageName, target.appName, "${target.delaySeconds}s")
                }
            }

            PermissionNotice(
                hasUsage = state.hasUsagePermission,
                hasOverlay = state.hasOverlayPermission,
                onUsage = viewModel::requestUsagePermission,
                onOverlay = viewModel::requestOverlayPermission
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Header(greeting: String, date: String) {
    Surface(color = MaterialTheme.colorScheme.primary, tonalElevation = 0.dp) {
        Row(
        Modifier.fillMaxWidth().statusBarsPadding()
            .padding(start = 24.dp, end = 20.dp, top = 28.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(greeting, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f), fontSize = 20.sp)
                Text("Teman Seremind", color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(date.replaceFirstChar { it.uppercase() },
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
            }
            Box(Modifier.size(58.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = .16f)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun SummaryCard(total: Int, cancelled: Int, opened: Int) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$total", fontSize = 54.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("kali kamu melewati gerbang hari ini", modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.titleMedium)
            }
            val safeRatio = if (total == 0) 0f else cancelled.toFloat() / total
            LinearProgressIndicator(
                progress = { safeRatio },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = .35f)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$cancelled dicegah", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("$opened dibuka", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ShortcutRow(
    onJournal: () -> Unit, onStatistics: () -> Unit, onApps: () -> Unit, onArticles: () -> Unit
) {
    val items = listOf(
        Triple(Icons.Default.Star, "Jurnal", onJournal),
        Triple(Icons.Default.Star, "Statistik", onStatistics),
        Triple(Icons.Default.List, "Aplikasi", onApps),
        Triple(Icons.Default.Star, "Artikel", onArticles)
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        items.forEach { (icon, label, action) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp)) {
                IconButton(onClick = action, modifier = Modifier.size(58.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .16f))) {
                    Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
                Text(label, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PermissionNotice(hasUsage: Boolean, hasOverlay: Boolean, onUsage: () -> Unit, onOverlay: () -> Unit) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasUsage && hasOverlay) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (hasUsage && hasOverlay) "Seremind aktif" else "Izin diperlukan",
                fontWeight = FontWeight.Bold,
                color = if (hasUsage && hasOverlay) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
            PermissionStatusRow(
                label = "Akses Penggunaan",
                isGranted = hasUsage,
                onClick = onUsage
            )
            PermissionStatusRow(
                label = "Tampil di Atas App",
                isGranted = hasOverlay,
                onClick = onOverlay
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isGranted) "✓" else "!",
                color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onClick) {
            Text(if (isGranted) "Kelola" else "Aktifkan")
        }
    }
}

@Composable
fun AppRankItem(packageName: String, name: String, delay: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName, name, modifier = Modifier.size(36.dp).clip(CircleShape))
            Text(name, fontWeight = FontWeight.Medium)
        }
        Text("Jeda $delay", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}
