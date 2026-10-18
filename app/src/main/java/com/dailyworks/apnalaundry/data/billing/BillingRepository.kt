package com.dailyworks.apnalaundry.data.billing

import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.data.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Billing data access for the paywall: create the subscription, read status, and
 * poll for the webhook-confirmed activation after the user approves the mandate
 * in Razorpay Checkout. Every good status saves the subscription (active + end
 * date) in [Prefs]; the free-orders gate reads that instead of the network.
 */
class BillingRepository(private val api: BillingApi, private val prefs: Prefs) {

    suspend fun status(): BillingStatus = api.status().also { s ->
        prefs.saveSubscription(
            active = s.hasAccess, endsAtIso = s.subscription?.accessUntil, freeOrders = s.freeOrders,
            paywallGroup = s.paywallGroup,
        )
        // Paywall test variant -> CleverTap (Paywall_Variant + paywall_variant on events):
        // the server-set variant's name, else the legacy group.
        Analytics.setPaywallVariant(s.paywall?.variant?.takeIf { it.isNotBlank() } ?: s.paywallGroup)
    }

    /** Refresh the saved subscription; failures keep the last saved one. */
    suspend fun refreshSaved() {
        // App start: the saved paywall-test group stamps events even before (or without) the network.
        Analytics.setPaywallVariant(prefs.paywallGroup.first())
        runCatching { status() }
    }

    /** [status], retrying a failed call [retries] more times with backoff; null if all fail. */
    suspend fun statusWithRetry(retries: Int = 2): BillingStatus? {
        val delays = longArrayOf(1_000, 2_000, 4_000)
        for (attempt in 0..retries) {
            runCatching { status() }.onSuccess { return it }
            if (attempt < retries) delay(delays[minOf(attempt, delays.lastIndex)])
        }
        return null
    }

    /** The saved subscription gives access now (no network; false until a check saved one). */
    suspend fun savedActive(): Boolean = prefs.subscriptionActive()

    /** This shop's free orders, as last saved from the server (no network; 5 until then). */
    suspend fun savedFreeOrders(): Int = prefs.freeOrders(DEFAULT_FREE_ORDERS)

    suspend fun subscribe(plan: String, trial: Boolean = false): SubscribeResponse = api.subscribe(plan, trial)

    suspend fun cancel(): CancelResponse = api.cancel()

    /**
     * Poll /status until the subscription is active (the server webhook flips it
     * after Razorpay confirms the mandate). Returns the status once active, or
     * the last status on timeout. Transient errors are ignored and retried.
     */
    suspend fun pollUntilActive(timeoutMs: Long = 90_000, intervalMs: Long = 3_000): BillingStatus? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val status = runCatching { status() }.getOrNull()
            if (status?.hasActiveSubscription == true) return status
            delay(intervalMs)
        }
        return runCatching { status() }.getOrNull()
    }
}
