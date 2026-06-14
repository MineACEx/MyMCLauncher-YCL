package com.mymc.launcher

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.mymc.launcher.util.EnvDetector

/**
 * YCL启动器 — Application 入口
 * 负责全局初始化：环境检测、图片加载器配置、日志初始化
 */
class YCLApplication : Application(), SingletonImageLoader.Factory {

    /** 当前运行环境类型 */
    lateinit var envType: EnvDetector.EnvType
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 检测当前设备运行环境（无Root / Root / Termux）
        envType = EnvDetector.detectEnvironment()

        // 初始化全局日志
        android.util.Log.i("YCL-LAUNCHER", """
            ╔══════════════════════════════════╗
            ║     YCL启动器 v1.0.0           ║
            ║     设备环境: ${envType.name}  ║
            ║     Android ${Build.VERSION.SDK_INT}              ║
            ╚══════════════════════════════════╝
        """.trimIndent())
    }

    // ============================================================
    // Coil 全局图片加载器（支持网络图片、本地文件、高斯模糊）
    // ============================================================
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .apply {
                okHttpClient {
                    okhttp3.OkHttpClient.Builder()
                        .followRedirects(true)
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                }
            }
            .build()
    }

    companion object {
        lateinit var instance: YCLApplication
            private set
    }
}