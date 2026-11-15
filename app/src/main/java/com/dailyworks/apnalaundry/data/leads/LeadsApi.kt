package com.dailyworks.apnalaundry.data.leads

import com.dailyworks.apnalaundry.BuildConfig
import com.dailyworks.apnalaundry.data.remote.TokenManager
import com.dailyworks.apnalaundry.data.sync.NotLoggedInException
import com.dailyworks.apnalaundry.data.sync.SyncHttpException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable private data class AmountBody(val amountPaise: Int? = null)
@Serializable private data class ReasonBody(val reason: String)
@Serializable private data class VerifyBody(val orderId: String, val paymentId: String, val signature: String)
@Serializable private data class CodeBody(val serverAuthCode: String)
@Serializable private data class UrlBody(val url: String)
@Serializable private data class NameBody(val name: String)
@Serializable private data class DeviceBody(val token: String, val appVersion: Int? = null)

/**
 * Authenticated calls to /api/laundry/leads and /api/laundry/devices. Same Bearer + one-401-refresh
 * pattern as BillingApi. Error replies (4xx) are parsed too, so callers read `code`.
 */
class LeadsApi(private val client: HttpClient, private val tokens: TokenManager) {

    private val base = BuildConfig.API_BASE_URL

    suspend fun screen(): LeadsScreenWire {
        val res = authed { client.get("$base/leads") { bearerAuth(it) } }
        if (res.status.value !in 200..299) throw SyncHttpException(res.status.value)
        return res.body()
    }

    suspend fun lead(id: String): LeadReply = parse(authed { client.get("$base/leads/$id") { bearerAuth(it) } }) { LeadReply(code = it) }

    suspend fun seen(id: String) { runCatching { authed { client.post("$base/leads/$id/seen") { bearerAuth(it) } } } }

    suspend fun accept(id: String): LeadReply = parse(authed { client.post("$base/leads/$id/accept") { bearerAuth(it) } }) { LeadReply(code = it) }

    suspend fun decline(id: String, reason: String): LeadReply =
        parse(authed { client.post("$base/leads/$id/decline") { json(it); setBody(ReasonBody(reason)) } }) { LeadReply(code = it) }

    /** A Razorpay Order that pays this lead (and tops the wallet up with anything above the price). */
    suspend fun payOrder(id: String, amountPaise: Int?): OrderReply =
        parse(authed { client.post("$base/leads/$id/pay-order") { json(it); setBody(AmountBody(amountPaise)) } }) { OrderReply(code = it) }

    suspend fun topup(amountPaise: Int): OrderReply =
        parse(authed { client.post("$base/leads/wallet/topup") { json(it); setBody(AmountBody(amountPaise)) } }) { OrderReply(code = it) }

    suspend fun verify(orderId: String, paymentId: String, signature: String): VerifyReply =
        parse(authed { client.post("$base/leads/wallet/verify") { json(it); setBody(VerifyBody(orderId, paymentId, signature)) } }) { VerifyReply(code = it) }

    suspend fun wallet(): WalletReply = parse(authed { client.get("$base/leads/wallet") { bearerAuth(it) } }) { WalletReply() }

    suspend fun gbpConnect(serverAuthCode: String): GbpReply =
        parse(authed { client.post("$base/leads/gbp/connect") { json(it); setBody(CodeBody(serverAuthCode)) } }) { GbpReply(code = it) }

    suspend fun gbpMapsLink(url: String): GbpReply =
        parse(authed { client.post("$base/leads/gbp/maps-link") { json(it); setBody(UrlBody(url)) } }) { GbpReply(code = it) }

    suspend fun gbpPickLocation(name: String): GbpReply =
        parse(authed { client.post("$base/leads/gbp/location") { json(it); setBody(NameBody(name)) } }) { GbpReply(code = it) }

    suspend fun gbpDisconnect(): GbpReply = parse(authed { client.delete("$base/leads/gbp") { bearerAuth(it) } }) { GbpReply(code = it) }

    /** This phone's FCM token, so the server can push a new lead. True when the server took it. */
    suspend fun registerDevice(token: String, appVersion: Int): Boolean {
        val res = authed { client.post("$base/devices") { json(it); setBody(DeviceBody(token, appVersion)) } }
        return res.status.value in 200..299
    }

    suspend fun unregisterDevice(token: String) {
        authed { client.delete("$base/devices") { json(it); setBody(DeviceBody(token)) } }
    }

    private fun HttpRequestBuilder.json(token: String) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
    }

    /** The parsed body, or [fallback] with "HTTP_<status>" when the body isn't JSON of that shape. */
    private suspend inline fun <reified T> parse(res: HttpResponse, fallback: (String) -> T): T =
        runCatching { res.body<T>() }.getOrElse { fallback("HTTP_${res.status.value}") }

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
