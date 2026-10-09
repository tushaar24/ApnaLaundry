package com.dailyworks.apnalaundry.analytics

import android.app.Application
import android.content.Context
import android.os.Bundle
import com.dailyworks.apnalaundry.R
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import java.util.Currency

/**
 * Meta (Facebook) SDK app events for Meta ads — custom events:
 *   login_success            OTP login completed
 *   subscription_initiated   Razorpay Checkout opened for the subscription
 *   subscription_failed      Razorpay Checkout closed without completing (error / dismissed)
 *   subscription_successful  Razorpay Checkout reported the payment as successful
 *
 * The manifest sets AutoInitEnabled=false, so nothing is sent until [init] finds
 * an app id + client token in strings.xml (blank = disabled).
 */
object MetaEvents {
    private var logger: AppEventsLogger? = null
    private val INR: Currency = Currency.getInstance("INR")

    /** Call from Application.onCreate(). */
    fun init(app: Application) {
        if (appId(app).isBlank() || clientToken(app).isBlank()) return
        FacebookSdk.setAutoInitEnabled(true)
        FacebookSdk.fullyInitialize()
        AppEventsLogger.activateApp(app)
        logger = AppEventsLogger.newLogger(app)
    }

    private fun appId(context: Context): String =
        runCatching { context.getString(R.string.facebook_app_id) }.getOrDefault("")

    private fun clientToken(context: Context): String =
        runCatching { context.getString(R.string.facebook_client_token) }.getOrDefault("")

    // ---- identity (improves Meta's ad attribution match rate; hashed by the SDK) ----

    fun identify(userId: String, phone: String) {
        if (logger == null) return
        AppEventsLogger.setUserID(userId)
        val digits = phone.filter { it.isDigit() }
        val e164 = if (digits.length == 10) "91$digits" else digits
        AppEventsLogger.setUserData(null, null, null, e164, null, null, null, null, null, "in")
    }

    fun clearIdentity() {
        if (logger == null) return
        AppEventsLogger.clearUserID()
        AppEventsLogger.clearUserData()
    }

    // ---- events ----

    fun loginSuccess(isNewUser: Boolean) =
        log("login_success", params("is_new_user" to isNewUser.toString()))

    /** [amountPaise] = what this approval charges now (the ₹2 trial, else the plan amount). */
    fun subscriptionInitiated(subscriptionId: String, plan: String, amountPaise: Int) =
        logValue("subscription_initiated", amountPaise, params("plan" to plan, "subscription_id" to subscriptionId))

    fun subscriptionFailed(subscriptionId: String?, plan: String, reason: String) =
        log(
            "subscription_failed",
            params("plan" to plan, "subscription_id" to subscriptionId, "reason" to reason.take(100)),
        )

    fun subscriptionSuccessful(subscriptionId: String?, plan: String, amountPaise: Int) =
        logValue("subscription_successful", amountPaise, params("plan" to plan, "subscription_id" to subscriptionId))

    // ---- internals ----

    private fun params(vararg pairs: Pair<String, String?>): Bundle = Bundle().apply {
        putString("platform", "android")
        for ((k, v) in pairs) if (v != null) putString(k, v)
    }

    private fun log(name: String, params: Bundle) {
        logger?.logEvent(name, params)
    }

    private fun logValue(name: String, amountPaise: Int, params: Bundle) {
        val l = logger ?: return
        params.putString(AppEventsConstants.EVENT_PARAM_CURRENCY, INR.currencyCode)
        l.logEvent(name, amountPaise / 100.0, params)
    }
}
