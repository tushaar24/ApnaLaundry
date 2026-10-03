package com.dailyworks.apnalaundry.data

import com.dailyworks.apnalaundry.data.remote.AuthApi
import com.dailyworks.apnalaundry.data.remote.parseIsoMs
import com.dailyworks.apnalaundry.data.sync.SyncManager
import kotlinx.coroutines.flow.first

/**
 * Real OTP auth (challenge flow ported from HealthProduct) plus the
 * login/logout data lifecycle:
 *  - verify success -> store credentials -> initial sync. If the server
 *    already holds this account's shop, setup is skipped (pull restored
 *    everything); otherwise defaults are seeded and the setup flow runs.
 *  - logout refuses to discard unsynced work: it pushes first and fails
 *    (keeping the session) if that isn't possible.
 */
class AuthRepository(
    private val api: AuthApi,
    private val prefs: Prefs,
    private val syncManager: SyncManager,
    private val repo: LaundryRepository,
) {
    /** In-flight OTP challenge; held by the ViewModel between the two steps. */
    data class Challenge(
        val id: String,
        val token: String,
        val phone: String,
        val nextSendAtMs: Long,
        val attemptsRemaining: Int,
    )

    class AuthException(message: String, val attemptsRemaining: Int? = null) : Exception(message)

    suspend fun requestOtp(phone: String): Result<Challenge> = runCatching {
        val reply = runCatching { api.requestOtp(phone) }
            .getOrElse { throw AuthException("No internet — check your connection and try again") }
        val body = reply.body
        if (body?.success != true || body.challengeId == null || body.challengeToken == null) {
            throw AuthException(body?.message ?: "Couldn't send OTP — try again")
        }
        Challenge(
            id = body.challengeId,
            token = body.challengeToken,
            phone = phone,
            nextSendAtMs = parseIsoMs(body.nextSendAt),
            attemptsRemaining = body.attemptsRemaining ?: 5,
        )
    }

    suspend fun verifyOtp(challenge: Challenge, otp: String): Result<Unit> = runCatching {
        val deviceId = prefs.deviceId()
        val reply = runCatching { api.verifyOtp(challenge.id, challenge.token, otp, deviceId) }
            .getOrElse { throw AuthException("No internet — check your connection and try again") }
        val body = reply.body
        if (body?.success != true) {
            throw AuthException(
                body?.message ?: "Verification failed — try again",
                attemptsRemaining = body?.attemptsRemaining,
            )
        }
        val user = body.user ?: throw AuthException("Verification failed — try again")

        // Local data left over from a DIFFERENT account (e.g. a dead session
        // followed by logging in with another number) must never be pushed
        // into this one.
        val prevPhone = prefs.userPhone.first()
        if (prevPhone != null && prevPhone != user.phone) {
            repo.clearAll()
            prefs.setLastSyncCheckpoint(0)
            prefs.setSetupDone(false)
        }

        prefs.setCredentials(
            access = body.accessToken ?: throw AuthException("Verification failed — try again"),
            accessExpiresAt = parseIsoMs(body.accessExpiresAt),
            refresh = body.refreshToken ?: throw AuthException("Verification failed — try again"),
            userId = user.id,
            phone = user.phone,
        )
        prefs.setLoggedIn(true)

        // Initial sync: pull this account's data if it exists. Failures are
        // non-fatal — the app works offline and syncs later.
        syncManager.syncNow()
        if (repo.hasShop()) {
            // Existing account restored from the server — skip the setup flow.
            prefs.setSetupDone(true)
        } else {
            repo.ensureSeeded(shopPhone = user.phone)
            syncManager.syncNow()
        }
    }

    /**
     * Push-then-logout. Fails (session kept) if unsynced work can't be pushed
     * for a transient reason — but a dead session can never sync again, so it
     * doesn't block logout.
     */
    suspend fun logout(): Result<Unit> = runCatching {
        if (syncManager.hasPendingChanges()) {
            val r = syncManager.syncNow()
            val authDead = (r as? SyncManager.Result.Error)?.authDead == true
            if (!authDead && syncManager.hasPendingChanges()) {
                throw AuthException("Couldn't sync your latest changes — check internet and try again")
            }
        }
        prefs.accessToken.first()?.let { token ->
            runCatching { api.logout(token) } // best-effort server-side revoke
        }
        prefs.logoutAndReset()
        repo.clearAll()
    }
}
