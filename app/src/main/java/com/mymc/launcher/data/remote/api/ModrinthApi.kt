package com.mymc.launcher.data.remote.api

import com.mymc.launcher.data.remote.dto.ModrinthSearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Modrinth API 接口
 *
 * 用于搜索 Mod、光影、材质包，获取项目详情和版本列表
 * Base URL: https://api.modrinth.com/v2/
 */
interface ModrinthApi {

    /**
     * 搜索项目（Mod / 光影 / 材质）
     *
     * @param query 搜索关键词
     * @param facets 过滤条件（如 [["categories:forge"], ["project_type:mod"]]）
     * @param limit 返回数量上限
     * @param offset 偏移量
     */
    @GET("search")
    suspend fun search(
        @Query("query") query: String,
        @Query("facets") facets: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<ModrinthSearchResponse>

    /**
     * 获取项目详情
     *
     * @param projectId 项目 ID
     */
    @GET("project/{project_id}")
    suspend fun getProject(
        @Path("project_id") projectId: String
    ): Response<Any>

    /**
     * 获取项目的版本列表
     *
     * @param projectId 项目 ID
     */
    @GET("project/{project_id}/version")
    suspend fun getProjectVersions(
        @Path("project_id") projectId: String
    ): Response<Any>
}