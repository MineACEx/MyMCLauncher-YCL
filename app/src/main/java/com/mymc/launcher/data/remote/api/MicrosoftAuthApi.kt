package com.mymc.launcher.data.remote.api

import com.mymc.launcher.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * 微软 OAuth 认证 API 接口
 *
 * 包括设备码流程、Xbox Live 认证、Minecraft 认证
 */
interface MicrosoftAuthApi {

    // ==================== 设备码流程 ====================

    /**
     * 请求设备码（第一步）
     * POST https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode
     */
    @POST
    suspend fun requestDeviceCode(
        @Url url: String,
        @Body request: DeviceCodeRequest
    ): Response<DeviceCodeResponse>

    /**
     * 使用设备码换取 Token（第二步）
     * POST https://login.microsoftonline.com/consumers/oauth2/v2.0/token
     */
    @POST
    suspend fun requestToken(
        @Url url: String,
        @Body request: TokenRequest
    ): Response<TokenResponse>

    // ==================== Xbox Live 认证 ====================

    /**
     * Xbox Live 用户认证
     * POST https://user.auth.xboxlive.com/user/authenticate
     */
    @POST("user/authenticate")
    suspend fun authenticateXBox(
        @Body request: XBoxAuthRequest
    ): Response<XBoxAuthResponse>

    /**
     * XSTS 令牌获取
     * POST https://xsts.auth.xboxlive.com/xsts/authorize
     */
    @POST("xsts/authorize")
    suspend fun acquireXsts(
        @Body request: XBoxAuthRequest
    ): Response<XstsResponse>

    // ==================== Minecraft 认证 ====================

    /**
     * 使用 XSTS 令牌换取 Minecraft 令牌
     * POST https://api.minecraftservices.com/authentication/login_with_xbox
     */
    @POST("authentication/login_with_xbox")
    suspend fun authenticateMinecraft(
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: Map<String, String>
    ): Response<MinecraftAuthResponse>

    /**
     * 获取 Minecraft 角色信息
     * GET https://api.minecraftservices.com/minecraft/profile
     */
    @GET("minecraft/profile")
    suspend fun getMinecraftProfile(
        @Header("Authorization") authorization: String
    ): Response<MinecraftProfileResponse>
}