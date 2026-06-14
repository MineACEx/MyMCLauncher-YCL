package com.mymc.launcher.util

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 日志工具类
 *
 * 支持 INFO / WARN / ERROR 三级日志分级，
 * 同时输出到 Android Logcat 和本地日志文件，每条日志带有时间戳。
 * 使用单线程 Executor 保证写入文件时的线程安全。
 */
object LogUtil {

    /** 日志级别枚举 */
    enum class Level(val tag: String) {
        INFO("INFO"),
        WARN("WARN"),
        ERROR("ERROR")
    }

    /** 默认日志标签 */
    private const val DEFAULT_TAG = "YCL-Launcher"

    /** 日志文件名 */
    private const val LOG_FILE_NAME = "ycl_launcher.log"

    /** 最大日志文件大小：5MB */
    private const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024L

    /** 时间戳格式 */
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /** 单线程写入器，保证日志写入的线程安全与顺序性 */
    private val logExecutor = Executors.newSingleThreadExecutor()

    /** 日志文件目录缓存 */
    private var logDir: File? = null

    /** 是否启用文件写入 */
    private var fileLogEnabled = false

    /**
     * 初始化日志系统，设置日志文件存储目录并启用文件写入
     *
     * @param logDirectory 日志文件存储目录
     */
    fun init(logDirectory: File) {
        if (!logDirectory.exists()) {
            logDirectory.mkdirs()
        }
        logDir = logDirectory
        fileLogEnabled = true
        info(DEFAULT_TAG, "日志系统初始化完成，日志目录: ${logDirectory.absolutePath}")
    }

    /**
     * 记录 INFO 级别日志
     *
     * @param tag     日志标签
     * @param message 日志内容
     */
    fun info(tag: String, message: String) {
        log(Level.INFO, tag, message, null)
    }

    /**
     * 记录 WARN 级别日志
     *
     * @param tag     日志标签
     * @param message 日志内容
     */
    fun warn(tag: String, message: String) {
        log(Level.WARN, tag, message, null)
    }

    /**
     * 记录 ERROR 级别日志
     *
     * @param tag     日志标签
     * @param message 日志内容
     */
    fun error(tag: String, message: String) {
        log(Level.ERROR, tag, message, null)
    }

    /**
     * 记录 ERROR 级别日志，附带异常堆栈信息
     *
     * @param tag       日志标签
     * @param message   日志内容
     * @param throwable 异常对象
     */
    fun error(tag: String, message: String, throwable: Throwable) {
        log(Level.ERROR, tag, message, throwable)
    }

    /**
     * 核心日志记录方法
     *
     * 同时输出到 Logcat 和本地文件。
     *
     * @param level     日志级别
     * @param tag       日志标签
     * @param message   日志内容
     * @param throwable 异常对象，可为 null
     */
    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        val fullTag = "[$DEFAULT_TAG][$tag]"
        val fullMessage = buildString {
            append(message)
            if (throwable != null) {
                append("\n")
                append(getStackTraceString(throwable))
            }
        }

        // 输出到 Android Logcat
        when (level) {
            Level.INFO -> Log.i(fullTag, fullMessage)
            Level.WARN -> Log.w(fullTag, fullMessage)
            Level.ERROR -> Log.e(fullTag, fullMessage)
        }

        // 异步写入文件
        if (fileLogEnabled && logDir != null) {
            logExecutor.execute {
                writeToFile(level, tag, message, throwable)
            }
        }
    }

    /**
     * 将日志内容写入文件
     */
    private fun writeToFile(level: Level, tag: String, message: String, throwable: Throwable?) {
        try {
            val logDir = this.logDir ?: return
            val logFile = File(logDir, LOG_FILE_NAME)

            // 检查并清理超大日志文件
            if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
                val backupFile = File(logDir, "${LOG_FILE_NAME}.bak")
                if (backupFile.exists()) {
                    backupFile.delete()
                }
                logFile.renameTo(backupFile)
            }

            val timestamp = timeFormat.format(Date())
            val logLine = buildString {
                append(timestamp)
                append(" [${level.tag}]")
                append(" [$tag]")
                append(" $message")
                if (throwable != null) {
                    append("\n")
                    append(getStackTraceString(throwable))
                }
                append("\n")
            }

            FileWriter(logFile, true).use { writer ->
                writer.append(logLine)
                writer.flush()
            }
        } catch (e: Exception) {
            // 写入文件失败时仅输出到 Logcat，避免递归调用
            Log.e(DEFAULT_TAG, "日志写入文件失败", e)
        }
    }

    /**
     * 获取异常堆栈信息的字符串表示
     */
    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            throwable.printStackTrace(pw)
        }
        return sw.toString()
    }

    /**
     * 获取日志文件的绝对路径
     *
     * @return 日志文件路径，若未初始化则返回 null
     */
    fun getLogFilePath(): String? {
        val dir = logDir ?: return null
        return File(dir, LOG_FILE_NAME).absolutePath
    }

    /**
     * 清空日志文件内容
     */
    fun clearLogFile() {
        logExecutor.execute {
            try {
                val dir = logDir ?: return@execute
                val logFile = File(dir, LOG_FILE_NAME)
                if (logFile.exists()) {
                    logFile.writeText("")
                }
            } catch (e: Exception) {
                Log.e(DEFAULT_TAG, "清空日志文件失败", e)
            }
        }
    }

    /**
     * 读取最近的日志内容
     *
     * @param maxLines 最大返回行数，默认 200
     * @return 日志行列表（按时间顺序排列）
     */
    fun readRecentLogs(maxLines: Int = 200): List<String> {
        return try {
            val dir = logDir ?: return emptyList()
            val logFile = File(dir, LOG_FILE_NAME)
            if (!logFile.exists()) return emptyList()
            val lines = logFile.readLines()
            if (lines.size <= maxLines) lines else lines.takeLast(maxLines)
        } catch (e: Exception) {
            Log.e(DEFAULT_TAG, "读取日志文件失败", e)
            emptyList()
        }
    }
}