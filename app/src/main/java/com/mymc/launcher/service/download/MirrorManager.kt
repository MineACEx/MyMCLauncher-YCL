package com.mymc.launcher.service.download

import com.mymc.launcher.util.LogUtil
import java.net.HttpURLConnection
import java.net.URL

/**
 * 下载镜像源管理器 —— 参照 FCL 的 DownloadProvider + BalancedDownloadProvider
 *
 * FCL 参考：
 * - com.tungsten.fclcore.download.DownloadProvider
 * - com.tungsten.fclcore.download.BMCLAPIDownloadProvider
 * - com.tungsten.fclcore.download.BalancedDownloadProvider
 *
 * 核心功能：
 * - 多下载源：Mojang 官方 + BMCLAPI 国内镜像
 * - 自动故障切换：一个源失败自动尝试下一个
 * - URL 自动转换：将 Mojang 官方 URL 转为当前镜像源 URL
 * - 连接检测：启动时检测各源可用性
 */
object MirrorManager {
    private const val TAG = "MirrorManager"

    /**
     * 下载源类型
     */
    enum class MirrorType(val displayName: String, val isForChina: Boolean) {
        MOJANG("Mojang 官方", false),
        BMCLAPI("BMCLAPI 国内镜像", true)
    }

    /**
     * 下载源配置
     */
    data class MirrorConfig(
        val type: MirrorType,
        val baseUrl: String,
        val versionManifestUrl: String,
        val assetsBaseUrl: String,
        val librariesBaseUrl: String
    )

    // ==================== 下载源配置 ====================

    private val MOJANG_CONFIG = MirrorConfig(
        type = MirrorType.MOJANG,
        baseUrl = "https://launchermeta.mojang.com",
        versionManifestUrl = "https://launchermeta.mojang.com/mc/game/version_manifest.json",
        assetsBaseUrl = "https://resources.download.minecraft.net",
        librariesBaseUrl = "https://libraries.minecraft.net"
    )

    private val BMCLAPI_CONFIG = MirrorConfig(
        type = MirrorType.BMCLAPI,
        baseUrl = "https://bmclapi2.bangbang93.com",
        versionManifestUrl = "https://bmclapi2.bangbang93.com/mc/game/version_manifest.json",
        assetsBaseUrl = "https://bmclapi2.bangbang93.com/assets",
        librariesBaseUrl = "https://bmclapi2.bangbang93.com/maven"
    )

    /** 所有可用下载源（按优先级排列） */
    val allMirrors: List<MirrorConfig> = listOf(BMCLAPI_CONFIG, MOJANG_CONFIG)

    /** 当前激活的主下载源 */
    var currentMirror: MirrorType = MirrorType.BMCLAPI
        private set

    /** 各下载源可用性状态 */
    private val mirrorAvailability = mutableMapOf<MirrorType, Boolean>()

    // ==================== 公共 API ====================

    /** 获取版本清单地址 */
    fun getVersionManifestUrl(): String {
        val config = getActiveConfig()
        return config.versionManifestUrl
    }

    /**
     * 将 Mojang 官方 URL 转换为当前镜像源 URL
     *
     * BMCLAPI 转换规则：
     * - launchermeta.mojang.com          → bmclapi2.bangbang93.com
     * - resources.download.minecraft.net → bmclapi2.bangbang93.com/assets
     * - libraries.minecraft.net          → bmclapi2.bangbang93.com/maven
     * - launcher.mojang.com              → bmclapi2.bangbang93.com
     * - maven.minecraftforge.net         → bmclapi2.bangbang93.com/maven  (Forge)
     * - maven.fabricmc.net              → bmclapi2.bangbang93.com/maven  (Fabric)
     */
    fun convertUrl(originalUrl: String): String {
        if (currentMirror == MirrorType.MOJANG) return originalUrl

        return originalUrl
            .replace("https://launchermeta.mojang.com", BMCLAPI_CONFIG.baseUrl)
            .replace("https://resources.download.minecraft.net", BMCLAPI_CONFIG.assetsBaseUrl)
            .replace("https://libraries.minecraft.net", BMCLAPI_CONFIG.librariesBaseUrl)
            .replace("https://launcher.mojang.com", BMCLAPI_CONFIG.baseUrl)
            .replace("https://maven.minecraftforge.net", BMCLAPI_CONFIG.librariesBaseUrl)
            .replace("https://maven.fabricmc.net", BMCLAPI_CONFIG.librariesBaseUrl)
            .replace("http://launchermeta.mojang.com", BMCLAPI_CONFIG.baseUrl)
            .replace("http://resources.download.minecraft.net", BMCLAPI_CONFIG.assetsBaseUrl)
    }

