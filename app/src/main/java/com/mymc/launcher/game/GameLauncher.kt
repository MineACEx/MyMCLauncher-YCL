package com.mymc.launcher.game

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mymc.launcher.data.local.PreferencesManager
import com.mymc.launcher.domain.model.GameVersion
import com.mymc.launcher.service.version.VersionManager
import com.mymc.launcher.util.LogUtil
import com.mymc.launcher.ui.activity.GameLaunchActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader

/**
 * 游戏启动器
 *
 * 参照 FCL 的 DefaultLauncher + FCLGameLauncher 设计：
 * - 解析版本 JSON 获取 mainClass、libraries 等信息
 * - 构建 classpath
 * - 生成启动参数
 * - 启动游戏进程（通过 GameLaunchActivity）
 *
 * FCL 参考：
 * - com.tungsten.fclcore.launch.DefaultLauncher
 * - com.tungsten.fcl.game.FCLGameLauncher
 * - com.tungsten.fclcore.launch.Launcher
 */
object GameLauncher {
    private const val TAG = "GameLauncher"

    private val gson = Gson()

    /**
     * 启动游戏
     *
     * @param context 上下文
     * @param version 要启动的版本
     */
    suspend fun launch(context: Context, version: GameVersion): Boolean = withContext(Dispatchers.IO) {
        try {
            LogUtil.info(TAG, "准备启动游戏: ${version.versionId}")

            val versionManager = VersionManager.getInstance(context)
            val prefsManager = PreferencesManager.getInstance(context)
            val versionDir = File(context.filesDir, ".minecraft/versions/${version.versionId}")

            // 1. 解析版本 JSON
            val jsonFile = File(versionDir, "${version.versionId}.json")
            if (!jsonFile.exists()) {
                LogUtil.error(TAG, "版本 JSON 不存在: ${jsonFile.absolutePath}")
                return@withContext false
            }

            val versionMeta = try {
                FileReader(jsonFile).use { reader ->
                    gson.fromJson(reader, JsonObject::class.java)
                }
            } catch (e: Exception) {
                LogUtil.error(TAG, "解析版本 JSON 失败", e)
                return@withContext false
            }

            // 2. 获取主类
            val mainClass = versionMeta.get("mainClass")?.asString
            if (mainClass.isNullOrEmpty()) {
                LogUtil.error(TAG, "版本 JSON 缺少 mainClass")
                return@withContext false
            }

            // 3. 构建 classpath
            val classpath = buildClasspath(versionMeta, versionDir, context)

            // 4. 获取资源目录
            val assetIndex = versionMeta.get("assetIndex")?.asJsonObject
            val assetIndexId = assetIndex?.get("id")?.asString ?: version.versionId

            val assetsDir = File(context.filesDir, ".minecraft/assets")
            val runDir = versionManager.getRunDirectory(version.versionId)

            // 5. 从 PreferencesManager 读取 JVM 参数和 DPI
            val javaArgs = prefsManager.jvmArgsFlow.first()
            val customDpi = prefsManager.dpiFlow.first()
            LogUtil.info(TAG, "JVM 参数: $javaArgs, 自定义 DPI: $customDpi")

            // 6. 构建启动参数
            val launchArgs = buildLaunchArgs(
                version = version,
                mainClass = mainClass,
                classpath = classpath,
                assetIndexId = assetIndexId,
                assetsDir = assetsDir,
                gameDir = runDir,
                javaArgs = javaArgs,
                customDpi = customDpi
            )

            LogUtil.info(TAG, "启动参数: ${launchArgs.joinToString(" ")}")

            // 7. 启动 GameLaunchActivity
            val intent = Intent(context, GameLaunchActivity::class.java).apply {
                putExtra("version_id", version.versionId)
                putExtra("main_class", mainClass)
                putExtra("classpath", classpath)
                putExtra("launch_args", launchArgs)
                putExtra("game_dir", runDir.absolutePath)
                putExtra("assets_dir", assetsDir.absolutePath)
                putExtra("asset_index", assetIndexId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            LogUtil.info(TAG, "游戏启动请求已发送: ${version.versionId}")
            true
        } catch (e: Exception) {
            LogUtil.error(TAG, "启动游戏失败: ${version.versionId}", e)
            false
        }
    }

    /**
     * 构建 classpath
     *
     * 参照 FCL DefaultGameRepository.getClasspath + DefaultLauncher.generateCommandLine
     */
    private fun buildClasspath(
        versionMeta: JsonObject,
        versionDir: File,
        context: Context
    ): Array<String> {
        val classpath = mutableListOf<String>()

        // 添加 libraries
        val libraries = versionMeta.get("libraries")?.asJsonArray
        if (libraries != null) {
            val librariesDir = File(context.filesDir, ".minecraft/libraries")
            for (lib in libraries) {
                try {
                    val libObj = lib.asJsonObject
                    val downloads = libObj.get("downloads")?.asJsonObject
                    val artifact = downloads?.get("artifact")?.asJsonObject
                    if (artifact == null) continue

                    val libPath = artifact.get("path")?.asString ?: continue
                    val libFile = File(librariesDir, libPath)

                    // 跳过 native 库
                    val classifiers = libObj.get("natives")?.asJsonObject
                    if (classifiers != null) continue

                    if (libFile.exists()) {
                        classpath.add(libFile.absolutePath)
                    }
                } catch (_: Exception) { }
            }
        }

        // 添加客户端 jar
        val jarFile = File(versionDir, "${versionDir.name}.jar")
        if (jarFile.exists()) {
            classpath.add(jarFile.absolutePath)
        }

        return classpath.toTypedArray()
    }

    /**
     * 构建启动参数
     *
     * 参照 FCL DefaultLauncher.generateCommandLine
     */
    private fun buildLaunchArgs(
        version: GameVersion,
        mainClass: String,
        classpath: Array<String>,
        assetIndexId: String,
        assetsDir: File,
        gameDir: File,
        javaArgs: String,
        customDpi: Int = 0
    ): Array<String> {
        val args = mutableListOf<String>()

        // JVM 参数（从 PreferencesManager 读取）
        if (javaArgs.isNotBlank()) {
            javaArgs.split("\\s+".toRegex()).forEach { arg ->
                if (arg.isNotBlank()) args.add(arg)
            }
        } else {
            args.add("-Xmx2G")
            args.add("-XX:+UseG1GC")
        }
        args.add("-Djava.library.path=${gameDir.absolutePath}/natives")
        args.add("-Dminecraft.client.jar=${gameDir.absolutePath}/${version.versionId}.jar")
        args.add("-Duser.home=${gameDir.absolutePath}")
        args.add("-Dlog4j2.formatMsgNoLookups=true")

        // 主类
        args.add(mainClass)

        // 游戏参数
        args.add("--username")
        args.add("Player")
        args.add("--version")
        args.add(version.versionId)
        args.add("--gameDir")
        args.add(gameDir.absolutePath)
        args.add("--assetsDir")
        args.add(assetsDir.absolutePath)
        args.add("--assetIndex")
        args.add(assetIndexId)
        args.add("--uuid")
        args.add("00000000-0000-0000-0000-000000000000")
        args.add("--accessToken")
        args.add("0")
        args.add("--userType")
        args.add("mojang")
        args.add("--versionType")
        args.add("release")

        // 自定义 DPI（> 0 时生效）
        if (customDpi > 0) {
            args.add("--width")
            args.add("${customDpi}")
            LogUtil.info(TAG, "应用自定义 DPI: $customDpi")
        }

        return args.toTypedArray()
    }
}