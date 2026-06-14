package com.mymc.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.mymc.launcher.service.theme.ThemeManager

/**
 * 全局背景图片组件。
 * 从 ThemeManager 读取 backgroundPath 状态。
 * 若用户设置了自定义背景图片路径则通过 Coil 加载显示，
 * 否则使用纯色背景填充。
 * 背景铺满全屏，使用 ContentScale.Crop 裁剪适配。
 */
@Composable
fun BackgroundImage(modifier: Modifier = Modifier) {
    val backgroundPath by ThemeManager.backgroundPath.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        if (backgroundPath.isNotBlank()) {
            AsyncImage(
                model = backgroundPath,
                contentDescription = "主界面背景图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A2E))
            )
        }
    }
}