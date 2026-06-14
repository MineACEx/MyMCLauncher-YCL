package com.mymc.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
 * 使用分层渲染实现真正的玻璃拟态效果：
 * - 底层：blur 模糊的背景层（半透明 + 高斯模糊）
 * - 中层：渐变高光层（模拟玻璃边缘光反射）
 * - 顶层：内容层（无模糊，清晰锐利）
 *
 * @param modifier         外部 Modifier
 * @param blurRadius       高斯模糊半径，默认 12.dp
 * @param cornerRadius     卡片圆角半径，默认 16.dp
 * @param backgroundColor  卡片背景色，默认半透明白色
 * @param borderColor      边框颜色，默认白色半透明
 * @param enableClickAnimation 是否启用按压缩放动效，默认 true
 * @param contentPadding   内容内边距，默认 16.dp
 * @param content          卡片内容
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 12.dp,
    cornerRadius: Dp = 16.dp,
    backgroundColor: Color = Color.White.copy(alpha = 0.12f),
    borderColor: Color = Color.White.copy(alpha = 0.25f),
    enableClickAnimation: Boolean = true,
    contentPadding: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    val shape: Shape = RoundedCornerShape(cornerRadius)
    val animatedModifier = if (enableClickAnimation) modifier.scaleOnClick(0.97f) else modifier

    Box(
        modifier = animatedModifier
            .clip(shape)
    ) {
        // 第一层：模糊背景层（高斯模糊 + 半透明背景色）
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(radius = blurRadius)
                .background(backgroundColor, shape)
        )

        // 第二层：微妙渐变高光（模拟玻璃边缘光反射）
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = shape
                )
        )

        // 第三层：边框高光
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(width = 1.dp, color = borderColor, shape = shape)
        )

        // 第四层：内容层（无模糊，清晰锐利）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
        ) {
            content()
        }
    }
}