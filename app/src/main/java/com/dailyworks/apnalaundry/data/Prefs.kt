package com.dailyworks.apnalaundry.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "apna_prefs")

/** Small app-level flags kept out of the domain DB. */
class Prefs(private val context: Context) {
    private val LOGGED_IN = booleanPreferencesKey("logged_in")
    private val SETUP_DONE = booleanPreferencesKey("setup_done")
    private val HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")

    val loggedIn: Flow<Boolean> = context.dataStore.data.map { it[LOGGED_IN] ?: false }
    val setupDone: Flow<Boolean> = context.dataStore.data.map { it[SETUP_DONE] ?: false }
    val hideAmounts: Flow<Boolean> = context.dataStore.data.map { it[HIDE_AMOUNTS] ?: false }

    suspend fun setLoggedIn(v: Boolean) { context.dataStore.edit { it[LOGGED_IN] = v } }
    suspend fun setSetupDone(v: Boolean) { context.dataStore.edit { it[SETUP_DONE] = v } }
    suspend fun setHideAmounts(v: Boolean) { context.dataStore.edit { it[HIDE_AMOUNTS] = v } }

    suspend fun logoutAndReset() {
        context.dataStore.edit { it[LOGGED_IN] = false; it[SETUP_DONE] = false; it[HIDE_AMOUNTS] = false }
    }
}
