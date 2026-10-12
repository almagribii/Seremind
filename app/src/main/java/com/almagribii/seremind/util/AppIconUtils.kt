package com.almagribii.seremind.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun AppIcon(
    packageName: String,
    appName: String,
    modifier: Modifier = Modifier,
    drawable: Drawable? = null
) {
    val context = LocalContext.current
    val resolvedDrawable = remember(packageName, drawable) {
        drawable ?: try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }

    if (resolvedDrawable != null) {
        val bitmap = remember(resolvedDrawable) {
            if (resolvedDrawable is BitmapDrawable) {
                resolvedDrawable.bitmap
            } else {
                val width = if (resolvedDrawable.intrinsicWidth > 0) resolvedDrawable.intrinsicWidth else 96
                val height = if (resolvedDrawable.intrinsicHeight > 0) resolvedDrawable.intrinsicHeight else 96
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                resolvedDrawable.setBounds(0, 0, canvas.width, canvas.height)
                resolvedDrawable.draw(canvas)
                bmp
            }
        }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = appName,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (appName.isNotBlank()) appName.take(1).uppercase() else "?",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp
            )
        }
    }
}
