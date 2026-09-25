package com.dailyworks.apnalaundry.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class OtpRequest(val phone: String)
@Serializable data class OtpResponse(val ok: Boolean, val message: String)
@Serializable data class VerifyRequest(val phone: String, val otp: String)
@Serializable data class VerifyResponse(val ok: Boolean, val token: String? = null, val message: String)

/**
 * Demo auth over Ktor. A [MockEngine] plays the role of the backend so the whole
 * flow is real Ktor request/response plumbing while staying fully offline.
 * Fixed demo credentials: phone 9876543210, OTP 123456.
 */
class AuthApi(private val client: HttpClient = defaultClient()) {

    suspend fun requestOtp(phone: String): OtpResponse =
        client.post("$BASE/auth/request-otp") {
            contentType(ContentType.Application.Json)
            setBody(OtpRequest(phone))
        }.body()

    suspend fun verifyOtp(phone: String, otp: String): VerifyResponse =
        client.post("$BASE/auth/verify-otp") {
            contentType(ContentType.Application.Json)
            setBody(VerifyRequest(phone, otp))
        }.body()

    companion object {
        const val BASE = "https://demo.apnalaundry.local"
        const val DEMO_PHONE = "9876543210"
        const val DEMO_OTP = "123456"

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun defaultClient(): HttpClient {
            val engine = MockEngine { request ->
                delay(500) // simulate network latency
                val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val bodyText = (request.body as? TextContent)?.text.orEmpty()
                when (request.url.encodedPath) {
                    "/auth/request-otp" -> {
                        val phone = runCatching { json.decodeFromString(OtpRequest.serializer(), bodyText).phone }.getOrDefault("")
                        val res = if (phone == DEMO_PHONE) OtpResponse(true, "OTP sent")
                        else OtpResponse(false, "This demo only accepts 9876543210")
                        respond(json.encodeToString(OtpResponse.serializer(), res), HttpStatusCode.OK, headers)
                    }
                    "/auth/verify-otp" -> {
                        val req = runCatching { json.decodeFromString(VerifyRequest.serializer(), bodyText) }
                            .getOrDefault(VerifyRequest("", ""))
                        val res = if (req.phone == DEMO_PHONE && req.otp == DEMO_OTP)
                            VerifyResponse(true, "demo-token-xyz", "Verified")
                        else VerifyResponse(false, null, "Wrong OTP. Use 123456")
                        respond(json.encodeToString(VerifyResponse.serializer(), res), HttpStatusCode.OK, headers)
                    }
                    else -> respond("Not found", HttpStatusCode.NotFound, headers)
                }
            }
            return HttpClient(engine) {
                install(ContentNegotiation) { json(json) }
            }
        }
    }
}