    /**
     * 获取资源下载 URL（根据 hash 值）
     *
     * @param hash 资源文件 SHA1 哈希值
     * @return 完整的资源下载 URL
     */
    fun getAssetUrl(hash: String): String {
        val config = getActiveConfig()
        val subPath = hash.substring(0, 2)
        return "${config.assetsBaseUrl}/$subPath/$hash"
    }

    /**
     * 获取多源 URL 列表（用于下载失败时自动切换）
     *
     * 参照 FCL BalancedDownloadProvider：将原始 URL 转换为所有可用源的 URL 列表，
     * 下载时按顺序尝试，第一个成功即停止。
     *
     * @param originalUrl 原始 Mojang URL
     * @return 所有可用源的 URL 列表（当前源优先）
     */
    fun getFallbackUrls(originalUrl: String): List<String> {
        val urls = mutableListOf<String>()

        // 当前源优先
        urls.add(convertUrl(originalUrl))

        // 添加其他可用源
        for (mirror in allMirrors) {
            if (mirror.type == currentMirror) continue
            val fallbackUrl = originalUrl
                .replace("https://launchermeta.mojang.com", mirror.baseUrl)
                .replace("https://resources.download.minecraft.net", mirror.assetsBaseUrl)
                .replace("https://libraries.minecraft.net", mirror.librariesBaseUrl)
            if (fallbackUrl != urls.first()) {
                urls.add(fallbackUrl)
            }
        }

        return urls.distinct()
    }

    /**
     * 检测所有下载源的可用性
     *
     * 参照 FCL 的 AutoDownloadProvider 启动时检测逻辑。
     */
    fun checkAvailability(onResult: (MirrorType, Boolean) -> Unit) {
        Thread {
            for (mirror in allMirrors) {
                val available = testConnection(mirror.versionManifestUrl)
                mirrorAvailability[mirror.type] = available
                LogUtil.info(TAG, "下载源 ${mirror.type.displayName}: ${if (available) "可用" else "不可用"}")
                onResult(mirror.type, available)
            }

            // 自动选择最佳下载源
            if (mirrorAvailability[MirrorType.BMCLAPI] == true) {
                switchMirror(MirrorType.BMCLAPI)
            } else if (mirrorAvailability[MirrorType.MOJANG] == true) {
                switchMirror(MirrorType.MOJANG)
            }
        }.start()
    }

    /**
     * 切换镜像源
     */
    fun switchMirror(type: MirrorType) {
        currentMirror = type
        LogUtil.info(TAG, "镜像源已切换至: ${type.displayName}")
    }

    /**
     * 获取当前镜像源是否可用
     */
    fun isCurrentMirrorAvailable(): Boolean {
        return mirrorAvailability[currentMirror] ?: false
    }

    // ==================== 私有方法 ====================

    private fun getActiveConfig(): MirrorConfig {
        return when (currentMirror) {
            MirrorType.MOJANG -> MOJANG_CONFIG
            MirrorType.BMCLAPI -> BMCLAPI_CONFIG
        }
    }

    /**
     * 测试 URL 连接是否可用（HEAD 请求，超时 5 秒）
     */
    private fun testConnection(url: String): Boolean {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "HEAD"
            connection.connect()
            val code = connection.responseCode
            connection.disconnect()
            code in 200..399
        } catch (e: Exception) {
            LogUtil.warn(TAG, "连接测试失败: $url - ${e.message}")
            false
        }
    }
}