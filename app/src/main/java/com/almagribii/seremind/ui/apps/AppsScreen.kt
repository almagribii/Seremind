package com.almagribii.seremind.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almagribii.seremind.data.local.entity.AppTarget
import com.almagribii.seremind.ui.theme.LightMint
import com.almagribii.seremind.util.AppIcon

private val categoryOrder = listOf("Media Sosial", "Video", "Game", "Lainnya")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(viewModel: AppsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }
    var pickerSearchQuery by remember { mutableStateOf("") }

    val filteredTargets = uiState.targets.filter {
        it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
    }
    val groupedTargets = filteredTargets.groupBy { appCategory(it) }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            AppsHeader()
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).offset(y = (-20).dp),
                placeholder = { Text("Cari aplikasi...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                shape = RoundedCornerShape(50.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .18f)
                ),
                singleLine = true
            )

            if (filteredTargets.isEmpty()) {
                EmptyTargets()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    categoryOrder.forEach { category ->
                        val targets = groupedTargets[category].orEmpty()
                        if (targets.isNotEmpty()) {
                            item {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                )
                            }
                            items(targets, key = { it.id }) { target ->
                                AppTargetRow(
                                    target = target,
                                    onToggle = { viewModel.toggleTargetActive(target, it) }
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAppPicker = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Tambah aplikasi", modifier = Modifier.size(28.dp))
        }
    }

    if (showAppPicker) {
        AppPickerSheet(
            installedApps = uiState.installedApps,
            targets = uiState.targets,
            searchQuery = pickerSearchQuery,
            onSearchChange = { pickerSearchQuery = it },
            onDismiss = { showAppPicker = false },
            onAdd = { packageName, appName ->
                viewModel.addTarget(packageName, appName)
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun AppsHeader() {
    Surface(color = MaterialTheme.colorScheme.primary, tonalElevation = 0.dp) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 28.dp, top = 24.dp, bottom = 38.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("Aplikasi", color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Kelola aplikasi yang dijeda", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f),
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AppTargetRow(target: AppTarget, onToggle: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = LightMint.copy(alpha = .22f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = target.packageName,
                appName = target.appName,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(target.appName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Jeda: ${target.delaySeconds} detik",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f),
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = target.isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = .45f)
                )
            )
        }
    }
}

@Composable
private fun EmptyTargets() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Belum ada aplikasi target", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Tekan tombol + untuk memilih aplikasi yang ingin diberi jeda reflektif.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    installedApps: List<com.almagribii.seremind.util.InstalledAppInfo>,
    targets: List<AppTarget>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Pilih aplikasi target", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari aplikasi terinstal...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(50.dp),
                singleLine = true
            )
            val apps = installedApps.filter {
                it.appName.contains(searchQuery, true) || it.packageName.contains(searchQuery, true)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    val added = targets.any { it.packageName == app.packageName }
                    ListItem(
                        leadingContent = {
                            AppIcon(
                                packageName = app.packageName,
                                appName = app.appName,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                                drawable = app.icon
                            )
                        },
                        headlineContent = { Text(app.appName, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(app.packageName, fontSize = 11.sp) },
                        trailingContent = {
                            if (added) Text("Ditambahkan", color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            else TextButton(onClick = { onAdd(app.packageName, app.appName) }) { Text("Tambah") }
                        }
                    )
                }
            }
        }
    }
}

private fun appCategory(target: AppTarget): String {
    val value = "${target.packageName} ${target.appName}".lowercase()
    return when {
        listOf("instagram", "tiktok", "twitter", "facebook", "whatsapp", "telegram").any(value::contains) ->
            "Media Sosial"
        listOf("youtube", "netflix", "vidio", "spotify", "twitch").any(value::contains) ->
            "Video"
        listOf("mobile legends", "pubg", "game", "genshin", "free fire", "minecraft").any(value::contains) ->
            "Game"
        else -> "Lainnya"
    }
}
