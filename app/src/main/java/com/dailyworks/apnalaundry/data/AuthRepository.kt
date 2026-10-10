package com.dailyworks.apnalaundry.data

import android.app.Activity
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.analytics.MetaEvents
import com.dailyworks.apnalaundry.data.remote.AuthApi
import com.dailyworks.apnalaundry.data.remote.CredentialsResponse
import com.dailyworks.apnalaundry.data.remote.FirebasePhoneAuth
import com.dailyworks.apnalaundry.data.remote.parseIsoMs
import com.dailyworks.apnalaundry.data.sync.SyncManager
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * OTP login — Firebase phone auth for real numbers, the backend's challenge
 * flow for app-review numbers — plus the login/logout data lifecycle:
 *  - verify success -> store credentials -> initial sync. If the server
 *    already holds this account's shop, setup is skipped (pull restored
 *    everything); otherwise defaults are seeded and the setup flow runs.
 *  - logout refuses to discard unsynced work: it pushes first and fails
 *    (keeping the session) if that isn't possible.
 */
class AuthRepository(
    private val api: AuthApi,
    private val firebase: FirebasePhoneAuth,
    private val prefs: Prefs,
    private val syncManager: SyncManager,
    private val repo: LaundryRepository,
) {
    /** In-flight OTP; held by the ViewModel between the two steps. */
    sealed interface Challenge {
        val phone: String
        val nextSendAtMs: Long

        /** App-review number (10000000xx): the backend's own deterministic OTP, no SMS. */
        data class Backend(
            val id: String,
            val token: String,
            override val phone: String,
            override val nextSendAtMs: Long,
            val attemptsRemaining: Int,
        ) : Challenge

        /** Every real number: Firebase sent the SMS. */
        data class Firebase(
            val verificationId: String,
            val resendToken: PhoneAuthProvider.ForceResendingToken?,
            override val phone: String,
            override val nextSendAtMs: Long,
        ) : Challenge
    }

    class AuthException(
        message: String,
        val attemptsRemaining: Int? = null,
        /** The code was wrong but the same OTP can be retried. */
        val wrongCode: Boolean = false,
    ) : Exception(message)

    /**
     * Sends (or resends, passing the [previous] challenge) the OTP. For real
     * numbers Firebase may read the SMS itself, before or after the code-sent
     * step: that credential goes to [onAutoVerified], to log in via [verifyCredential].
     */
    suspend fun requestOtp(
        activity: Activity,
        phone: String,
        previous: Challenge?,
        onAutoVerified: (PhoneAuthCredential) -> Unit,
    ): Result<Challenge> = runCatching {
        if (isReviewNumber(phone)) requestBackendOtp(phone)
        else requestFirebaseOtp(activity, phone, (previous as? Challenge.Firebase)?.resendToken, onAutoVerified)
    }

    private suspend fun requestBackendOtp(phone: String): Challenge {
        val reply = runCatching { api.requestOtp(phone) }.getOrElse { throw AuthException(OFFLINE) }
        val body = reply.body
        if (body?.success != true || body.challengeId == null || body.challengeToken == null) {
            throw AuthException(body?.message ?: "Couldn't send OTP — try again")
        }
        return Challenge.Backend(
            id = body.challengeId,
            token = body.challengeToken,
            phone = phone,
            nextSendAtMs = parseIsoMs(body.nextSendAt),
            attemptsRemaining = body.attemptsRemaining ?: 5,
        )
    }

    private suspend fun requestFirebaseOtp(
        activity: Activity,
        phone: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
        onAutoVerified: (PhoneAuthCredential) -> Unit,
    ): Challenge = suspendCancellableCoroutine { cont ->
        fun challenge(id: String, token: PhoneAuthProvider.ForceResendingToken?) = Challenge.Firebase(
            verificationId = id,
            resendToken = token,
            phone = phone,
            nextSendAtMs = System.currentTimeMillis() + RESEND_COOLDOWN_MS,
        )
        firebase.send(activity, phone, resendToken) { event ->
            when (event) {
                is FirebasePhoneAuth.Event.CodeSent ->
                    if (cont.isActive) cont.resume(challenge(event.verificationId, event.resendToken))
                is FirebasePhoneAuth.Event.AutoVerified -> {
                    // Instant verification can skip CodeSent entirely.
                    if (cont.isActive) cont.resume(challenge("", resendToken))
                    onAutoVerified(event.credential)
                }
                is FirebasePhoneAuth.Event.Failed ->
                    if (cont.isActive) cont.resumeWithException(sendError(event.error))
            }
        }
    }

    suspend fun verifyOtp(challenge: Challenge, otp: String): Result<Unit> = when (challenge) {
        is Challenge.Backend -> runCatching {
            val reply = runCatching { api.verifyOtp(challenge.id, challenge.token, otp, prefs.deviceId()) }
                .getOrElse { throw AuthException(OFFLINE) }
            val body = reply.body
            if (body?.success != true) {
                val attempts = body?.attemptsRemaining
                throw AuthException(
                    body?.message ?: "Verification failed — try again",
                    attemptsRemaining = attempts,
                    wrongCode = attempts != null && attempts > 0,
                )
            }
            finishLogin(body)
        }
        is Challenge.Firebase ->
            if (challenge.verificationId.isEmpty()) Result.failure(AuthException("Request a new OTP first"))
            else verifyCredential(firebase.credential(challenge.verificationId, otp))
    }

    /** Logs in with a Firebase credential (typed code or auto-read SMS). */
    suspend fun verifyCredential(credential: PhoneAuthCredential): Result<Unit> = runCatching {
        val idToken = try {
            firebase.idToken(credential)
        } catch (e: FirebaseException) {
            throw verifyError(e)
        }
        val reply = runCatching { api.firebaseLogin(idToken, prefs.deviceId()) }
            .getOrElse { throw AuthException(OFFLINE) }
        val body = reply.body
        if (body?.success != true) throw AuthException(body?.message ?: "Verification failed — try again")
        finishLogin(body)
    }

    private fun sendError(e: FirebaseException) = AuthException(
        when (e) {
            is FirebaseNetworkException -> OFFLINE
            is FirebaseTooManyRequestsException -> "Too many OTP requests — try again later"
            is FirebaseAuthInvalidCredentialsException -> "Enter a valid 10-digit mobile number"
            else -> "Couldn't send OTP — try again"
        },
    )

    private fun verifyError(e: FirebaseException) = when {
        e is FirebaseNetworkException -> AuthException(OFFLINE)
        e is FirebaseTooManyRequestsException -> AuthException("Too many attempts — try again later")
        e is FirebaseAuthInvalidCredentialsException && e.errorCode == "ERROR_SESSION_EXPIRED" ->
            AuthException("OTP expired — request a new one")
        e is FirebaseAuthInvalidCredentialsException -> AuthException("Wrong OTP", wrongCode = true)
        else -> AuthException("Verification failed — try again")
    }

    /** Server credentials in hand: store them, then the initial sync / seed. */
    private suspend fun finishLogin(body: CredentialsResponse) {
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
        // non-fatal — the app works offline and syncs later. A just-created
        // account has nothing to pull, so it skips straight to seeding.
        if (!body.isNewUser) syncManager.syncNow()
        val existingAccount = repo.hasShop()
        if (!existingAccount) {
            repo.ensureSeeded(shopPhone = user.phone)
            // Just pulled (or nothing to pull) — only the seed needs to go up.
            syncManager.pushNow()
        }
        // An existing shop isn't proof of setup (the seed went up at first
        // login): an account that never onboarded still gets name -> rate list
        // after paying.
        val onboarded = repo.isOnboarded()
        prefs.setSetupDone(onboarded)

        // Identify the owner so every event attributes to this shop, then the
        // funnel event. is_new_user / needs_setup = this account had no shop yet.
        Analytics.identify(user.id, user.phone, repo.currentShopName())
        Analytics.loggedIn(isNewUser = !existingAccount, needsSetup = !onboarded)
        MetaEvents.identify(user.id, user.phone)
        MetaEvents.loginSuccess(isNewUser = !existingAccount)
    }

    private companion object {
        const val OFFLINE = "No internet — check your connection and try again"
        const val RESEND_COOLDOWN_MS = 30_000L

        // 10000000xx: the backend's app-review numbers (not real Indian mobiles).
        fun isReviewNumber(phone: String) = phone.startsWith("10000000")
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
        Analytics.loggedOut()
        MetaEvents.clearIdentity()
        prefs.logoutAndReset()
        repo.clearAll()
    }
}
