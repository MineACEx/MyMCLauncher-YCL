package com.mymc.launcher.domain.model

/**
 * 游戏版本类型枚举
 */
enum class GameVersionType {
    /** 原版（Vanilla） */
    VANILLA,
    /** Forge 加载器 */
    FORGE,
    /** Fabric 加载器 */
    FABRIC
}

/**
 * 游戏版本模型
 *
 * @property id 唯一标识
 * @property versionId 游戏版本号（如 "1.21"）
 * @property type 游戏版本类型
 * @property loaderVersion 加载器版本号（Forge/Fabric 时使用）
 * @property installed 是否已安装
 * @property installPath 安装路径
 * @property downloadUrl 下载地址
 * @property fileSize 文件大小（字节）
 */
data class GameVersion(
    val id: String,
    val versionId: String,
    val type: GameVersionType,
    val loaderVersion: String? = null,
    val installed: Boolean = false,
    val installPath: String? = null,
    val downloadUrl: String,
    val fileSize: Long = 0
)