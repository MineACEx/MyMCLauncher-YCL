package com.mymc.launcher.data.remote.api

import com.mymc.launcher.data.remote.dto.MojangVersionDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Mojang API 接口
 *
 * 用于获取 Minecraft 版本清单、版本元数据及资源索引
 */
interface MojangApi {

    /**
     * 获取 Mojang 版本清单
     * URL: https://launchermeta.mojang.com/mc/game/version_manifest.json
     */
    @GET("mc/game/version_manifest.json")
    suspend fun getVersionManifest(): Response<MojangVersionDto>

    /**
     * 获取单个版本的元数据 JSON
     *
     * @param versionUrl 版本元数据的完整 URL（来自版本清单中的 VersionEntry.url）
     */
    @GET
    suspend fun getVersionMetadata(@Url versionUrl: String): Response<Any>

    /**
     * 获取资源索引文件
     *
     * @param assetIndexUrl 资源索引的完整 URL
     */
    @GET
    suspend fun getAssetIndex(@Url assetIndexUrl: String): Response<Any>
}