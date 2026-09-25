package com.dailyworks.apnalaundry.data

import com.dailyworks.apnalaundry.data.remote.AuthApi

/** Demo auth flow: request OTP, verify, and persist the logged-in flag. */
class AuthRepository(
    private val api: AuthApi,
    private val prefs: Prefs,
) {
    suspend fun requestOtp(phone: String): Result<String> = runCatching {
        val res = api.requestOtp(phone)
        if (res.ok) res.message else throw IllegalStateException(res.message)
    }

    suspend fun verifyOtp(phone: String, otp: String): Result<Unit> = runCatching {
        val res = api.verifyOtp(phone, otp)
        if (!res.ok) throw IllegalStateException(res.message)
        prefs.setLoggedIn(true)
    }
}
