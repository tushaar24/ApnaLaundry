package com.dailyworks.apnalaundry.ui.screens.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.billing.BillingRepository
import com.dailyworks.apnalaundry.data.billing.BillingStatus
import com.dailyworks.apnalaundry.data.billing.CheckoutBridge
import com.dailyworks.apnalaundry.data.billing.CheckoutResult
import com.dailyworks.apnalaundry.data.billing.RazorpayCheckout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class PaywallStage { PLANS, WAITING, DONE }
enum class PaywallPlan { ANNUAL, MONTHLY }

data class PaywallUiState(
    val status: BillingStatus? = null,
    val loaded: Boolean = false, // true once the first /status call resolves (ok or failed)
    val stage: PaywallStage = PaywallStage.PLANS,
    val plan: PaywallPlan = PaywallPlan.ANNUAL,
    // The plan the SERVER put on the subscription — analytics use this, never
    // the local pick.
    val purchasedPlan: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    // Razorpay Checkout came back without paying (failed, cancelled or Back) —
    // show the "try again" sheet over the plans.
    val retrySheet: Boolean = false,
) {
    val hasActive: Boolean get() = status?.hasActiveSubscription == true
    val annualAmount: Int get() = status?.plans?.annual?.amount ?: 499900
    val monthlyAmount: Int get() = status?.plans?.monthly?.amount ?: 49900
    val trialAmount: Int get() = status?.plans?.trial?.amount ?: 200

    // No active subscription and the backend says the paywall is due → the
    // non-cancellable hard gate. False if billing is unconfigured/unreachable so
    // an outage never locks the owner out of their shop.
    val shouldHardGate: Boolean get() = status?.configured == true && !hasActive && status?.paywallDue == true
}

/**
 * Drives the paywall: loads billing status, creates the subscription, hands it to
 * Razorpay Standard Checkout (which owns the UPI AutoPay approval UI), then
 * waits for the server webhook to confirm activation. A Checkout that ends
 * without paying opens [PaywallUiState.retrySheet]; other failures surface as
 * [PaywallUiState.error] back on the plans screen.
 */
class PaywallViewModel(
    private val repo: BillingRepository,
    private val bridge: CheckoutBridge,
    private val prefs: Prefs,
) : ViewModel() {

    private val _ui = MutableStateFlow(PaywallUiState())
    val ui: StateFlow<PaywallUiState> = _ui

    private var shownTracked = false

    // More than one PaywallViewModel can exist (gate, Settings → Subscription)
    // and all collect the shared CheckoutBridge — only the instance that
    // actually launched Checkout may react to a result.
    private var awaitingCheckout = false

    init {
        refresh()
        viewModelScope.launch {
            bridge.results.collect { result ->
                if (!awaitingCheckout) return@collect
                awaitingCheckout = false
                when (result) {
                    is CheckoutResult.Success -> onCheckoutApproved()
                    is CheckoutResult.Failure -> {
                        onCheckoutFailed(result.code, result.description)
                        _ui.value = _ui.value.copy(error = null, retrySheet = true)
                    }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val status = runCatching { repo.status() }.getOrNull()
            _ui.value = _ui.value.copy(status = status ?: _ui.value.status, loaded = true)
            // Only count it when the plans are actually on screen (not for a
            // subscribed user passing through the gate).
            if (status != null && status.configured && !status.hasActiveSubscription && !shownTracked) {
                shownTracked = true
                Analytics.paywallShown()
            }
        }
    }

    fun selectPlan(plan: PaywallPlan) { _ui.value = _ui.value.copy(plan = plan) }

    /** Create the subscription and open Razorpay Checkout for the mandate. */
    fun pay(activity: Activity) {
        val s = _ui.value
        if (s.busy) return
        _ui.value = s.copy(busy = true, error = null, retrySheet = false)
        // The user's chosen plan; the ₹2 trial is layered on top of it. The
        // server prices it from its own table (never trusts the client for money).
        val planArg = if (s.plan == PaywallPlan.ANNUAL) "annual" else "monthly"
        Analytics.planSelected(planArg)
        viewModelScope.launch {
            try {
                val res = repo.subscribe(planArg)
                if (!res.success || res.subscriptionId == null || res.keyId == null) {
                    throw IllegalStateException(res.message ?: "Could not start subscription")
                }
                // The server decides the real plan (never trust the client for money).
                _ui.value = _ui.value.copy(purchasedPlan = res.plan)
                Analytics.checkoutStarted(res.plan, res.amount, res.trialAmount)
                val contact = runCatching { prefs.userPhone.first() }.getOrNull()
                awaitingCheckout = true
                RazorpayCheckout.launch(activity, res, contact = contact, email = null)
            } catch (e: Exception) {
                onCheckoutFailed(0, e.message)
                // e.g. ALREADY_SUBSCRIBED from another device — refetch so the
                // active state renders instead of a broken Pay button.
                refresh()
            }
        }
    }

    /** Retry sheet CTA: open Checkout again for the same plan. */
    fun retry(activity: Activity) {
        _ui.value = _ui.value.copy(retrySheet = false)
        pay(activity)
    }

    fun dismissRetry() { _ui.value = _ui.value.copy(retrySheet = false) }

    /** Cancel the active subscription, then refetch status. */
    fun cancel() {
        if (_ui.value.busy) return
        _ui.value = _ui.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                Analytics.subscriptionCancelRequested(_ui.value.status?.subscription?.plan ?: "monthly")
                val res = repo.cancel()
                if (!res.success) throw IllegalStateException(res.message ?: "Could not cancel")
                val status = runCatching { repo.status() }.getOrNull()
                _ui.value = _ui.value.copy(status = status ?: _ui.value.status, busy = false)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false, error = e.message ?: "Could not cancel — try again")
            }
        }
    }

    private fun purchasedOrSelected(): String =
        _ui.value.purchasedPlan ?: _ui.value.plan.name.lowercase()

    private fun onCheckoutApproved() {
        _ui.value = _ui.value.copy(stage = PaywallStage.WAITING)
        viewModelScope.launch {
            val status = repo.pollUntilActive()
            if (status?.hasActiveSubscription == true) {
                Analytics.checkoutSucceeded(purchasedOrSelected())
                _ui.value = _ui.value.copy(status = status, stage = PaywallStage.DONE, busy = false)
            } else {
                // Approved on-device but not yet confirmed — let the user re-check.
                _ui.value = _ui.value.copy(
                    stage = PaywallStage.PLANS, busy = false,
                    error = "Payment is processing. If it was approved, pull to refresh in a moment.",
                )
            }
        }
    }

    private fun onCheckoutFailed(code: Int, description: String?) {
        val msg = description?.takeIf { it.isNotBlank() } ?: "Payment was not completed"
        Analytics.checkoutFailed(purchasedOrSelected(), msg)
        _ui.value = _ui.value.copy(stage = PaywallStage.PLANS, busy = false, error = msg)
    }
}
