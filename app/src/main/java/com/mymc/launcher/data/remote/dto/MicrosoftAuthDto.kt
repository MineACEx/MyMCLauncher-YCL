package com.mymc.launcher.data.remote.dto

import com.google.gson.annotations.SerializedName

// ==================== 微软 OAuth 设备码流程 ====================

/**
 * 设备码请求
 */
data class DeviceCodeRequest(
    @SerializedName("client_id")
    val clientId: String,
    @SerializedName("scope")
    val scope: String
)

/**
 * 设备码响应
 */
data class DeviceCodeResponse(
    @SerializedName("device_code")
    val deviceCode: String,
    @SerializedName("user_code")
    val userCode: String,
    @SerializedName("verification_uri")
    val verificationUri: String,
    @SerializedName("expires_in")
    val expiresIn: Int,
    @SerializedName("interval")
    val interval: Int
)

// ==================== 微软 OAuth 令牌 ====================

/**
 * 令牌请求
 */
data class TokenRequest(
    @SerializedName("client_id")
    val clientId: String,
    @SerializedName("device_code")
    val deviceCode: String? = null,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    @SerializedName("grant_type")
    val grantType: String
)

/**
 * 令牌响应
 */
data class TokenResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    @SerializedName("expires_in")
    val expiresIn: Int
)

// ==================== Xbox Live 认证 ====================

/**
 * Xbox Live 认证请求
 */
data class XBoxAuthRequest(
    @SerializedName("Properties")
    val properties: XBoxAuthProperties
)

data class XBoxAuthProperties(
    @SerializedName("AuthMethod")
    val authMethod: String,
    @SerializedName("SiteName")
    val siteName: String,
    @SerializedName("RpsTicket")
    val rpsTicket: String
)

/**
 * Xbox Live 认证响应
 */
data class XBoxAuthResponse(
    @SerializedName("Token")
    val token: String,
    @SerializedName("DisplayClaims")
    val displayClaims: DisplayClaims
)

data class DisplayClaims(
    @SerializedName("xui")
    val xui: List<XuiClaim>
)

data class XuiClaim(
    @SerializedName("uhs")
    val uhs: String
)

// ==================== XSTS 认证 ====================

/**
 * XSTS 认证响应
 */
data class XstsResponse(
    @SerializedName("Token")
    val token: String
)

// ==================== Minecraft 认证 ====================

/**
 * Minecraft 认证响应
 */
data class MinecraftAuthResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    @SerializedName("expires_in")
    val expiresIn: Int
)

// ==================== Minecraft 角色信息 ====================

/**
 * Minecraft 角色查询响应
 */
data class MinecraftProfileResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String
)