package com.dailyworks.apnalaundry.analytics

import android.app.Application
import android.content.Context
import com.clevertap.android.sdk.ActivityLifecycleCallback
import com.clevertap.android.sdk.CleverTapAPI
import com.dailyworks.apnalaundry.R
import com.dailyworks.apnalaundry.domain.OrderLine
import com.dailyworks.apnalaundry.domain.PayMethod

/**
 * CleverTap event tracker for the ApnaLaundry funnel taxonomy (see
 * docs/analytics-events.md). One method per event, with the SAME names and
 * property keys as the website's `Analytics` wrapper (web/src/analytics/
 * events.ts), so CleverTap funnels line up across platforms.
 *
 * No-ops entirely until the manifest CleverTap account id/token are set, so
 * nothing breaks before credentials are provided.
 */
object Analytics {
    private var ct: CleverTapAPI? = null
    private var enabled = false

    /**
     * Register CleverTap's activity-lifecycle callbacks. MUST run before
     * Application.super.onCreate(). Skipped when unconfigured.
     */
    fun registerLifecycle(app: Application) {
        if (accountId(app).isBlank()) return
        ActivityLifecycleCallback.register(app)
    }

    /** Resolve the default instance. Call after super.onCreate(). */
    fun init(app: Application) {
        if (accountId(app).isBlank()) return
        ct = CleverTapAPI.getDefaultInstance(app)
        enabled = ct != null
    }

    private fun accountId(context: Context): String =
        runCatching { context.getString(R.string.clevertap_account_id) }.getOrDefault("")

    // ---- identity ----

    fun identify(userId: String, phone: String, name: String?) {
        if (!enabled) return
        val profile = hashMapOf<String, Any>(
            "Identity" to userId,
            "Phone" to if (phone.startsWith("+")) phone else "+91$phone",
        )
        if (!name.isNullOrBlank()) profile["Name"] = name
        ct?.onUserLogin(profile)
    }

    fun updateProfile(fields: Map<String, Any>) {
        if (!enabled) return
        ct?.pushProfile(HashMap(fields))
    }

    private fun track(name: String, props: Map<String, Any?> = emptyMap()) {
        if (!enabled) return
        val clean = HashMap<String, Any>()
        for ((k, v) in props) if (v != null) clean[k] = v
        // Stamped last so no event prop can override them.
        clean["platform"] = "android"
        clean["source"] = "android"
        ct?.pushEvent(name, clean)
    }

    private fun methodName(m: PayMethod): String = when (m) {
        PayMethod.CASH -> "cash"; PayMethod.UPI -> "upi"; PayMethod.NONE -> "none"
    }

    // ---- screens ----

    fun screen(screen: String) = track("Screen Viewed", mapOf("screen" to screen))

    // ---- auth ----

    fun otpRequested(phoneType: String, isResend: Boolean) =
        track("OTP Requested", mapOf("phone_type" to phoneType, "is_resend" to isResend))

    fun otpRequestFailed(reason: String) =
        track("OTP Request Failed", mapOf("reason" to reason))

    fun otpSubmitted() = track("OTP Submitted")

    fun otpVerificationFailed(reason: String, attemptsRemaining: Int?) =
        track("OTP Verification Failed", mapOf("reason" to reason, "attempts_remaining" to attemptsRemaining))

    fun loggedIn(isNewUser: Boolean, needsSetup: Boolean) =
        track("Logged In", mapOf("is_new_user" to isNewUser, "needs_setup" to needsSetup))

    fun setupCompleted(servicesCount: Int, expressPct: Int) =
        track("Setup Completed", mapOf("services_count" to servicesCount, "express_pct" to expressPct))

    fun loggedOut() = track("Logged Out")

    // ---- onboarding (after the paywall: intro -> name -> services -> bill) ----

    /** Each onboarding step as it appears, incl. a resumed step after a restart. */
    fun onboardingStepViewed(step: String, index: Int) =
        track("Onboarding Step Viewed", mapOf("step" to step, "step_index" to index))

