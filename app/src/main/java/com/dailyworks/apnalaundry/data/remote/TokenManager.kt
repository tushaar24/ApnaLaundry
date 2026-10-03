package com.dailyworks.apnalaundry.data.remote

import com.dailyworks.apnalaundry.data.Prefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hands out a valid access token for API calls, refreshing (single-flight,
 * mutex-guarded) when the stored one is within 30s of expiry — HealthProduct's
 * client-side token policy. Refresh tokens are single-use on the server, so
 * the rotated pair is persisted atomically before this returns.
 *
 * Returns null when there is no usable token: offline-with-expired-access
 * (sync just retries later) or a dead session (401 on refresh — credentials
 * are wiped and loggedIn is flipped off so the nav gate shows login).
 */
class TokenManager(private val prefs: Prefs, private val api: AuthApi) {
    private val mutex = Mutex()

    suspend fun validAccessToken(): String? = mutex.withLock {
        val access = prefs.accessToken.first() ?: return null
        val expiresAt = prefs.accessExpiresAt.first()
        if (System.currentTimeMillis() < expiresAt - 30_000) return access
        refreshLocked()
    }

    /** Called after a request still got a 401 with a fresh-looking token. */
    suspend fun forceRefresh(): String? = mutex.withLock { refreshLocked() }

    private suspend fun refreshLocked(): String? {
        val refresh = prefs.refreshToken.first() ?: return null
        val reply = runCatching { api.refresh(refresh) }.getOrNull()
            ?: return null // offline — try again on the next sync
        val body = reply.body
        if (reply.status == 401) {
            // Session revoked/expired on the server: this login is dead.
            prefs.clearCredentials()
            prefs.setLoggedIn(false)
            return null
        }
        if (body?.success != true || body.accessToken == null || body.refreshToken == null) return null
        prefs.setAccessPair(body.accessToken, parseIsoMs(body.accessExpiresAt), body.refreshToken)
        return body.accessToken
    }
}
