package com.mymc.launcher.service.download

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

/**
 * Android 前台 Service，用于后台下载任务管理与通知进度展示。
 * 通过 startForeground 显示下载进度通知，确保下载任务在后台不被系统杀死。
 *
 * 使用方式：
 *   1. 通过 Intent 传递下载 URL 与目标路径启动 Service
 *   2. Service 在 onStartCommand 中开始下载
 *   3. 下载过程中持续更新通知进度条
 *   4. 下载完成或失败后自动停止 Service
 */
class DownloadService : Service() {

    /** 通知渠道 ID */
    private val channelId = "download_channel"

    /** 前台通知 ID */
    private val notificationId = 1001

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadUrl = intent?.getStringExtra(EXTRA_DOWNLOAD_URL) ?: ""
        val savePath = intent?.getStringExtra(EXTRA_SAVE_PATH) ?: ""
        val fileName = intent?.getStringExtra(EXTRA_FILE_NAME) ?: "download"

        if (downloadUrl.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 构建前台通知并开始下载
        val notification = buildNotification(fileName, 0)
        startForeground(notificationId, notification)

        startDownload(downloadUrl, savePath, fileName)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 创建下载通知渠道（适配 Android O+）。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "下载通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示文件下载进度"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 构建下载进度通知。
     *
     * @param fileName 正在下载的文件名
     * @param progress 下载进度 0-100
     */
    private fun buildNotification(fileName: String, progress: Int): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else null

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("正在下载")
            .setContentText(fileName)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * 开始执行下载任务，并在回调中更新通知进度。
     *
     * @param url      下载文件 URL
     * @param savePath 保存目标目录路径
     * @param fileName 保存文件名
     */
    private fun startDownload(url: String, savePath: String, fileName: String) {
        Thread {
            try {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    stopWithError("下载失败: HTTP ${connection.responseCode}")
                    return@Thread
                }

                val fileLength = connection.contentLength
                val inputStream = connection.inputStream
                val targetDir = java.io.File(savePath)
                if (!targetDir.exists()) targetDir.mkdirs()

                val outputFile = java.io.File(targetDir, fileName)
                val outputStream = java.io.FileOutputStream(outputFile)

                val buffer = ByteArray(8192)
                var totalBytesRead: Long = 0
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (fileLength > 0) {
                        val progress = ((totalBytesRead * 100) / fileLength).toInt()
                        updateNotification(fileName, progress)
                    }
                }

                outputStream.close()
                inputStream.close()
                connection.disconnect()

                // 下载完成
                updateNotification(fileName, 100)
                stopSelf()
            } catch (e: Exception) {
                stopWithError("下载失败: ${e.message}")
            }
        }.start()
    }

    /**
     * 更新通知进度。
     */
    private fun updateNotification(fileName: String, progress: Int) {
        val notification = buildNotification(fileName, progress)
        notificationManager.notify(notificationId, notification)
    }

    /**
     * 下载失败时更新通知并停止 Service。
     */
    private fun stopWithError(message: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else null

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("下载失败")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    companion object {
        /** Intent Extra 键：下载 URL */
        const val EXTRA_DOWNLOAD_URL = "download_url"

        /** Intent Extra 键：保存路径 */
        const val EXTRA_SAVE_PATH = "save_path"

        /** Intent Extra 键：文件名 */
        const val EXTRA_FILE_NAME = "file_name"
    }
}