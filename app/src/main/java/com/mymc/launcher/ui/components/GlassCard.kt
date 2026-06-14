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
 *
 * @param modifier   外部 Modifier，用于控制卡片的尺寸、外边距等
 * @param blurRadius 高斯模糊半径，默认 12.dp
 * @param cornerRadius 卡片圆角半径，默认 16.dp
 * @param backgroundColor 卡片背景色，默认半透明白色
 * @param content   卡片内部的可组合内容
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 12.dp,
    cornerRadius: Dp = 16.dp,
    backgroundColor: Color = Color.White.copy(alpha = 0.15f),
    content: @Composable () -> Unit
) {
    val shape: Shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .blur(radius = blurRadius)
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