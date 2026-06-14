package com.mymc.launcher.domain.model

/**
 * 账号类型枚举
 */
enum class AccountType {
    /** 离线模式 */
    OFFLINE,
    /** 微软正版账号 */
    MICROSOFT,
    /** LittleSkin 外置登录 */
    LITTLESKIN
}

/**
 * 账号信息模型
 *
 * @property id 唯一标识
 * @property username 用户名
 * @property uuid 玩家 UUID
 * @property accessToken 访问令牌
 * @property accountType 账号类型
 * @property isLoggedIn 是否已登录
 * @property avatarUrl 头像 URL
 */
data class AccountInfo(
    val id: String,
    val username: String,
    val uuid: String,
    val accessToken: String,
    val accountType: AccountType,
    val isLoggedIn: Boolean = false,
    val avatarUrl: String? = null,
    val refreshToken: String? = null
)