package com.mymc.launcher

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.mymc.launcher.service.download.MirrorManager
import com.mymc.launcher.service.java.JavaEnvironmentManager
import com.mymc.launcher.service.theme.ThemeManager
import com.mymc.launcher.util.EnvDetector
import com.mymc.launcher.util.LogUtil

/**
 * YCL启动器 — Application 入口
 * 负责全局初始化：环境检测、图片加载器配置、日志初始化、Java环境检测、镜像源检测
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

        // 初始化主题管理器
        ThemeManager.init(this)

        // 启动时检测镜像源可用性（BMCLAPI / Mojang 官方）
        MirrorManager.checkAvailability { type, available ->
            android.util.Log.i("YCL-MIRROR", "镜像源 ${type.displayName}: ${if (available) "可用" else "不可用"}")
        }

        // 启动时检测 Java 环境（异步检测已安装的嵌入式 Java）
        Thread {
            try {
                val javaEnv = JavaEnvironmentManager.getInstance(this)
                val installed = javaEnv.getInstalledVersions()
                android.util.Log.i("YCL-JAVA", "已安装 Java 环境: ${if (installed.isEmpty()) "无" else installed.joinToString(", ")}")
                android.util.Log.i("YCL-JAVA", "设备架构: ${javaEnv.getDeviceArchitecture()}")
            } catch (e: Exception) {
                android.util.Log.w("YCL-JAVA", "Java 环境检测失败: ${e.message}")
            }
        }.start()
    }

    // ============================================================
    // Coil 全局图片加载器（支持网络图片、本地文件、高斯模糊）
    // ============================================================
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .followRedirects(true)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        return ImageLoader.Builder(context)
            .crossfade(true)
            .components { add(coil3.network.okhttp.OkHttpNetworkFetcherFactory(okHttpClient)) }
            .build()
    }

    companion object {
        lateinit var instance: YCLApplication
            private set
    }
}