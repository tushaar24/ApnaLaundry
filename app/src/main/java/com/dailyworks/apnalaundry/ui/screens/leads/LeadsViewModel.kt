package com.dailyworks.apnalaundry.ui.screens.leads

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.data.billing.CheckoutBridge
import com.dailyworks.apnalaundry.data.billing.CheckoutResult
import com.dailyworks.apnalaundry.data.billing.RazorpayCheckout
import com.dailyworks.apnalaundry.data.leads.LeadReply
import com.dailyworks.apnalaundry.data.leads.LeadWire
import com.dailyworks.apnalaundry.data.leads.LeadsRepository
import com.dailyworks.apnalaundry.data.leads.LeadsScreenWire
import com.dailyworks.apnalaundry.data.leads.OrderWire
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the pay / top-up flow is doing, for the sheets. */
data class LeadPayUi(
    val busy: Boolean = false,
    // Shown under the buttons; null = nothing to say.
    val error: String? = null,
    // The lead just paid (wallet or Razorpay) — the sheet shows the number.
    val paidLead: LeadWire? = null,
    // Money just added (paise), when the payment was a top-up (with or without a lead).
    val toppedUpPaise: Int? = null,
    // Paid, but the lead had expired / been taken back meanwhile: the money is in the wallet.
    val leadGone: Boolean = false,
    // The wallet is short for this lead: show "pay now" / "add money" instead.
    val shortForLead: LeadWire? = null,
    // Razorpay said paid but the server hasn't confirmed yet (no signature / offline): it will, by itself.
    val confirming: Boolean = false,
)

/**
 * Get customers (1.0.22): pays a lead from the wallet, or opens Razorpay Standard Checkout for an
 * Order (pay this lead now / add money) and hands the result to the server, which credits the
 * wallet once and pays the lead. Checkout results come through the shared [CheckoutBridge]; only
 * the instance that opened Checkout reacts (the paywall's ViewModel collects the same flow).
 */
