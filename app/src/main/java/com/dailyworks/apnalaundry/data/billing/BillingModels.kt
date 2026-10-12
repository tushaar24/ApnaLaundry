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
) {
    /** When the paid period (or a ₹2 trial's free days) ends — access runs until then. */
    val accessUntil: String?
        get() = if (trialAmount > 0 && paidCount == 0) chargeAt ?: currentEnd else currentEnd ?: chargeAt
}

@Serializable
data class BillingStatus(
    val success: Boolean = false,
    val configured: Boolean = false,
    val paywallDue: Boolean = false,
    val hasActiveSubscription: Boolean = false,
    // One ₹2 trial per shop: false once a subscription has got past checkout,
    // and the next subscription charges the full plan price straight away.
    val trialEligible: Boolean = true,
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

// trial = false: no ₹2 trial — the plan is charged in full on approval (the
// Android app gives free orders instead of a trial). The website never sends it.
@Serializable
data class SubscribeRequest(val plan: String, val trial: Boolean = false)

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
