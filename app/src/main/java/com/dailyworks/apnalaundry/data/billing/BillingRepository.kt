package com.dailyworks.apnalaundry.data.billing

import com.dailyworks.apnalaundry.data.Prefs
import kotlinx.coroutines.delay

/**
 * Billing data access for the paywall: create the subscription, read status, and
 * poll for the webhook-confirmed activation after the user approves the mandate
 * in Razorpay Checkout. Every good status updates the local "subscription
 * active" cache in [Prefs], used only when billing can't be reached.
 */
class BillingRepository(private val api: BillingApi, private val prefs: Prefs) {

    suspend fun status(): BillingStatus = api.status().also { s ->
        if (s.hasAccess) prefs.markSubActive() else prefs.clearSubActive()
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

    /** A recent (7-day) server-confirmed active subscription for this user. */
    suspend fun cachedActive(): Boolean = prefs.subActiveCached()

    suspend fun subscribe(plan: String): SubscribeResponse = api.subscribe(plan)

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
