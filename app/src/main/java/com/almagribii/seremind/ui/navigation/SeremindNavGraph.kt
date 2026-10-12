package com.almagribii.seremind.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.almagribii.seremind.ui.apps.AppsScreen
import com.almagribii.seremind.ui.apps.AppsViewModel
import com.almagribii.seremind.ui.home.HomeScreen
import com.almagribii.seremind.ui.home.HomeViewModel
import com.almagribii.seremind.ui.journal.*
import com.almagribii.seremind.ui.onboarding.OnboardingScreen
import com.almagribii.seremind.ui.onboarding.OnboardingViewModel
import com.almagribii.seremind.ui.overlay.GateOverlay
import com.almagribii.seremind.ui.recovery.RecoveryScreen
import com.almagribii.seremind.ui.recovery.RecoveryViewModel
import com.almagribii.seremind.ui.settings.SettingsScreen
import com.almagribii.seremind.ui.settings.SettingsViewModel
import com.almagribii.seremind.ui.statistics.StatisticsScreen
import com.almagribii.seremind.ui.statistics.StatisticsViewModel

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Beranda", Icons.Default.Home)
    object Statistics : Screen("statistics", "Statistik", Icons.Default.Star)
    object Apps : Screen("apps", "Aplikasi", Icons.Default.List)
    object Journal : Screen("journal", "Jurnal", Icons.Default.Star)
    object Settings : Screen("settings", "Pengaturan", Icons.Default.Settings)
    object Onboarding : Screen("onboarding", "Onboarding", Icons.Default.Home)
    object GateOverlay : Screen("gate_overlay/{packageName}/{appName}/{delaySeconds}", "Overlay", Icons.Default.Home) {
        fun createRoute(packageName: String, appName: String, delaySeconds: Int) = "gate_overlay/$packageName/$appName/$delaySeconds"
    }
    object Recovery : Screen("recovery", "Pemulihan", Icons.Default.Home)
    object JournalEditor : Screen("journal_editor?prompt={prompt}", "Editor Jurnal", Icons.Default.Star) {
        fun createRoute(prompt: String?) = if (prompt != null) "journal_editor?prompt=$prompt" else "journal_editor"
    }
    object JournalDetail : Screen("journal_detail/{entryId}", "Detail Jurnal", Icons.Default.Star) {
        fun createRoute(entryId: Int) = "journal_detail/$entryId"
    }
    object ArticleDetail : Screen("article_detail/{articleId}", "Detail Artikel", Icons.Default.Star) {
        fun createRoute(articleId: Int) = "article_detail/$articleId"
    }
}

