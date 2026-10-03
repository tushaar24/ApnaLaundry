package com.dailyworks.apnalaundry.data.billing

import kotlinx.coroutines.delay

/**
 * Billing data access for the paywall: create the subscription, read status, and
 * poll for the webhook-confirmed activation after the user approves the mandate
 * in Razorpay Checkout.
 */
class BillingRepository(private val api: BillingApi) {

    suspend fun status(): BillingStatus = api.status()

    suspend fun subscribe(plan: String): SubscribeResponse = api.subscribe(plan)

    suspend fun cancel(): CancelResponse = api.cancel()

    /**
     * Poll /status until the subscription is active (the server webhook flips it
     * after Razorpay confirms the mandate). Returns true once active, false on
     * timeout. Transient errors are ignored and retried.
     */
    suspend fun pollUntilActive(timeoutMs: Long = 90_000, intervalMs: Long = 3_000): BillingStatus? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val status = runCatching { api.status() }.getOrNull()
            if (status?.hasActiveSubscription == true) return status
            delay(intervalMs)
        }
        return runCatching { api.status() }.getOrNull()
    }
}
