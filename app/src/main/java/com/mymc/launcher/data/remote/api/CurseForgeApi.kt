package com.mymc.launcher.data.remote.api

import com.mymc.launcher.data.remote.dto.CurseForgeSearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CurseForge API 接口
 *
 * 用于搜索 Mod 及获取文件下载链接
 * Base URL: https://api.curseforge.com/v1/
 */
interface CurseForgeApi {

    /**
     * 搜索 Mod
     *
     * @param gameId 游戏 ID（Minecraft = 432）
     * @param searchFilter 搜索关键词
     * @param pageSize 每页数量
     * @param index 页偏移
     */
    @GET("mods/search")
    suspend fun searchMods(
        @Query("gameId") gameId: Int = 432,
        @Query("searchFilter") searchFilter: String,
        @Query("pageSize") pageSize: Int = 20,
        @Query("index") index: Int = 0
    ): Response<CurseForgeSearchResponse>

    /**
     * 获取 Mod 详情
     *
     * @param modId Mod ID
     */
    @GET("mods/{modId}")
    suspend fun getMod(
        @Path("modId") modId: Int
    ): Response<Any>

    /**
     * 获取 Mod 的文件列表
     *
     * @param modId Mod ID
     */
    @GET("mods/{modId}/files")
    suspend fun getModFiles(
        @Path("modId") modId: Int
    ): Response<Any>

    /**
     * 获取文件的下载链接
     *
     * @param modId Mod ID
     * @param fileId 文件 ID
     */
    @GET("mods/{modId}/files/{fileId}/download-url")
    suspend fun getFileDownloadUrl(
        @Path("modId") modId: Int,
        @Path("fileId") fileId: Int
    ): Response<Any>
}