package com.dailyworks.apnalaundry.data.billing

import com.dailyworks.apnalaundry.BuildConfig
import com.dailyworks.apnalaundry.data.remote.TokenManager
import com.dailyworks.apnalaundry.data.sync.NotLoggedInException
import com.dailyworks.apnalaundry.data.sync.SyncHttpException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Authenticated calls to /api/laundry/billing. Same Bearer + one-401-refresh
 * pattern as SyncApi. subscribe() returns the Razorpay subscription id + public
 * key; the UI hands both to Razorpay Standard Checkout for the UPI AutoPay
 * mandate approval.
 */
class BillingApi(private val client: HttpClient, private val tokens: TokenManager) {

    private val base = BuildConfig.API_BASE_URL

    suspend fun status(): BillingStatus {
        val res = authed { token -> client.get("$base/billing/status") { bearerAuth(token) } }
        if (res.status.value !in 200..299) throw SyncHttpException(res.status.value)
        return res.body()
    }

    suspend fun subscribe(plan: String): SubscribeResponse {
        val res = authed { token ->
            client.post("$base/billing/subscribe") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(SubscribeRequest(plan))
            }
        }
        return res.body()
    }

    suspend fun cancel(): CancelResponse {
        val res = authed { token -> client.post("$base/billing/cancel") { bearerAuth(token) } }
        return res.body()
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
