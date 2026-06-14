package com.mymc.launcher.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.mymc.launcher.service.theme.ThemeManager

/**
 * YCL启动器 — 全局 Compose 主题
 * 支持亮色/暗色 + 自定义主题色
 */
@Composable
fun YCLTheme(
    themeManager: ThemeManager = ThemeManager, // 单例 object 直接引用
    content: @Composable () -> Unit
) {
    // 订阅主题状态
    val isDarkMode by themeManager.isDarkMode.collectAsState()
    val themeColor by themeManager.themeColor.collectAsState()

    // 将十六进制字符串转为 Color
    val primaryColor = try {
        Color(android.graphics.Color.parseColor(themeColor))
    } catch (_: Exception) {
        DeepSeaBlueDark
    }

    // 构建配色方案（null 时跟随系统）
    val colorScheme = if (isDarkMode ?: isSystemInDarkTheme()) {
        buildDarkColorScheme(primaryColor)
    } else {
        buildLightColorScheme(primaryColor)
    }

    // 设置系统状态栏颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !(isDarkMode ?: false)
                isAppearanceLightNavigationBars = !(isDarkMode ?: false)
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = YCLTypography,
        content = content
    )
}

// ============================================================
// 亮色配色方案构建
// ============================================================
private fun buildLightColorScheme(primary: Color) = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = DeepSeaBlueContainer,
    onPrimaryContainer = DeepSeaBlueDark,
    secondary = primary.copy(alpha = 0.7f),
    onSecondary = Color.White,
    secondaryContainer = primary.copy(alpha = 0.12f),
    onSecondaryContainer = primary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOnSurfaceVariant.copy(alpha = 0.3f),
    error = Error,
    onError = Color.White,
)

// ============================================================
// 暗色配色方案构建
// ============================================================
private fun buildDarkColorScheme(primary: Color) = darkColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primary.copy(alpha = 0.3f),
    onPrimaryContainer = Color(0xFFB3D4FF),
    secondary = primary.copy(alpha = 0.6f),
    onSecondary = Color.White,
    secondaryContainer = primary.copy(alpha = 0.2f),
    onSecondaryContainer = Color(0xFFB3D4FF),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOnSurfaceVariant.copy(alpha = 0.3f),
    error = Error,
    onError = Color.White,
)