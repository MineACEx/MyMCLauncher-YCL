package com.mymc.launcher.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Mojang 版本清单响应 DTO
 *
 * 对应 Mojang API: https://launchermeta.mojang.com/mc/game/version_manifest.json
 */
data class MojangVersionDto(
    @SerializedName("versions")
    val versions: List<VersionEntry>
) {
    /**
     * 版本条目
     *
     * @property id 版本 ID（如 "1.21"）
     * @property type 版本类型（如 "release", "snapshot"）
     * @property url 该版本元数据的 JSON URL
     */
    data class VersionEntry(
        @SerializedName("id")
        val id: String,
        @SerializedName("type")
        val type: String,
        @SerializedName("url")
        val url: String
    )
}