package com.chotobela.core.network

import com.chotobela.core.network.dto.GameDto
import com.chotobela.core.network.dto.ReviewDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

/** Catalog operations backed by Supabase in live mode, demo catalog otherwise. */
class StoreRepository(
    private val provider: SupabaseClientProvider
) : StoreApi {

    override suspend fun featuredGames(): List<GameDto> =
        remoteOrDemo { client ->
            client.postgrest.from("games")
                .select { eq("featured", true) }
                .decodeList()
        }

    override suspend fun trendingGames(): List<GameDto> =
        remoteOrDemo { client ->
            client.postgrest.from("games")
                .select { eq("trending", true) }
                .decodeList()
        }

    override suspend fun recentlyAdded(limit: Int): List<GameDto> =
        remoteOrDemo { client ->
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
                .select { eq("category", category) }
                .decodeList()
        }

    override suspend fun categories(): List<String> =
        runCatchingRemote {
            val client = provider.clientOrNull() ?: return DemoCatalog.games.map { it.category }
            client.postgrest.from("games")
                .select { }
                .decodeList<GameDto>()
        }.getOrDefault(DemoCatalog.games)
            .map { it.category }
            .distinct()

    override suspend fun search(query: String): List<GameDto> {
        val q = query.trim()
        if (q.isEmpty()) return recentlyAdded()
        return remoteOrDemo { client ->
            client.postgrest.from("games")
                .select { ilike("title", "%$q%") }
                .decodeList()
        }.filter { it.title.contains(q, ignoreCase = true) }
    }

    override suspend fun gameById(id: String): GameDto? =
        runCatchingRemote {
            val client = provider.clientOrNull() ?: error("demo mode")
            client.postgrest.from("games")
                .select { eq("id", id) }
                .decodeSingleOrNull<GameDto>()
        }.getOrNull() ?: DemoCatalog.games.firstOrNull { it.id == id }

    override suspend fun reviewsFor(gameId: String): List<ReviewDto> =
        runCatchingRemote {
            val client = provider.clientOrNull() ?: return emptyList()
            client.postgrest.from("reviews")
                .select { eq("game_id", gameId) }
                .decodeList<ReviewDto>()
        }.getOrDefault(emptyList())

    /** Live query with graceful fallback to the demo catalog. */
    private suspend fun remoteOrDemo(
        block: suspend (SupabaseClient) -> List<GameDto>
    ): List<GameDto> =
        runCatchingRemote {
            val client = provider.clientOrNull() ?: error("demo mode")
            block(client)
        }.fold(onSuccess = { it }, onFailure = { DemoCatalog.games })

    private inline fun <T> runCatchingRemote(block: () -> T): Result<T> = runCatching(block)
}
