package com.dailyworks.apnalaundry.ui.screens.login

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.data.AuthRepository
import com.google.firebase.auth.PhoneAuthCredential
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val phone: String = "",
    val otp: String = "",
    val step: Step = Step.PHONE,
    val loading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    val resendInSecs: Int = 0,
    val attemptsRemaining: Int? = null,
    /** The current OTP came from a resend (changes the waiting line's copy). */
    val resent: Boolean = false,
) {
    enum class Step { PHONE, OTP }

    // Real Indian mobiles start 6-9; 10000000xx are the backend's app-review
    // numbers (deterministic OTP, no SMS).
    val phoneValid get() = phone.length == 10 &&
        (phone.first() in '6'..'9' || phone.startsWith("10000000"))
    val otpValid get() = otp.length == 6
}

private const val WRONG_OTP = "Wrong OTP. Check your SMS again."

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    private var challenge: AuthRepository.Challenge? = null
    private var countdownJob: Job? = null
    private var autoVerifyJob: Job? = null
    private var verifyJob: Job? = null

    fun onPhone(v: String) { _ui.value = _ui.value.copy(phone = v.filter { it.isDigit() }.take(10), error = null) }
    fun onOtp(v: String) { _ui.value = _ui.value.copy(otp = v.filter { it.isDigit() }.take(6), error = null) }

    fun backToPhone() {
        countdownJob?.cancel()
        challenge = null
        _ui.value = _ui.value.copy(step = AuthUiState.Step.PHONE, otp = "", error = null, resendInSecs = 0, attemptsRemaining = null, resent = false)
    }

    /**
     * Sends (or resends) the OTP. Real numbers go through Firebase phone auth,
     * which needs the [activity] (its reCAPTCHA fallback) and may read the SMS
     * itself — then the user is logged in without typing anything.
     */
    fun requestOtp(activity: Activity) {
        val s = _ui.value
        if (!s.phoneValid || s.loading || s.resendInSecs > 0) return
        _ui.value = s.copy(loading = true, error = null)
        Analytics.otpRequested(
            phoneType = if (s.phone.startsWith("10000000")) "review" else "real",
            isResend = s.step == AuthUiState.Step.OTP,
        )
        val previous = challenge.takeIf { s.step == AuthUiState.Step.OTP }
        viewModelScope.launch {
            auth.requestOtp(activity, s.phone, previous, onAutoVerified = ::onAutoVerified)
                .onSuccess { ch ->
                    challenge = ch
                    _ui.value = _ui.value.copy(
                        // An instant (no-SMS) verification may already be logging in.
                        loading = autoVerifyJob?.isActive == true, step = AuthUiState.Step.OTP,
                        otp = if (autoVerifyJob?.isActive == true) _ui.value.otp else "",
                        attemptsRemaining = (ch as? AuthRepository.Challenge.Backend)?.attemptsRemaining,
                        resent = s.step == AuthUiState.Step.OTP,
                    )
                    startResendCountdown(ch.nextSendAtMs)
                }
                .onFailure {
                    Analytics.otpRequestFailed(it.message ?: "Couldn't send OTP")
                    _ui.value = _ui.value.copy(loading = false, error = it.message)
                }
        }
    }

    /** Firebase read the OTP SMS on the device: log straight in. */
    private fun onAutoVerified(credential: PhoneAuthCredential) {
        if (_ui.value.done || autoVerifyJob?.isActive == true || verifyJob?.isActive == true) return
        credential.smsCode?.let { _ui.value = _ui.value.copy(otp = it, error = null) }
        _ui.value = _ui.value.copy(loading = true)
        Analytics.otpSubmitted()
        autoVerifyJob = viewModelScope.launch { onVerifyResult(auth.verifyCredential(credential), typed = null) }
    }

    fun verify() {
        val s = _ui.value
        val ch = challenge
        if (!s.otpValid || s.loading) return
        if (ch == null) {
            _ui.value = s.copy(error = "Request a new OTP first")
            return
        }
        _ui.value = s.copy(loading = true, error = null)
        Analytics.otpSubmitted()
        verifyJob = viewModelScope.launch { onVerifyResult(auth.verifyOtp(ch, s.otp), typed = s.otp) }
    }

    private fun onVerifyResult(result: Result<Unit>, typed: String?) {
        result
            .onSuccess { _ui.value = _ui.value.copy(loading = false, done = true) }
            .onFailure { e ->
                val ae = e as? AuthRepository.AuthException
                val attempts = ae?.attemptsRemaining
                Analytics.otpVerificationFailed(e.message ?: "Verification failed", attempts)
                // A rejected code (still retryable) keeps the digits, shown red,
                // until edited; anything else (expired, exhausted, offline) shows
                // the error text.
                val wrongCode = ae?.wrongCode == true
                _ui.value = _ui.value.copy(
                    loading = false,
                    error = if (wrongCode) WRONG_OTP else e.message,
                    otp = if (wrongCode) typed ?: _ui.value.otp else "",
                    attemptsRemaining = attempts ?: _ui.value.attemptsRemaining,
                )
            }
    }

    private fun startResendCountdown(nextSendAtMs: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val left = ((nextSendAtMs - System.currentTimeMillis()) / 1000L).toInt()
                _ui.value = _ui.value.copy(resendInSecs = maxOf(0, left))
                if (left <= 0) break
                delay(1_000)
            }
        }
    }
}
