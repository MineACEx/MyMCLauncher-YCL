package com.mymc.launcher.service.game

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mymc.launcher.domain.model.AccountInfo
import com.mymc.launcher.domain.model.AccountType
import com.mymc.launcher.domain.model.GameVersionType
import com.mymc.launcher.service.account.AccountManager
import com.mymc.launcher.service.process.ProcessLogLine
import com.mymc.launcher.service.process.ProcessManager
import com.mymc.launcher.service.version.VersionManager
import com.mymc.launcher.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileReader

/**
 * Minecraft 游戏启动核心类
 *
 * 负责构建完整的 Minecraft 启动命令行，整合：
 * - Java 运行时路径
 * - JVM 启动参数
 * - 游戏版本参数（主类、classpath）
 * - Forge / Fabric 加载器参数
 * - 玩家账号认证参数
 *
 * 启动流程：
 * 1. buildLaunchCommand() 构建命令
 * 2. launch() 通过 ProcessManager 执行命令
 * 3. 通过 callback / Flow 实时输出启动日志
 *
 * 使用方式：
 * ```kotlin
 * val launcher = GameLauncher(context)
 * launcher.launch(
 *     gameVersion = "1.21",
 *     javaVersion = "21",
 *     jvmArgs = "-Xmx2G",
 *     maxMemory = 2048,
 *     onLog = { log -> println(log) }
 * )
 * ```
 */
