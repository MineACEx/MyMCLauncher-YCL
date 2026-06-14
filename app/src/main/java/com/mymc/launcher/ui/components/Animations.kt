package com.mymc.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput

/**
 * YCL 启动器 — 通用动画工具集
 *
 * 提供一键点击缩放、列表项入场动画等可复用动效组件。
 * 遵循 Material 3 灵动简洁的设计语言，所有动画使用 spring() 曲线。
 */

// ============================================================
// 点击缩放动效（Modifier 扩展）
// ============================================================

/**
 * 为任意 Composable 添加灵动的按压缩放效果。
 *
 * 使用方式：
 * ```kotlin
 * Button(modifier = Modifier.scaleOnClick()) { ... }
 * Card(modifier = Modifier.scaleOnClick(0.96f)) { ... }
 * ```
 *
 * @param targetScale 按压时目标缩放倍率（0.95 = 缩小到 95%）
 */
fun Modifier.scaleOnClick(targetScale: Float = 0.95f): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 400f
        ),
        label = "scale_press"
    )

    this
        .scale(scale)
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                }
            )
        }
}

// ============================================================
// 列表项入场动画参数
// ============================================================

/**
 * 列表项默认入场动画参数
 */
object ListItemAnimation {
    /** 基础延迟（毫秒） */
    const val BASE_DELAY = 60
    /** 每个条目的延迟增量（毫秒） */
    const val STEP_DELAY = 30
    /** 入场动画时长（毫秒） */
    const val DURATION = 350
}

// ============================================================
// 列表项入场动画
// ============================================================

/**
 * 带动画入场的列表项包装器。
 *
 * 从下方滑入 + 淡入，按索引依次延迟。
 *
 * ```kotlin
 * LazyColumn {
 *     items(list) { item ->
 *         AnimatedListItem(index = list.indexOf(item)) {
 *             MyCard(item)
 *         }
 *     }
 * }
 * ```
 */
@Composable
fun AnimatedListItem(
    index: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    // 触发动画的布尔状态
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = tween(
                durationMillis = ListItemAnimation.DURATION,
                delayMillis = ListItemAnimation.BASE_DELAY + index * ListItemAnimation.STEP_DELAY
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = ListItemAnimation.DURATION,
                delayMillis = ListItemAnimation.BASE_DELAY + index * ListItemAnimation.STEP_DELAY
            )
        ),
        content = content
    )
}

// ============================================================
// 页面内容淡入
// ============================================================

/**
 * 页面级内容淡入动画包装器。
 *
 * ```kotlin
 * FadeInContent {
 *     Column { ... }
 * }
 * ```
 */
@Composable
fun FadeInContent(content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) + scaleIn(
            initialScale = 0.98f,
            animationSpec = tween(300)
        ),
        content = content
    )
}