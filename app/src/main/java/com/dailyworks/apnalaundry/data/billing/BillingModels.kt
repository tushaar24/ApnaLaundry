package com.dailyworks.apnalaundry.data.billing

import kotlinx.serialization.Serializable

// Wire types for /api/laundry/billing/*. Mirrors the website's billing client
// so both platforms drive the same ₹2-trial paywall + UPI AutoPay flow.
// Unknown keys are ignored (AuthApi.json), so extra server fields are harmless.

@Serializable
data class PlanAmount(val amount: Int = 0)

@Serializable
data class BillingPlans(
    val monthly: PlanAmount = PlanAmount(),
    val annual: PlanAmount = PlanAmount(),
    val trial: PlanAmount = PlanAmount(),
)

@Serializable
data class BillingSubscriptionDto(
    val id: String = "",
    val plan: String = "",
    val status: String = "",
    val amount: Int = 0,
    val trialAmount: Int = 0,
    val paidCount: Int = 0,
    val currentEnd: String? = null,
    val chargeAt: String? = null,
)

@Serializable
data class BillingStatus(
    val success: Boolean = false,
    val configured: Boolean = false,
    val paywallDue: Boolean = false,
    val hasActiveSubscription: Boolean = false,
    val plans: BillingPlans = BillingPlans(),
    val subscription: BillingSubscriptionDto? = null,
) {
    /**
     * Cancelled, but still inside the period it already paid for (the server
     * says the paywall isn't due yet). Only a cancelled plan qualifies, so
     * "billing unconfigured" never gets in this way.
     */
    val inCancelGrace: Boolean get() = subscription?.status == "cancelled" && !paywallDue

    /** May use the app: an active plan, or a cancelled one still in its paid period. */
    val hasAccess: Boolean get() = hasActiveSubscription || inCancelGrace
}

@Serializable
data class SubscribeRequest(val plan: String)

@Serializable
data class SubscribeResponse(
    val success: Boolean = false,
    // Razorpay subscription id (sub_…) — passed to Razorpay Checkout.
    val subscriptionId: String? = null,
    // Public Razorpay key id for Checkout.
    val keyId: String? = null,
    // Hosted authorization link (fallback; Checkout is the primary path).
    val shortUrl: String? = null,
    val plan: String = "",
    val amount: Int = 0,
    val trialAmount: Int = 0,
    val reused: Boolean = false,
    val message: String? = null,
)

@Serializable
data class CancelResponse(val success: Boolean = false, val message: String? = null)