@Composable
fun SeremindNavGraph(startDestination: String = Screen.Home.route) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavScreens = listOf(
        Screen.Home,
        Screen.Apps,
        Screen.Journal,
        Screen.Settings
    )

    val showBottomBar = bottomNavScreens.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {}
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
            composable(Screen.Home.route) {
                val vm: HomeViewModel = viewModel()
                HomeScreen(
                    viewModel = vm,
                    onNavigateToJournal = { prompt ->
                        navController.navigate(Screen.JournalEditor.createRoute(prompt))
                    },
                    onNavigateToApps = { navController.navigate(Screen.Apps.route) },
                    onNavigateToStatistics = { navController.navigate(Screen.Statistics.route) }
                )
            }
            composable(Screen.Statistics.route) {
                val vm: StatisticsViewModel = viewModel()
                StatisticsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Apps.route) {
                val vm: AppsViewModel = viewModel()
                AppsScreen(viewModel = vm)
            }
            composable(Screen.Settings.route) {
                val vm: SettingsViewModel = viewModel()
                SettingsScreen(viewModel = vm)
            }
            composable(Screen.Journal.route) {
                val vm: JournalViewModel = viewModel()
                JournalScreen(
                    viewModel = vm,
                    onNavigateToEditor = { prompt -> navController.navigate(Screen.JournalEditor.createRoute(prompt)) },
                    onNavigateToDetail = { id -> navController.navigate(Screen.JournalDetail.createRoute(id)) },
                    onNavigateToArticle = { id -> navController.navigate(Screen.ArticleDetail.createRoute(id)) }
                )
            }
            composable(Screen.Onboarding.route) {
                val vm: OnboardingViewModel = viewModel()
                OnboardingScreen(
                    viewModel = vm,
                    onFinished = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                route = Screen.GateOverlay.route,
                arguments = listOf(
                    navArgument("packageName") { type = NavType.StringType },
                    navArgument("appName") { type = NavType.StringType },
                    navArgument("delaySeconds") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
                val appName = backStackEntry.arguments?.getString("appName") ?: ""
                val delaySeconds = backStackEntry.arguments?.getInt("delaySeconds") ?: 5

                GateOverlay(
                    packageName = packageName,
                    appName = appName,
                    delaySeconds = delaySeconds,
                    onCancel = { navController.popBackStack() },
                    onOpenAllowed = { navController.popBackStack() },
                    onWriteReflection = { prompt ->
                        navController.navigate(Screen.JournalEditor.createRoute(prompt))
                    }
                )
            }
            composable(Screen.Recovery.route) {
                val vm: RecoveryViewModel = viewModel()
                RecoveryScreen(
                    viewModel = vm,
                    onContinue = { navController.popBackStack() },
                    onWriteJournal = { emotion ->
                        navController.navigate(Screen.JournalEditor.createRoute("Pemulihan dari rasa: $emotion"))
                    }
                )
            }
            composable(
                route = Screen.JournalEditor.route,
                arguments = listOf(navArgument("prompt") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val prompt = backStackEntry.arguments?.getString("prompt")
                val vm: JournalViewModel = viewModel()
                JournalEditorScreen(
                    viewModel = vm,
                    initialPrompt = prompt,
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.JournalDetail.route,
                arguments = listOf(navArgument("entryId") { type = NavType.IntType })
            ) { backStackEntry ->
                val entryId = backStackEntry.arguments?.getInt("entryId") ?: 0
                val vm: JournalViewModel = viewModel()
                JournalDetailScreen(
                    viewModel = vm,
                    entryId = entryId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.ArticleDetail.route,
                arguments = listOf(navArgument("articleId") { type = NavType.IntType })
            ) { backStackEntry ->
                val articleId = backStackEntry.arguments?.getInt("articleId") ?: 0
                val vm: JournalViewModel = viewModel()
                ArticleDetailScreen(
                    viewModel = vm,
                    articleId = articleId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        if (showBottomBar) {
            SeremindBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                screens = bottomNavScreens,
                currentRoute = currentRoute,
                onNavigate = { screen ->
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
}

@Composable
private fun SeremindBottomBar(
    modifier: Modifier = Modifier,
    screens: List<Screen>,
    currentRoute: String?,
    onNavigate: (Screen) -> Unit
) {
    val selectedIndex = screens.indexOfFirst { it.route == currentRoute }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(105.dp)
            .navigationBarsPadding()
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val itemWidth = widthPx / screens.size
        val targetCenter = if (selectedIndex >= 0) {
            itemWidth * selectedIndex + itemWidth / 2f
        } else {
            widthPx / 2f
        }
        val notchCenter by androidx.compose.animation.core.animateFloatAsState(
            targetValue = targetCenter,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = 0.8f,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
            ),
            label = "bottomBarNotch"
        )
        val notchWidth = with(density) { 92.dp.toPx() }
        val notchDepth = with(density) { 32.dp.toPx() }
        val barTop = with(density) { 32.dp.toPx() }
        val corner = with(density) { 12.dp.toPx() }
        val barColor = MaterialTheme.colorScheme.surface

        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    shadowElevation = 10.dp.toPx()
                    clip = false
                    shape = GenericShape { size, _ ->
                        moveTo(0f, barTop)
                        lineTo(notchCenter - notchWidth / 2f, barTop)
                        cubicTo(
                            notchCenter - notchWidth * .35f, barTop,
                            notchCenter - notchWidth * .30f, barTop + notchDepth,
                            notchCenter, barTop + notchDepth
                        )
                        cubicTo(
                            notchCenter + notchWidth * .30f, barTop + notchDepth,
                            notchCenter + notchWidth * .35f, barTop,
                            notchCenter + notchWidth / 2f, barTop
                        )
                        lineTo(size.width, barTop)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                }
        ) {
            val path = Path().apply {
                moveTo(0f, barTop)
                lineTo(notchCenter - notchWidth / 2f, barTop)
                cubicTo(
                    notchCenter - notchWidth * .35f, barTop,
                    notchCenter - notchWidth * .30f, barTop + notchDepth,
                    notchCenter, barTop + notchDepth
                )
                cubicTo(
                    notchCenter + notchWidth * .30f, barTop + notchDepth,
                    notchCenter + notchWidth * .35f, barTop,
                    notchCenter + notchWidth / 2f, barTop
                )
                lineTo(size.width, barTop)
                lineTo(size.width, size.height - corner)
                quadraticBezierTo(size.width, size.height, size.width - corner, size.height)
                lineTo(corner, size.height)
                quadraticBezierTo(0f, size.height, 0f, size.height - corner)
                close()
            }
            drawPath(path, barColor)
        }

        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(65.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            screens.forEachIndexed { index, screen ->
                val selected = index == selectedIndex
                val iconOffset by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (selected) (-38).dp else (-10).dp,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = .75f,
                        stiffness = 280f
                    ),
                    label = "bottomBarIcon"
                )
                val itemColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = .42f),
                    label = "bottomBarLabel"
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null
                    ) { onNavigate(screen) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Surface(
                            Modifier.offset(y = iconOffset).size(54.dp).shadow(8.dp, CircleShape),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(screen.icon, screen.title, tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                        }
                    } else {
                        Icon(
                            screen.icon, screen.title, tint = itemColor,
                            modifier = Modifier.offset(y = iconOffset).size(24.dp)
                        )
                    }
                    Text(
                        screen.title,
                        color = itemColor,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}
