package com.chotobela.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GameDto(
    val id: String,
    val title: String,
    val description: String = "",
    val platform: String,
    val core: String,
    val version: String = "1.0.0",
    @SerialName("cover_image") val coverImage: String? = null,
    val screenshots: List<String> = emptyList(),
    @SerialName("download_url") val downloadUrl: String = "",
    @SerialName("file_hash") val fileHash: String? = null,
    val size: Long = 0L,
    val developer: String = "",
    val year: Int = 0,
    val rating: Double = 0.0,
    @SerialName("download_count") val downloadCount: Long = 0L,
    val featured: Boolean = false,
    val trending: Boolean = false,
    val category: String = "arcade",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ReviewDto(
    val id: String,
    @SerialName("game_id") val gameId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("username") val username: String = "player",
    val rating: Int,
    val comment: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ProfileDto(
    val id: String,
    val username: String,
    val avatar: String? = null,
    @SerialName("total_playtime_seconds") val totalPlaytimeSeconds: Long = 0L,
    @SerialName("games_played") val gamesPlayed: Int = 0
)

@Serializable
data class FavoriteDto(
    @SerialName("user_id") val userId: String,
    @SerialName("game_id") val gameId: String,
    @SerialName("created_at") val createdAt: String? = null
)
