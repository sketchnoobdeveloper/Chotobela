package com.chotobela.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "games",
    indices = [Index(value = ["title"]), Index(value = ["platform"])]
)
data class GameEntity(
    @PrimaryKey val id: String,
    val title: String,
    val platform: String,
    val core: String,
    val description: String = "",
    val developer: String = "",
    val year: Int = 0,
    val coverUrl: String? = null,
    val rating: Double = 0.0,
    val romPath: String,
    val sizeBytes: Long = 0L,
    val favorite: Boolean = false,
    val addedAt: Long,
    val lastPlayedAt: Long? = null,
    val playtimeSeconds: Long = 0L
)
