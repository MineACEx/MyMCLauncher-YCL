package com.mymc.launcher.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * YCL启动器 — 主题色定义
 * 默认深海蓝（Deep Sea Blue #0D47A1）
 * 支持用户自定义主题色
 */

// ============================================================
// 深海蓝 — 默认主题色
// ============================================================
val DeepSeaBlue = Color(0xFF0D47A1)
val DeepSeaBlueLight = Color(0xFF1565C0)
val DeepSeaBlueDark = Color(0xFF0A3578)
val DeepSeaBlueContainer = Color(0xFFD1E4FF)

// ============================================================
// 亮色主题调色板
// ============================================================
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF5F7FA)
val LightSurfaceVariant = Color(0xFFE8ECF0)
val LightOnBackground = Color(0xFF1A1A2E)
val LightOnSurface = Color(0xFF2D2D44)
val LightOnSurfaceVariant = Color(0xFF6B7280)

// ============================================================
// 暗色主题调色板
// ============================================================
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E2E)
val DarkSurfaceVariant = Color(0xFF2A2A3C)
val DarkOnBackground = Color(0xFFE0E0E0)
val DarkOnSurface = Color(0xFFC0C0D0)
val DarkOnSurfaceVariant = Color(0xFF9090A0)

// ============================================================
// 功能色
// ============================================================
val Success = Color(0xFF4CAF50)
val Warning = Color(0xFFFFC107)
val Error = Color(0xFFE53935)
val Info = Color(0xFF2196F3)

// ============================================================
// 预置主题色选项（供用户选择）
// ============================================================
val PresetThemeColors = listOf(
    "深海蓝" to Color(0xFF0D47A1),
    "樱花粉" to Color(0xFFE91E63),
    "翡翠绿" to Color(0xFF009688),
    "琥珀橙" to Color(0xFFFF6F00),
    "紫罗兰" to Color(0xFF7C4DFF),
    "石墨灰" to Color(0xFF607D8B),
    "暗夜黑" to Color(0xFF212121),
)