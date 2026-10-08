package com.dailyworks.mylaundry.support.data

import kotlinx.serialization.Serializable

// Wire models for /api/laundry/support/* (backend routes/laundry-support.js).
// Dates are ISO-8601 strings; parse with java.time.Instant.

@Serializable
data class SubSummary(
    val state: String, // none | trial | paid | cancelled | halted | expired
    val status: String? = null,
    val plan: String? = null,
    val paidCount: Int = 0,
    val currentEnd: String? = null,
    val startedAt: String? = null,
)

@Serializable
data class CallSummary(
    val count: Int = 0,
    val lastAt: String? = null,
    val lastOutcome: String? = null,
    val lastAgent: String? = null,
    val agents: List<String> = emptyList(),
)

@Serializable
data class OwnerSummary(
    val id: String,
    val phone: String,
    val name: String? = null,
    val shopName: String? = null,
    val setupDone: Boolean = false,
    val joinedAt: String,
    val lastLoginAt: String? = null,
    val lastActiveAt: String? = null,
    val orders: Int = 0,
    val customers: Int = 0,
    val subscription: SubSummary,
    val calls: CallSummary = CallSummary(),
    val tagIds: List<String> = emptyList(),
)

@Serializable
data class ShopsPage(
    val items: List<OwnerSummary>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val counts: Map<String, Int> = emptyMap(),
)

@Serializable
data class ShopInfo(val name: String, val phone: String, val closeTime: String, val expressPct: Int)

@Serializable
data class Usage(val services: Int = 0, val ordersByStatus: Map<String, Int> = emptyMap())

@Serializable
data class SubRow(
    val id: String,
    val plan: String,
    val status: String,
    val state: String,
    val amount: Int,
    val trialAmount: Int = 0,
    val paidCount: Int = 0,
    val currentEnd: String? = null,
    val chargeAt: String? = null,
    val endedAt: String? = null,
    val createdAt: String,
)

@Serializable
data class PaymentRow(
    val id: String,
    val amount: Int,
    val status: String,
    val method: String? = null,
    val errorDescription: String? = null,
    val createdAt: String,
    val capturedAt: String? = null,
)

@Serializable
data class TagDto(val id: String, val name: String, val color: String, val calls: Int? = null)

@Serializable
data class RecordingDto(
    val uploaded: Boolean = false,
    val mime: String? = null,
    val bytes: Int? = null,
    val uploadedAt: String? = null,
)

@Serializable
data class CallDto(
    val id: String,
    val userId: String,
    val phone: String,
    val agentName: String,
    val startedAt: String,
    val endedAt: String? = null,
    val durationSec: Int? = null,
    val outcome: String? = null,
    val note: String = "",
    val tags: List<TagDto> = emptyList(),
    val recording: RecordingDto = RecordingDto(),
    val shopName: String? = null,
    val ownerName: String? = null,
)

@Serializable
data class ShopDetail(
    val owner: OwnerSummary,
    val shop: ShopInfo? = null,
    val usage: Usage = Usage(),
    val subscriptions: List<SubRow> = emptyList(),
    val payments: List<PaymentRow> = emptyList(),
    val calls: List<CallDto> = emptyList(),
)

@Serializable
data class CallsPage(val items: List<CallDto>, val total: Int, val page: Int, val pageSize: Int)

@Serializable internal data class CallEnvelope(val call: CallDto)
@Serializable internal data class TagEnvelope(val tag: TagDto)
@Serializable internal data class TagsEnvelope(val tags: List<TagDto>)
@Serializable internal data class AgentsEnvelope(val agents: List<String>)
@Serializable data class UploadTarget(val uploadUrl: String, val path: String)
@Serializable internal data class RecordingUrl(val url: String)
@Serializable internal data class ErrorBody(val message: String? = null)
@Serializable internal data class OkBody(val success: Boolean = true)

/** Call outcomes the backend accepts, with the label the agent sees. */
enum class Outcome(val label: String) {
    CONNECTED("Connected"),
    NO_ANSWER("No answer"),
    BUSY("Busy"),
    SWITCHED_OFF("Switched off"),
    WRONG_NUMBER("Wrong number"),
    CALL_BACK("Call back later");

    companion object {
        fun of(v: String?) = entries.firstOrNull { it.name == v }
    }
}

/** Shop-list filter state; maps 1:1 to GET /shops query params. */
data class ShopFilters(
    val q: String = "",
    val sub: Set<String> = emptySet(),
    val setup: String? = null, // done | pending
    val orders: String? = null, // has | none
    val activeWithinDays: Int? = null,
    val inactiveForDays: Int? = null,
    val called: String? = null, // never | any
    val agent: String? = null,
    val tagIds: Set<String> = emptySet(),
    val sort: String = "joined_desc",
) {
    /** Number of filters set in the sheet (search, state chips and sort excluded). */
    val sheetCount: Int
        get() = listOfNotNull(setup, orders, activeWithinDays, inactiveForDays, called, agent).size +
            (if (tagIds.isEmpty()) 0 else 1)

    fun toQuery(page: Int, pageSize: Int): Map<String, String> = buildMap {
        if (q.isNotBlank()) put("q", q.trim())
        if (sub.isNotEmpty()) put("sub", sub.joinToString(","))
        setup?.let { put("setup", it) }
        orders?.let { put("orders", it) }
        activeWithinDays?.let { put("activeWithinDays", it.toString()) }
        inactiveForDays?.let { put("inactiveForDays", it.toString()) }
        called?.let { put("called", it) }
        agent?.let { put("agent", it) }
        if (tagIds.isNotEmpty()) put("tag", tagIds.joinToString(","))
        put("sort", sort)
        put("page", page.toString())
        put("pageSize", pageSize.toString())
    }
}
