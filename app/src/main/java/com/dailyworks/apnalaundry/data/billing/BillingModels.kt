package com.dailyworks.apnalaundry.data.billing

import kotlinx.serialization.Serializable

// Wire types for /api/laundry/billing/*. Mirrors the website's billing client
// so both platforms drive the same A/B paywall + UPI AutoPay flow.

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
    val variant: String? = null,
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
    val variant: String = "free_50", // trial_2 | free_50
    val orderCount: Int = 0,
    val freeOrderThreshold: Int = 50,
    val paywallDue: Boolean = false,
    val hasActiveSubscription: Boolean = false,
    val plans: BillingPlans = BillingPlans(),
    val subscription: BillingSubscriptionDto? = null,
)

@Serializable
data class SubscribeRequest(val plan: String)

@Serializable
data class SubscribeResponse(
    val success: Boolean = false,
    val subscriptionId: String? = null,
    // UPI AutoPay intent: open it to pick a UPI app, or render it as a QR code.
    val intentUrl: String? = null,
    val plan: String = "",
    val variant: String = "",
    val amount: Int = 0,
    val trialAmount: Int = 0,
    val reused: Boolean = false,
    val message: String? = null,
)

@Serializable
data class CancelResponse(val success: Boolean = false, val message: String? = null)
