package com.dailyworks.apnalaundry.data.sync

import com.dailyworks.apnalaundry.BuildConfig
import com.dailyworks.apnalaundry.data.MAIN_SHOP
import com.dailyworks.apnalaundry.data.remote.TokenManager
import com.dailyworks.apnalaundry.data.shops.ShopRef
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

class NotLoggedInException : Exception("Not logged in")
class SyncHttpException(val status: Int) : Exception("Sync request failed with HTTP $status")
/** Adding a shop needs a plan — the server refused (403 PLAN_REQUIRED). */
class PlanRequiredException : Exception("Adding a shop needs a plan")
/** Staff (1.0.20): the owner removed this phone's staff login for [shop] (or deleted the shop). */
class StaffRemovedException(val shop: String) : Exception("Removed from this shop")
/** Staff (1.0.20): the owner's plan has ended — staff can't work in [shop] until it's renewed. */
class OwnerPlanEndedException(val shop: String) : Exception("The owner's plan has ended")

/**
 * Authenticated calls to /api/laundry/sync. Attaches a valid access token
 * (via [TokenManager]); on a 401 it force-refreshes once and retries, and
 * gives up with [NotLoggedInException] if the session is really dead.
 *
 * Multi-shop: [shop] names the shop a call is for (X-Laundry-Shop). The main
 * shop sends no header, so its calls are byte-for-byte what they were before
 * — and still work against a server that predates multi-shop.
 */
class SyncApi(
    private val client: HttpClient,
    private val tokens: TokenManager,
    private val shops: com.dailyworks.apnalaundry.data.shops.ShopDirectory,
) {

    private val base = BuildConfig.API_BASE_URL

    suspend fun push(changes: SyncChanges, shop: String = MAIN_SHOP): PushResponse {
        val res = authed(shop) { token ->
            client.post("$base/sync/push") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                shop(shop)
                setBody(PushRequest(changes))
            }
        }
        if (res.status.value !in 200..299) throw SyncHttpException(res.status.value)
        return res.body()
    }

    /** [afterSeq] (sync v2): rows the server numbered after it; 0 = by the [since] time checkpoint. */
    suspend fun pull(since: Long, shop: String = MAIN_SHOP, afterSeq: Long = 0): PullResponse {
        val res = authed(shop) { token ->
            client.get("$base/sync/pull") {
                parameter("since", since)
                if (afterSeq > 0) parameter("afterSeq", afterSeq)
                bearerAuth(token)
                shop(shop)
            }
        }
        if (res.status.value !in 200..299) throw SyncHttpException(res.status.value)
        return res.body()
    }

    /** Uploads a (pre-resized) JPEG shop logo; returns its id for shop.logoId. */
    suspend fun uploadLogo(jpegBase64: String, shop: String = MAIN_SHOP): String {
        val res = authed { token ->
            client.post("$base/shop/logo") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                shop(shop)
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
    suspend fun sampleBillLink(shop: String = MAIN_SHOP): String {
        val res = authed { token ->
            client.post("$base/bills/sample/link") { bearerAuth(token); shop(shop) }
        }
        val body: BillLinkResponse = runCatching { res.body<BillLinkResponse>() }.getOrDefault(BillLinkResponse(false))
        if (res.status.value !in 200..299 || !body.success || body.token.isNullOrBlank()) {
            throw SyncHttpException(res.status.value)
        }
        return body.token
    }

    /** Token of an order's public bill page (mylaundry.work/b/<token>); the order must be synced. */
    suspend fun billLink(orderId: Int, shop: String = MAIN_SHOP): String {
        val res = authed(shop) { token ->
            client.post("$base/bills/$orderId/link") { bearerAuth(token); shop(shop) }
        }
        val body: BillLinkResponse = runCatching { res.body<BillLinkResponse>() }.getOrDefault(BillLinkResponse(false))
        if (res.status.value !in 200..299 || !body.success || body.token.isNullOrBlank()) {
            throw SyncHttpException(res.status.value)
        }
        return body.token
    }

    /** The owner's shops, the main one first (GET /shops). */
    suspend fun listShops(): List<ShopRef> {
        val res = authed { token -> client.get("$base/shops") { bearerAuth(token) } }
        if (res.status.value !in 200..299) throw SyncHttpException(res.status.value)
        return res.body<ShopsResponse>().shops
    }

    /** Adds a shop (needs a plan — else [PlanRequiredException]); returns it with its new id. */
    suspend fun addShop(name: String): ShopRef {
        val res = authed { token ->
            client.post("$base/shops") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(AddShopRequest(name))
            }
        }
        val body = runCatching { res.body<AddShopResponse>() }.getOrDefault(AddShopResponse(false))
        if (res.status.value == 403 && body.code == "PLAN_REQUIRED") throw PlanRequiredException()
        if (res.status.value !in 200..299 || body.shop == null) throw SyncHttpException(res.status.value)
        return body.shop
    }

    /** Deletes an extra shop on the server (soft: hidden, its sync refused). Never the main shop. */
    suspend fun deleteShop(id: String) {
        val res = authed { token -> client.delete("$base/shops/$id") { bearerAuth(token) } }
        // 404: already gone (deleted on another phone) — same end result.
        if (res.status.value !in 200..299 && res.status.value != 404) throw SyncHttpException(res.status.value)
    }

    /** The owner's extra shops name themselves; the main shop and staff entries send nothing (the server pins a staff login to its shop). */
    private fun HttpRequestBuilder.shop(id: String) {
        if (id != MAIN_SHOP && shops.ref(id)?.staff == null) header("X-Laundry-Shop", id)
    }

    /**
     * A block of order (or customer) numbers for this phone (POST /ids/reserve), so phones in one
     * shop never hand out the same number. [floor] = this phone's own next number.
     */
    suspend fun reserveIds(kind: String, count: Int, floor: Int, shop: String = MAIN_SHOP): IntRange {
        val res = authed(shop) { token ->
            client.post("$base/ids/reserve") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                shop(shop)
                setBody(ReserveRequest(kind, count, floor))
            }
        }
        if (res.status.value !in 200..299) throw SyncHttpException(res.status.value)
        val body = res.body<ReserveResponse>()
        return body.from until body.to
    }

    /** Where this phone works as staff (GET /me/contexts), with each one's token. */
    suspend fun contexts(shop: String = MAIN_SHOP): ContextsResponse {
        val res = authed(shop) { token -> client.get("$base/me/contexts") { bearerAuth(token) } }
        if (res.status.value !in 200..299) throw SyncHttpException(res.status.value)
        return res.body()
    }

    /** A shop logo's bytes (public, immutable per id). */
    suspend fun fetchLogo(logoId: String): ByteArray? {
        val res = client.get("$base/public/logos/$logoId")
        return if (res.status.value in 200..299) res.body<ByteArray>() else null
    }

    /**
     * [shop]'s login: a staff entry's own token, else the owner's. A staff entry (not the main
     * slot) answering 401 was removed — [StaffRemovedException], never a logout; 402 = the owner's
     * plan has ended ([OwnerPlanEndedException]).
     */
    private suspend fun authed(shop: String, block: suspend (String) -> HttpResponse): HttpResponse {
        val staff = shops.ref(shop)?.staff
        if (staff != null && shop != MAIN_SHOP) {
            val res = block(staff.token)
            if (res.status.value == 401) throw StaffRemovedException(shop)
            if (res.status.value == 402) throw OwnerPlanEndedException(shop)
            return res
        }
        val res = authed(block)
        if (res.status.value == 402 && staff != null) throw OwnerPlanEndedException(shop)
        return res
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
