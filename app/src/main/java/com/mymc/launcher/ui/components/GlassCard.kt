package com.mymc.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局高斯模糊毛玻璃卡片组件。
 *
 * 参照 FCL ThemeEngine 的 glassmorphism 渲染方式：
 * 多层叠加实现真实玻璃效果：
 * - 底层：强模糊背景层（高斯模糊 + 极低透明度，模拟磨砂玻璃）
 * - 中层：渐变光泽层（模拟玻璃边缘的光线折射）
 * - 上层：边框层（细白边框，增强玻璃感）
 * - 顶层：清晰内容层
 *
 * 透明度可根据 isDarkMode 参数自动调整。
 *
 * @param modifier         外部 Modifier
 * @param blurRadius       高斯模糊半径，默认 20.dp（更强模糊）
 * @param cornerRadius     卡片圆角半径，默认 16.dp
 * @param backgroundColor  卡片背景色，默认极低透明度
 * @param borderColor      边框颜色，默认白色半透明
 * @param isDarkMode       是否为深色模式，影响透明度
 * @param enableClickAnimation 是否启用按压缩放动效，默认 true
 * @param contentPadding   内容内边距，默认 16.dp
 * @param content          卡片内容
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 20.dp,
    cornerRadius: Dp = 16.dp,
    backgroundColor: Color = Color.White.copy(alpha = 0.08f),
    borderColor: Color = Color.White.copy(alpha = 0.18f),
    isDarkMode: Boolean = true,
    enableClickAnimation: Boolean = true,
    contentPadding: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    val shape: Shape = RoundedCornerShape(cornerRadius)
    // 根据深色/浅色模式调整背景透明度
    val bgAlpha = if (isDarkMode) 0.08f else 0.15f
    val gradientAlpha = if (isDarkMode) 0.06f else 0.12f
    val borderAlpha = if (isDarkMode) 0.18f else 0.30f

    val animatedModifier = if (enableClickAnimation) modifier.scaleOnClick(0.97f) else modifier

    Box(
        modifier = animatedModifier
            .clip(shape)
    ) {
        // ── 第一层：模糊背景层（高模糊 + 极低透明度 = 磨砂玻璃效果）──
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(radius = blurRadius)
                .background(backgroundColor.copy(alpha = bgAlpha), shape)
        )

        // ── 第二层：微妙渐变光泽（模拟玻璃边缘的光线折射）──
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = gradientAlpha),
                            Color.Transparent,
                            Color.Transparent,
                            Color.White.copy(alpha = gradientAlpha * 0.3f)
                        )
                    ),
                    shape = shape
                )
        )

        // ── 第三层：对角线光泽（增强玻璃质感）──
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = gradientAlpha * 0.5f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.White.copy(alpha = gradientAlpha * 0.2f)
                        )
                    ),
                    shape = shape
                )
        )

        // ── 第四层：边框高光（细白边框模拟玻璃边缘）──
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(width = 1.dp, color = borderColor.copy(alpha = borderAlpha), shape = shape)
        )

        // ── 第五层：内容层（无模糊，清晰锐利）──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
        ) {
            content()
        }
    }
}

/**
 * 强模糊 GlassCard 变体 — 用于主面板（如首页、大卡片）
 * 透明度更高，模糊更强，更有玻璃通透感。
 */
@Composable
fun GlassCardStrong(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    isDarkMode: Boolean = true,
    contentPadding: Dp = 20.dp,
    content: @Composable () -> Unit
) {
    GlassCard(
        modifier = modifier,
        blurRadius = 28.dp,
        cornerRadius = cornerRadius,
        backgroundColor = Color.White.copy(alpha = 0.06f),
        borderColor = Color.White.copy(alpha = 0.15f),
        isDarkMode = isDarkMode,
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * 轻量 GlassCard 变体 — 用于列表项、小卡片
 * 透明度更低，更紧凑。
 */
@Composable
fun GlassCardCompact(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    isDarkMode: Boolean = true,
    contentPadding: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    GlassCard(
        modifier = modifier,
        blurRadius = 12.dp,
        cornerRadius = cornerRadius,
        backgroundColor = Color.White.copy(alpha = 0.10f),
        borderColor = Color.White.copy(alpha = 0.20f),
        isDarkMode = isDarkMode,
        contentPadding = contentPadding,
        content = content
    )
}