class GameLauncher(
    private val context: Context
) {
    companion object {
        private const val TAG = "GameLauncher"

        /** 默认 JVM 参数 */
        private const val DEFAULT_JVM_ARGS = "-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M"

        /** Minecraft 原版主类 */
        private const val VANILLA_MAIN_CLASS = "net.minecraft.client.main.Main"

        /** Minecraft 启动器包装主类 */
        private const val LAUNCHER_MAIN_CLASS = "net.minecraft.launchwrapper.Launch"

        /** Forge 主类 */
        private const val FORGE_MAIN_CLASS = "net.minecraftforge.installer.SimpleInstaller"

        /** 平台库后缀 */
        private val NATIVE_SUFFIX = listOf(
            "linux-aarch64", "linux-arm64",
            "natives-linux", "natives-linux-aarch64"
        )
    }

    /** Gson 实例，用于解析版本 JSON */
    private val gson = Gson()

    /** 进程管理器 */
    private val processManager = ProcessManager()

    /** 账号管理器 */
    private val accountManager = AccountManager.getInstance(context)

    /**
     * 构建完整的 Minecraft 启动命令行
     *
     * 生成类似以下的命令：
     * ```
     * java -Xmx2G -Djava.library.path=natives/ ... -cp classpath mainClass --username Steve ...
     * ```
     *
     * @param gameVersion Minecraft 游戏版本号，如 "1.21"、"1.20.1-forge"
     * @param javaPath    Java 可执行文件的绝对路径
     * @param jvmArgs     JVM 启动参数（覆盖默认值）
     * @param maxMemory   最大内存分配（MB），如 2048
     * @param account     登录账号信息（可选，默认取当前登录账号）
     * @return 启动命令参数列表，构建失败返回空列表
     */
    fun buildLaunchCommand(
        gameVersion: String,
        javaPath: String,
        jvmArgs: String = DEFAULT_JVM_ARGS,
        maxMemory: Int = 2048,
        account: AccountInfo? = null
    ): List<String> {
        LogUtil.info(TAG, "构建启动命令: 版本=$gameVersion, 内存=${maxMemory}M")

        val command = mutableListOf<String>()
        val resolvedAccount = account ?: accountManager.currentAccount.value

        // 确定版本类型（原版/Forge/Fabric）
        val versionType = detectVersionType(gameVersion)

        // 解析版本的元数据 JSON
        val versionMeta = loadVersionMetadata(gameVersion)
        if (versionMeta == null) {
            LogUtil.error(TAG, "无法加载版本元数据: $gameVersion")
            return emptyList()
        }

        // 1. Java 可执行文件路径
        command.add(javaPath)

        // 2. JVM 参数
        // 内存参数
        command.add("-Xmx${maxMemory}M")
        command.add("-Xms${(maxMemory / 2).coerceAtLeast(256)}M")

        // 用户自定义 JVM 参数
        val parsedJvmArgs = jvmArgs.split("\\s+".toRegex()).filter { it.isNotBlank() }
        command.addAll(parsedJvmArgs)

        // 3. 系统属性（-D 参数）
        val nativesDir = getNativesDir(gameVersion)
        command.add("-Djava.library.path=$nativesDir")
        command.add("-Dminecraft.launcher.brand=YCL-Launcher")
        command.add("-Dminecraft.launcher.version=1.0.0")
        command.add("-Dlog4j2.formatMsgNoLookups=true")

        // 4. Classpath
        val classpath = buildClasspath(gameVersion, versionMeta)
        if (classpath.isNotEmpty()) {
            command.add("-cp")
            command.add(classpath)
        }

        // 5. 主类
        val mainClass = getMainClass(versionType, versionMeta)
        command.add(mainClass)

        // 6. 游戏参数
        val gameArgs = buildGameArgs(gameVersion, versionMeta, resolvedAccount, versionType)
        command.addAll(gameArgs)

        LogUtil.info(TAG, "启动命令构建完成，共 ${command.size} 个参数")
        return command
    }

    /**
     * 启动 Minecraft 游戏
     *
     * 先构建启动命令，再通过 ProcessManager 执行并实时输出日志。
     *
     * @param gameVersion MC 版本号
     * @param javaVersion Java 版本号（调用 JavaManager 获取路径）
     * @param jvmArgs     JVM 参数
     * @param maxMemory   最大内存（MB）
     * @param account     登录账号
     * @param onLog       启动日志回调（参数为日志行字符串）
     * @return Flow<ProcessLogLine> 实时日志流
     */
    fun launch(
        gameVersion: String,
        javaVersion: String,
        jvmArgs: String = DEFAULT_JVM_ARGS,
        maxMemory: Int = 2048,
        account: AccountInfo? = null,
        onLog: ((String) -> Unit)? = null
    ): Flow<ProcessLogLine> {
        val javaManager = com.mymc.launcher.service.java.JavaManager.getInstance(context)
        val javaPath = javaManager.getJavaPath(javaVersion)
        if (javaPath == null) {
            LogUtil.error(TAG, "Java $javaVersion 未安装，无法启动游戏")
            throw IllegalStateException("Java $javaVersion 未安装，请先下载安装")
        }

        val command = buildLaunchCommand(
            gameVersion = gameVersion,
            javaPath = javaPath,
            jvmArgs = jvmArgs,
            maxMemory = maxMemory,
            account = account
        )

        if (command.isEmpty()) {
            LogUtil.error(TAG, "启动命令构建失败，无法启动游戏")
            throw IllegalStateException("启动命令构建失败")
        }

        val workDir = File(context.filesDir, ".minecraft")
        val envVars = buildEnvironmentVars()

        return processManager.startGame(
            launchCommand = command,
            javaPath = javaPath,
            envVars = envVars,
            workDir = workDir
        ).also { flow ->
            // 如果提供了 onLog 回调，启动协程收集日志
            if (onLog != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    flow.collect { logLine ->
                        when (logLine) {
                            is ProcessLogLine.StdOut -> onLog("[OUT] ${logLine.line}")
                            is ProcessLogLine.StdErr -> onLog("[ERR] ${logLine.line}")
                            is ProcessLogLine.Exit -> onLog("[EXIT] 退出码: ${logLine.exitCode}")
                        }
                    }
                }
            }
        }
    }

    // ==================== 内部构建方法 ====================

    /**
     * 构建 classpath 字符串
     *
     * 将版本 JSON 中列出的所有 libraries 拼接为 classpath，
     * 并添加核心 client.jar。
     *
     * @param gameVersion 版本号
     * @param versionMeta 版本元数据 JSON
     * @return 平台相关的 classpath 字符串（使用 ":" 分隔）
     */
    private fun buildClasspath(gameVersion: String, versionMeta: JsonObject): String {
        val minecraftDir = File(context.filesDir, ".minecraft")
        val librariesDir = File(minecraftDir, "libraries")
        val versionsDir = File(minecraftDir, "versions")

        val classpathEntries = mutableListOf<String>()

        // 添加客户端 jar
        val clientJar = File(versionsDir, "$gameVersion/$gameVersion.jar")
        if (clientJar.exists()) {
            classpathEntries.add(clientJar.absolutePath)
        }

        // 添加所有依赖库
        val libraries = versionMeta.get("libraries")?.asJsonArray
        if (libraries != null) {
            for (libElement in libraries) {
                val libObj = libElement.asJsonObject
                val name = libObj.get("name")?.asString ?: continue

                // 解析 library 名称获取文件路径
                // 格式: "group:artifact:version"
                val parts = name.split(":")
                if (parts.size < 3) continue

                val group = parts[0]
                val artifact = parts[1]
                val version = parts[2]

                val groupPath = group.replace(".", "/")
                val libDir = File(librariesDir, "$groupPath/$artifact/$version")
                val jarFile = File(libDir, "$artifact-$version.jar")

                if (jarFile.exists()) {
                    classpathEntries.add(jarFile.absolutePath)
                }
            }
        }

        // Linux/Android 使用冒号分隔 classpath
        return classpathEntries.joinToString(":")
    }

    /**
     * 获取 natives 库目录路径
     *
     * @param gameVersion 版本号
     * @return natives 目录路径
     */
    private fun getNativesDir(gameVersion: String): String {
        val minecraftDir = File(context.filesDir, ".minecraft")
        val nativesDir = File(minecraftDir, "versions/$gameVersion/natives")
        if (!nativesDir.exists()) {
            nativesDir.mkdirs()
        }
        return nativesDir.absolutePath
    }

    /**
     * 根据版本类型获取主类名
     *
     * @param versionType 版本类型
     * @param versionMeta 版本元数据
     * @return 主类全限定名
     */
    private fun getMainClass(versionType: GameVersionType, versionMeta: JsonObject): String {
        // 优先从版本 JSON 中读取 mainClass
        val jsonMainClass = versionMeta.get("mainClass")?.asString
        if (!jsonMainClass.isNullOrEmpty()) {
            return jsonMainClass
        }

        // 根据类型选择默认主类
        return when (versionType) {
            GameVersionType.VANILLA -> VANILLA_MAIN_CLASS
            GameVersionType.FORGE -> LAUNCHER_MAIN_CLASS
            GameVersionType.FABRIC -> "net.fabricmc.loader.impl.launch.knot.KnotClient"
        }
    }

    /**
     * 构建游戏启动参数（--username --uuid 等）
     *
     * @param gameVersion 版本号
     * @param versionMeta 版本元数据
     * @param account     账号信息
     * @param versionType 版本类型
     * @return 游戏参数字符串列表
     */
    private fun buildGameArgs(
        gameVersion: String,
        versionMeta: JsonObject,
        account: AccountInfo?,
        versionType: GameVersionType
    ): List<String> {
        val gameArgs = mutableListOf<String>()

        val mcUsername = account?.username ?: "Player"
        val mcUuid = account?.uuid ?: "00000000-0000-0000-0000-000000000000"
        val mcAccessToken = account?.accessToken ?: "0"

        val minecraftDir = File(context.filesDir, ".minecraft")
        val assetsDir = File(minecraftDir, "assets")

        // 获取 assetIndex
        val assetIndex = versionMeta.get("assetIndex")?.asJsonObject
            ?.get("id")?.asString ?: gameVersion

        // 基础认证参数
        gameArgs.add("--username")
        gameArgs.add(mcUsername)
        gameArgs.add("--version")
        gameArgs.add(gameVersion)
        gameArgs.add("--gameDir")
        gameArgs.add(minecraftDir.absolutePath)
        gameArgs.add("--assetsDir")
        gameArgs.add(assetsDir.absolutePath)
        gameArgs.add("--assetIndex")
        gameArgs.add(assetIndex)
        gameArgs.add("--uuid")
        gameArgs.add(mcUuid)
        gameArgs.add("--accessToken")
        gameArgs.add(mcAccessToken)

        // 用户类型
        val userType = when (account?.accountType) {
            AccountType.MICROSOFT -> "msa"
            AccountType.LITTLESKIN -> "mojang"
            else -> "legacy"
        }
        gameArgs.add("--userType")
        gameArgs.add(userType)

        // 版本类型
        gameArgs.add("--versionType")
        gameArgs.add(when (versionType) {
            GameVersionType.VANILLA -> "release"
            GameVersionType.FORGE -> "Forge"
            GameVersionType.FABRIC -> "Fabric"
        })

        // 从版本 JSON 中解析 game arguments（如果有）
        val arguments = versionMeta.get("arguments")?.asJsonObject
        if (arguments != null) {
            val gameArguments = arguments.get("game")?.asJsonArray
            if (gameArguments != null) {
                for (arg in gameArguments) {
                    when {
                        arg.isJsonPrimitive -> {
                            gameArgs.add(arg.asString)
                        }
                        arg.isJsonObject -> {
                            // 条件参数（如根据 feature 标志启用），简化处理：跳过
                            val rules = arg.asJsonObject.get("rules")?.asJsonArray
                            val value = arg.asJsonObject.get("value")
                            if (value != null) {
                                if (value.isJsonPrimitive) {
                                    gameArgs.add(value.asString)
                                } else if (value.isJsonArray) {
                                    value.asJsonArray.forEach { v ->
                                        if (v.isJsonPrimitive) gameArgs.add(v.asString)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 如果没有从 JSON 中找到 arguments，使用旧版 minecraftArguments
        if (gameArgs.size <= 14) { // 只有基础参数
            val minecraftArguments = versionMeta.get("minecraftArguments")?.asString
            if (!minecraftArguments.isNullOrEmpty()) {
                // 解析旧版参数字符串，替换变量占位符
                val resolved = minecraftArguments
                    .replace("\${auth_player_name}", mcUsername)
                    .replace("\${version_name}", gameVersion)
                    .replace("\${game_directory}", minecraftDir.absolutePath)
                    .replace("\${assets_root}", assetsDir.absolutePath)
                    .replace("\${assets_index_name}", assetIndex)
                    .replace("\${auth_uuid}", mcUuid)
                    .replace("\${auth_access_token}", mcAccessToken)
                    .replace("\${user_type}", userType)
                    .replace("\${version_type}", "release")
                    .replace("\${user_properties}", "{}")

                // 将参数字符串拆分为参数列表
                val additionalArgs = resolved.split("\\s+".toRegex()).filter { it.isNotBlank() }
                gameArgs.addAll(additionalArgs)
            }
        }

        // 全屏和窗口设置
        gameArgs.add("--fullscreen")
        gameArgs.add("false")

        return gameArgs
    }

    /**
     * 构建环境变量
     *
     * @return MutableMap 环境变量键值对
     */
    private fun buildEnvironmentVars(): Map<String, String> {
        val env = mutableMapOf<String, String>()

        // OpenGL 相关环境变量（Android 设备上通常需要）
        env["LIBGL_ALWAYS_SOFTWARE"] = "1"

        // Mesa 相关
        env["MESA_GL_VERSION_OVERRIDE"] = "3.3"
        env["MESA_GLSL_VERSION_OVERRIDE"] = "330"

        // 防止 Java AWT 无头异常
        env["JAVA_TOOL_OPTIONS"] = "-Djava.awt.headless=true"

        return env
    }

    // ==================== 版本元数据加载 ====================

    /**
     * 加载版本元数据 JSON
     *
     * 从 .minecraft/versions/{version}/{version}.json 读取。
     *
     * @param version 版本号
     * @return 版本元数据 JsonObject，失败返回 null
     */
    private fun loadVersionMetadata(version: String): JsonObject? {
        val minecraftDir = File(context.filesDir, ".minecraft")
        val jsonFile = File(minecraftDir, "versions/$version/$version.json")

        if (!jsonFile.exists()) {
            LogUtil.warn(TAG, "版本 JSON 不存在: ${jsonFile.absolutePath}")
            return null
        }

        return try {
            FileReader(jsonFile).use { reader ->
                gson.fromJson(reader, JsonObject::class.java)
            }
        } catch (e: Exception) {
            LogUtil.error(TAG, "解析版本 JSON 失败: ${jsonFile.absolutePath}", e)
            null
        }
    }

    /**
     * 检测版本类型
     *
     * 根据版本号判断是原版 / Forge / Fabric：
     * - 包含 "forge" -> FORGE
     * - 包含 "fabric" -> FABRIC
     * - 其他 -> VANILLA
     *
     * @param version 版本号
     * @return 版本类型
     */
    private fun detectVersionType(version: String): GameVersionType {
        val lower = version.lowercase()
        return when {
            lower.contains("forge") -> GameVersionType.FORGE
            lower.contains("fabric") -> GameVersionType.FABRIC
            else -> GameVersionType.VANILLA
        }
    }
}