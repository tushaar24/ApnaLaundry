package com.dailyworks.apnalaundry.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "apna_prefs")

/** Small app-level flags + auth credentials + sync cursor, kept out of the domain DB. */
class Prefs(private val context: Context) {
    private val LOGGED_IN = booleanPreferencesKey("logged_in")
    private val SETUP_DONE = booleanPreferencesKey("setup_done")
    private val HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")

    // Auth (opaque server tokens — see data/remote/TokenManager)
    private val ACCESS_TOKEN = stringPreferencesKey("access_token")
    private val ACCESS_EXPIRES_AT = longPreferencesKey("access_expires_at") // epoch ms
    private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    private val USER_ID = stringPreferencesKey("user_id")
    private val USER_PHONE = stringPreferencesKey("user_phone")
    private val DEVICE_ID = stringPreferencesKey("device_id") // stable installation id, survives logout

    // Sync cursor: the server checkpoint from the last successful pull.
    private val SYNC_CHECKPOINT = longPreferencesKey("sync_checkpoint")

    val loggedIn: Flow<Boolean> = context.dataStore.data.map { it[LOGGED_IN] ?: false }
    val setupDone: Flow<Boolean> = context.dataStore.data.map { it[SETUP_DONE] ?: false }
    val hideAmounts: Flow<Boolean> = context.dataStore.data.map { it[HIDE_AMOUNTS] ?: false }
    val accessToken: Flow<String?> = context.dataStore.data.map { it[ACCESS_TOKEN] }
    val accessExpiresAt: Flow<Long> = context.dataStore.data.map { it[ACCESS_EXPIRES_AT] ?: 0L }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[REFRESH_TOKEN] }
    val userPhone: Flow<String?> = context.dataStore.data.map { it[USER_PHONE] }
    val lastSyncCheckpoint: Flow<Long> = context.dataStore.data.map { it[SYNC_CHECKPOINT] ?: 0L }

    suspend fun setLoggedIn(v: Boolean) { context.dataStore.edit { it[LOGGED_IN] = v } }
    suspend fun setSetupDone(v: Boolean) { context.dataStore.edit { it[SETUP_DONE] = v } }
    suspend fun setHideAmounts(v: Boolean) { context.dataStore.edit { it[HIDE_AMOUNTS] = v } }
    suspend fun setLastSyncCheckpoint(v: Long) { context.dataStore.edit { it[SYNC_CHECKPOINT] = v } }

    suspend fun setCredentials(access: String, accessExpiresAt: Long, refresh: String, userId: String, phone: String) {
        context.dataStore.edit {
            it[ACCESS_TOKEN] = access
            it[ACCESS_EXPIRES_AT] = accessExpiresAt
            it[REFRESH_TOKEN] = refresh
            it[USER_ID] = userId
            it[USER_PHONE] = phone
        }
    }

    suspend fun setAccessPair(access: String, accessExpiresAt: Long, refresh: String) {
        context.dataStore.edit {
            it[ACCESS_TOKEN] = access
            it[ACCESS_EXPIRES_AT] = accessExpiresAt
            it[REFRESH_TOKEN] = refresh
        }
    }

    /**
     * Drops the tokens (dead session) but KEEPS user id/phone — the next
     * login compares them to decide whether local data belongs to the same
     * account (AuthRepository.verifyOtp) or must be wiped first.
     */
    suspend fun clearCredentials() {
        context.dataStore.edit {
            it.remove(ACCESS_TOKEN); it.remove(ACCESS_EXPIRES_AT); it.remove(REFRESH_TOKEN)
        }
    }

    /** Stable per-install device id (HealthProduct's device_installation_id). */
    suspend fun deviceId(): String {
        context.dataStore.data.first()[DEVICE_ID]?.let { return it }
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { prefs ->
            if (prefs[DEVICE_ID] == null) prefs[DEVICE_ID] = id
        }
        return context.dataStore.data.first()[DEVICE_ID] ?: id
    }

    suspend fun logoutAndReset() {
        context.dataStore.edit {
            it[LOGGED_IN] = false; it[SETUP_DONE] = false; it[HIDE_AMOUNTS] = false
            it[SYNC_CHECKPOINT] = 0L
            it.remove(ACCESS_TOKEN); it.remove(ACCESS_EXPIRES_AT); it.remove(REFRESH_TOKEN)
            it.remove(USER_ID); it.remove(USER_PHONE)
            // DEVICE_ID survives — it identifies the installation, not the user.
        }
    }
}
