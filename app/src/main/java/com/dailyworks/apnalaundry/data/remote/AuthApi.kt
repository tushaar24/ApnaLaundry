package com.dailyworks.apnalaundry.data.remote

import com.dailyworks.apnalaundry.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Wire types for /api/laundry/auth/* (success and error responses share the
// same shape; `code` carries the server's stable error code).

@Serializable data class AuthUserDto(val id: String, val phone: String, val name: String? = null)

@Serializable
data class OtpChallengeResponse(
    val success: Boolean,
    val challengeId: String? = null,
    val challengeToken: String? = null,
    val expiresAt: String? = null,
    val nextSendAt: String? = null,
    val attemptsRemaining: Int? = null,
    val code: String? = null,
    val message: String? = null,
    val retryAt: String? = null,
)

@Serializable
data class CredentialsResponse(
    val success: Boolean,
    val user: AuthUserDto? = null,
    val accessToken: String? = null,
    val accessExpiresAt: String? = null,
    val refreshToken: String? = null,
    val sessionExpiresAt: String? = null,
    /** verify-otp only: account created just now, so the server holds no data for it yet. */
    val isNewUser: Boolean = false,
    val code: String? = null,
    val message: String? = null,
    val attemptsRemaining: Int? = null,
)

/** Parsed response + HTTP status, so callers can distinguish 401 from offline. */
data class ApiReply<T>(val status: Int, val body: T?)

/** Server ISO timestamp (Date.toISOString()) -> epoch ms; 0 if unparseable. */
fun parseIsoMs(iso: String?): Long {
    if (iso.isNullOrBlank()) return 0L
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(iso)!!.time
    }.getOrDefault(0L)
}

/**
 * OTP login against the backend (ported from HealthProduct's identity flow):
 * request-otp -> challenge id + secret challenge token; verify-otp (Bearer
 * challenge token) -> opaque access/refresh credentials.
 */
class AuthApi(private val client: HttpClient = defaultClient()) {

    private val base = BuildConfig.API_BASE_URL

    suspend fun requestOtp(phone: String): ApiReply<OtpChallengeResponse> {
        val res = client.post("$base/auth/request-otp") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("phone" to phone))
        }
        return ApiReply(res.status.value, runCatching<OtpChallengeResponse> { res.body() }.getOrNull())
    }

    suspend fun verifyOtp(
        challengeId: String, challengeToken: String, otp: String, deviceId: String,
    ): ApiReply<CredentialsResponse> {
        val res = client.post("$base/auth/verify-otp") {
            contentType(ContentType.Application.Json)
            bearerAuth(challengeToken)
            setBody(mapOf("challengeId" to challengeId, "otp" to otp, "deviceId" to deviceId))
        }
        return ApiReply(res.status.value, runCatching<CredentialsResponse> { res.body() }.getOrNull())
    }

    suspend fun refresh(refreshToken: String): ApiReply<CredentialsResponse> {
        val res = client.post("$base/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("refreshToken" to refreshToken))
        }
        return ApiReply(res.status.value, runCatching<CredentialsResponse> { res.body() }.getOrNull())
    }

    suspend fun logout(accessToken: String): Int =
        client.post("$base/auth/logout") { bearerAuth(accessToken) }.status.value

    suspend fun me(accessToken: String): Int =
        client.get("$base/auth/me") { bearerAuth(accessToken) }.status.value

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun defaultClient(): HttpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
            }
            expectSuccess = false // 4xx bodies are parsed, not thrown
        }
    }
}
