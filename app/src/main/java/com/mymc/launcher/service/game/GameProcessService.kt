package com.mymc.launcher.service.game

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mymc.launcher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Android 前台 Service，用于管理 Minecraft 游戏进程与日志输出。
 *
 * 功能：
 *   - 以独立进程启动 Minecraft 游戏
 *   - 实时读取游戏输出流与错误流作为日志
 *   - 通过通知显示游戏运行状态
 *   - 进程异常或退出后自动停止 Service
 *
 * 使用方式：
 *   1. 构造 Intent 传入游戏启动参数（Java 路径、游戏参数等）
 *   2. 调用 startForegroundService 启动
 *   3. 游戏运行期间可通过日志回调观察输出
 *   4. 游戏退出后 Service 自动停止
 */
class GameProcessService : Service() {

    /** 通知渠道 ID */
    private val channelId = "game_process_channel"

    /** 前台通知 ID */
    private val notificationId = 2001

    /** 协程作用域 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val javaPath = intent?.getStringExtra(EXTRA_JAVA_PATH) ?: ""
        val gameArgs = intent?.getStringArrayListExtra(EXTRA_GAME_ARGS) ?: arrayListOf()
        val workingDir = intent?.getStringExtra(EXTRA_WORKING_DIR) ?: ""

        if (javaPath.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 启动前台通知
        val notification = buildNotification("游戏运行中...")
        startForeground(notificationId, notification)

        // 启动游戏进程
        launchGameProcess(javaPath, gameArgs, workingDir)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * 创建游戏进程通知渠道。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "游戏进程",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示 Minecraft 游戏运行状态"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 构建游戏运行状态通知。
     *
     * @param content 通知内容文本
     */
    private fun buildNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("YCL启动器 - 游戏运行中")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * 启动 Minecraft 游戏进程并实时捕获日志输出。
     *
     * @param javaPath   Java 可执行文件路径
     * @param gameArgs   游戏启动参数列表（包含主类、JVM 参数、classpath 等）
     * @param workingDir 游戏运行工作目录（通常为 .minecraft 目录）
     */
    private fun launchGameProcess(
        javaPath: String,
        gameArgs: List<String>,
        workingDir: String
    ) {
        Thread {
            try {
                // 构建启动命令
                val command = mutableListOf<String>()
                command.add(javaPath)
                command.addAll(gameArgs)

                val processBuilder = ProcessBuilder(command)
                if (workingDir.isNotBlank()) {
                    processBuilder.directory(java.io.File(workingDir))
                }
                processBuilder.redirectErrorStream(false)

                val process = processBuilder.start()

                // 启动日志读取线程 —— 标准输出
                val stdoutThread = Thread {
                    try {
                        process.inputStream.bufferedReader().use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                onGameLog("[INFO] $line")
                            }
                        }
                    } catch (e: Exception) {
                        onGameLog("[ERROR] 读取标准输出异常: ${e.message}")
                    }
                }
                stdoutThread.isDaemon = true
                stdoutThread.start()

                // 启动日志读取线程 —— 标准错误输出
                val stderrThread = Thread {
                    try {
                        process.errorStream.bufferedReader().use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                onGameLog("[STDERR] $line")
                            }
                        }
                    } catch (e: Exception) {
                        onGameLog("[ERROR] 读取错误输出异常: ${e.message}")
                    }
                }
                stderrThread.isDaemon = true
                stderrThread.start()

                // 等待游戏进程结束
                val exitCode = process.waitFor()

                // 等待日志线程完成
                stdoutThread.join(3000)
                stderrThread.join(3000)

                // 游戏进程退出处理
                onGameProcessExit(exitCode)
            } catch (e: Exception) {
                onGameProcessError("游戏启动失败: ${e.message}")
            }
        }.start()
    }

    /**
     * 游戏日志输出回调。
     * 默认通过 Android Log 输出，子类或外部观察者可覆盖此方法获取日志。
     *
     * @param logLine 单行日志内容
     */
    private fun onGameLog(logLine: String) {
        android.util.Log.d("GameProcess", logLine)
    }

    /**
     * 游戏进程正常退出时的回调。
     *
     * @param exitCode 进程退出码
     */
    private fun onGameProcessExit(exitCode: Int) {
        val message = if (exitCode == 0) {
            "游戏已退出（正常）"
        } else {
            "游戏异常退出，退出码: $exitCode"
        }
        showTerminatedNotification(message)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    /**
     * 游戏进程启动或运行异常时的回调。
     *
     * @param errorMessage 错误描述
     */
    private fun onGameProcessError(errorMessage: String) {
        showTerminatedNotification(errorMessage)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    /**
     * 显示游戏结束通知。
     */
    private fun showTerminatedNotification(content: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("YCL启动器")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    companion object {
        /** Intent Extra 键：Java 可执行文件路径 */
        const val EXTRA_JAVA_PATH = "java_path"

        /** Intent Extra 键：游戏启动参数列表 */
        const val EXTRA_GAME_ARGS = "game_args"

        /** Intent Extra 键：游戏运行工作目录 */
        const val EXTRA_WORKING_DIR = "working_dir"
    }
}