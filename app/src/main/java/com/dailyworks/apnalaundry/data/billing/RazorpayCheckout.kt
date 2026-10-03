package com.dailyworks.apnalaundry.data.billing

import android.app.Activity
import com.razorpay.Checkout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject

/**
 * Razorpay Standard Checkout for UPI AutoPay subscriptions. Razorpay owns the
 * mandate-approval UI (its own UPI app picker on mobile, QR on desktop), so we
 * just hand it the subscription id we created server-side. No raw S2S.
 *
 * Results arrive on the hosting Activity's PaymentResultWithDataListener (see
 * MainActivity), which forwards them into [CheckoutBridge] for the ViewModel.
 */
object RazorpayCheckout {
    /** Open Razorpay Checkout for the subscription's mandate authorization. */
    fun launch(activity: Activity, sub: SubscribeResponse, contact: String?, email: String?) {
        val key = sub.keyId ?: return
        val subscriptionId = sub.subscriptionId ?: return
        val checkout = Checkout()
        checkout.setKeyID(key)
        val options = JSONObject().apply {
            put("name", "ApnaLaundry")
            put(
                "description",
                when {
                    sub.trialAmount > 0 -> "Start ₹2 trial · UPI AutoPay"
                    sub.plan == "annual" -> "Yearly plan · UPI AutoPay"
                    else -> "Monthly plan · UPI AutoPay"
                },
            )
            put("subscription_id", subscriptionId)
            put("theme", JSONObject().put("color", "#1D4ED8"))
            val prefill = JSONObject()
            if (!contact.isNullOrBlank()) prefill.put("contact", contact)
            if (!email.isNullOrBlank()) prefill.put("email", email)
            if (prefill.length() > 0) put("prefill", prefill)
        }
        checkout.open(activity, options)
    }
}

/** The result of a Razorpay Checkout attempt, surfaced from the Activity. */
sealed interface CheckoutResult {
    data class Success(val paymentId: String?) : CheckoutResult
    data class Failure(val code: Int, val description: String?) : CheckoutResult
}

/**
 * Bridges Checkout results from the Activity (which must implement Razorpay's
 * PaymentResultWithDataListener) to the ViewModel. replay = 1 so a result
 * posted during an Activity/collector gap (e.g. while switching back from the
 * UPI app) is not lost; collectors must gate on "did I start a checkout" so a
 * replayed result never triggers a ViewModel that wasn't paying.
 */
class CheckoutBridge {
    private val _results = MutableSharedFlow<CheckoutResult>(replay = 1, extraBufferCapacity = 8)
    val results: SharedFlow<CheckoutResult> = _results
    fun post(result: CheckoutResult) { _results.tryEmit(result) }
}
