package com.chotobela.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import timber.log.Timber

/**
 * Lazily creates the Supabase client only when live mode is active.
 * In demo mode [clientOrNull] returns null and callers use local fallbacks.
 */
class SupabaseClientProvider {

    @Volatile
    private var cached: SupabaseClient? = null

    fun clientOrNull(): SupabaseClient? {
        if (SupabaseConfig.isDemoMode) return null
        return cached ?: synchronized(this) {
            cached ?: runCatching {
                createSupabaseClient(
                    supabaseUrl = SupabaseConfig.URL,
                    supabaseKey = SupabaseConfig.ANON_KEY
                ) {
                    install(GoTrue)
                    install(Postgrest)
                    install(Storage)
                }
            }.onFailure { Timber.e(it, "Failed to create Supabase client") }
                .getOrNull()
                ?.also { cached = it }
        }
    }
}
