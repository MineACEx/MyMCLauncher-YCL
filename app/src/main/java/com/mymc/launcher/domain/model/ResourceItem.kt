package com.mymc.launcher.domain.model

/**
 * 资源类型枚举
 */
enum class ResourceType {
    /** Mod 模组 */
    MOD,
    /** 光影包 */
    SHADER,
    /** 材质包 */
    TEXTURE
}

/**
 * 资源项模型
 *
 * @property id 唯一标识
 * @property name 资源名称
 * @property description 资源描述
 * @property resourceType 资源类型
 * @property author 作者
 * @property downloadUrl 下载地址
 * @property iconUrl 图标 URL
 * @property fileSize 文件大小（字节）
 * @property gameVersion 适用的游戏版本
 * @property loaderType 加载器类型（Forge/Fabric 等）
 * @property installed 是否已安装
 * @property enabled 是否启用
 * @property installPath 安装路径
 */
data class ResourceItem(
    val id: String,
    val name: String,
    val description: String,
    val resourceType: ResourceType,
    val author: String,
    val downloadUrl: String,
    val iconUrl: String? = null,
    val fileSize: Long = 0,
    val gameVersion: String,
    val loaderType: String? = null,
    val installed: Boolean = false,
    val enabled: Boolean = true,
    val installPath: String? = null
)