package com.dailyworks.apnalaundry.data.leads

import kotlinx.serialization.Serializable
import java.time.Instant

// Wire types for /api/laundry/leads (courses backend routes/laundry-leads.js). Unknown keys are
// ignored (AuthApi.json), so the server can grow them. Money is in paise, as everywhere on the API.

@Serializable
data class LeadWire(
    val id: String = "",
    // offered | paid | declined | expired | cancelled | refunded
    val status: String = "offered",
    val customerName: String = "",
    // ******3210 until paid; the full number on a paid lead.
    val customerPhone: String = "",
    val area: String = "",
    val service: String = "",
    val note: String = "",
    val pricePaise: Int = 0,
    val expiresAt: String? = null,
    val secondsLeft: Int = 0,
    val seenAt: String? = null,
    val paidAt: String? = null,
    val paidVia: String? = null,
    val declinedAt: String? = null,
    val refundedAt: String? = null,
    val createdAt: String? = null,
) {
    val expiresAtMs: Long get() = parseMs(expiresAt)
    val createdAtMs: Long get() = parseMs(createdAt)
    val paidAtMs: Long get() = parseMs(paidAt)
    /** Still open for the owner to take, by the phone's clock. */
    fun isOpen(now: Long = System.currentTimeMillis()): Boolean = status == "offered" && expiresAtMs > now
    val isPaid: Boolean get() = status == "paid"
    /** The 10 digits of a revealed number; empty while masked. */
    val phoneDigits: String get() = customerPhone.filter(Char::isDigit).takeIf { it.length == 10 && !customerPhone.startsWith("*") } ?: ""
}

internal fun parseMs(iso: String?): Long = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

@Serializable
data class WalletWire(val balancePaise: Int = 0)

@Serializable
data class LeadsConfigWire(
    val markupPaise: Int = 1000,
    val topupChoicesPaise: List<Int> = listOf(10000, 20000, 50000, 100000),
    val topupMinPaise: Int = 5000,
    val topupMaxPaise: Int = 1000000,
    val razorpayConfigured: Boolean = false,
    val razorpayKeyId: String? = null,
    val pushRegistered: Boolean = false,
    val pushConfigured: Boolean = false,
)

@Serializable
data class GbpLocationWire(val name: String = "", val title: String = "", val address: String = "", val mapsUri: String? = null)

/** The owner's Google Business Profile connection: none | connected | maps_link | revoked | error. */
@Serializable
data class GbpWire(
    val configured: Boolean = false,
    val googleClientId: String? = null,
    val status: String = "none",
    val email: String? = null,
    val locationName: String? = null,
    val locationTitle: String? = null,
    val locations: List<GbpLocationWire> = emptyList(),
    val mapsUrl: String? = null,
    val apiError: String? = null,
) {
    val isConnected: Boolean get() = status == "connected"
    val hasMapsLink: Boolean get() = !mapsUrl.isNullOrBlank()
    val isSet: Boolean get() = isConnected || hasMapsLink
}

/** GET /leads — everything the Get customers screen draws. */
@Serializable
data class LeadsScreenWire(
    val success: Boolean = false,
    val wallet: WalletWire = WalletWire(),
    val config: LeadsConfigWire = LeadsConfigWire(),
    val gbp: GbpWire = GbpWire(),
    val leads: List<LeadWire> = emptyList(),
)

/** Accept / decline / one lead: the lead and the wallet after it; `code` on a refusal. */
@Serializable
data class LeadReply(
    val success: Boolean = false,
    val code: String? = null,
    val message: String? = null,
    val lead: LeadWire? = null,
    val wallet: WalletWire? = null,
    val pricePaise: Int? = null,
    val shortPaise: Int? = null,
)

@Serializable
data class OrderWire(val orderId: String = "", val keyId: String = "", val amountPaise: Int = 0, val pricePaise: Int? = null, val leadId: String? = null)

@Serializable
data class OrderReply(val success: Boolean = false, val code: String? = null, val message: String? = null, val order: OrderWire? = null, val contact: String? = null)

@Serializable
data class VerifyReply(
    val success: Boolean = false,
    val code: String? = null,
    val message: String? = null,
    val alreadyDone: Boolean = false,
    val creditedPaise: Int = 0,
    // The lead expired or was taken back while paying: the money stayed in the wallet.
    val leadGone: Boolean = false,
    val wallet: WalletWire? = null,
    val lead: LeadWire? = null,
)

@Serializable
data class WalletTxnWire(
    val id: String = "",
    // topup | lead | refund | adjust
    val kind: String = "",
    val amountPaise: Int = 0,
    val balanceAfter: Int = 0,
    val leadId: String? = null,
    val note: String = "",
    val at: String? = null,
) {
    val atMs: Long get() = parseMs(at)
}

@Serializable
data class WalletReply(val success: Boolean = false, val wallet: WalletWire = WalletWire(), val txns: List<WalletTxnWire> = emptyList())

@Serializable
data class GbpReply(val success: Boolean = false, val code: String? = null, val message: String? = null, val gbp: GbpWire? = null)

@Serializable
data class OkReply(val success: Boolean = false, val code: String? = null, val message: String? = null)
