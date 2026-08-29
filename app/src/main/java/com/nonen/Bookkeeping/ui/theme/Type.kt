package com.nonen.Bookkeeping.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Apple HIG / SF Pro 风格字阶（参照 inkqilin-ledger Type.kt）
val Typography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = 0.37.sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.36.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.35.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.35.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.08).sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.08).sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.06.sp,
    ),
)