    fun logoUploaded() = track("Logo Uploaded")

    /** "Test on WhatsApp" on the bill step: a sample bill sent to the owner's own chat. */
    fun testBillSent(template: String, editing: Boolean) =
        track("Test Bill Sent", mapOf("template" to template, "from" to if (editing) "settings" else "onboarding"))

    // ---- order creation & lifecycle ----

    fun newOrderStarted(entryPoint: String, isEdit: Boolean) =
        track("New Order Started", mapOf("entry_point" to entryPoint, "is_edit" to isEdit))

    fun orderSaved(
        orderId: Int, isEdit: Boolean, hasBill: Boolean, pickup: String, delivery: String,
        express: Boolean, discount: Int, fee: Int, pieces: Int, kg: Double,
        servicesCount: Int, total: Int, quickBill: Boolean,
    ) = track(
        "Order Saved",
        mapOf(
            "order_id" to orderId, "is_edit" to isEdit, "has_bill" to hasBill, "pickup" to pickup,
            "delivery" to delivery, "express" to express, "discount" to discount, "fee" to fee,
            "pieces" to pieces, "kg" to kg, "services_count" to servicesCount, "total" to total,
            "quick_bill" to quickBill,
        ),
    )

    fun orderPickedUp(orderId: Int) = track("Order Picked Up", mapOf("order_id" to orderId))

    fun clothesCounted(orderId: Int, nextStatus: String, total: Int) =
        track("Clothes Counted", mapOf("order_id" to orderId, "next_status" to nextStatus, "total" to total))

    fun orderMarkedReady(orderId: Int) = track("Order Marked Ready", mapOf("order_id" to orderId))

    fun orderDelivered(
        orderId: Int, total: Int, amountReceived: Int, method: PayMethod,
        toKhata: Int, fromAdvance: Int, lines: List<OrderLine>,
    ) {
        track(
            "Order Delivered",
            mapOf(
                "order_id" to orderId, "total" to total, "amount_received" to amountReceived,
                "method" to methodName(method), "to_khata" to toKhata, "from_advance" to fromAdvance,
            ),
        )
        charged(total, methodName(method), orderId, lines)
    }

    fun orderCancelled(orderId: Int, reason: String) =
        track("Order Cancelled", mapOf("order_id" to orderId, "reason" to reason))

    fun orderRescheduled(orderId: Int, kind: String, notify: Boolean) =
        track("Order Rescheduled", mapOf("order_id" to orderId, "kind" to kind, "notify" to notify))

    // ---- payments & khata ----

    fun paymentReceived(amount: Int, method: PayMethod, type: String, customerId: String, orderId: Int?) =
        track(
            "Payment Received",
            mapOf(
                "amount" to amount, "method" to methodName(method), "type" to type,
                "customer_id" to customerId, "order_id" to orderId,
            ),
        )

    fun oldBaakiAdded(amount: Int, customerId: String) =
        track("Old Baaki Added", mapOf("amount" to amount, "customer_id" to customerId))

    /** CleverTap reserved revenue event. */
    private fun charged(amount: Int, paymentMethod: String, orderId: Int, lines: List<OrderLine>) {
        if (!enabled) return
        val details = hashMapOf<String, Any>(
            "platform" to "android",
            "source" to "android",
            "Amount" to amount,
            "payment_method" to paymentMethod,
            "order_id" to orderId,
        )
        val items = ArrayList<HashMap<String, Any>>()
        for (l in lines) {
            items.add(
                hashMapOf(
                    "service" to l.serviceName, "item" to l.itemName, "qty" to l.qty, "amount" to l.amt,
                ),
            )
        }
        ct?.pushChargedEvent(details, items)
    }

    // ---- customers, bills, rates, engagement ----

    fun customerAdded(entryPoint: String, withOldBaaki: Boolean) =
        track("Customer Added", mapOf("entry_point" to entryPoint, "with_old_baaki" to withOldBaaki))

    fun reminderSent(customerId: String, amount: Int) =
        track("Reminder Sent", mapOf("customer_id" to customerId, "amount" to amount))

