package com.dailyworks.apnalaundry.data.remote

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await

/**
 * Firebase phone auth (project shweta-makeover): Firebase sends the OTP SMS,
 * auto-reads it on the device (its SMS carries the app hash), and checks the
 * code. We only keep the resulting ID token, which the backend trades for our
 * own session — the Firebase sign-in itself is dropped straight away.
 */
class FirebasePhoneAuth {

    sealed interface Event {
        /** SMS sent; the code typed by the user is checked against [verificationId]. */
        data class CodeSent(
            val verificationId: String,
            val resendToken: PhoneAuthProvider.ForceResendingToken,
        ) : Event

        /** The device read the SMS itself (or verified the number instantly). */
        data class AutoVerified(val credential: PhoneAuthCredential) : Event

        data class Failed(val error: FirebaseException) : Event
    }

    private val auth get() = FirebaseAuth.getInstance()

    /** Sends (or, with [resendToken], resends) the OTP to a 10-digit Indian number. */
    fun send(
        activity: Activity,
        phone: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
        onEvent: (Event) -> Unit,
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) =
                onEvent(Event.CodeSent(id, token))

            override fun onVerificationCompleted(credential: PhoneAuthCredential) =
                onEvent(Event.AutoVerified(credential))

            override fun onVerificationFailed(e: FirebaseException) = onEvent(Event.Failed(e))
        }
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber("+91$phone")
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .apply { resendToken?.let(::setForceResendingToken) }
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun credential(verificationId: String, code: String): PhoneAuthCredential =
        PhoneAuthProvider.getCredential(verificationId, code)

    /** Signs in with [credential] and returns a fresh ID token (throws Firebase errors, e.g. a wrong code). */
    suspend fun idToken(credential: PhoneAuthCredential): String {
        val user = auth.signInWithCredential(credential).await().user
            ?: error("Firebase sign-in returned no user")
        val token = user.getIdToken(true).await().token ?: error("Firebase returned no ID token")
        auth.signOut()
        return token
    }
}