class LeadsViewModel(
    private val repo: LeadsRepository,
    private val bridge: CheckoutBridge,
) : ViewModel() {

    val state: StateFlow<LeadsScreenWire?> = repo.state
    val loading: StateFlow<Boolean> = repo.loading
    val failed: StateFlow<Boolean> = repo.failed

    private val _pay = MutableStateFlow(LeadPayUi())
    val pay: StateFlow<LeadPayUi> = _pay

    private data class Pending(val order: OrderWire, val forLead: LeadWire?, val topupPaise: Int)
    private var pending: Pending? = null

    init {
        viewModelScope.launch {
            bridge.results.collect { result ->
                val p = pending ?: return@collect
                pending = null
                when (result) {
                    is CheckoutResult.Success -> confirm(p, result)
                    is CheckoutResult.Failure -> {
                        Analytics.leadPayFailed(p.forLead?.id, p.order.amountPaise, result.code)
                        // Razorpay's cancel code is 0 (Checkout.PAYMENT_CANCELED): the owner closed it — no error text.
                        _pay.value = _pay.value.copy(busy = false, error = if (result.code == 0) null else tr("Payment didn't go through — try again"))
                    }
                }
            }
        }
    }

    fun refresh() { viewModelScope.launch { repo.refresh() } }

    fun seen(lead: LeadWire) { viewModelScope.launch { runCatching { repo.seen(lead.id) } } }

    fun resetPay() { _pay.value = LeadPayUi() }

    /** Pay from the wallet. A short wallet switches the sheet to pay-now / add-money. */
    fun payFromWallet(lead: LeadWire) {
        val s = repo.current()
        Analytics.leadPayTapped(lead.id, lead.pricePaise, "wallet", s?.wallet?.balancePaise ?: 0)
        _pay.value = LeadPayUi(busy = true)
        viewModelScope.launch {
            val r: LeadReply = runCatching { repo.accept(lead.id) }.getOrElse { LeadReply(code = "OFFLINE") }
            _pay.value = when {
                r.success && r.lead != null -> { Analytics.leadPaid(lead.id, lead.pricePaise, "wallet"); LeadPayUi(paidLead = r.lead) }
                r.code == "INSUFFICIENT_BALANCE" -> { Analytics.leadPayBlocked(lead.id, r.shortPaise ?: lead.pricePaise); LeadPayUi(shortForLead = lead) }
                else -> LeadPayUi(error = errorText(r.code, r.message))
            }
        }
    }

    /** Razorpay for exactly this lead's price (or [amountPaise] when more goes into the wallet too). */
    fun payNow(activity: Activity, lead: LeadWire, amountPaise: Int? = null) {
        val s = repo.current()
        Analytics.leadPayTapped(lead.id, lead.pricePaise, if (amountPaise != null && amountPaise > lead.pricePaise) "topup" else "razorpay", s?.wallet?.balancePaise ?: 0)
        _pay.value = _pay.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val r = runCatching { repo.payOrder(lead.id, amountPaise) }.getOrElse { null }
            val order = r?.order
            if (r == null || !r.success || order == null) {
                _pay.value = _pay.value.copy(busy = false, error = errorText(r?.code ?: "OFFLINE", r?.message))
                return@launch
            }
            val extra = order.amountPaise - lead.pricePaise
            if (extra > 0) Analytics.walletTopupStarted(order.amountPaise, forLead = true)
            pending = Pending(order, lead, extra)
            RazorpayCheckout.launchOrder(
                activity, keyId = order.keyId, orderId = order.orderId, amountPaise = order.amountPaise,
                description = if (extra > 0) "MyLaundry · customer lead + wallet" else "MyLaundry · customer lead",
                contact = r.contact,
            )
        }
    }

    /** Add money to the wallet (no lead). */
    fun topup(activity: Activity, amountPaise: Int) {
        Analytics.walletTopupStarted(amountPaise, forLead = false)
        _pay.value = _pay.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val r = runCatching { repo.topup(amountPaise) }.getOrElse { null }
            val order = r?.order
            if (r == null || !r.success || order == null) {
                _pay.value = _pay.value.copy(busy = false, error = errorText(r?.code ?: "OFFLINE", r?.message))
                return@launch
            }
            pending = Pending(order, null, order.amountPaise)
            RazorpayCheckout.launchOrder(activity, keyId = order.keyId, orderId = order.orderId, amountPaise = order.amountPaise, description = "MyLaundry · wallet", contact = r.contact)
        }
    }

    fun decline(lead: LeadWire, reason: String) {
        Analytics.leadDeclined(lead.id, lead.pricePaise, reason)
        viewModelScope.launch { runCatching { repo.decline(lead.id, reason) } }
    }

    private suspend fun confirm(p: Pending, result: CheckoutResult.Success) {
        val paymentId = result.paymentId
        val signature = result.signature
        val orderId = result.orderId ?: p.order.orderId
        if (paymentId.isNullOrBlank() || signature.isNullOrBlank()) {
            // Paid, but nothing to verify with: the server confirms it from Razorpay by itself
            // (webhook, then a 3-minute sweep). Show "confirming" and look again a few times.
            _pay.value = _pay.value.copy(busy = false, confirming = true)
            awaitServerConfirmation(p)
            return
        }
        val r = runCatching { repo.verify(orderId, paymentId, signature) }.getOrNull()
        if (r == null || !r.success) {
            // The money left the owner's account; never show a failure that reads as "pay again".
            _pay.value = _pay.value.copy(busy = false, confirming = true)
            awaitServerConfirmation(p)
            return
        }
        if (p.topupPaise > 0 || p.forLead == null) Analytics.walletToppedUp(r.creditedPaise, forLead = p.forLead != null)
        val paid = r.lead?.takeIf { it.isPaid }
        if (paid != null && p.forLead != null) Analytics.leadPaid(paid.id, paid.pricePaise, "razorpay")
        _pay.value = LeadPayUi(paidLead = paid, toppedUpPaise = if (p.topupPaise > 0 || p.forLead == null) r.creditedPaise else null, leadGone = r.leadGone)
    }

    private suspend fun awaitServerConfirmation(p: Pending) {
        val delays = longArrayOf(2_000, 4_000, 8_000, 15_000)
        for (d in delays) {
            delay(d)
            if (!runCatching { repo.refresh() }.getOrDefault(false)) continue
            val lead = p.forLead?.let { repo.lead(it.id) }
            val paid = lead?.takeIf { it.isPaid }
            if (paid != null || (p.forLead == null && (repo.current()?.wallet?.balancePaise ?: 0) > 0)) {
                _pay.value = LeadPayUi(paidLead = paid, toppedUpPaise = if (p.forLead == null) p.order.amountPaise else null)
                return
            }
        }
        // Still not confirmed: the sweep will do it within minutes; leave the sheet on "confirming".
    }

    private fun errorText(code: String?, message: String?): String = when (code) {
        "LEAD_EXPIRED" -> tr("This lead has expired")
        "LEAD_GONE", "LEAD_PAID" -> tr("This lead is no longer open")
        "BILLING_UNAVAILABLE" -> tr("Payments aren't available right now — try again later")
        "OFFLINE" -> tr("No internet — check your connection and try again")
        else -> message?.takeIf { it.isNotBlank() } ?: tr("Something went wrong — try again")
    }
}
