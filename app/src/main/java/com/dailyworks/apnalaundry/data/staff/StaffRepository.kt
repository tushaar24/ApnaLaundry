package com.dailyworks.apnalaundry.data.staff

import com.dailyworks.apnalaundry.BuildConfig
import com.dailyworks.apnalaundry.data.remote.TokenManager
import com.dailyworks.apnalaundry.data.MAIN_SHOP
import com.dailyworks.apnalaundry.data.shops.ShopDirectory
import com.dailyworks.apnalaundry.data.sync.NotLoggedInException
import com.dailyworks.apnalaundry.domain.StaffActivity
import com.dailyworks.apnalaundry.domain.StaffActivityPage
import com.dailyworks.apnalaundry.domain.StaffMember
import com.dailyworks.apnalaundry.domain.StaffPermission
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.time.Instant

// Wire shapes of /api/laundry/staff (courses backend routes/laundry-staff.js).
@Serializable private data class Today(val orders: Int = 0, val collected: Int = 0)
@Serializable private data class StaffWire(
    val id: String, val name: String = "", val phone: String = "", val occupation: String = "",
    val permissions: Map<String, Boolean> = emptyMap(), val lastSeenAt: String? = null, val today: Today? = null,
)
@Serializable private data class ListWire(val success: Boolean = false, val planActive: Boolean = false, val staff: List<StaffWire> = emptyList())
@Serializable private data class OneWire(val success: Boolean = false, val staff: StaffWire? = null, val code: String? = null, val message: String? = null)
@Serializable private data class SaveBody(val name: String, val phone: String? = null, val occupation: String, val permissions: Map<String, Boolean>)
@Serializable private data class Tiles(val ordersTaken: Int = 0, val delivered: Int = 0, val collected: Int = 0)
@Serializable private data class ActWire(
    val at: String = "", val action: String = "", val orderId: Int? = null, val custId: String? = null,
    val amount: Int? = null, val method: String? = null, val detail: String = "",
)
@Serializable private data class ActivityWire(val success: Boolean = false, val tiles: Tiles = Tiles(), val rows: List<ActWire> = emptyList())

/** A refused save, with the server's code (PLAN_REQUIRED, PHONE_TAKEN, OWN_NUMBER, PHONE_INVALID, STAFF_LIMIT…). */
class StaffException(val code: String, message: String) : Exception(message)

private fun parseTime(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

private fun StaffWire.toDomain() = StaffMember(
    id = id, name = name, phone = phone, occupation = occupation,
    permissions = permissions.filterValues { it }.keys.mapNotNull { StaffPermission.of(it) }.toSet(),
    lastSeenAt = parseTime(lastSeenAt), todayOrders = today?.orders ?: 0, todayCollected = today?.collected ?: 0,
)

/**
 * The owner's staff for the shop on screen (1.0.20, docs/specs/staff.md). Not synced into Room:
 * the list and each member's 7-day log come from the server when a Staff screen opens.
 */
class StaffRepository(
    private val client: HttpClient,
    private val tokens: TokenManager,
    private val shops: ShopDirectory,
    private val prefs: com.dailyworks.apnalaundry.data.Prefs,
) {
    private val base = BuildConfig.API_BASE_URL

    private val _staff = MutableStateFlow<List<StaffMember>?>(null) // null = not loaded yet
    val staff: StateFlow<List<StaffMember>?> = _staff
    private val _planActive = MutableStateFlow(true)
    val planActive: StateFlow<Boolean> = _planActive

    /** Reloads the list (the Staff screens and Settings call this). */
    suspend fun refresh() {
        val res = authed { client.get("$base/staff") { auth(it) } }
        if (res.status.value !in 200..299) throw StaffException("HTTP_${res.status.value}", "Could not load staff")
        val body = res.body<ListWire>()
        _planActive.value = body.planActive
        _staff.value = body.staff.map { it.toDomain() }
        // A shop with staff numbers its orders from reserved blocks (data/sync/IdPool).
        prefs.setShopHasStaff(shops.activeId.value, body.staff.isNotEmpty())
    }

    /** Adds a staff member; their app can log in right away. */
    suspend fun add(name: String, phone: String, occupation: String, permissions: Set<StaffPermission>): StaffMember =
        save(null, SaveBody(name, phone, occupation, wire(permissions)))

    suspend fun update(id: String, name: String, occupation: String, permissions: Set<StaffPermission>): StaffMember =
        save(id, SaveBody(name, null, occupation, wire(permissions)))

    /** Removes a staff member: their phone loses this shop at its next sync. */
    suspend fun remove(id: String) {
        val res = authed { client.delete("$base/staff/$id") { auth(it) } }
        if (res.status.value !in 200..299 && res.status.value != 404) throw StaffException("HTTP_${res.status.value}", "Could not remove")
        _staff.value = _staff.value?.filterNot { it.id == id }
    }

    suspend fun activity(id: String): StaffActivityPage {
        val res = authed { client.get("$base/staff/$id/activity") { auth(it) } }
        if (res.status.value !in 200..299) throw StaffException("HTTP_${res.status.value}", "Could not load activity")
        val body = res.body<ActivityWire>()
        return StaffActivityPage(
            body.tiles.ordersTaken, body.tiles.delivered, body.tiles.collected,
            body.rows.map { StaffActivity(parseTime(it.at) ?: 0L, it.action, it.orderId, it.custId, it.amount, it.method, it.detail) },
        )
    }

    /** The shop changed (multi-shop): forget the other shop's list. */
    fun clear() { _staff.value = null }

    private fun wire(p: Set<StaffPermission>) = StaffPermission.entries.associate { it.key to (it in p) }

    private suspend fun save(id: String?, body: SaveBody): StaffMember {
        val res = authed {
            val url = if (id == null) "$base/staff" else "$base/staff/$id"
            val block: HttpRequestBuilder.() -> Unit = { auth(it); contentType(ContentType.Application.Json); setBody(body) }
            if (id == null) client.post(url, block) else client.patch(url, block)
        }
        val one = runCatching { res.body<OneWire>() }.getOrDefault(OneWire())
        if (res.status.value !in 200..299 || one.staff == null) {
            throw StaffException(one.code ?: "HTTP_${res.status.value}", one.message ?: "Could not save")
        }
        val m = one.staff.toDomain()
        _staff.value = (_staff.value.orEmpty().filterNot { it.id == m.id } + m)
        return m
    }

    private fun HttpRequestBuilder.auth(token: String) {
        bearerAuth(token)
        val shop = shops.activeId.value
        if (shop != MAIN_SHOP) header("X-Laundry-Shop", shop)
    }

    private suspend fun authed(block: suspend (String) -> HttpResponse): HttpResponse {
        val token = tokens.validAccessToken() ?: throw NotLoggedInException()
        val first = block(token)
        if (first.status.value != 401) return first
        val fresh = tokens.forceRefresh() ?: throw NotLoggedInException()
        return block(fresh)
    }
}
