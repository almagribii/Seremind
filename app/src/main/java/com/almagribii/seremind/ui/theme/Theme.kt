package com.almagribii.seremind.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = SagePrimary,
    onPrimary = Color.White,
    primaryContainer = LightMint.copy(alpha = 0.28f),
    onPrimaryContainer = CharcoalText,
    secondary = SoftAmber,
    secondaryContainer = LightMint.copy(alpha = 0.22f),
    onSecondaryContainer = CharcoalText,
    background = LightBackground,
    surface = LightSurface,
    onSecondary = Color.White,
    onBackground = CharcoalText,
    onSurface = CharcoalText,
    error = DustyRose
)

@Composable
fun SeremindTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            findActivity(view.context)?.let { activity ->
                val window = activity.window
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private tailrec fun findActivity(context: Context): Activity? {
    return when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }
}
