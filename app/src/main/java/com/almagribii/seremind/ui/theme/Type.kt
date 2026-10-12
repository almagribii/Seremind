package com.almagribii.seremind.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.almagribii.seremind.R

private val JakartaSans = FontFamily(
    Font(R.font.jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_sans_bold, FontWeight.Bold)
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    )
) .withJakartaSans()

private fun Typography.withJakartaSans() = copy(
    displayLarge = displayLarge.copy(fontFamily = JakartaSans),
    displayMedium = displayMedium.copy(fontFamily = JakartaSans),
    displaySmall = displaySmall.copy(fontFamily = JakartaSans),
    headlineLarge = headlineLarge.copy(fontFamily = JakartaSans),
    headlineMedium = headlineMedium.copy(fontFamily = JakartaSans),
    headlineSmall = headlineSmall.copy(fontFamily = JakartaSans),
    titleLarge = titleLarge.copy(fontFamily = JakartaSans),
    titleMedium = titleMedium.copy(fontFamily = JakartaSans),
    titleSmall = titleSmall.copy(fontFamily = JakartaSans),
    bodyLarge = bodyLarge.copy(fontFamily = JakartaSans),
    bodyMedium = bodyMedium.copy(fontFamily = JakartaSans),
    bodySmall = bodySmall.copy(fontFamily = JakartaSans),
    labelLarge = labelLarge.copy(fontFamily = JakartaSans),
    labelMedium = labelMedium.copy(fontFamily = JakartaSans),
    labelSmall = labelSmall.copy(fontFamily = JakartaSans)
)
