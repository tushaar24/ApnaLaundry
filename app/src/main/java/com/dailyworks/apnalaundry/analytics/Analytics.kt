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
        // Debug builds: CleverTap logs (incl. push token + received pushes) in Logcat, tag "CleverTap".
        if (com.dailyworks.apnalaundry.BuildConfig.DEBUG) CleverTapAPI.setDebugLevel(CleverTapAPI.LogLevel.VERBOSE)
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
        // Agreed on the login screen ("…and to get updates on WhatsApp"); CleverTap
        // sends WhatsApp only to profiles with this set.
        profile["MSG-whatsapp"] = true
        ct?.onUserLogin(profile)
    }

    fun updateProfile(fields: Map<String, Any>) {
        if (!enabled) return
        ct?.pushProfile(HashMap(fields))
    }

    // ---- the paywall test (2026-10-14) ----
    // The shop's group (hard_trial | free1) from /billing/status: saved on the
    // CleverTap profile as Paywall_Variant (the server saves it too) and stamped
    // on every event as paywall_variant, so any funnel or export splits by it.
    // null = not in the test: nothing saved, nothing stamped.
    @Volatile private var paywallVariant: String? = null
    @Volatile private var paywallVariantSaved: String? = null

    fun setPaywallVariant(group: String?) {
        val g = group?.takeIf { it.isNotBlank() }
        paywallVariant = g
        if (g == null || g == paywallVariantSaved) return
        paywallVariantSaved = g
        updateProfile(mapOf("Paywall_Variant" to g))
    }

    // The first-customer test (2026-10-24): the account's CustomerFlow key from
    // GET /experiments, saved on the profile as Customer_Flow (the server saves
    // it too) and stamped on every event as customer_flow. null = not in the test.
    @Volatile private var customerFlow: String? = null
    @Volatile private var customerFlowSaved: String? = null

    fun setCustomerFlow(flow: String?) {
        customerFlow = flow
        if (flow == null || flow == customerFlowSaved) return
        customerFlowSaved = flow
        updateProfile(mapOf("Customer_Flow" to flow))
    }

    private fun track(name: String, props: Map<String, Any?> = emptyMap()) {
        if (!enabled) return
        val clean = HashMap<String, Any>()
        for ((k, v) in props) if (v != null) clean[k] = v
        paywallVariant?.let { clean["paywall_variant"] = it }
        customerFlow?.let { clean["customer_flow"] = it }
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

    /** [method]: firebase | sms (the server's own, MSG91) | review. */
    fun otpSent(method: String) = track("OTP Sent", mapOf("method" to method))

    fun otpRequestFailed(reason: String) =
        track("OTP Request Failed", mapOf("reason" to reason))

    fun otpSubmitted() = track("OTP Submitted")

    fun otpVerificationFailed(reason: String, attemptsRemaining: Int?) =
        track("OTP Verification Failed", mapOf("reason" to reason, "attempts_remaining" to attemptsRemaining))

    fun loggedIn(isNewUser: Boolean, needsSetup: Boolean) {
        // A date property, for "signed up in the last 7 days" segments and journeys.
        if (isNewUser) updateProfile(mapOf("signup_date" to java.util.Date()))
        track("Logged In", mapOf("is_new_user" to isNewUser, "needs_setup" to needsSetup))
    }

    fun setupCompleted(servicesCount: Int, expressPct: Int) =
        track("Setup Completed", mapOf("services_count" to servicesCount, "express_pct" to expressPct))

    fun loggedOut() {
        track("Logged Out")
        paywallVariant = null
        paywallVariantSaved = null
        customerFlow = null
        customerFlowSaved = null
    }

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

    /**
     * Left New/Edit order without saving. `stage` is how far they got:
     * customer (none chosen) → clothes (customer, no clothes) → save (both, never tapped save).
     */
    fun newOrderAbandoned(
        entryPoint: String, isEdit: Boolean, stage: String, customerSet: Boolean, customerAdded: Boolean,
        customerFormOpened: Boolean, clothesAdded: Boolean, pieces: Int, kg: Double, total: Int, seconds: Long,
        customerSheetOpens: Int = 0, saveBlockedTaps: Int = 0, addressNeeded: Boolean = false, addressSheetOpened: Boolean = false,
    ) = track(
        "New Order Abandoned",
        mapOf(
            "entry_point" to entryPoint, "is_edit" to isEdit, "stage" to stage,
            "customer_set" to customerSet, "customer_added" to customerAdded,
            "customer_form_opened" to customerFormOpened, "clothes_added" to clothesAdded,
            "pieces" to pieces, "kg" to kg, "total" to total, "seconds_on_screen" to seconds,
            "customer_sheet_opens" to customerSheetOpens, "save_blocked_taps" to saveBlockedTaps,
            "address_needed" to addressNeeded, "address_sheet_opened" to addressSheetOpened,
        ),
    )

    // ---- adding a customer on New order (the first-customer test, 2026-10-24) ----

    /** An existing customer picked on New order. [from] = recent | search; [queryLength] = what was typed. */
    fun customerSelected(from: String, queryLength: Int) =
        track("Customer Selected", mapOf("from" to from, "query_length" to queryLength))

    /** The customer search on New order was typed in (once per screen). */
    fun customerSearchUsed() = track("Customer Search Used")

    /** Pickup or delivery switched. [leg] = pickup | delivery, [to] = shop | home. */
    fun orderRouteChanged(leg: String, to: String, customerSet: Boolean, hasAddress: Boolean) =
        track("Order Route Changed", mapOf("leg" to leg, "to" to to, "customer_set" to customerSet, "has_address" to hasAddress))

    /** The greyed New order button tapped. [reason] = customer | address | clothes. */
    fun orderSaveBlocked(reason: String, isEdit: Boolean) =
        track("Order Save Blocked", mapOf("reason" to reason, "is_edit" to isEdit))

    /**
     * The customer sheet appeared. [ctx] = order | list; [mode] = new | edit;
     * [trigger] = auto (opened by itself) | add_button | add_with_query (typed a
     * name/number first) | address_card | list | other; [addressShown] = the
     * address field is on the sheet; [prefilled] = none | name | phone.
     */
    fun customerSheetOpened(
        ctx: String, mode: String, trigger: String, addressShown: Boolean, addressNeeded: Boolean,
        prefilled: String, customers: Int, guideOn: Boolean,
    ) = track(
        "Customer Sheet Opened",
        mapOf(
            "ctx" to ctx, "mode" to mode, "trigger" to trigger, "address_shown" to addressShown,
            "address_needed" to addressNeeded, "prefilled" to prefilled, "customers" to customers, "guide_on" to guideOn,
        ),
    )

    /** First typing in a field of the customer sheet (once per field per sheet). [field] = name | phone | address | old_khata. */
    fun customerSheetFieldEdited(field: String, ctx: String, seconds: Long) =
        track("Customer Sheet Field Edited", mapOf("field" to field, "ctx" to ctx, "seconds_since_open" to seconds))

    /**
     * More than 10 digits typed or pasted in the phone field (it keeps the
     * first 10). [lead] = 91 | 0 | other: what the extra digits most likely were.
     */
    fun customerPhoneTrimmed(digits: Int, lead: String) =
        track("Customer Phone Trimmed", mapOf("digits" to digits, "lead" to lead))

    /** "Choose from contacts" tapped. [permission] = granted | asking. */
    fun customerContactsTapped(permission: String) =
        track("Customer Contacts Tapped", mapOf("permission" to permission))

    /** The contacts permission dialog answered (no = Android's own picker opens). */
    fun contactsPermissionAnswered(granted: Boolean) =
        track("Contacts Permission Answered", mapOf("granted" to granted))

    /**
     * A contact tapped. [source] = in_app (our list) | system (Android's picker);
     * [ok] = it had a 10-digit mobile; [alreadySaved] = that number is already a customer.
     */
    fun customerContactPicked(source: String, ok: Boolean, alreadySaved: Boolean) =
        track("Customer Contact Picked", mapOf("source" to source, "ok" to ok, "already_saved" to alreadySaved))

    /** The contact list left without picking. [contacts] = how many it listed (in_app); [searched] = typed in its search. */
    fun customerContactsClosed(source: String, contacts: Int?, searched: Boolean) =
        track("Customer Contacts Closed", mapOf("source" to source, "contacts" to contacts, "searched" to searched))

    /** "Already saved as …" shown: the number typed/picked is an existing customer. */
    fun customerDuplicateShown(ctx: String) = track("Customer Duplicate Shown", mapOf("ctx" to ctx))

    /**
     * The greyed "Save customer" tapped. [reason] = the first thing missing:
     * name | phone_empty | phone_short | duplicate | address.
     */
    fun customerSaveBlocked(reason: String, ctx: String, nameFilled: Boolean, phoneDigits: Int, addressFilled: Boolean) =
        track(
            "Customer Save Blocked",
            mapOf("reason" to reason, "ctx" to ctx, "name_filled" to nameFilled, "phone_digits" to phoneDigits, "address_filled" to addressFilled),
        )

    /**
     * The customer sheet went away. [how] = saved | used_existing | dismissed
     * (back, swipe, tap outside); [missing] = what still blocked Save when it
     * was dismissed (name | phone_empty | phone_short | duplicate | address | none);
     * [method] = typed | contacts (where the saved number came from).
     */
    fun customerSheetClosed(
        ctx: String, mode: String, trigger: String, how: String, seconds: Long, nameFilled: Boolean, phoneDigits: Int,
        addressShown: Boolean, addressFilled: Boolean, contactsTapped: Boolean, contactPicked: Boolean,
        blockedTaps: Int, missing: String, method: String?,
    ) = track(
        "Customer Sheet Closed",
        mapOf(
            "ctx" to ctx, "mode" to mode, "trigger" to trigger, "how" to how, "seconds_on_screen" to seconds,
            "name_filled" to nameFilled, "phone_digits" to phoneDigits, "address_shown" to addressShown,
            "address_filled" to addressFilled, "contacts_tapped" to contactsTapped, "contact_picked" to contactPicked,
            "blocked_taps" to blockedTaps, "missing" to missing, "method" to method,
        ),
    )

    /**
     * v2's address-only sheet appeared. [trigger] = route (switched to home) |
     * customer_saved | customer_picked (a customer set while home was on) |
     * address_card ("Add"/"Change" next to Address) | save_button (the greyed
     * "Add address first"); [hadAddress] = the customer already has one.
     */
    fun addressSheetOpened(trigger: String, hadAddress: Boolean, guideOn: Boolean) =
        track("Address Sheet Opened", mapOf("trigger" to trigger, "had_address" to hadAddress, "guide_on" to guideOn))

    /** The address sheet went away. [how] = saved | dismissed. */
    fun addressSheetClosed(trigger: String, how: String, seconds: Long, edited: Boolean, filled: Boolean, blockedTaps: Int) =
        track(
            "Address Sheet Closed",
            mapOf("trigger" to trigger, "how" to how, "seconds_on_screen" to seconds, "edited" to edited, "filled" to filled, "blocked_taps" to blockedTaps),
        )

    fun orderSaved(
        orderId: Int, isEdit: Boolean, hasBill: Boolean, pickup: String, delivery: String,
        express: Boolean, discount: Int, fee: Int, pieces: Int, kg: Double,
        servicesCount: Int, total: Int, quickBill: Boolean, ordersCount: Int? = null,
    ) = track(
        "Order Saved",
        mapOf(
            "order_id" to orderId, "is_edit" to isEdit, "has_bill" to hasBill, "pickup" to pickup,
            "delivery" to delivery, "express" to express, "discount" to discount, "fee" to fee,
            "pieces" to pieces, "kg" to kg, "services_count" to servicesCount, "total" to total,
            "quick_bill" to quickBill,
            // New orders only: the shop's orders after this one, deleted ones not
            // counted — CleverTap journeys trigger on it (1st, 2nd, 3rd order).
            "orders_count" to ordersCount,
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

    fun orderStatusChanged(orderId: Int, from: String, to: String) =
        track("Order Status Changed", mapOf("order_id" to orderId, "from" to from, "to" to to))

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

    /**
     * Laundry tags sent to the Bluetooth printer (Android). [mode] = "piece" | "order",
     * [paper] = "58mm" | "80mm", [from] = "bill" | "order_detail" | "menu".
     */
    fun tagsPrinted(orderId: Int, tags: Int, mode: String, paper: String, from: String) =
        track("Tags Printed", mapOf("order_id" to orderId, "tags" to tags, "mode" to mode, "paper" to paper, "from" to from))

    /** [reason] = "connect" | "write" | "bluetooth_off" | "permission" | "no_bluetooth". */
    fun tagsPrintFailed(orderId: Int, reason: String, from: String) =
        track("Tags Print Failed", mapOf("order_id" to orderId, "reason" to reason, "from" to from))

    fun combinedBillSent(customerId: String, orders: Int, total: Int, toPay: Int) =
        track("Combined Bill Sent", mapOf("customer_id" to customerId, "orders" to orders, "total" to total, "to_pay" to toPay, "channel" to "whatsapp"))

    fun summaryShared(period: String) = track("Summary Shared", mapOf("period" to period))

    fun ratesOpened(from: String) = track("Rates Opened", mapOf("from" to from))

    fun serviceAdded(mode: String) = track("Service Added", mapOf("mode" to mode))

    fun serviceEdited(serviceId: String) = track("Service Edited", mapOf("service_id" to serviceId))

    fun serviceDeleted(serviceId: String) = track("Service Deleted", mapOf("service_id" to serviceId))

    fun earningsPeriodChanged(period: String) =
        track("Earnings Period Changed", mapOf("period" to period))

    // ---- the guide (first order → ready → delivered + money → khata → earnings) ----
    // part: 1–5, part_name: new_order | ready | deliver | khata | earnings.

    /** source: auto (a new shop's first Home) | settings (Settings → How to use the app). */
    fun tourStarted(source: String) = track("Tour Started", mapOf("source" to source))

    /** A guide card on screen (or a tip inside a sheet); sound = its voice is on. */
    fun tourStepViewed(step: String, part: Int, partName: String, sound: Boolean) =
        track("Tour Step Viewed", mapOf("step" to step, "part" to part, "part_name" to partName, "sound" to sound))

    fun tourPartCompleted(part: Int, partName: String) =
        track("Tour Part Completed", mapOf("part" to part, "part_name" to partName))

    /** "Later" on the order's card: hidden until the app is opened again. */
    fun tourLater(step: String, part: Int) = track("Tour Later", mapOf("step" to step, "part" to part))

    fun tourSkipped(step: String, part: Int) = track("Tour Skipped", mapOf("step" to step, "part" to part))

    /** The last card's Finish: all five parts seen. */
    fun tourCompleted() = track("Tour Completed")

    /** The guide's sound button: on = turned the voice back on. */
    fun tourSound(on: Boolean, step: String) =
        track(if (on) "Tour Sound On" else "Tour Sound Off", mapOf("step" to step))

    // ---- multi-shop (paid) ----
    /** from: home | settings; has_plan; free = the 2nd shop (no plan needed). Neither = the paywall. */
    fun addShopTapped(from: String, hasPlan: Boolean, free: Boolean) =
        track("Add Shop Tapped", mapOf("from" to from, "has_plan" to hasPlan, "free" to free))

    fun shopAdded(shopCount: Int) = track("Shop Added", mapOf("shop_count" to shopCount))

    fun shopSwitched(shopCount: Int) = track("Shop Switched", mapOf("shop_count" to shopCount))

    /** shop_count: shops left after the delete. */
    fun shopDeleted(shopCount: Int) = track("Shop Deleted", mapOf("shop_count" to shopCount))

    /** Earnings: which shops are counted — this | all | some. */
    fun earningsShopsChanged(selection: String, shops: Int) =
        track("Earnings Shops Changed", mapOf("selection" to selection, "shops" to shops))

    // ---- support ----
    /** method: call | whatsapp; from: settings | tour_end. */
    fun supportContacted(method: String, from: String) =
        track("Support Contacted", mapOf("method" to method, "from" to from))

    fun orderSearchOpened() = track("Order Search Opened")

    // ---- billing / paywall funnel (₹2 trial hard gate) ----
    // The client fires the UI funnel; the backend fires the money-confirmed
    // events (Subscription Activated / Charged / Payment Failed / Halted /
    // Cancelled) from the Razorpay webhook.
    // from: free_orders (the gate after the free orders) | plan_ended | add_shop | settings
    // (Settings → Subscription, or a mylaundry://subscription push) | hard_trial (the paywall
    // test's no-close paywall after login) | first_order (the test's paywall after the first
    // order + guide). order_count: the shop's orders, deleted ones not counted.
    // amount: the default plan's price (paise); trial: the ₹2 trial is offered; video: the intro video is on it.
    fun paywallShown(from: String, defaultPlan: String, orderCount: Int, amount: Int, trial: Boolean, video: Boolean) =
        track(
            "Paywall Shown",
            mapOf(
                "from" to from, "default_plan" to defaultPlan, "order_count" to orderCount,
                "amount" to amount, "trial" to trial, "video" to video,
            ),
        )

    /** Left a shown paywall without paying. how: close_button | back; seconds: since it was shown. */
    fun paywallClosed(from: String, how: String, seconds: Long) =
        track("Paywall Closed", mapOf("from" to from, "how" to how, "seconds_on_screen" to seconds))

    /** The billing check before a paywall failed even after retries (offline); the owner sees "Try again". */
    fun paywallCheckFailed(from: String) = track("Paywall Check Failed", mapOf("from" to from))

    // ---- push ----
    // A push tap's deep link opened a screen (mylaundry://<screen>).
    fun deepLinkOpened(screen: String) = track("Deep Link Opened", mapOf("screen" to screen))

    // Push permission on the login screen (where = "login"). prompted = false:
    // no dialog was needed (Android 12 and older, or already allowed).
    fun notificationPermissionAnswered(granted: Boolean, where: String, prompted: Boolean) =
        track("Notification Permission Answered", mapOf("granted" to granted, "where" to where, "prompted" to prompted))

    // Notifications switched on/off for the app (any Android version, incl. from
    // phone Settings) — fired when the status differs from the last one seen.
    fun notificationsStatusChanged(enabled: Boolean) =
        track("Notifications Status Changed", mapOf("enabled" to enabled))

    // Free orders used up (Android): the notice before the paywall, and its "See plans" tap.
    fun freeOrdersOverShown(orders: Int) = track("Free Orders Over Shown", mapOf("orders" to orders))
    fun freeOrdersOverPlansTapped() = track("Free Orders Over Plans Tapped")


    fun paywallFaqOpened() = track("Paywall FAQ Opened")

    // Paywall intro video (autoplays with sound; muted = the user muted it).
    fun paywallVideoStarted(muted: Boolean) =
        track("Paywall Video Started", mapOf("muted" to muted))

    fun paywallVideoUnmuted() = track("Paywall Video Unmuted")

    fun paywallVideoCompleted() = track("Paywall Video Completed")

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

    fun checkoutFailed(plan: String, reason: String) =
        track("Checkout Failed", mapOf("plan" to plan, "reason" to reason))

    fun subscriptionCancelRequested(plan: String) =
        track("Subscription Cancel Requested", mapOf("plan" to plan))
}
