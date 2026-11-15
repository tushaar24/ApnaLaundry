package com.dailyworks.apnalaundry.data.leads

import com.dailyworks.apnalaundry.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Get customers (1.0.22, docs/specs/leads.md): the one [state] the Leads screens and Home's
 * "new customer waiting" card read. Nothing is kept in Room — a lead lives an hour, and the
 * server is the source of truth for money (the wallet) and for the number (shown only once paid).
 * Refreshed when the screen opens, after every action, at app start when the feature is on, and
 * when a lead push arrives (push/Push.kt).
 */
class LeadsRepository(private val api: LeadsApi, private val prefs: Prefs) {

    private val _state = MutableStateFlow<LeadsScreenWire?>(null) // null = never loaded
    val state: StateFlow<LeadsScreenWire?> = _state

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed

    /** Reload everything; a failure keeps what's on screen and sets [failed]. */
    suspend fun refresh(): Boolean {
        _loading.value = true
        val r = runCatching { api.screen() }
        _loading.value = false
        r.onSuccess { _state.value = it; _failed.value = false }.onFailure { _failed.value = true }
        return r.isSuccess
    }

    fun current(): LeadsScreenWire? = _state.value

    fun lead(id: String): LeadWire? = _state.value?.leads?.firstOrNull { it.id == id }

    /** The first lead still waiting for the owner, if any. */
    fun waiting(now: Long = System.currentTimeMillis()): LeadWire? = _state.value?.leads?.firstOrNull { it.isOpen(now) }

    suspend fun seen(id: String) = api.seen(id)

    /** Pay from the wallet. The reply's `code` says why not (INSUFFICIENT_BALANCE, LEAD_EXPIRED…). */
    suspend fun accept(id: String): LeadReply = api.accept(id).also { apply(it.lead, it.wallet) }

    suspend fun decline(id: String, reason: String): LeadReply = api.decline(id, reason).also { apply(it.lead, null) }

    suspend fun payOrder(id: String, amountPaise: Int?): OrderReply = api.payOrder(id, amountPaise)

    suspend fun topup(amountPaise: Int): OrderReply = api.topup(amountPaise)

    /** Razorpay came back paid: tell the server; it credits once and pays the lead if still open. */
    suspend fun verify(orderId: String, paymentId: String, signature: String): VerifyReply =
        api.verify(orderId, paymentId, signature).also { apply(it.lead, it.wallet) }

    suspend fun wallet(): WalletReply = api.wallet().also { r -> if (r.success) _state.value = _state.value?.copy(wallet = r.wallet) }

    suspend fun connectGbp(serverAuthCode: String): GbpReply = api.gbpConnect(serverAuthCode).also { applyGbp(it.gbp) }
    suspend fun setMapsLink(url: String): GbpReply = api.gbpMapsLink(url).also { applyGbp(it.gbp) }
    suspend fun pickLocation(name: String): GbpReply = api.gbpPickLocation(name).also { applyGbp(it.gbp) }
    suspend fun disconnectGbp(): GbpReply = api.gbpDisconnect().also { applyGbp(it.gbp) }

    /** Logout / account switch: forget the other account's leads. */
    fun clear() { _state.value = null; _failed.value = false }

    private fun apply(lead: LeadWire?, wallet: WalletWire?) {
        val s = _state.value ?: return
        var leads = s.leads
        if (lead != null) leads = if (leads.any { it.id == lead.id }) leads.map { if (it.id == lead.id) lead else it } else listOf(lead) + leads
        _state.value = s.copy(leads = leads, wallet = wallet ?: s.wallet)
    }

    private fun applyGbp(gbp: GbpWire?) {
        if (gbp == null) return
        val s = _state.value ?: return
        _state.value = s.copy(gbp = gbp)
    }
}
