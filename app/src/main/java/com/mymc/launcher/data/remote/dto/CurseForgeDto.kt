package com.mymc.launcher.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * CurseForge 搜索响应 DTO
 *
 * 对应 CurseForge API 搜索接口
 */
data class CurseForgeSearchResponse(
    @SerializedName("data")
    val data: List<CurseForgeMod>
)

/**
 * CurseForge Mod 项目
 *
 * @property id Mod ID
 * @property name Mod 名称
 * @property summary 简介
 * @property authors 作者列表
 * @property logo Logo 图片信息
 * @property latestFiles 最新文件列表
 */
data class CurseForgeMod(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("summary")
    val summary: String,
    @SerializedName("authors")
    val authors: List<Author> = emptyList(),
    @SerializedName("logo")
    val logo: Logo? = null,
    @SerializedName("latestFiles")
    val latestFiles: List<FileInfo> = emptyList()
)

/**
 * 作者信息
 */
data class Author(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String
)

/**
 * Logo 图片信息
 */
data class Logo(
    @SerializedName("url")
    val url: String?
)

/**
 * 文件信息
 *
 * @property id 文件 ID
 * @property downloadUrl 下载地址
 * @property fileLength 文件长度（字节）
 */
data class FileInfo(
    @SerializedName("id")
    val id: Int,
    @SerializedName("downloadUrl")
    val downloadUrl: String?,
    @SerializedName("fileLength")
    val fileLength: Long
)