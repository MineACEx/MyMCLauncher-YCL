package com.mymc.launcher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局高斯模糊毛玻璃卡片组件。
 * 使用 Modifier.blur() 实现高斯模糊效果，内部通过 Box + Surface 组合实现圆角裁剪与背景色。
 * 支持 scaleOnClick 按压缩放动效。
 *
 * @param modifier   外部 Modifier
 * @param blurRadius 高斯模糊半径，默认 12.dp
 * @param cornerRadius 卡片圆角半径，默认 16.dp
 * @param backgroundColor 卡片背景色，默认半透明白色
 * @param enableClickAnimation 是否启用按压缩放动效，默认 true
 * @param content   卡片内容
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 12.dp,
    cornerRadius: Dp = 16.dp,
    backgroundColor: Color = Color.White.copy(alpha = 0.15f),
    enableClickAnimation: Boolean = true,
    content: @Composable () -> Unit
) {
    val shape: Shape = RoundedCornerShape(cornerRadius)
    val animatedModifier = if (enableClickAnimation) modifier.scaleOnClick(0.97f) else modifier

    Box(
        modifier = animatedModifier.blur(radius = blurRadius)
    ) {
        Surface(
            shape = shape,
            color = backgroundColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}