package com.chotobela.core.network

import com.chotobela.core.network.dto.GameDto
import com.chotobela.core.network.dto.ReviewDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter

/** Catalog operations backed by Supabase in live mode, demo catalog otherwise. */
class StoreRepository(
    private val provider: SupabaseClientProvider
) : StoreApi {

    override suspend fun featuredGames(): List<GameDto> =
        remoteOrDemo { client ->
            client.postgrest.from("games")
                .select {
                    filter { eq("featured", true) }
                }
                .decodeList()
        }

    override suspend fun trendingGames(): List<GameDto> =
        remoteOrDemo { client ->
            client.postgrest.from("games")
                .select {
                    filter { eq("trending", true) }
                }
                .decodeList()
        }

    override suspend fun recentlyAdded(limit: Int): List<GameDto> =
        remoteOrDemo(limit) { client ->
            client.postgrest.from("games")
                .select {
                    order("created_at", Order.DESCENDING)
                    limit(count = limit.toLong())
                }
                .decodeList()
        }

    override suspend fun byCategory(category: String): List<GameDto> =
        remoteOrDemo { client ->
            client.postgrest.from("games")
                .select {
                    filter { eq("category", category) }
                }
                .decodeList()
        }

    override suspend fun categories(): List<String> =
        runCatchingRemote {
            val client = provider.clientOrNull() ?: error("demo mode")
            client.postgrest.from("games")
                .select { }
                .decodeList<GameDto>()
        }.getOrElse { DemoCatalog.games }
            .map { it.category }
            .distinct()

    override suspend fun search(query: String): List<GameDto> {
        val q = query.trim()
        if (q.isEmpty()) return recentlyAdded()
        return remoteOrDemo { client ->
            client.postgrest.from("games")
                .select {
                    filter { ilike("title", "%$q%") }
                }
                .decodeList()
        }.filter { it.title.contains(q, ignoreCase = true) }
    }

    override suspend fun gameById(id: String): GameDto? =
        runCatchingRemote {
            val client = provider.clientOrNull() ?: return DemoCatalog.games.firstOrNull { it.id == id }
            client.postgrest.from("games")
                .select {
                    filter { eq("id", id) }
                }
                .decodeSingleOrNull<GameDto>()
        }.getOrNull() ?: DemoCatalog.games.firstOrNull { it.id == id }

    override suspend fun reviewsFor(gameId: String): List<ReviewDto> =
        runCatchingRemote {
            val client = provider.clientOrNull() ?: return emptyList()
            client.postgrest.from("reviews")
                .select {
                    filter { eq("game_id", gameId) }
                }
                .decodeList<ReviewDto>()
        }.getOrDefault(emptyList())

    /** Live query with graceful fallback to the demo catalog. */
    private suspend fun remoteOrDemo(
        fallbackArg: Any? = null,
        block: suspend (SupabaseClient) -> List<GameDto>
    ): List<GameDto> {
        @Suppress("UNUSED_VARIABLE") val arg = fallbackArg // kept for logging context in future
        return runCatchingRemote {
            val client = provider.clientOrNull() ?: error("demo mode")
            block(client)
        }.fold(
            onSuccess = { it },
            onFailure = { DemoCatalog.games }
        )
    }

    private inline fun <T> runCatchingRemote(block: () -> T): Result<T> = runCatching(block)
}
