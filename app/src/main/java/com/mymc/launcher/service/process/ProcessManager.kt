package com.mymc.launcher.service.process

import com.mymc.launcher.util.EnvDetector
import com.mymc.launcher.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 多环境进程调用工具
 *
 * 根据 EnvDetector 自动选择执行方式：
 * - **无 Root**：使用 Runtime.exec() 或 ProcessBuilder
 * - **Root**：通过 su -c 执行，自动请求 Root 权限
 * - **Termux**：直接使用 ProcessBuilder（Termux 环境已配置 PATH）
 *
 * 提供实时 stdout/stderr 日志输出，通过 Kotlin Flow 向外暴露出。
 *
 * 使用方式：
 * ```kotlin
 * val pm = ProcessManager()
 * pm.startGame(launchCommand, javaPath, envVars, workDir)
 *     .collect { logLine -> println(logLine) }
 * ```
 */
class ProcessManager {

    companion object {
        private const val TAG = "ProcessManager"

        /** 默认的输入流缓冲区大小 */
        private const val BUFFER_SIZE = 8192
    }

    /** 协程作用域 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 当前运行环境类型（缓存） */
    private val envType: EnvDetector.EnvType by lazy {
        EnvDetector.detectEnvironment()
    }

    /**
     * 进程执行结果
     *
     * @property exitCode    进程退出码
     * @property outputLines 标准输出行列表
     * @property errorLines  标准错误输出行列表
     */
    data class ProcessResult(
        val exitCode: Int,
        val outputLines: List<String>,
        val errorLines: List<String>
    )

    /**
     * 执行命令行
     *
     * 根据当前环境自动选择执行方式（Root / 非 Root / Termux）。
     *
     * @param command  要执行的命令（完整命令字符串或数组）
     * @param envVars  环境变量映射，key-value 对
     * @param workDir  工作目录，可选
     * @return ProcessResult 包含退出码和输出内容
     */
    suspend fun executeCommand(
        command: List<String>,
        envVars: Map<String, String> = emptyMap(),
        workDir: File? = null
    ): ProcessResult = withContext(Dispatchers.IO) {
        LogUtil.info(TAG, "执行命令 (${envType.name}): ${command.joinToString(" ")}")

        val resolvedCommand = resolveCommandForEnv(command)
        val processBuilder = ProcessBuilder(resolvedCommand)

        // 设置工作目录
        workDir?.let {
            if (it.exists()) {
                processBuilder.directory(it)
            }
        }

        // 设置环境变量
        val environment = processBuilder.environment()
        envVars.forEach { (key, value) ->
            environment[key] = value
        }

        // 合并错误输出流到标准输出
        processBuilder.redirectErrorStream(false)

        try {
            val process = processBuilder.start()

            val outputLines = mutableListOf<String>()
            val errorLines = mutableListOf<String>()

            // 并行读取 stdout 和 stderr
            val stdoutJob = scope.launch {
                BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        outputLines.add(line!!)
                        LogUtil.info(TAG, "[stdout] $line")
                    }
                }
            }

