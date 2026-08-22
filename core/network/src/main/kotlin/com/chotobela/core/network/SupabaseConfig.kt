package com.chotobela.core.network

import timber.log.Timber

/**
 * Central configuration for the Chotobela backend.
 *
 * Credentials come from local.properties via BuildConfig and are never committed.
 * When missing, [isDemoMode] is true and all data sources fall back to the seeded
 * demo catalog so the full experience works offline / before backend provisioning.
 */
object SupabaseConfig {

    const val URL: String = BuildConfig.SUPABASE_URL
    const val ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY

    val isConfigured: Boolean
        get() = URL.isNotBlank() && ANON_KEY.isNotBlank()

    val isDemoMode: Boolean
        get() = !isConfigured

    fun logMode() {
        if (isDemoMode) {
            Timber.i("Chotobela backend: DEMO MODE (no Supabase credentials configured)")
        } else {
            Timber.i("Chotobela backend: LIVE (Supabase configured)")
        }
    }
}
