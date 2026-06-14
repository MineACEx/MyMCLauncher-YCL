package com.mymc.launcher.domain.model

/**
 * 下载状态枚举
 */
enum class DownloadStatus {
    /** 等待中 */
    PENDING,
    /** 下载中 */
    DOWNLOADING,
    /** 已暂停 */
    PAUSED,
    /** 已完成 */
    COMPLETED,
    /** 下载失败 */
    FAILED
}

/**
 * 下载任务模型
 *
 * @property id 唯一标识
 * @property url 下载地址
 * @property fileName 文件名
 * @property savePath 保存路径
 * @property totalSize 文件总大小（字节）
 * @property downloadedBytes 已下载字节数
 * @property status 下载状态
 * @property progress 下载进度（0.0 ~ 1.0）
 * @property sha256 文件 SHA-256 校验值
 */
data class DownloadTask(
    val id: String,
    val url: String,
    val fileName: String,
    val savePath: String,
    val totalSize: Long,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Float = 0f,
    val sha256: String? = null
)