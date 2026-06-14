package com.mymc.launcher.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Modrinth 搜索响应 DTO
 *
 * 对应 Modrinth API: https://api.modrinth.com/v2/search
 */
data class ModrinthSearchResponse(
    @SerializedName("hits")
    val hits: List<ModrinthProject>
)

/**
 * Modrinth 项目
 *
 * @property projectId 项目 ID
 * @property title 项目标题
 * @property description 项目描述
 * @property author 作者
 * @property iconUrl 图标 URL
 * @property versions 支持的版本列表
 * @property categories 分类列表
 * @property downloads 下载量
 */
data class ModrinthProject(
    @SerializedName("project_id")
    val projectId: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("author")
    val author: String,
    @SerializedName("icon_url")
    val iconUrl: String? = null,
    @SerializedName("versions")
    val versions: List<String> = emptyList(),
    @SerializedName("categories")
    val categories: List<String> = emptyList(),
    @SerializedName("downloads")
    val downloads: Int = 0
)