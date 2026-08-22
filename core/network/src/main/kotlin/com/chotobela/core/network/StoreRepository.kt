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

class StoreRepository(
    private val provider: SupabaseClientProvider
) : StoreApi {

    override suspend fun featuredGames(): List<GameDto> = fetchOrDemo { client ->
        client.postgrest.from("games")
            .select {
                filter { eq("featured", true) }
            }
            .decodeList<GameDto>()
    }.filter { it.featured }

    override suspend fun trendingGames(): List<GameDto> = fetchOrDemo { client ->
        client.postgrest.from("games")
            .select {
                filter { eq("trending", true) }
            }
            .decodeList<GameDto>()
    }.filter { it.trending }

    override suspend fun recentlyAdded(limit: Int): List<GameDto> = fetchOrDemo(limit) { client ->
        client.postgrest.from("games")
            .select {
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(count = limit.toLong())
            }
            .decodeList<GameDto>()
    }

    override suspend fun byCategory(category: String): List<GameDto> = fetchOrDemo(category) { client ->
        client.postgrest.from("games")
            .select {
                filter { eq("category", category) }
            }
            .decodeList<GameDto>()
    }

    override suspend fun categories(): List<String> =
        fetchOrDemo(Unit) { _ -> DemoCatalog.games.map { it.category } }
            .map { it.category }
            .distinct()

    override suspend fun search(query: String): List<GameDto> {
        val q = query.trim()
        if (q.isEmpty()) return recentlyAdded()
        return fetchOrDemo(q) { client ->
            client.postgrest.from("games")
                .select {
                    filter { ilike("title", "%$q%") }
                }
                .decodeList<GameDto>()
        }.filter { it.title.contains(q, ignoreCase = true) }
    }

    override suspend fun gameById(id: String): GameDto? =
        fetchOrDemo(id) { client ->
            client.postgrest.from("games")
                .select {
                    filter { eq("id", id) }
                }
                .decodeSingleOrNull<GameDto>()
        }

    override suspend fun reviewsFor(gameId: String): List<ReviewDto> =
        runCatchingRemote {
            val client = provider.clientOrNull() ?: return emptyList()
            client.postgrest.from("reviews")
                .select { filter { eq("game_id", gameId) } }
                .decodeList<ReviewDto>()
        }.getOrDefault(emptyList())

    private inline fun <T, R> fetchOrDemo(fallbackArg: T, block: (io.github.jan.supabase.SupabaseClient) -> List<GameDto>): List<GameDto> =
        runCatchingRemote {
            val client = provider.clientOrNull() ?: throw IllegalStateException("demo")
            block(client)
        }.getOrElse { demoGames(fallbackArg) }

    private fun demoGames(@Suppress("UNUSED_PARAMETER") arg: Any?): List<GameDto> = DemoCatalog.games

    private inline fun <T> runCatchingRemote(block: () -> T): Result<T> =
        runCatching(block)
}
