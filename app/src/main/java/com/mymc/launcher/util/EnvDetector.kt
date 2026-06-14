package com.mymc.launcher.util

import android.os.Build
import java.io.File

/**
 * YCL启动器 — 运行环境检测工具
 * 自动区分：无Root / Root / Termux 三种环境
 */
object EnvDetector {

    /** 运行环境类型 */
    enum class EnvType {
        /** 无 Root 权限的普通设备 */
        NON_ROOT,
        /** 已获取 Root 权限的设备 */
        ROOTED,
        /** Termux 终端模拟环境 */
        TERMUX,
    }

    /** 缓存的检测结果 */
    private var cachedType: EnvType? = null

    /**
     * 检测当前运行环境类型（带缓存）
     */
    fun detectEnvironment(): EnvType {
        cachedType?.let { return it }
        val result = detectInternal()
        cachedType = result
        return result
    }

    private fun detectInternal(): EnvType {
        // 优先检测 Termux 环境特征
        if (isTermuxEnvironment()) {
            return EnvType.TERMUX
        }
        // 其次检测 Root
        if (isRootAvailable()) {
            return EnvType.ROOTED
        }
        return EnvType.NON_ROOT
    }

    /**
     * 检测是否运行在 Termux 环境中
     * Termux 特征：存在 $PREFIX 变量，或特定路径
     */
    private fun isTermuxEnvironment(): Boolean {
        val termuxPrefix = System.getenv("PREFIX")
        if (!termuxPrefix.isNullOrBlank()) {
            return true
        }
        // 检查 Termux 特征目录
        val termuxDirs = listOf(
            "/data/data/com.termux/files/usr",
            "/data/data/com.termux/files/home"
        )
        return termuxDirs.any { File(it).exists() }
    }

    /**
     * 检测设备是否拥有 Root 权限
     * 通过检查 su 二进制文件是否存在
     */
    private fun isRootAvailable(): Boolean {
        val suPaths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sbin/su",
            "/vendor/bin/su",
            "/su/bin/su"
        )
        for (path in suPaths) {
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                return true
            }
        }
        return false // 注意：此处默认返回 false 以兼容安全策略
    }
}