package com.dailyworks.apnalaundry.data.sync

import com.dailyworks.apnalaundry.BuildConfig
import com.dailyworks.apnalaundry.data.remote.TokenManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

class NotLoggedInException : Exception("Not logged in")
class SyncHttpException(val status: Int) : Exception("Sync request failed with HTTP $status")

/**
 * Authenticated calls to /api/laundry/sync. Attaches a valid access token
 * (via [TokenManager]); on a 401 it force-refreshes once and retries, and
 * gives up with [NotLoggedInException] if the session is really dead.
 */
class SyncApi(private val client: HttpClient, private val tokens: TokenManager) {

    private val base = BuildConfig.API_BASE_URL

    suspend fun push(changes: SyncChanges): PushResponse {
        val res = authed { token ->
            client.post("$base/sync/push") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(PushRequest(changes))
            }
        }
        if (res.status.value !in 200..299) throw SyncHttpException(res.status.value)
        return res.body()
    }

    suspend fun pull(since: Long): PullResponse {
        val res = authed { token ->
            client.get("$base/sync/pull") {
                parameter("since", since)
                bearerAuth(token)
            }
        }
        if (res.status.value !in 200..299) throw SyncHttpException(res.status.value)
        return res.body()
    }

    /** Uploads a (pre-resized) JPEG shop logo; returns its id for shop.logoId. */
    suspend fun uploadLogo(jpegBase64: String): String {
        val res = authed { token ->
            client.post("$base/shop/logo") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(LogoUpload("image/jpeg", jpegBase64))
            }
        }
        val body: LogoUploadResponse = runCatching { res.body<LogoUploadResponse>() }.getOrDefault(LogoUploadResponse(false))
        if (res.status.value !in 200..299 || !body.success || body.logoId.isNullOrBlank()) {
            throw SyncHttpException(res.status.value)
        }
        return body.logoId
    }

    /** Token of the shop's sample-bill page (mylaundry.work/b/<token>). */
    suspend fun sampleBillLink(): String {
        val res = authed { token ->
            client.post("$base/bills/sample/link") { bearerAuth(token) }
        }
        val body: BillLinkResponse = runCatching { res.body<BillLinkResponse>() }.getOrDefault(BillLinkResponse(false))
        if (res.status.value !in 200..299 || !body.success || body.token.isNullOrBlank()) {
            throw SyncHttpException(res.status.value)
        }
        return body.token
    }

    /** Token of an order's public bill page (mylaundry.work/b/<token>); the order must be synced. */
    suspend fun billLink(orderId: Int): String {
        val res = authed { token ->
            client.post("$base/bills/$orderId/link") { bearerAuth(token) }
        }
        val body: BillLinkResponse = runCatching { res.body<BillLinkResponse>() }.getOrDefault(BillLinkResponse(false))
        if (res.status.value !in 200..299 || !body.success || body.token.isNullOrBlank()) {
            throw SyncHttpException(res.status.value)
        }
        return body.token
    }

    /** A shop logo's bytes (public, immutable per id). */
    suspend fun fetchLogo(logoId: String): ByteArray? {
        val res = client.get("$base/public/logos/$logoId")
        return if (res.status.value in 200..299) res.body<ByteArray>() else null
    }

    private suspend fun authed(block: suspend (String) -> HttpResponse): HttpResponse {
        val token = tokens.validAccessToken() ?: throw NotLoggedInException()
        val first = block(token)
        if (first.status.value != 401) return first
        val fresh = tokens.forceRefresh() ?: throw NotLoggedInException()
        val second = block(fresh)
        if (second.status.value == 401) throw NotLoggedInException()
        return second
    }
}
