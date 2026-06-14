package com.mymc.launcher.data.remote.api

import com.mymc.launcher.data.remote.dto.AuthenticateRequest
import com.mymc.launcher.data.remote.dto.AuthenticateResponse
import com.mymc.launcher.data.remote.dto.RefreshRequest
import com.mymc.launcher.data.remote.dto.RefreshResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Yggdrasil 认证 API 接口
 *
 * LittleSkin 外置登录认证协议
 * Base URL: https://littleskin.cn/api/yggdrasil/
 */
interface YggdrasilApi {

    /**
     * 账号登录
     *
     * @param request 认证请求体
     */
    @POST("authserver/authenticate")
    suspend fun authenticate(
        @Body request: AuthenticateRequest
    ): Response<AuthenticateResponse>

    /**
     * 刷新令牌
     *
     * @param request 刷新请求体
     */
    @POST("authserver/refresh")
    suspend fun refresh(
        @Body request: RefreshRequest
    ): Response<RefreshResponse>

    /**
     * 验证令牌有效性
     *
     * @param accessToken 待验证的 accessToken（作为 JSON body 发送）
     */
    @POST("authserver/validate")
    suspend fun validate(
        @Body body: Map<String, String>
    ): Response<Unit>

    /**
     * 登出 / 吊销令牌
     *
     * @param username 用户名
     * @param password 密码
     */
    @POST("authserver/signout")
    suspend fun signout(
        @Body body: Map<String, String>
    ): Response<Unit>
}