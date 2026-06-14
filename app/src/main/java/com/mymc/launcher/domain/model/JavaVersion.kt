package com.mymc.launcher.domain.model

/**
 * Java 版本模型
 *
 * @property id 唯一标识
 * @property version Java 版本号（如 "8", "17", "21", "25"）
 * @property downloadUrl Java 下载地址
 * @property architecture 系统架构，默认 arm64
 * @property installed 是否已安装
 * @property installPath 安装路径
 * @property fileSize 文件大小（字节）
 */
data class JavaVersion(
    val id: String,
    val version: String,
    val downloadUrl: String,
    val architecture: String = "arm64",
    val installed: Boolean = false,
    val installPath: String? = null,
    val fileSize: Long = 0
)