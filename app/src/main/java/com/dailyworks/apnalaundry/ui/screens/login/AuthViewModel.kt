package com.dailyworks.apnalaundry.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.data.AuthRepository
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
) {
    enum class Step { PHONE, OTP }

    // Real Indian mobiles start 6-9; 10000000xx are the backend's app-review
    // numbers (deterministic OTP, no SMS).
    val phoneValid get() = phone.length == 10 &&
        (phone.first() in '6'..'9' || phone.startsWith("10000000"))
    val otpValid get() = otp.length == 6
}

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    private var challenge: AuthRepository.Challenge? = null
    private var countdownJob: Job? = null

    fun onPhone(v: String) { _ui.value = _ui.value.copy(phone = v.filter { it.isDigit() }.take(10), error = null) }
    fun onOtp(v: String) { _ui.value = _ui.value.copy(otp = v.filter { it.isDigit() }.take(6), error = null) }

    fun backToPhone() {
        countdownJob?.cancel()
        challenge = null
        _ui.value = _ui.value.copy(step = AuthUiState.Step.PHONE, otp = "", error = null, resendInSecs = 0, attemptsRemaining = null)
    }

    /** Sends (or resends) the OTP. Resending supersedes the old challenge server-side. */
    fun requestOtp() {
        val s = _ui.value
        if (!s.phoneValid || s.loading || s.resendInSecs > 0) return
        _ui.value = s.copy(loading = true, error = null)
        Analytics.otpRequested(
            phoneType = if (s.phone.startsWith("10000000")) "review" else "real",
            isResend = s.step == AuthUiState.Step.OTP,
        )
        viewModelScope.launch {
            auth.requestOtp(s.phone)
                .onSuccess { ch ->
                    challenge = ch
                    _ui.value = _ui.value.copy(
                        loading = false, step = AuthUiState.Step.OTP, otp = "",
                        attemptsRemaining = ch.attemptsRemaining,
                    )
                    startResendCountdown(ch.nextSendAtMs)
                }
                .onFailure {
                    Analytics.otpRequestFailed(it.message ?: "Couldn't send OTP")
                    _ui.value = _ui.value.copy(loading = false, error = it.message)
                }
        }
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
        viewModelScope.launch {
            auth.verifyOtp(ch, s.otp)
                .onSuccess { _ui.value = _ui.value.copy(loading = false, done = true) }
                .onFailure { e ->
                    val attempts = (e as? AuthRepository.AuthException)?.attemptsRemaining
                    Analytics.otpVerificationFailed(e.message ?: "Verification failed", attempts)
                    _ui.value = _ui.value.copy(
                        loading = false, error = e.message, otp = "",
                        attemptsRemaining = attempts ?: _ui.value.attemptsRemaining,
                    )
                }
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
