package com.chotobela.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted storage for sensitive values (auth tokens, session keys).
 * Uses Android Keystore-backed master key.
 */
@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "chotobela_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Fallback for devices with Keystore issues - plain prefs, non-critical data only
        context.getSharedPreferences("chotobela_secure_fallback", Context.MODE_PRIVATE)
    }

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, default: String? = null): String? =
        prefs.getString(key, default)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val KEY_AUTH_TOKEN = "supabase_access_token"
        const val KEY_REFRESH_TOKEN = "supabase_refresh_token"
        const val KEY_USER_ID = "user_id"
    }
}
