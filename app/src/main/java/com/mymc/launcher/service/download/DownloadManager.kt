package com.mymc.launcher.service.download

import android.content.Context
import com.mymc.launcher.domain.model.DownloadStatus
import com.mymc.launcher.domain.model.DownloadTask
import com.mymc.launcher.util.FileUtil
import com.mymc.launcher.util.HashUtil
import com.mymc.launcher.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 下载管理器
 *
 * 管理多任务并发下载队列，支持：
 * - 断点续传（HTTP Range 头）
 * - SHA256 哈希校验
 * - 暂停 / 恢复 / 取消下载
 * - 下载完成后自动解压（若文件为 .zip / .tar.gz）
 * - 通过 StateFlow 暴露下载状态列表，供 UI 层观察
 *
 * 使用方式：
 * ```kotlin
 * val dm = DownloadManager(context)
 * dm.tasks.collect { tasks -> ... }
 * dm.enqueue(DownloadTask(...))
 * dm.pause(taskId)
 * dm.resume(taskId)
 * ```
 */
class DownloadManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "DownloadManager"

        /** 最大并发下载数 */
        private const val MAX_CONCURRENT = 3

        /** IO 缓冲区大小：16KB */
        private const val BUFFER_SIZE = 16384

        /** 连接超时（毫秒） */
        private const val CONNECT_TIMEOUT = 30000

        /** 读取超时（毫秒） */
        private const val READ_TIMEOUT = 30000
    }

    /** 协程作用域，使用 SupervisorJob 保证单个任务失败不影响其他任务 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 内部任务存储（线程安全） */
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())

    /** 对外暴露的下载任务状态流 */
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    /** 当前正在执行的任务及其 Job 的映射 */
    private val activeJobs = ConcurrentHashMap<String, Job>()

    /** 暂停标志位映射（任务级） */
    private val pauseFlags = ConcurrentHashMap<String, AtomicBoolean>()

    /** 取消标志位映射（任务级） */
    private val cancelFlags = ConcurrentHashMap<String, AtomicBoolean>()

    /** 当前正在下载的任务数 */
    private val activeCount = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * 将下载任务加入队列
     *
     * 如果当前并发数未达上限，任务会立即开始下载；
     * 否则任务将排队等待。
     *
     * @param task 下载任务
     */
    fun enqueue(task: DownloadTask) {
        LogUtil.info(TAG, "任务入队: ${task.fileName} (${task.id})")

        // 添加到任务列表
        _tasks.update { current -> current + task }

        // 初始化标志位
        pauseFlags[task.id] = AtomicBoolean(false)
        cancelFlags[task.id] = AtomicBoolean(false)

        // 尝试开始下载
        startNextIfPossible()
    }

    /**
     * 暂停指定下载任务
     *
     * @param taskId 任务 ID
     */
    fun pause(taskId: String) {
        LogUtil.info(TAG, "暂停任务: $taskId")
        pauseFlags[taskId]?.set(true)
        updateTaskStatus(taskId, DownloadStatus.PAUSED)
    }

    /**
     * 恢复指定下载任务
     *
     * @param taskId 任务 ID
     */
    fun resume(taskId: String) {
        LogUtil.info(TAG, "恢复任务: $taskId")
        pauseFlags[taskId]?.set(false)
        updateTaskStatus(taskId, DownloadStatus.PENDING)
        startNextIfPossible()
    }

    /**
     * 取消指定下载任务
     *
     * 取消后任务将从队列中移除，并终止对应的下载 Job。
     *
     * @param taskId 任务 ID
     */
    fun cancel(taskId: String) {
        LogUtil.info(TAG, "取消任务: $taskId")
        cancelFlags[taskId]?.set(true)
        pauseFlags[taskId]?.set(true)

        // 取消对应的协程 Job
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        activeCount.decrementAndGet()

        // 清理标志位
        pauseFlags.remove(taskId)
        cancelFlags.remove(taskId)

        // 从任务列表中移除
        _tasks.update { current -> current.filter { it.id != taskId } }

        // 启动下一个排队任务
        startNextIfPossible()
    }

    /**
     * 释放下载管理器占用的所有资源
     *
     * 取消所有进行中的任务，关闭协程作用域。
     */
    fun destroy() {
        LogUtil.info(TAG, "销毁下载管理器")
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        pauseFlags.clear()
        cancelFlags.clear()
        scope.cancel()
        _tasks.value = emptyList()
    }

    // ==================== 内部下载调度 ====================

    /**
     * 如果当前并发数未达上限，从队列中取出一个 PENDING 任务开始下载
     */
    private fun startNextIfPossible() {
        if (activeCount.get() >= MAX_CONCURRENT) return
        // 找到第一个未被暂停的待处理任务
        val pendingTask = _tasks.value.find { task ->
            task.status == DownloadStatus.PENDING && pauseFlags[task.id]?.get() != true
        }
        if (pendingTask != null) {
            startDownload(pendingTask)
            activeCount.incrementAndGet()
        }
    }

    /**
     * 启动单个任务的下载流程
     */
    private fun startDownload(task: DownloadTask) {
        updateTaskStatus(task.id, DownloadStatus.DOWNLOADING)

        val job = scope.launch {
            try {
                executeDownload(task)
            } catch (e: Exception) {
                LogUtil.error(TAG, "下载任务异常: ${task.fileName}", e)
                updateTaskProgress(task.id, 0f, 0, task.totalSize, DownloadStatus.FAILED)
            } finally {
                activeJobs.remove(task.id)
                activeCount.decrementAndGet()
                startNextIfPossible()
            }
        }
        activeJobs[task.id] = job
    }

    /**
     * 执行实际的下载操作
     *
     * 支持：
     * - HTTP Range 断点续传
     * - 下载进度回调
     * - SHA256 哈希校验
     * - 下载完成后自动解压
     */
    private suspend fun executeDownload(task: DownloadTask) {
        val targetFile = File(task.savePath, task.fileName)
        FileUtil.ensureParentDir(targetFile)

        // 检查是否已被取消
        val cancelFlag = cancelFlags[task.id] ?: return

        // 获取已下载的字节数（用于断点续传）
        val existingLength = if (targetFile.exists()) targetFile.length() else 0L

        var connection: HttpURLConnection? = null
        try {
            val url = URL(task.url)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                // 如果已有部分文件，设置 Range 头
                if (existingLength > 0) {
                    setRequestProperty("Range", "bytes=$existingLength-")
                }
                connect()
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK &&
                responseCode != HttpURLConnection.HTTP_PARTIAL
            ) {
                LogUtil.error(TAG, "下载失败，HTTP $responseCode: ${task.url}")
                updateTaskProgress(task.id, 0f, existingLength, task.totalSize, DownloadStatus.FAILED)
                return
            }

            val appendMode = responseCode == HttpURLConnection.HTTP_PARTIAL
            val totalLength = if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                val contentRange = connection.getHeaderField("Content-Range")
                if (contentRange != null) {
                    val parts = contentRange.split("/")
                    parts.lastOrNull()?.toLongOrNull() ?: connection.contentLength.toLong() + existingLength
                } else {
                    connection.contentLength.toLong() + existingLength
                }
            } else {
                connection.contentLength.toLong()
            }

            val inputStream: InputStream = connection.inputStream
            var downloaded = existingLength

            FileOutputStream(targetFile, appendMode).use { fos ->
                inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // 检查暂停标志
                        if (pauseFlags[task.id]?.get() == true) {
                            LogUtil.info(TAG, "任务已暂停: ${task.fileName}")
                            updateTaskProgress(
                                task.id,
                                if (totalLength > 0) downloaded.toFloat() / totalLength.toFloat() else 0f,
                                downloaded,
                                totalLength,
                                DownloadStatus.PAUSED
                            )
                            return
                        }

                        // 检查取消标志
                        if (cancelFlag.get()) {
                            LogUtil.info(TAG, "任务已取消: ${task.fileName}")
                            return
                        }

                        fos.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        val progress = if (totalLength > 0) {
                            downloaded.toFloat() / totalLength.toFloat()
                        } else {
                            0f
                        }
                        updateTaskProgress(task.id, progress, downloaded, totalLength, DownloadStatus.DOWNLOADING)
                    }
                }
            }

            // SHA256 哈希校验
            if (!task.sha256.isNullOrEmpty()) {
                LogUtil.info(TAG, "开始 SHA256 校验: ${task.fileName}")
                val actualHash = HashUtil.sha256File(targetFile)
                if (!actualHash.equals(task.sha256, ignoreCase = true)) {
                    LogUtil.error(TAG, "SHA256 校验失败: 期望 ${task.sha256}, 实际 $actualHash")
                    updateTaskProgress(task.id, 0f, downloaded, totalLength, DownloadStatus.FAILED)
                    return
                }
                LogUtil.info(TAG, "SHA256 校验通过: ${task.fileName}")
            }

            // 下载完成，自动解压（若文件是压缩包）
            if (isCompressedFile(targetFile)) {
                LogUtil.info(TAG, "开始自动解压: ${task.fileName}")
                val destDir = File(task.savePath, "extracted")
                val unzipSuccess = FileUtil.unzipFileWithProgress(
                    zipFile = targetFile,
                    destDir = destDir,
                    onProgress = { entryName, _ ->
                        updateTaskStatus(task.id, DownloadStatus.DOWNLOADING)
                    }
                )
                if (!unzipSuccess) {
                    LogUtil.warn(TAG, "自动解压失败: ${task.fileName}")
                }
            }

            updateTaskProgress(task.id, 1f, downloaded, totalLength, DownloadStatus.COMPLETED)
            LogUtil.info(TAG, "下载完成: ${task.fileName} (${FileUtil.formatFileSize(downloaded)})")

        } catch (e: Exception) {
            LogUtil.error(TAG, "下载异常: ${task.fileName}", e)
            updateTaskProgress(task.id, 0f, existingLength, task.totalSize, DownloadStatus.FAILED)
        } finally {
            connection?.disconnect()
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 更新任务的状态
     */
    private fun updateTaskStatus(taskId: String, status: DownloadStatus) {
        _tasks.update { current ->
            current.map { task ->
                if (task.id == taskId) task.copy(status = status) else task
            }
        }
    }

    /**
     * 更新任务的进度（已下载字节数、进度百分比、状态）
     */
    private fun updateTaskProgress(
        taskId: String,
        progress: Float,
        downloadedBytes: Long,
        totalSize: Long,
        status: DownloadStatus
    ) {
        _tasks.update { current ->
            current.map { task ->
                if (task.id == taskId) {
                    task.copy(
                        progress = progress.coerceIn(0f, 1f),
                        downloadedBytes = downloadedBytes,
                        totalSize = if (totalSize > 0) totalSize else task.totalSize,
                        status = status
                    )
                } else {
                    task
                }
            }
        }
    }

    /**
     * 判断文件是否为压缩文件（需要解压）
     *
     * 支持的格式：.zip, .tar.gz, .tgz
     */
    private fun isCompressedFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".zip") || name.endsWith(".tar.gz") || name.endsWith(".tgz")
    }

    /**
     * 生成唯一任务 ID
     */
    fun generateTaskId(): String {
        return UUID.randomUUID().toString()
    }
}