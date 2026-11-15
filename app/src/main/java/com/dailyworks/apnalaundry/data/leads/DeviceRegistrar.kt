package com.dailyworks.apnalaundry.data.leads

import android.content.Context
import com.dailyworks.apnalaundry.BuildConfig
import com.dailyworks.apnalaundry.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Tells the server this phone's push token (POST /api/laundry/devices), so a new lead can be
 * pushed to the owner the moment the team sends it. The token comes from Firebase (push/Push.kt);
 * it is sent after login and again when it changes, re-sent at most once a day per account so the
 * server knows the phone is still around, and removed at logout. Never for a staff-only phone
 * (its token would get a 403 — leads are the owner's).
 */
class DeviceRegistrar(context: Context, private val api: LeadsApi, private val prefs: Prefs) {
    private val sp = context.applicationContext.getSharedPreferences("push", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Firebase handed us a token (app start / refresh). */
    fun onToken(token: String) {
        val had = sp.getString(KEY_TOKEN, null)
        sp.edit().putString(KEY_TOKEN, token).apply()
        if (had != token) sp.edit().remove(KEY_REGISTERED).apply()
        scope.launch { runCatching { sync() } }
    }

    /** Register if logged in and not registered for this account + token in the last day. */
    suspend fun sync() {
        if (!prefs.loggedIn.first()) return
        val token = sp.getString(KEY_TOKEN, null) ?: return
        val user = prefs.userId.first() ?: return
        val key = "$user:${token.hashCode()}"
        val fresh = sp.getString(KEY_REGISTERED, null) == key && System.currentTimeMillis() - sp.getLong(KEY_AT, 0L) < DAY_MS
        if (fresh) return
        if (api.registerDevice(token, BuildConfig.VERSION_CODE)) {
            sp.edit().putString(KEY_REGISTERED, key).putLong(KEY_AT, System.currentTimeMillis()).apply()
        }
    }

    /** Logout: the server stops pushing this owner's leads to this phone. Best effort. */
    suspend fun forget() {
        val token = sp.getString(KEY_TOKEN, null)
        sp.edit().remove(KEY_REGISTERED).remove(KEY_AT).apply()
        if (token != null) runCatching { api.unregisterDevice(token) }
    }

    private companion object {
        const val KEY_TOKEN = "fcm_token"
        const val KEY_REGISTERED = "fcm_registered_for"
        const val KEY_AT = "fcm_registered_at"
        const val DAY_MS = 24 * 3600 * 1000L
    }
}