            val stderrJob = scope.launch {
                BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        errorLines.add(line!!)
                        LogUtil.warn(TAG, "[stderr] $line")
                    }
                }
            }

            // 等待进程结束
            val exitCode = process.waitFor()

            // 等待流读取完成
            stdoutJob.join()
            stderrJob.join()

            LogUtil.info(TAG, "进程退出，exitCode: $exitCode")
            ProcessResult(
                exitCode = exitCode,
                outputLines = outputLines,
                errorLines = errorLines
            )
        } catch (e: Exception) {
            LogUtil.error(TAG, "进程执行异常", e)
            ProcessResult(
                exitCode = -1,
                outputLines = emptyList(),
                errorLines = listOf(e.message ?: "未知错误")
            )
        }
    }

    /**
     * 启动 Minecraft 游戏进程
     *
     * 将启动命令通过 Flow 实时输出日志，并在进程退出时返回 exitCode。
     *
     * @param launchCommand 完整的启动命令参数列表
     * @param javaPath      Java 可执行文件路径
     * @param envVars       环境变量
     * @param workDir       工作目录
     * @return Flow<ProcessLogLine> 实时日志流
     */
    fun startGame(
        launchCommand: List<String>,
        javaPath: String,
        envVars: Map<String, String> = emptyMap(),
        workDir: File? = null
    ): Flow<ProcessLogLine> {
        val channel = Channel<ProcessLogLine>(Channel.BUFFERED)

        scope.launch(Dispatchers.IO) {
            try {
                LogUtil.info(TAG, "启动 Minecraft (${envType.name})")
                LogUtil.info(TAG, "Java: $javaPath")
                LogUtil.info(TAG, "命令: ${launchCommand.joinToString(" ")}")

                val resolvedCommand = resolveCommandForEnv(launchCommand)
                val processBuilder = ProcessBuilder(resolvedCommand)

                // 设置工作目录
                workDir?.let {
                    if (it.exists()) {
                        processBuilder.directory(it)
                        LogUtil.info(TAG, "工作目录: ${it.absolutePath}")
                    }
                }

                // 设置环境变量
                val environment = processBuilder.environment()
                envVars.forEach { (key, value) ->
                    environment[key] = value
                }

                // 不合并错误流，分开处理以分类日志
                processBuilder.redirectErrorStream(false)

                val process = processBuilder.start()

                // 读取标准输出
                val stdoutJob = scope.launch {
                    BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            channel.send(ProcessLogLine.StdOut(line!!))
                        }
                    }
                }

                // 读取标准错误
                val stderrJob = scope.launch {
                    BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            channel.send(ProcessLogLine.StdErr(line!!))
                        }
                    }
                }

                // 监听进程退出
                val exitCode = process.waitFor()

                // 等待流读取完成
                stdoutJob.join()
                stderrJob.join()

                // 发送退出事件
                channel.send(ProcessLogLine.Exit(exitCode))
                LogUtil.info(TAG, "Minecraft 进程退出，exitCode: $exitCode")
            } catch (e: Exception) {
                LogUtil.error(TAG, "启动 Minecraft 失败", e)
                channel.send(ProcessLogLine.StdErr("启动失败: ${e.message}"))
                channel.send(ProcessLogLine.Exit(-1))
            } finally {
                channel.close()
            }
        }

        return channel.receiveAsFlow()
    }

    /**
     * 释放资源
     *
     * 取消所有正在执行的协程。
     */
    fun destroy() {
        scope.cancel()
    }

    // ==================== 私有方法 ====================

    /**
     * 根据运行环境解析最终执行的命令
     *
     * - NON_ROOT: 直接使用 ProcessBuilder
     * - ROOTED: 通过 su -c 包装命令
     * - TERMUX: 直接使用 ProcessBuilder
     *
     * @param originalCommand 原始命令列表
     * @return 处理后的命令列表
     */
    private fun resolveCommandForEnv(originalCommand: List<String>): List<String> {
        return when (envType) {
            EnvDetector.EnvType.NON_ROOT -> {
                // 直接执行
                originalCommand
            }
            EnvDetector.EnvType.ROOTED -> {
                // 通过 su -c 包装执行
                // 将命令列表拼接为 shell 命令字符串
                val shellCommand = originalCommand.joinToString(" ") { arg ->
                    // 对包含空格的参数加引号
                    if (arg.contains(" ")) "\"$arg\"" else arg
                }
                listOf("su", "-c", shellCommand)
            }
            EnvDetector.EnvType.TERMUX -> {
                // Termux 环境直接使用 ProcessBuilder
                originalCommand
            }
        }
    }

    }

/**
 * 进程日志行密封类
 *
 * 用于区分标准输出、标准错误和进程退出事件。
 */
sealed class ProcessLogLine {
    /**
     * 标准输出行
     */
    data class StdOut(val line: String) : ProcessLogLine()

    /**
     * 标准错误行
     */
    data class StdErr(val line: String) : ProcessLogLine()

    /**
     * 进程退出事件
     */
    data class Exit(val exitCode: Int) : ProcessLogLine()
}