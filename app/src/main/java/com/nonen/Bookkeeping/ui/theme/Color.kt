package com.nonen.Bookkeeping.ui.theme

import androidx.compose.ui.graphics.Color

// Apple HIG 风格调色板（参照 inkqilin-ledger 设计）
val InkPrimary = Color(0xFF34C759)      // Apple 绿
val InkPrimaryDark = Color(0xFF30D158)
val InkSecondary = Color(0xFF007AFF)    // Apple 蓝
val InkSecondaryDark = Color(0xFF0A84FF)

val AppleGreen = Color(0xFF34C759)
val AppleBlue = Color(0xFF007AFF)
val AppleOrange = Color(0xFFFF9500)
val AppleRed = Color(0xFFFF3B30)
val AppleTeal = Color(0xFF5AC8FA)
val AppleIndigo = Color(0xFF5856D6)
val ApplePurple = Color(0xFFAF52DE)

val IncomeColor = AppleGreen
val ExpenseColor = AppleOrange

val BackgroundLight = Color(0xFFF5F5F7)          // Apple 浅灰底
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF1D1D1F)
val OnSurfaceVariantLight = Color(0xFF6E6E73)
val OutlineLight = Color(0xFFD1D1D6)
val SurfaceVariantLight = Color(0xFFE5E5EA)
val UnselectedTabLight = Color(0xFF8E8E93)

val BackgroundDark = Color(0xFF0B0B0F)
val SurfaceDark = Color(0xFF111318)
val OnSurfaceDark = Color(0xFFFFFFFF)
val OnSurfaceVariantDark = Color(0xB3FFFFFF)     // 白 @ 70%
val OutlineDark = Color(0xFF2E2F32)
val SurfaceVariantDark = Color(0xFF1C1E24)
val UnselectedTabDark = Color(0x8CFFFFFF)        // 白 @ 55%

// 图表 / 分类排行用调色板（inkqilin 饼图配色）
val ChartColors = listOf(
    Color(0xFFFF2D55), Color(0xFF007AFF), Color(0xFFFF9500), Color(0xFF34C759),
    Color(0xFFAF52DE), Color(0xFFFF3B30), Color(0xFF5AC8FA), Color(0xFFFFCC00),
    Color(0xFF8E8E93), Color(0xFF00C7BE), Color(0xFFFF6482), Color(0xFF30B0C7),
)
