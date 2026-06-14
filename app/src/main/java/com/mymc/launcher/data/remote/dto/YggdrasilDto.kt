package com.mymc.launcher.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Yggdrasil 认证请求 —— 登录
 *
 * 对应 LittleSkin 外置登录协议
 */
data class AuthenticateRequest(
    @SerializedName("username")
    val username: String,
    @SerializedName("password")
    val password: String,
    @SerializedName("clientToken")
    val clientToken: String?,
    @SerializedName("requestUser")
    val requestUser: Boolean = true
)

/**
 * Yggdrasil 认证响应 —— 登录
 */
data class AuthenticateResponse(
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("clientToken")
    val clientToken: String,
    @SerializedName("availableProfiles")
    val availableProfiles: List<Profile>,
    @SerializedName("selectedProfile")
    val selectedProfile: Profile?
)

/**
 * 角色信息
 */
data class Profile(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String
)

/**
 * Yggdrasil 令牌刷新请求
 */
data class RefreshRequest(
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("clientToken")
    val clientToken: String
)

/**
 * Yggdrasil 令牌刷新响应
 */
data class RefreshResponse(
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("clientToken")
    val clientToken: String
)