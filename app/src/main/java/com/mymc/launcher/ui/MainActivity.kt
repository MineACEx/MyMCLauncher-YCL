package com.mymc.launcher.ui

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.mymc.launcher.service.theme.ThemeManager
import com.mymc.launcher.ui.navigation.YCLNavGraph
import com.mymc.launcher.ui.theme.YCLTheme
import com.mymc.launcher.util.LogUtil

/**
 * YCL启动器 — 主 Activity
 * 应用唯一入口，强制横屏，使用 Compose 构建 UI
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 强制横屏
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // 边到边显示
        enableEdgeToEdge()

        // 初始化日志系统
        LogUtil.init(filesDir.resolve("logs"))

        // 初始化主题管理器
        ThemeManager.init(this)

        LogUtil.info("MainActivity", "YCL启动器已启动")

        setContent {
            YCLTheme {
                val navController = rememberNavController()
                YCLNavGraph(navController = navController)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 确保恢复后保持横屏
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}