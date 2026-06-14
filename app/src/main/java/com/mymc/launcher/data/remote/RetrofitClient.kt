package com.mymc.launcher.data.remote

import com.google.gson.GsonBuilder
import com.mymc.launcher.data.remote.api.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 网络客户端单例
 *
 * 为各个 API 服务提供统一的 Retrofit 实例管理，涵盖：
 * - Mojang API（版本清单、资源索引）
 * - Modrinth API（Mod / 光影 / 材质搜索）
 * - CurseForge API（Mod 搜索与下载）
 * - LittleSkin Yggdrasil API（外置登录认证）
 * - Microsoft OAuth API（正版登录认证）
 */
object RetrofitClient {

    // ======================== Base URL 常量 ========================

    /** Mojang 启动器元数据 API */
    private const val BASE_URL_MOJANG = "https://launchermeta.mojang.com/"

    /** BMCLAPI 国内镜像 */
    private const val BASE_URL_MOJANG_MIRROR = "https://bmclapi2.bangbang93.com/"

    /** Modrinth API v2 */
    private const val BASE_URL_MODRINTH = "https://api.modrinth.com/v2/"

    /** CurseForge API v1 */
    private const val BASE_URL_CURSEFORGE = "https://api.curseforge.com/v1/"

    /** LittleSkin Yggdrasil 认证 API */
    private const val BASE_URL_LITTLESKIN = "https://littleskin.cn/api/yggdrasil/"

    /** 微软 OAuth 登录 API */
    private const val BASE_URL_MICROSOFT_LOGIN = "https://login.microsoftonline.com/"

    /** Xbox Live 认证 API */
    private const val BASE_URL_XBOX_LIVE = "https://user.auth.xboxlive.com/"

    /** XSTS 认证 API */
    private const val BASE_URL_XSTS = "https://xsts.auth.xboxlive.com/"

    /** Minecraft 服务 API */
    private const val BASE_URL_MINECRAFT_SERVICES = "https://api.minecraftservices.com/"

    // ======================== 日志拦截器 ========================

    /** HTTP 日志拦截器 —— 开发阶段使用 BODY 级别，发布时切换为 NONE */
    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    // ======================== OkHttpClient 构建 ========================

    /** 通用的 OkHttpClient，带日志拦截器和超时配置 */
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ======================== Gson 实例 ========================

    /** 通用 Gson 实例（宽松模式，适配各种 API 字段差异） */
    private val gson by lazy {
        GsonBuilder()
            .setLenient()
            .create()
    }

    // ======================== Retrofit 实例 ========================

    /** Mojang API（使用 BMCLAPI 国内镜像） */
    val mojangApi: MojangApi by lazy {
        createRetrofit(BASE_URL_MOJANG_MIRROR).create(MojangApi::class.java)
    }

    /** Modrinth API Retrofit 实例 */
    val modrinthApi: ModrinthApi by lazy {
        createRetrofit(BASE_URL_MODRINTH).create(ModrinthApi::class.java)
    }

    /** CurseForge API Retrofit 实例 */
    val curseForgeApi: CurseForgeApi by lazy {
        createRetrofit(BASE_URL_CURSEFORGE).create(CurseForgeApi::class.java)
    }

    /** LittleSkin Yggdrasil 认证 Retrofit 实例 */
    val yggdrasilApi: YggdrasilApi by lazy {
        createRetrofit(BASE_URL_LITTLESKIN).create(YggdrasilApi::class.java)
    }

    /** 微软 OAuth 登录 Retrofit 实例 */
    val microsoftLoginApi: MicrosoftAuthApi by lazy {
        // 注意：微软 OAuth 使用 @Url 动态指定完整路径，此 baseUrl 仅作为占位
        createRetrofit(BASE_URL_MICROSOFT_LOGIN).create(MicrosoftAuthApi::class.java)
    }

    /** Xbox Live 认证 Retrofit 实例 */
    val xboxLiveApi: MicrosoftAuthApi by lazy {
        createRetrofit(BASE_URL_XBOX_LIVE).create(MicrosoftAuthApi::class.java)
    }

    /** XSTS 认证 Retrofit 实例 */
    val xstsApi: MicrosoftAuthApi by lazy {
        createRetrofit(BASE_URL_XSTS).create(MicrosoftAuthApi::class.java)
    }

    /** Minecraft 服务 Retrofit 实例 */
    val minecraftServicesApi: MicrosoftAuthApi by lazy {
        createRetrofit(BASE_URL_MINECRAFT_SERVICES).create(MicrosoftAuthApi::class.java)
    }

    // ======================== 便捷构建方法 ========================

    /**
     * 根据给定的 baseUrl 创建 Retrofit 实例
     *
     * @param baseUrl API 的基础地址
     * @return 配置好的 Retrofit 实例
     */
    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
}