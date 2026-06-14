package com.mymc.launcher.service.version

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mymc.launcher.data.remote.RetrofitClient
import com.mymc.launcher.domain.model.GameVersion
import com.mymc.launcher.domain.model.GameVersionType
import com.mymc.launcher.service.download.MirrorManager
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
 * 参照 FCL 的 DefaultGameRepository + DefaultDependencyManager 设计：
 * - 对接 Mojang API（经 BMCLAPI 国内镜像）
 * - 下载版本 JSON、核心 jar、libraries 及 assets 资源
 * - 扫描本地已安装版本
 * - 安装 Forge / Fabric 等模组加载器
 * - 版本隔离：每个版本独立目录 (.minecraft/versions/{versionId}/)
 *
 * FCL 参考：
 * - com.tungsten.fclcore.game.DefaultGameRepository
 * - com.tungsten.fclcore.download.DefaultDependencyManager
 * - com.tungsten.fclcore.download.VersionList
 */
class VersionManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "VersionManager"
        private const val VERSIONS_DIR = "versions"
        private const val ASSETS_DIR = "assets"
        private const val LIBRARIES_DIR = "libraries"

        @Volatile private var instance: VersionManager? = null

        fun getInstance(context: Context): VersionManager {
            return instance ?: synchronized(this) {
                instance ?: VersionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // ==================== 目录结构（参照 FCL DefaultGameRepository） ====================

    private val minecraftDir: File get() = File(context.filesDir, ".minecraft")
    private val versionsDir: File get() = File(minecraftDir, VERSIONS_DIR)
    private val assetsDir: File get() = File(minecraftDir, ASSETS_DIR)
    private val librariesDir: File get() = File(minecraftDir, LIBRARIES_DIR)

    private val gson = Gson()

    // ==================== 版本列表获取 ====================

    /**
     * 从 Mojang API（经 BMCLAPI 镜像）获取版本清单
     *
     * 返回 release + snapshot 全部版本，版本列表页自行过滤。
     */
    suspend fun fetchVersionList(
        onResult: (List<GameVersion>) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            LogUtil.info(TAG, "正在从镜像源获取版本清单...")
            val manifestUrl = MirrorManager.getVersionManifestUrl()
            val response = RetrofitClient.mojangApi.getVersionManifest()
            if (!response.isSuccessful || response.body() == null) {
                LogUtil.error(TAG, "获取版本清单失败: HTTP ${response.code()}")
                onResult(emptyList())
                return@withContext
            }

            val manifest = response.body()!!
            val versions = manifest.versions
                .filter { it.type == "release" }
                .map { entry ->
                    GameVersion(
                        id = "vanilla_${entry.id}",
                        versionId = entry.id,
                        type = GameVersionType.VANILLA,
                        downloadUrl = MirrorManager.convertUrl(entry.url),
                        fileSize = 0,
                        installed = isVersionInstalled(entry.id)
                    )
                }
            LogUtil.info(TAG, "获取到 ${versions.size} 个正式版")
            onResult(versions)
        } catch (e: Exception) {
            LogUtil.error(TAG, "获取版本清单异常", e)
            onResult(emptyList())
        }
    }

    // ==================== 版本下载与安装（参照 FCL GameDownloadTask + GameLibrariesTask + GameAssetDownloadTask） ====================

    /**
     * 下载并安装完整 Minecraft 版本
     *
     * 流程（对应 FCL 的 checkGameCompletionAsync）：
     * 1. 下载版本 JSON 元数据
     * 2. 下载客户端 jar (client.jar)
     * 3. 下载所有 libraries（参照 FCL GameLibrariesTask）
     * 4. 下载 assets 资源索引与资源文件（参照 FCL GameAssetDownloadTask）
     *
     * @param version    游戏版本号，如 "1.21"
     * @param onProgress 进度回调 (0.0~1.0)
     * @return 安装成功返回 true
     */
    suspend fun downloadVersion(
        version: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            LogUtil.info(TAG, "============================================")
            LogUtil.info(TAG, "开始下载版本: $version")
            LogUtil.info(TAG, "当前镜像源: ${MirrorManager.currentMirror.name}")
            LogUtil.info(TAG, "============================================")

            ensureDirectories()
            onProgress(0f)

            // ── 阶段 1: 下载版本 JSON (0% → 2%) ──
            val versionJsonUrl = getVersionJsonUrl(version)
            if (versionJsonUrl.isNullOrEmpty()) {
                LogUtil.error(TAG, "无法获取版本 $version 的元数据 URL")
                return@withContext false
            }
            // 使用 MirrorManager 转换 URL
            val mirroredJsonUrl = MirrorManager.convertUrl(versionJsonUrl)

            val versionDir = File(versionsDir, version)
            versionDir.mkdirs()

            val jsonFile = File(versionDir, "$version.json")
            val jsonDownloaded = FileUtil.downloadWithResume(
                downloadUrl = mirroredJsonUrl,
                targetFile = jsonFile
            )
            if (!jsonDownloaded) {
                LogUtil.error(TAG, "版本 JSON 下载失败")
                return@withContext false
            }
            onProgress(0.02f)

            // ── 阶段 2: 解析版本 JSON ──
            val versionMeta = parseJsonFile(jsonFile)
            if (versionMeta == null) {
                LogUtil.error(TAG, "版本 JSON 解析失败")
                return@withContext false
            }

            val downloads = versionMeta.get("downloads")?.asJsonObject
            val clientDownload = downloads?.get("client")?.asJsonObject
            val clientJarUrl = clientDownload?.get("url")?.asString
            val clientSha1 = clientDownload?.get("sha1")?.asString
            val clientSize = clientDownload?.get("size")?.asLong ?: 0L

            val assetIndex = versionMeta.get("assetIndex")?.asJsonObject
            val assetIndexUrl = assetIndex?.get("url")?.asString
            val assetIndexId = assetIndex?.get("id")?.asString
            val assetIndexSha1 = assetIndex?.get("sha1")?.asString

            val libraries = versionMeta.get("libraries")?.asJsonArray ?: JsonArray()
            val totalLibraries = libraries.size()

            // ── 阶段 3: 下载客户端 jar (2% → 22%) ──
            val clientJarFile = File(versionDir, "$version.jar")
            if (clientJarUrl != null) {
                val jarDownloaded = FileUtil.downloadWithResume(
                    downloadUrl = MirrorManager.convertUrl(clientJarUrl),
                    targetFile = clientJarFile,
                    onProgress = { downloaded, total ->
                        val p = 0.02f + 0.20f * (downloaded.toFloat() / total.coerceAtLeast(1).toFloat())
                        onProgress(p)
                    }
                )
                if (!jarDownloaded) {
                    LogUtil.error(TAG, "客户端 jar 下载失败")
                    return@withContext false
                }
                // SHA1 校验
                if (!clientSha1.isNullOrEmpty()) {
                    val actual = HashUtil.sha1File(clientJarFile)
                    if (!actual.equals(clientSha1, ignoreCase = true)) {
                        LogUtil.error(TAG, "客户端 jar SHA1 校验失败: 期望 $clientSha1, 实际 $actual")
                        clientJarFile.delete()
                        return@withContext false
                    }
                }
            }
            onProgress(0.22f)

            // ── 阶段 4: 下载 libraries (22% → 52%) ──
            // 参照 FCL GameLibrariesTask：检查每个 library 是否存在，如果不存在则下载
            var completedLibs = 0
            for (lib in libraries) {
                try {
                    val libObj = lib.asJsonObject
                    val downloads2 = libObj.get("downloads")?.asJsonObject
                    val artifact = downloads2?.get("artifact")?.asJsonObject
                    if (artifact == null) {
                        completedLibs++
                        continue
                    }
                    val libUrl = artifact.get("url")?.asString
                    val libPath = artifact.get("path")?.asString
                    val libSha1 = artifact.get("sha1")?.asString
                    val libSize = artifact.get("size")?.asLong ?: 0L

                    if (libUrl.isNullOrEmpty() || libPath.isNullOrEmpty()) {
                        completedLibs++
                        continue
                    }

                    // 检查是否为当前平台需要的 library（native 过滤）
                    val rules = libObj.get("rules")?.asJsonArray
                    if (rules != null && !checkRules(rules)) {
                        completedLibs++
                        continue
                    }

                    val libFile = File(librariesDir, libPath)
                    if (!libFile.exists() || libFile.length() == 0L) {
                        libFile.parentFile?.mkdirs()
                        FileUtil.downloadWithResume(
                            downloadUrl = MirrorManager.convertUrl(libUrl),
                            targetFile = libFile
                        )
                    }
                } catch (e: Exception) {
                    LogUtil.warn(TAG, "Library 下载失败: ${lib.asJsonObject.get("name")?.asString ?: "未知"}, 原因: ${e.message}")
                }
                completedLibs++
                val p = 0.22f + 0.30f * (completedLibs.toFloat() / totalLibraries.coerceAtLeast(1).toFloat())
                onProgress(p)
            }
            onProgress(0.52f)

            // ── 阶段 5: 下载 assets (52% → 100%) ──
            if (assetIndexUrl != null && assetIndexId != null) {
                val indexDir = File(assetsDir, "indexes")
                indexDir.mkdirs()
                val indexFile = File(indexDir, "$assetIndexId.json")

                // 下载 asset index JSON
                if (!indexFile.exists()) {
                    val idxDownloaded = FileUtil.downloadWithResume(
                        downloadUrl = MirrorManager.convertUrl(assetIndexUrl),
                        targetFile = indexFile
                    )
                    if (!idxDownloaded) {
                        LogUtil.warn(TAG, "Asset index 下载失败，跳过资源")
                        onProgress(1f)
                        LogUtil.info(TAG, "版本 $version 安装完成（不含资源）")
                        return@withContext true
                    }
                }

                // 解析 asset index 并下载
                val index = parseJsonFile(indexFile)
                if (index != null) {
                    val objects = index.get("objects")?.asJsonObject
                    if (objects != null) {
                        val entries = objects.entrySet().toList()
                        val totalObjects = entries.size
                        var completedObjects = 0

                        for (entry in entries) {
                            val obj = entry.value.asJsonObject
                            val hash = obj.get("hash")?.asString ?: continue
                            val subPath = hash.substring(0, 2)
                            val assetFile = File(assetsDir, "objects/$subPath/$hash")

                            if (!assetFile.exists() || assetFile.length() == 0L) {
                                assetFile.parentFile?.mkdirs()
                                FileUtil.downloadWithResume(
                                    downloadUrl = MirrorManager.getAssetUrl(hash),
                                    targetFile = assetFile
                                )
                            }
                            completedObjects++
                            // 只在每 50 个资源时更新进度
                            if (completedObjects % 50 == 0 || completedObjects == totalObjects) {
                                val p = 0.52f + 0.48f * (completedObjects.toFloat() / totalObjects.toFloat())
                                onProgress(p)
                            }
                        }
                    }
                }
            }

            onProgress(1f)
            LogUtil.info(TAG, "============================================")
            LogUtil.info(TAG, "版本 $version 安装完成!")
            LogUtil.info(TAG, "============================================")
            true
        } catch (e: Exception) {
            LogUtil.error(TAG, "下载版本 $version 失败", e)
            onProgress(0f)
            false
        }
    }

    // ==================== 本地版本扫描 ====================

    /**
     * 扫描本地已安装版本
     *
     * 参照 FCL DefaultGameRepository.refreshVersions：
     * 遍历 .minecraft/versions/ 目录，读取 JSON 判断版本类型。
     */
    suspend fun scanLocalVersions(): List<GameVersion> = withContext(Dispatchers.IO) {
        try {
            if (!versionsDir.exists()) return@withContext emptyList()
            val dirs = FileUtil.listDirectories(versionsDir)
            dirs.mapNotNull { dir ->
                val versionId = dir.name
                val jsonFile = File(dir, "$versionId.json")
                val jarFile = File(dir, "$versionId.jar")
                if (jsonFile.exists() && jarFile.exists()) {
                    val meta = parseJsonFile(jsonFile)
                    val type = detectVersionType(meta, versionId)
                    GameVersion(
                        id = "${type.name.lowercase()}_$versionId",
                        versionId = versionId,
                        type = type,
                        downloadUrl = "",
                        fileSize = FileUtil.calculateSize(dir),
                        installed = true,
                        installPath = dir.absolutePath
                    )
                } else null
            }
        } catch (e: Exception) {
            LogUtil.error(TAG, "扫描本地版本失败", e)
            emptyList()
        }
    }

    // ==================== Forge / Fabric 安装 ====================

    /**
     * 安装 Fabric 加载器
     *
     * 参照 FCL：使用 Fabric Meta API 获取加载器版本信息
     * 下载 fabric-loader jar 并创建版本 JSON
     */
    suspend fun installFabric(
        version: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            LogUtil.info(TAG, "开始安装 Fabric，目标版本: $version")
            onProgress(0f)

            // 1. 从 Fabric Meta API 获取最新加载器版本
            val loaderMetaUrl = "https://meta.fabricmc.net/v2/versions/loader/$version"
            val loaderJsonStr = try {
                URL(loaderMetaUrl).readText()
            } catch (e: Exception) {
                LogUtil.error(TAG, "获取 Fabric 加载器信息失败", e)
                return@withContext false
            }
            val loaderMeta = gson.fromJson(loaderJsonStr, JsonObject::class.java)
            val loaderObj = loaderMeta.get("loader")?.asJsonArray?.firstOrNull()?.asJsonObject
            val loaderVersion = loaderObj?.get("version")?.asString ?: return@withContext false.also {
                LogUtil.error(TAG, "无法解析 Fabric 加载器版本")
            }
            onProgress(0.2f)

            // 2. 下载 intermediary mappings
            val intermediaryObj = loaderMeta.get("intermediary")?.asJsonArray
                ?.firstOrNull()?.asJsonObject
            val intermediaryVersion = intermediaryObj?.get("version")?.asString
            onProgress(0.3f)

            // 3. 下载 fabric-loader jar
            val fabricDir = File(versionsDir, "$version-fabric-$loaderVersion")
            fabricDir.mkdirs()

            val loaderMavenPath = "net/fabricmc/fabric-loader/$loaderVersion/fabric-loader-$loaderVersion.jar"
            val loaderUrl = MirrorManager.convertUrl("https://maven.fabricmc.net/$loaderMavenPath")
            val loaderJar = File(librariesDir, loaderMavenPath)
            loaderJar.parentFile?.mkdirs()

            if (!loaderJar.exists()) {
                FileUtil.downloadWithResume(
                    downloadUrl = loaderUrl,
                    targetFile = loaderJar,
                    onProgress = { d, t ->
                        val p = 0.3f + 0.4f * (d.toFloat() / t.coerceAtLeast(1).toFloat())
                        onProgress(p)
                    }
                )
            }
            onProgress(0.7f)

            // 4. 创建 Fabric 版本 JSON
            val fabricVersionId = "$version-fabric-$loaderVersion"
            val fabricJson = JsonObject().apply {
                addProperty("id", fabricVersionId)
                addProperty("inheritsFrom", version)
                addProperty("type", "release")
                addProperty("mainClass", "net.fabricmc.loader.impl.launch.knot.KnotClient")
                add("arguments", JsonObject().apply {
                    add("game", JsonArray())
                })
                add("libraries", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("name", "net.fabricmc:fabric-loader:$loaderVersion")
                        add("downloads", JsonObject().apply {
                            add("artifact", JsonObject().apply {
                                addProperty("path", loaderMavenPath)
                                addProperty("url", "https://maven.fabricmc.net/")
                            })
                        })
                    })
                    if (intermediaryVersion != null) {
                        add(JsonObject().apply {
                            addProperty("name", "net.fabricmc:intermediary:$intermediaryVersion")
                            add("downloads", JsonObject().apply {
                                add("artifact", JsonObject().apply {
                                    addProperty("path", "net/fabricmc/intermediary/$intermediaryVersion/intermediary-$intermediaryVersion.jar")
                                    addProperty("url", "https://maven.fabricmc.net/")
                                })
                            })
                        })
                    }
                })
            }

            File(fabricDir, "$fabricVersionId.json").writeText(gson.toJson(fabricJson))

            onProgress(1f)
            LogUtil.info(TAG, "Fabric 加载器安装完成: $fabricVersionId")
            true
        } catch (e: Exception) {
            LogUtil.error(TAG, "安装 Fabric 失败: $version", e)
            false
        }
    }

    /**
     * 安装 Forge 加载器
     */
    suspend fun installForge(
        version: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            LogUtil.info(TAG, "开始安装 Forge，目标版本: $version")
            onProgress(0f)

            // Forge 需要通过其 Maven 仓库获取安装器
            // 简化实现：扫描 Forge Maven 获取最新版本
            val forgeMavenUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml"
            val metadata = try {
                URL(forgeMavenUrl).readText()
            } catch (e: Exception) {
                LogUtil.error(TAG, "获取 Forge Maven 元数据失败", e)
                return@withContext false
            }

            // 从 maven-metadata.xml 中解析版本号
            val versionRegex = Regex("<version>($version-[^<]+)</version>")
            val matches = versionRegex.findAll(metadata).toList()
            if (matches.isEmpty()) {
                LogUtil.error(TAG, "未找到 Forge 版本: $version")
                return@withContext false
            }

            // 取最新版本（列表最后一个通常是最高版本）
            val forgeFullVersion = matches.last().groupValues[1]
            val forgeVersionId = "$version-forge-$forgeFullVersion"
            LogUtil.info(TAG, "使用 Forge 版本: $forgeFullVersion")

            onProgress(0.3f)

            // 下载 Forge 安装器
            val forgeDir = File(versionsDir, forgeVersionId)
            forgeDir.mkdirs()

            val installerPath = "net/minecraftforge/forge/$forgeFullVersion/forge-$forgeFullVersion-installer.jar"
            val installerUrl = MirrorManager.convertUrl("https://maven.minecraftforge.net/$installerPath")
            val installerJar = File(librariesDir, installerPath)
            installerJar.parentFile?.mkdirs()

            if (!installerJar.exists()) {
                FileUtil.downloadWithResume(
                    downloadUrl = installerUrl,
                    targetFile = installerJar,
                    onProgress = { d, t ->
                        val p = 0.3f + 0.5f * (d.toFloat() / t.coerceAtLeast(1).toFloat())
                        onProgress(p)
                    }
                )
            }
            onProgress(0.8f)

            // 创建 Forge 版本 JSON
            val forgeJson = JsonObject().apply {
                addProperty("id", forgeVersionId)
                addProperty("inheritsFrom", version)
                addProperty("type", "release")
                addProperty("mainClass", "cpw.mods.bootstraplauncher.BootstrapLauncher")
                add("arguments", JsonObject().apply {
                    add("game", JsonArray().apply {
                        add("--username")
                        add("\${auth_player_name}")
                        add("--version")
                        add("\${version_name}")
                        add("--gameDir")
                        add("\${game_directory}")
                        add("--assetsDir")
                        add("\${assets_root}")
                        add("--assetIndex")
                        add("\${assets_index_name}")
                        add("--uuid")
                        add("\${auth_uuid}")
                        add("--accessToken")
                        add("\${auth_access_token}")
                        add("--userType")
                        add("\${user_type}")
                        add("--versionType")
                        add("Forge")
                    })
                })
                add("libraries", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("name", "net.minecraftforge:forge:$forgeFullVersion")
                        add("downloads", JsonObject().apply {
                            add("artifact", JsonObject().apply {
                                addProperty("path", installerPath)
                                addProperty("url", "https://maven.minecraftforge.net/")
                            })
                        })
                    })
                })
            }

            File(forgeDir, "$forgeVersionId.json").writeText(gson.toJson(forgeJson))

            onProgress(1f)
            LogUtil.info(TAG, "Forge 加载器安装完成: $forgeVersionId")
            true
        } catch (e: Exception) {
            LogUtil.error(TAG, "安装 Forge 失败: $version", e)
            false
        }
    }

    // ==================== 版本删除 ====================

    suspend fun deleteVersion(version: GameVersion): Boolean = withContext(Dispatchers.IO) {
        try {
            val versionDir = File(versionsDir, version.versionId)
            if (!versionDir.exists()) return@withContext true
            FileUtil.deleteRecursively(versionDir)
        } catch (e: Exception) {
            LogUtil.error(TAG, "删除版本失败: ${version.versionId}", e)
            false
        }
    }

    /**
     * 获取版本运行目录
     */
    fun getRunDirectory(versionId: String): File {
        val dir = File(minecraftDir, "runs/$versionId")
        dir.mkdirs()
        return dir
    }

    // ==================== 私有辅助方法 ====================

    private fun ensureDirectories() {
        minecraftDir.mkdirs()
        versionsDir.mkdirs()
        assetsDir.mkdirs()
        librariesDir.mkdirs()
    }

    private fun isVersionInstalled(versionId: String): Boolean {
        val dir = File(versionsDir, versionId)
        return File(dir, "$versionId.json").exists() && File(dir, "$versionId.jar").exists()
    }

    private suspend fun getVersionJsonUrl(version: String): String? {
        return try {
            val response = RetrofitClient.mojangApi.getVersionManifest()
            if (!response.isSuccessful || response.body() == null) return null
            response.body()!!.versions.find { it.id == version }?.url
        } catch (e: Exception) {
            LogUtil.error(TAG, "获取版本 JSON URL 失败: $version", e)
            null
        }
    }

    private fun parseJsonFile(file: File): JsonObject? {
        return try {
            FileReader(file).use { reader -> gson.fromJson(reader, JsonObject::class.java) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 检查 library 的 rules 是否允许当前平台
     *
     * 参照 FCL Library.appliesToCurrentEnvironment：
     * rules 为空或第一条匹配 os name 为 "linux" 且 action 为 "allow"
     */
    private fun checkRules(rules: JsonArray): Boolean {
        for (rule in rules) {
            val ruleObj = rule.asJsonObject
            val action = ruleObj.get("action")?.asString ?: "allow"
            val os = ruleObj.get("os")?.asJsonObject
            if (os != null) {
                val osName = os.get("name")?.asString
                // Android 运行 Minecraft 时 os.name 设为 "Linux"
                if (osName == "linux" || osName == null) {
                    return action == "allow"
                }
            }
        }
        return true
    }

    /**
     * 检测版本类型（原版 / Forge / Fabric）
     *
     * 参照 FCL LibraryAnalyzer.analyze：
     * 根据 mainClass 或 inheritsFrom + libraries 判断。
     */
    private fun detectVersionType(meta: JsonObject?, versionId: String): GameVersionType {
        if (meta == null) return GameVersionType.VANILLA
        val mainClass = meta.get("mainClass")?.asString ?: ""
        return when {
            mainClass.contains("fabricmc", ignoreCase = true) -> GameVersionType.FABRIC
            mainClass.contains("forge", ignoreCase = true) ||
            mainClass.contains("bootstraplauncher", ignoreCase = true) -> GameVersionType.FORGE
            versionId.contains("forge", ignoreCase = true) -> GameVersionType.FORGE
            versionId.contains("fabric", ignoreCase = true) -> GameVersionType.FABRIC
            else -> GameVersionType.VANILLA
        }
    }
}