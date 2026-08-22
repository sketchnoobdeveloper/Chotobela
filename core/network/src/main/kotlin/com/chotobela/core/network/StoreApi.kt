package com.chotobela.core.network

import com.chotobela.core.network.dto.GameDto
import com.chotobela.core.network.dto.ReviewDto

/** Catalog operations backed by Supabase in live mode, demo catalog otherwise. */
interface StoreApi {
    suspend fun featuredGames(): List<GameDto>
    suspend fun trendingGames(): List<GameDto>
    suspend fun recentlyAdded(limit: Int = 20): List<GameDto>
    suspend fun byCategory(category: String): List<GameDto>
    suspend fun categories(): List<String>
    suspend fun search(query: String): List<GameDto>
    suspend fun gameById(id: String): GameDto?
    suspend fun reviewsFor(gameId: String): List<ReviewDto>
}
