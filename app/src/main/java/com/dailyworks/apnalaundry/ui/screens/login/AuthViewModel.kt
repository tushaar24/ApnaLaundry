package com.dailyworks.apnalaundry.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyworks.apnalaundry.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val phone: String = "9876543210",   // demo number pre-filled
    val otp: String = "",
    val step: Step = Step.PHONE,
    val loading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
) {
    enum class Step { PHONE, OTP }
    val phoneValid get() = phone.length == 10
    val otpValid get() = otp.length == 6
}

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    fun onPhone(v: String) { _ui.value = _ui.value.copy(phone = v.filter { it.isDigit() }.take(10), error = null) }
    fun onOtp(v: String) { _ui.value = _ui.value.copy(otp = v.filter { it.isDigit() }.take(6), error = null) }
    fun backToPhone() { _ui.value = _ui.value.copy(step = AuthUiState.Step.PHONE, otp = "", error = null) }

    fun requestOtp() {
        val s = _ui.value
        if (!s.phoneValid || s.loading) return
        _ui.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            auth.requestOtp(s.phone)
                .onSuccess {
                    // simulate SMS auto-read of the demo OTP after a short beat
                    _ui.value = _ui.value.copy(loading = false, step = AuthUiState.Step.OTP, otp = "")
                }
                .onFailure { _ui.value = _ui.value.copy(loading = false, error = it.message) }
        }
    }

    fun autoFillDemoOtp() {
        if (_ui.value.step == AuthUiState.Step.OTP && _ui.value.otp.isEmpty()) {
            _ui.value = _ui.value.copy(otp = com.dailyworks.apnalaundry.data.remote.AuthApi.DEMO_OTP)
        }
    }

    fun verify() {
        val s = _ui.value
        if (!s.otpValid || s.loading) return
        _ui.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            auth.verifyOtp(s.phone, s.otp)
                .onSuccess { _ui.value = _ui.value.copy(loading = false, done = true) }
                .onFailure { _ui.value = _ui.value.copy(loading = false, error = it.message) }
        }
    }
}
