package com.almagribii.seremind.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
        Screen.Statistics,
        Screen.Apps,
        Screen.Journal,
        Screen.Settings
    )

    val showBottomBar = bottomNavScreens.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavScreens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
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
                StatisticsScreen(viewModel = vm)
            }
            composable(Screen.Apps.route) {
                val vm: AppsViewModel = viewModel()
                AppsScreen(viewModel = vm)
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
            composable(Screen.Settings.route) {
                val vm: SettingsViewModel = viewModel()
                SettingsScreen(viewModel = vm)
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
                    onOpenAllowed = {
                        navController.popBackStack()
                    },
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
    }
}