    fun billSent(orderId: Int) =
        track("Bill Sent", mapOf("order_id" to orderId, "channel" to "whatsapp"))

    fun billViewed(orderId: Int) = track("Bill Viewed", mapOf("order_id" to orderId))

    /** Bill PDF opened in the system share sheet ("Download"). [from] = "bill" | "order_detail". */
    fun billDownloaded(orderId: Int, from: String) =
        track("Bill Downloaded", mapOf("order_id" to orderId, "from" to from))

    fun combinedBillSent(customerId: String, orders: Int, total: Int, toPay: Int) =
        track("Combined Bill Sent", mapOf("customer_id" to customerId, "orders" to orders, "total" to total, "to_pay" to toPay, "channel" to "whatsapp"))

    fun summaryShared(period: String) = track("Summary Shared", mapOf("period" to period))

    fun ratesOpened(from: String) = track("Rates Opened", mapOf("from" to from))

    fun serviceAdded(mode: String) = track("Service Added", mapOf("mode" to mode))

    fun serviceEdited(serviceId: String) = track("Service Edited", mapOf("service_id" to serviceId))

    fun serviceDeleted(serviceId: String) = track("Service Deleted", mapOf("service_id" to serviceId))

    fun earningsPeriodChanged(period: String) =
        track("Earnings Period Changed", mapOf("period" to period))

    fun orderSearchOpened() = track("Order Search Opened")

    // ---- billing / paywall funnel (₹2 trial hard gate) ----
    // The client fires the UI funnel; the backend fires the money-confirmed
    // events (Subscription Activated / Charged / Payment Failed / Halted /
    // Cancelled) from the Razorpay webhook.
    fun paywallShown() = track("Paywall Shown")

    // Paywall intro video (autoplays with sound; muted = the user muted it).
    fun paywallVideoStarted(muted: Boolean) =
        track("Paywall Video Started", mapOf("muted" to muted))

    fun paywallVideoUnmuted() = track("Paywall Video Unmuted")

    fun paywallVideoCompleted() = track("Paywall Video Completed")

    fun paywallFaqOpened() = track("Paywall FAQ Opened")

    fun planSelected(plan: String) =
        track("Plan Selected", mapOf("plan" to plan))

    fun checkoutStarted(plan: String, amount: Int, trialAmount: Int) =
        track("Checkout Started", mapOf("plan" to plan, "amount" to amount, "trial_amount" to trialAmount))

    fun checkoutSucceeded(plan: String) =
        track("Checkout Succeeded", mapOf("plan" to plan))

    // The "try again" sheet shown when Razorpay Checkout closes unpaid.
    // reason: "failed" | "cancelled".
    fun paymentRetryShown(plan: String, reason: String) =
        track("Payment Retry Shown", mapOf("plan" to plan, "reason" to reason))

    /** [switchedFrom] = "annual" when a failed Yearly attempt switched to Monthly. */
    fun paymentRetryTapped(plan: String, switchedFrom: String? = null) =
        track("Payment Retry Tapped", if (switchedFrom != null) mapOf("plan" to plan, "switched_from" to switchedFrom) else mapOf("plan" to plan))

    fun paymentRetryDismissed(plan: String) =
        track("Payment Retry Dismissed", mapOf("plan" to plan))

    /** Razorpay reported success but the server didn't confirm the subscription in time. */
    fun checkoutUnconfirmed(plan: String) =
        track("Checkout Unconfirmed", mapOf("plan" to plan))

    /**
     * The gate let an unsubscribed user into the app without the paywall.
     * reason: "status_failed" | "not_configured" | "not_due".
     */
    fun paywallSkipped(reason: String) =
        track("Paywall Skipped", mapOf("reason" to reason))

    fun checkoutFailed(plan: String, reason: String) =
        track("Checkout Failed", mapOf("plan" to plan, "reason" to reason))

    fun subscriptionCancelRequested(plan: String) =
        track("Subscription Cancel Requested", mapOf("plan" to plan))
}
