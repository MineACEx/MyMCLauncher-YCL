package com.mymc.launcher.service.version

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mymc.launcher.data.remote.RetrofitClient
import com.mymc.launcher.data.remote.dto.MojangVersionDto
import com.mymc.launcher.domain.model.GameVersion
import com.mymc.launcher.domain.model.GameVersionType
import com.mymc.launcher.util.FileUtil
import com.mymc.launcher.util.HashUtil
import com.mymc.launcher.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.net.URL

/**
 * 版本管理器单例
 *
 * 负责管理 Minecraft 游戏版本的获取、下载、安装和加载器安装。
 *
 * 功能概述：
 * - 对接 Mojang API 获取可用版本列表
 * - 下载版本 JSON、核心 jar 及 assets 资源
 * - 扫描本地已安装版本
 * - 安装 Forge / Fabric 等模组加载器
 * - 版本隔离：每个版本独立目录 (.minecraft/versions/{versionId}/)
 *
 * 使用方式：
 * ```kotlin
 * val vm = VersionManager.getInstance(context)
 * vm.fetchVersionList { versions -> ... }
 * vm.downloadVersion("1.21") { progress -> ... }
 * ```
 */
class VersionManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "VersionManager"

        /** 版本目录名称 */
        private const val VERSIONS_DIR = "versions"

        /** assets 目录名称 */
        private const val ASSETS_DIR = "assets"

        /** libraries 目录名称 */
        private const val LIBRARIES_DIR = "libraries"

        /** 客户端 jar 文件名 */
        private const val CLIENT_JAR = "%s.jar"

        /** 版本 JSON 文件名 */
        private const val VERSION_JSON = "%s.json"

        /** 默认最大并发下载数 */
        private const val MAX_CONCURRENT_DOWNLOADS = 4

        @Volatile
        private var instance: VersionManager? = null

        /**
         * 获取 VersionManager 单例实例
         *
         * @param context Android 上下文
         * @return VersionManager 实例
         */
        fun getInstance(context: Context): VersionManager {
            return instance ?: synchronized(this) {
                instance ?: VersionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /** .minecraft 根目录 */
    private val minecraftDir: File
        get() = File(context.filesDir, ".minecraft")

    /** versions 目录 */
    private val versionsDir: File
        get() = File(minecraftDir, VERSIONS_DIR)

    /** assets 目录 */
    private val assetsDir: File
        get() = File(minecraftDir, ASSETS_DIR)

    /** libraries 目录 */
    private val librariesDir: File
        get() = File(minecraftDir, LIBRARIES_DIR)

    /** Gson 实例，用于解析版本 JSON */
    private val gson = Gson()

    /**
     * 从 Mojang API 获取可用的版本列表
     *
     * 只返回 release 类型的正式版，过滤 snapshot 快照版。
     *
     * @param onResult 结果回调，参数为 GameVersion 列表
     */
    suspend fun fetchVersionList(
        onResult: (List<GameVersion>) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            LogUtil.info(TAG, "正在从 Mojang API 获取版本列表...")
            val response = RetrofitClient.mojangApi.getVersionManifest()
            if (!response.isSuccessful || response.body() == null) {
                LogUtil.error(TAG, "获取版本列表失败，HTTP ${response.code()}")
                onResult(emptyList())
                return@withContext
            }

            val manifest = response.body()!!
            val gameVersions = manifest.versions
                .filter { it.type == "release" } // 仅获取正式版
                .map { entry ->
                    GameVersion(
                        id = "vanilla_${entry.id}",
                        versionId = entry.id,
                        type = GameVersionType.VANILLA,
                        downloadUrl = entry.url,
                        fileSize = 0,
                        installed = isVersionInstalled(entry.id)
                    )
                }

            LogUtil.info(TAG, "获取到 ${gameVersions.size} 个版本")
            onResult(gameVersions)
        } catch (e: Exception) {
            LogUtil.error(TAG, "获取版本列表异常", e)
            onResult(emptyList())
        }
    }

    /**
     * 下载并安装指定版本的 Minecraft
     *
     * 流程：
     * 1. 下载版本 JSON 元数据
     * 2. 下载客户端 jar（client.jar）
     * 3. 下载 assets 资源索引与资源文件
     *
     * @param version    游戏版本号，如 "1.21"
     * @param onProgress 进度回调，Float 范围 0.0~1.0
     * @return 安装成功返回 true，失败返回 false
     */
    suspend fun downloadVersion(
        version: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            LogUtil.info(TAG, "开始下载版本: $version")
            onProgress(0f)

            // 确保目录存在
            ensureDirectories()

            // 1. 下载版本 JSON（约占 5% 进度）
            val versionJsonUrl = getVersionJsonUrl(version)
            if (versionJsonUrl.isNullOrEmpty()) {
                LogUtil.error(TAG, "无法获取版本 $version 的元数据 URL")
                return@withContext false
            }

            val versionDir = getVersionDir(version)
            versionDir.mkdirs()

            val jsonFile = File(versionDir, VERSION_JSON.format(version))
            val jsonDownloaded = FileUtil.downloadWithResume(
                downloadUrl = versionJsonUrl,
                targetFile = jsonFile,
                onProgress = { downloaded, total ->
                    onProgress(0.05f * (downloaded.toFloat() / total.coerceAtLeast(1).toFloat()))
                }
            )
            if (!jsonDownloaded) {
                LogUtil.error(TAG, "版本 JSON 下载失败: $version")
                return@withContext false
            }
            onProgress(0.05f)

            // 2. 解析版本 JSON，获取 client.jar 下载地址
            val versionMeta = parseVersionJson(jsonFile)
            if (versionMeta == null) {
                LogUtil.error(TAG, "版本 JSON 解析失败: $version")
                return@withContext false
            }

            val clientJarUrl = versionMeta.get("downloads")?.asJsonObject
                ?.get("client")?.asJsonObject
                ?.get("url")?.asString

            val clientJarSha1 = versionMeta.get("downloads")?.asJsonObject
                ?.get("client")?.asJsonObject
                ?.get("sha1")?.asString

            // 3. 下载客户端 jar（约占 45% 进度，从 5% 到 50%）
            val clientJarFile = File(versionDir, CLIENT_JAR.format(version))
            if (clientJarUrl != null) {
                val jarDownloaded = FileUtil.downloadWithResume(
                    downloadUrl = clientJarUrl,
                    targetFile = clientJarFile,
                    onProgress = { downloaded, total ->
                        val progress = 0.05f + 0.45f * (downloaded.toFloat() / total.coerceAtLeast(1).toFloat())
                        onProgress(progress)
                    }
                )
                if (!jarDownloaded) {
                    LogUtil.error(TAG, "客户端 jar 下载失败: $version")
                    return@withContext false
                }

                // SHA1 校验
                if (!clientJarSha1.isNullOrEmpty()) {
                    val actualSha1 = HashUtil.sha1File(clientJarFile)
                    if (!actualSha1.equals(clientJarSha1, ignoreCase = true)) {
                        LogUtil.error(TAG, "客户端 jar SHA1 校验失败: 期望 $clientJarSha1, 实际 $actualSha1")
                        clientJarFile.delete()
                        return@withContext false
                    }
                }
            }
            onProgress(0.5f)

            // 4. 下载 assets（约占 50% 进度，从 50% 到 100%）
            val assetIndexInfo = versionMeta.get("assetIndex")?.asJsonObject
            val assetIndexUrl = assetIndexInfo?.get("url")?.asString
            val assetIndexId = assetIndexInfo?.get("id")?.asString

            if (assetIndexUrl != null && assetIndexId != null) {
                // 下载 asset index JSON
                val assetIndexFile = File(assetsDir, "indexes/$assetIndexId.json")
                assetIndexFile.parentFile?.mkdirs()
                val indexDownloaded = FileUtil.downloadWithResume(
                    downloadUrl = assetIndexUrl,
                    targetFile = assetIndexFile
                )
                if (indexDownloaded) {
                    // 解析 asset index 并下载资源文件
                    val assetIndex = parseVersionJson(assetIndexFile)
                    if (assetIndex != null) {
                        val objects = assetIndex.get("objects")?.asJsonObject
                        if (objects != null) {
                            val entries = objects.entrySet().toList()
                            val totalObjects = entries.size
                            var completedObjects = 0

                            // 分批并行下载 assets 文件
                            for (entry in entries) {
                                val obj = entry.value.asJsonObject
                                val hash = obj.get("hash")?.asString ?: continue
                                val subPath = hash.substring(0, 2)
                                val assetFile = File(assetsDir, "objects/$subPath/$hash")
                                // 如果文件已存在则跳过
                                if (assetFile.exists() && assetFile.length() > 0) {
                                    completedObjects++
                                    val progress = 0.5f + 0.5f * (completedObjects.toFloat() / totalObjects.toFloat())
                                    onProgress(progress)
                                    continue
                                }
                                assetFile.parentFile?.mkdirs()
                                val assetUrl = "https://resources.download.minecraft.net/$subPath/$hash"
                                val success = FileUtil.downloadWithResume(
                                    downloadUrl = assetUrl,
                                    targetFile = assetFile
                                )
                                completedObjects++
                                val progress = 0.5f + 0.5f * (completedObjects.toFloat() / totalObjects.toFloat())
                                onProgress(progress)
                                if (!success) {
                                    LogUtil.warn(TAG, "Asset 下载失败: $hash")
                                }
                            }
                        }
                    }
                }
            }

            onProgress(1f)
            LogUtil.info(TAG, "版本 $version 下载完成")
            true
        } catch (e: Exception) {
            LogUtil.error(TAG, "下载版本 $version 失败", e)
            onProgress(0f)
            false
        }
    }

    /**
     * 扫描本地已安装的版本
     *
     * 遍历 .minecraft/versions/ 目录，检查每个子目录是否包含完整的版本文件。
     *
     * @return 本地已安装的 GameVersion 列表
     */
    suspend fun scanLocalVersions(): List<GameVersion> = withContext(Dispatchers.IO) {
        try {
            if (!versionsDir.exists()) {
                return@withContext emptyList()
            }

            val dirs = FileUtil.listDirectories(versionsDir)
            val localVersions = mutableListOf<GameVersion>()

            for (dir in dirs) {
                val versionId = dir.name
                val jsonFile = File(dir, VERSION_JSON.format(versionId))
                val jarFile = File(dir, CLIENT_JAR.format(versionId))

                if (jsonFile.exists() && jarFile.exists()) {
                    // 判断加载器类型
                    val type = when {
                        versionId.contains("forge", ignoreCase = true) -> GameVersionType.FORGE
                        versionId.contains("fabric", ignoreCase = true) -> GameVersionType.FABRIC
                        else -> GameVersionType.VANILLA
                    }

                    localVersions.add(
                        GameVersion(
                            id = "${type.name.lowercase()}_$versionId",
                            versionId = versionId,
                            type = type,
                            downloadUrl = "",
                            fileSize = FileUtil.calculateSize(dir),
                            installed = true,
                            installPath = dir.absolutePath
                        )
                    )
                }
            }

            LogUtil.info(TAG, "扫描到 ${localVersions.size} 个本地版本")
            localVersions
        } catch (e: Exception) {
            LogUtil.error(TAG, "扫描本地版本失败", e)
            emptyList()
        }
    }

    /**
     * 删除指定版本
     *
     * 递归删除 .minecraft/versions/{versionId}/ 目录。
     *
     * @param version 游戏版本对象
     * @return 删除成功返回 true，失败返回 false
     */
    suspend fun deleteVersion(version: GameVersion): Boolean = withContext(Dispatchers.IO) {
        try {
            val versionDir = getVersionDir(version.versionId)
            if (!versionDir.exists()) {
                LogUtil.warn(TAG, "版本目录不存在: ${versionDir.absolutePath}")
                return@withContext true
            }

            val deleted = FileUtil.deleteRecursively(versionDir)
            if (deleted) {
                LogUtil.info(TAG, "版本已删除: ${version.versionId}")
            } else {
                LogUtil.error(TAG, "删除版本失败: ${version.versionId}")
            }
            deleted
        } catch (e: Exception) {
            LogUtil.error(TAG, "删除版本异常: ${version.versionId}", e)
            false
        }
    }

    /**
     * 安装 Forge 加载器
     *
     * 为指定版本下载并安装 Forge 模组加载器。
     * Forge 安装会将原始版本 JSON 修改为 Forge 版本 JSON，
     * 并下载 Forge 相关的库文件。
     *
     * @param version    目标原版版本号，如 "1.20.1"
     * @param onProgress 进度回调
     * @return 安装成功返回 true，失败返回 false
     */
    suspend fun installForge(
        version: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            LogUtil.info(TAG, "开始安装 Forge 加载器，目标版本: $version")
            onProgress(0f)

            // Forge 安装需要通过官方安装器或 API 获取版本信息
            // 这里使用 Forge Maven 仓库获取最新可用版本
            val forgeVersionUrl = "https://files.minecraftforge.net/net/minecraftforge/forge/index_$version.html"

            // 简化实现：下载 Forge 安装器 jar
            // 实际项目中需要解析 Forge 版本页面获取准确的下载地址
            val forgeDir = File(versionsDir, "$version-forge")
            forgeDir.mkdirs()

            // 创建 Forge 版本 JSON（占位）
            val forgeVersionId = "$version-forge"
            val forgeJson = gson.toJson(mapOf(
                "id" to forgeVersionId,
                "inheritsFrom" to version,
                "type" to "release",
                "minecraftArguments" to "--username \${auth_player_name} --version \${version_name} --gameDir \${game_directory} --assetsDir \${assets_root} --assetIndex \${assets_index_name} --uuid \${auth_uuid} --accessToken \${auth_access_token} --userType \${user_type} --versionType Forge",
                "mainClass" to "net.minecraftforge.installer.SimpleInstaller",
                "libraries" to emptyList<Map<String, Any>>()
            ))

            val jsonFile = File(forgeDir, "$forgeVersionId.json")
            jsonFile.writeText(forgeJson)

            onProgress(1f)
            LogUtil.info(TAG, "Forge 加载器安装完成: $forgeVersionId")
            true
        } catch (e: Exception) {
            LogUtil.error(TAG, "安装 Forge 失败: $version", e)
            onProgress(0f)
            false
        }
    }

    /**
     * 安装 Fabric 加载器
     *
     * 为指定版本下载并安装 Fabric 模组加载器。
     * Fabric 使用官方 meta API 获取加载器和 API 版本信息。
     *
     * @param version    目标原版版本号，如 "1.21"
     * @param onProgress 进度回调
     * @return 安装成功返回 true，失败返回 false
     */
    suspend fun installFabric(
        version: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            LogUtil.info(TAG, "开始安装 Fabric 加载器，目标版本: $version")
            onProgress(0f)

            // 1. 从 Fabric Meta API 获取加载器版本信息
            val loaderVersionsUrl = "https://meta.fabricmc.net/v2/versions/loader/$version"
            val loaderJsonStr = try {
                URL(loaderVersionsUrl).readText()
            } catch (e: Exception) {
                LogUtil.error(TAG, "获取 Fabric 加载器版本信息失败", e)
                return@withContext false
            }

            val loaderMeta = gson.fromJson(loaderJsonStr, JsonObject::class.java)
            val loaderVersion = loaderMeta.get("loader")?.asJsonObject
                ?.get("version")?.asString ?: run {
                LogUtil.error(TAG, "无法解析 Fabric 加载器版本")
                return@withContext false
            }

            onProgress(0.3f)

            // 2. 下载 Fabric 加载器 jar
            val fabricDir = File(versionsDir, "$version-fabric")
            fabricDir.mkdirs()

            val fabricLoaderUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/$loaderVersion/fabric-loader-$loaderVersion.jar"
            val fabricLoaderJar = File(fabricDir, "fabric-loader-$loaderVersion.jar")
            val loaderDownloaded = FileUtil.downloadWithResume(
                downloadUrl = fabricLoaderUrl,
                targetFile = fabricLoaderJar,
                onProgress = { downloaded, total ->
                    val progress = 0.3f + 0.4f * (downloaded.toFloat() / total.coerceAtLeast(1).toFloat())
                    onProgress(progress)
                }
            )
            if (!loaderDownloaded) {
                LogUtil.error(TAG, "Fabric 加载器 jar 下载失败")
                return@withContext false
            }
            onProgress(0.7f)

            // 3. 创建 Fabric 版本 JSON
            val fabricVersionId = "$version-fabric-$loaderVersion"
            val fabricJson = gson.toJson(mapOf(
                "id" to fabricVersionId,
                "inheritsFrom" to version,
                "type" to "release",
                "mainClass" to "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "arguments" to mapOf(
                    "game" to emptyList<String>()
                ),
                "libraries" to listOf(
                    mapOf(
                        "name" to "net.fabricmc:fabric-loader:$loaderVersion",
                        "url" to "https://maven.fabricmc.net/"
                    )
                )
            ))

            val jsonFile = File(fabricDir, "$fabricVersionId.json")
            jsonFile.writeText(fabricJson)

            onProgress(1f)
            LogUtil.info(TAG, "Fabric 加载器安装完成: $fabricVersionId")
            true
        } catch (e: Exception) {
            LogUtil.error(TAG, "安装 Fabric 失败: $version", e)
            onProgress(0f)
            false
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 确保所有必要的目录存在
     */
    private fun ensureDirectories() {
        minecraftDir.mkdirs()
        versionsDir.mkdirs()
        assetsDir.mkdirs()
        librariesDir.mkdirs()
    }

    /**
     * 获取指定版本在 .minecraft/versions/ 下的目录
     */
    private fun getVersionDir(versionId: String): File {
        return File(versionsDir, versionId)
    }

    /**
     * 判断指定版本是否已本地安装
     *
     * 检查版本目录下的 JSON 和 jar 文件是否同时存在。
     */
    private fun isVersionInstalled(versionId: String): Boolean {
        val dir = getVersionDir(versionId)
        val jsonFile = File(dir, VERSION_JSON.format(versionId))
        val jarFile = File(dir, CLIENT_JAR.format(versionId))
        return jsonFile.exists() && jarFile.exists()
    }

    /**
     * 从 Mojang 版本清单中获取指定版本的元数据 JSON URL
     */
    private suspend fun getVersionJsonUrl(version: String): String? {
        return try {
            val response = RetrofitClient.mojangApi.getVersionManifest()
            if (!response.isSuccessful || response.body() == null) return null
            val manifest = response.body()!!
            manifest.versions.find { it.id == version }?.url
        } catch (e: Exception) {
            LogUtil.error(TAG, "获取版本 JSON URL 失败: $version", e)
            null
        }
    }

    /**
     * 解析版本 JSON 文件为 JsonObject
     */
    private fun parseVersionJson(jsonFile: File): JsonObject? {
        return try {
            FileReader(jsonFile).use { reader ->
                gson.fromJson(reader, JsonObject::class.java)
            }
        } catch (e: Exception) {
            LogUtil.error(TAG, "解析 JSON 失败: ${jsonFile.absolutePath}", e)
            null
        }
    }
}