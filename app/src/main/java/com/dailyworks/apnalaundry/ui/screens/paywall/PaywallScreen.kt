package com.dailyworks.apnalaundry.ui.screens.paywall

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.core.view.WindowCompat
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.ui.components.AppBottomSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.data.LaundryRepository
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val WAS_MONTHLY = 799
private const val WAS_ANNUAL = 8999

// backend TRIAL_DAYS (laundry-razorpay.js) — only the retry sheet's trial wording uses it.
private const val TRIAL_DAYS = 7

/** "₹4,999" from paise. */
internal fun rupees(paise: Int): String {
    val whole = paise / 100
    val s = whole.toString()
    val sb = StringBuilder()
    val n = s.length
    for (i in 0 until n) {
        if (i != 0) {
            val fromRight = n - i
            if (fromRight % 2 == 1 && fromRight != 1 && fromRight < n) sb.append(',')
        }
        sb.append(s[i])
    }
    return "₹$sb"
}

/** "13 Oct 2027": the end of the period paid for today (a year or a month from now). */
private fun validTill(annual: Boolean): String {
    val d = LocalDate.now().let { if (annual) it.plusYears(1) else it.plusMonths(1) }
    return d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
}

/** What the paywall says, by why it opened. */
private data class OfferCopy(val tag: String?, val title: String, val body: String)

private fun offerCopy(from: String, orders: Int?, freeOrders: Int?): OfferCopy = when (from) {
    "free_orders" -> OfferCopy(
        if (orders != null && freeOrders != null) "${minOf(orders, freeOrders)} of $freeOrders free orders used" else "Free orders used",
        "Your free orders are finished",
        "Pick a plan to take new orders. Your old orders and khata are safe.",
    )
    "plan_ended" -> OfferCopy(
        "Plan ended",
        "Your plan has ended",
        "Pick a plan to take new orders in your shops. Your old orders and khata are safe.",
    )
    "add_shop" -> OfferCopy(
        null,
        "Add more shops",
        "Pick a plan to add more shops — each shop keeps its own customers, khata and bills.",
    )
    else -> OfferCopy(null, "Pick your plan", "Unlimited orders and bills on WhatsApp for your shop.")
}

/** The ✓ list: what the plan gives (shops first when that's why they're here). */
private fun offerFeatures(from: String): List<String> = buildList {
    if (from == "add_shop") add("Unlimited shops")
    add("Unlimited orders")
    add("Bills on WhatsApp")
    add("Khata for every customer")
    add("Daily earnings — cash and UPI")
    if (from != "add_shop") add("Unlimited shops")
}

/**
 * The paywall: Yearly (picked by default) or Monthly, charged in full today —
 * Android has no trial. When the server says `monthlyOnly` (new shops from
 * 2026-10-14 on 1.0.12+) it's [VideoOffer] instead: Monthly only, ₹199, video. Opens straight from New order when the free orders run
 * out (OrderLimitGate: [from] free_orders, with [orders] / [freeOrders] for the
 * "50 of 50 free orders used" tag, or plan_ended), from adding a shop past the
 * free ones (add_shop), and from Settings → Subscription, which shows the
 * active plan (with cancel) instead when there is one. [hardGate] hides the
 * close button and eats Back. [onDone] fires once the subscription is active.
 */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    onDone: () -> Unit,
    from: String,
    hardGate: Boolean = false,
    orders: Int? = null,
    freeOrders: Int? = null,
    vm: PaywallViewModel = koinViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity

    // Hard gate: the back button cannot dismiss the paywall. Otherwise Back = the ✕
    // (what Back did anyway on a screen of its own; on the first-order paywall,
    // drawn over the app, it closes the paywall instead of the screen behind it).
    BackHandler {
        if (hardGate) return@BackHandler // consume — no escape until subscribed
        vm.trackClosed(from, "back")
        onClose()
    }
    // The ✕ on the plans ("Paywall Closed"; the active-plan view's ✕ isn't one).
    val closeOffer = {
        vm.trackClosed(from, "close_button")
        onClose()
    }

    val perMonth = ui.annualAmount / 12
    val saveVsMonthly = ui.monthlyAmount * 12 - ui.annualAmount
    val offer = ui.stage != PaywallStage.DONE && ui.stage != PaywallStage.WAITING && !ui.hasActive

    // "Paywall Shown" once the plans are on screen: status known, no active plan.
    val laundry: LaundryRepository = koinInject()
    val known = ui.status != null
    LaunchedEffect(offer, known) {
        if (offer && known) vm.trackShown(from, laundry.liveOrderCount())
    }

    // A one-plan offer (Monthly ₹199 or Yearly ₹4,999) is the dark video design (VideoOffer), navy behind the status bar too.
    val videoOffer = offer && ui.singlePlan
    Box(
        Modifier.fillMaxSize()
            .then(if (videoOffer) Modifier.background(Pw.Bg) else Modifier.background(Tokens.Bg))
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        when {
            // No success screen — proceed straight through.
            ui.stage == PaywallStage.DONE -> LaunchedEffect(Unit) { onDone() }

            // Opened from Settings: the plan isn't known yet (this screen fetches it
            // fresh). Wait for it — falling through would flash the offer at
            // someone who is already subscribed.
            !hardGate && ui.status == null -> if (ui.failed) CheckFailed(onRetry = vm::refresh) else ShopLoader()

            ui.stage == PaywallStage.WAITING -> WaitingView(onBack = { vm.backToPlans() })

            // Already subscribed (e.g. opened from Settings): show the plan, not
            // a Pay button — Checkout can't re-authorize an active subscription.
            // A cancelled plan still running shows here too (until when, no Cancel).
            ui.hasActive || ui.inGrace -> ActiveView(ui = ui, onClose = onClose, onCancel = { vm.cancel() })

            // New shops (server: monthlyOnly / yearlyOnly): one plan, with the intro video.
            ui.singlePlan -> VideoOffer(
                ui = ui,
                tag = offerCopy(from, orders, freeOrders).tag,
                hardGate = hardGate,
                onClose = closeOffer,
                onPay = { vm.pay(activity) },
            )

            else -> Column(Modifier.fillMaxSize()) {
                val copy = offerCopy(from, orders, freeOrders)
                val annual = ui.plan == PaywallPlan.ANNUAL
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                ) {
                    // ✕ (hidden on the non-cancellable hard gate)
                    if (!hardGate) {
                        Box(
                            Modifier.padding(top = 6.dp).offset(x = (-12).dp).size(44.dp).tap(onClick = closeOffer),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Close, "Close", tint = Tokens.Ink, modifier = Modifier.size(24.dp)) }
                    } else {
                        Spacer(Modifier.height(20.dp))
                    }

                    copy.tag?.let {
                        Text(
                            it,
                            style = fig(12, FontWeight.Bold, Tokens.OrangeDeep),
                            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50))
                                .background(Tokens.OrangeLight).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(copy.title, style = bric(28, FontWeight.Bold))
                    Spacer(Modifier.height(8.dp))
                    Text(copy.body, style = fig(15, FontWeight.Normal, Tokens.InkSecondary))

                    Spacer(Modifier.height(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        offerFeatures(from).forEach { f ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(Tokens.BlueLight),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Outlined.Check, null, tint = Tokens.Blue, modifier = Modifier.size(16.dp)) }
                                Text(f, style = fig(15, FontWeight.SemiBold))
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    PlanCard(
                        selected = annual,
                        onSelect = { vm.selectPlan(PaywallPlan.ANNUAL) },
                        name = "Yearly",
                        note = "Only ${rupees(perMonth)} a month",
                        price = rupees(ui.annualAmount),
                        per = "/year",
                        was = rupees(WAS_ANNUAL * 100),
                        badge = "BEST VALUE · SAVE ${rupees(saveVsMonthly)}",
                    )
                    Spacer(Modifier.height(12.dp))
                    PlanCard(
                        selected = !annual,
                        onSelect = { vm.selectPlan(PaywallPlan.MONTHLY) },
                        name = "Monthly",
                        note = "Pay every month",
                        price = rupees(ui.monthlyAmount),
                        per = "/month",
                        was = rupees(WAS_MONTHLY * 100),
                        badge = null,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (annual) "You save ${rupees(saveVsMonthly)} vs paying monthly (${rupees(ui.monthlyAmount * 12)} a year)"
                        else "Pick Yearly and save ${rupees(saveVsMonthly)} a year",
                        style = fig(13, FontWeight.SemiBold, Tokens.BlueText),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ui.error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, style = fig(13, FontWeight.SemiBold, Tokens.OrangeText), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // Fixed bottom bar so the plans never push the button off-screen.
                Column(
                    Modifier.fillMaxWidth().background(Tokens.Card)
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                            .background(Tokens.Blue.copy(alpha = if (ui.busy) 0.6f else 1f))
                            .tap(enabled = !ui.busy) { vm.pay(activity) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when {
                                ui.busy -> "Starting…"
                                annual -> "Pay ${rupees(ui.annualAmount)} for 1 year"
                                else -> "Pay ${rupees(ui.monthlyAmount)} for 1 month"
                            },
                            style = fig(16, FontWeight.Bold, Tokens.OnDark),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Valid till ${validTill(annual)} · UPI, card or net banking",
                        style = fig(12, FontWeight.Normal, Tokens.Muted),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (ui.retrySheet && ui.stage == PaywallStage.PLANS && !ui.hasActive) {
        RetrySheet(
            plan = ui.effectivePlan,
            trialAmount = ui.trialAmount,
            hasTrial = ui.hasTrial,
            monthlyAmount = ui.monthlyAmount,
            busy = ui.busy,
            onRetry = { chosen -> vm.retry(activity, chosen) },
            onDismiss = { vm.dismissRetry() },
        )
    }
}

/**
 * Shown when Razorpay Checkout comes back unpaid (failed, cancelled or Back).
 * After a failed Yearly attempt it recommends the smaller Monthly plan
 * (mirror of the web paywall's retry sheet).
 */
@Composable
private fun RetrySheet(
    plan: PaywallPlan, trialAmount: Int, hasTrial: Boolean, monthlyAmount: Int, busy: Boolean,
    onRetry: (PaywallPlan) -> Unit, onDismiss: () -> Unit,
) {
    val trial = rupees(trialAmount)
    AppBottomSheet(
        title = if (hasTrial) "Your $TRIAL_DAYS free days are waiting" else "Your payment didn't go through",
        onDismiss = onDismiss,
        leading = {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Tokens.OrangeLight),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.WarningAmber, null, tint = Tokens.Orange, modifier = Modifier.size(28.dp)) }
        },
    ) {
        Column {
            Text(
                if (hasTrial) "The $trial didn't go through, so nothing was charged. Try once more — the $trial is refunded instantly and the full app opens right away."
                else "Nothing was charged. Try once more and the full app opens right away.",
                style = fig(15, FontWeight.Normal, Tokens.Muted),
            )
            if (plan == PaywallPlan.ANNUAL) {
                // A failed Yearly attempt: recommend the smaller Monthly plan.
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tokens.BlueLight)
                        .border(2.dp, Tokens.Blue, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("RECOMMENDED", style = fig(11, FontWeight.Bold, Tokens.Blue))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        Text("Try the Monthly plan", style = fig(16, FontWeight.Bold, Tokens.BlueText), modifier = Modifier.weight(1f))
                        Text("${rupees(monthlyAmount)}/month", style = bric(18, FontWeight.Bold, Tokens.BlueText), maxLines = 1, softWrap = false)
                    }
                    Text(
                        if (hasTrial) "Same $TRIAL_DAYS free days, $trial refunded instantly — then a smaller monthly payment."
                        else "A smaller payment today, then every month.",
                        style = fig(13, color = Tokens.BlueText),
                    )
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = "Switch to Monthly", enabled = !busy) { onRetry(PaywallPlan.MONTHLY) }
                Box(
                    Modifier.fillMaxWidth().height(48.dp).tap(enabled = !busy) { onRetry(PaywallPlan.ANNUAL) },
                    contentAlignment = Alignment.Center,
                ) { Text("Try Yearly again", style = fig(14, FontWeight.Bold, Tokens.Muted)) }
            } else {
                Spacer(Modifier.height(20.dp))
                PrimaryButton(text = "Try again", enabled = !busy) { onRetry(plan) }
            }
        }
    }
}

@Composable
private fun PlanCard(
    selected: Boolean,
    onSelect: () -> Unit,
    name: String,
    note: String,
    price: String,
    per: String,
    was: String,
    badge: String?,
) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .padding(top = if (badge != null) 11.dp else 0.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) Tokens.BlueLight else Tokens.Card)
                .border(if (selected) 2.dp else 1.5.dp, if (selected) Tokens.Blue else Tokens.CardBorder, RoundedCornerShape(16.dp))
                .tap(onClick = onSelect)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .border(2.dp, if (selected) Tokens.Blue else Tokens.FieldBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(Tokens.Blue)) }
            Column(Modifier.weight(1f)) {
                Text(name, style = fig(18, FontWeight.Bold))
                Text(note, style = fig(13, FontWeight.SemiBold, if (selected) Tokens.BlueText else Tokens.Muted))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(price, style = bric(24, FontWeight.Bold), maxLines = 1, softWrap = false)
                Row {
                    Text(was, style = fig(12, FontWeight.Normal, Tokens.Muted).copy(textDecoration = TextDecoration.LineThrough), maxLines = 1, softWrap = false)
                    Text(" $per", style = fig(12, FontWeight.Normal, Tokens.Muted), maxLines = 1, softWrap = false)
                }
            }
        }
        if (badge != null) {
            Text(
                badge,
                style = fig(11, FontWeight.ExtraBold, Tokens.OnDark),
                modifier = Modifier.padding(start = 14.dp).clip(RoundedCornerShape(50))
                    .background(Tokens.Orange).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun WaitingView(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Tokens.Blue)
        Spacer(Modifier.height(16.dp))
        Text("Confirming your subscription…", style = bric(22, FontWeight.Bold), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "We're confirming the UPI AutoPay approval. This updates automatically — it only takes a moment.",
            style = fig(14, FontWeight.Normal, Tokens.Muted),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text("Waiting for confirmation…", style = fig(13, FontWeight.Normal, Tokens.Faint))
        Spacer(Modifier.height(8.dp))
        Text(
            "Back to plans",
            style = fig(14, FontWeight.Bold, Tokens.Blue),
            modifier = Modifier.tap(onClick = onBack).padding(horizontal = 12.dp, vertical = 14.dp),
        )
    }
}

/**
 * The day a cancelled plan stops working: a ₹2 trial cancelled before its first
 * real charge runs to that charge date; a paid plan to its period end.
 */
internal fun accessUntilIso(sub: com.dailyworks.apnalaundry.data.billing.BillingSubscriptionDto?): String? {
    return sub?.accessUntil
}

/** "2026-10-16T…" -> "16 Oct 2026" (blank when unparseable). */
internal fun fmtChargeDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val date = runCatching { java.time.OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate() }
        .recoverCatching { java.time.LocalDate.parse(iso.take(10)) }
        .getOrNull() ?: return ""
    val mon = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[date.monthValue - 1]
    return "${date.dayOfMonth} $mon ${date.year}"
}

@Composable
private fun SummaryRow(k: String, v: String, last: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(k, style = fig(14, FontWeight.Normal, Tokens.Muted))
        Text(v, style = fig(14, FontWeight.Bold))
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
}

@Composable
private fun ActiveView(ui: PaywallUiState, onClose: () -> Unit, onCancel: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val sub = ui.status?.subscription
    val planName = if (sub?.plan == "annual") "Yearly" else "Monthly"
    // Cancelled, still inside the period it paid for: say until when; no Cancel.
    val cancelled = sub?.status == "cancelled"
    val until = if (cancelled) fmtChargeDate(accessUntilIso(sub)) else ""
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .padding(start = 4.dp, top = 4.dp)
                .size(48.dp)
                .tap(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Tokens.Ink, modifier = Modifier.size(24.dp)) }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(if (cancelled) Tokens.NeutralFill else Tokens.Blue),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Check, null, tint = if (cancelled) Tokens.InkSecondary else Tokens.OnDark, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.height(16.dp))
            Text(if (cancelled) "$planName plan is cancelled" else "$planName plan is active", style = bric(24, FontWeight.Bold), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                if (cancelled) "It still works" + (if (until.isNotEmpty()) " till $until" else " till the end of the period you paid for") + ". You won't be charged again."
                else "Unlimited orders. Nothing to do here.",
                style = fig(14, FontWeight.Normal, Tokens.Muted),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tokens.Card)
                    .border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)),
            ) {
                val next = fmtChargeDate(sub?.chargeAt)
                SummaryRow("Plan", planName)
                SummaryRow("Amount", "${rupees(sub?.amount ?: 0)}${if (sub?.plan == "annual") "/year" else "/month"}")
                if (cancelled) { if (until.isNotEmpty()) SummaryRow("Works till", until) }
                else if (next.isNotEmpty()) SummaryRow("Next charge", next)
                SummaryRow("Status", if (cancelled) "Cancelled" else if (sub?.status == "pending") "Payment retrying" else "Active", last = true)
            }
            if ((sub?.trialAmount ?: 0) > 0) {
                Spacer(Modifier.height(8.dp))
                Text("Started with the ₹2 trial.", style = fig(12, FontWeight.Normal, Tokens.Muted))
            }
            ui.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = fig(13, FontWeight.SemiBold, Tokens.OrangeText), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            if (cancelled) {
                Text("After that, the app asks you to pick a plan again.", style = fig(13, FontWeight.Normal, Tokens.Muted), textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
            } else if (confirming) {
                Text("Cancel the plan? New orders stop when it ends.", style = fig(14, FontWeight.SemiBold), textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                            .border(1.5.dp, Tokens.Orange, RoundedCornerShape(14.dp))
                            .tap { if (!ui.busy) onCancel() }.padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (ui.busy) "Cancelling…" else "Yes, cancel", style = fig(15, FontWeight.Bold, Tokens.OrangeText), textAlign = TextAlign.Center) }
                    Box(
                        Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(Tokens.Blue)
                            .tap { confirming = false }.padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Keep plan", style = fig(15, FontWeight.Bold, Tokens.OnDark), textAlign = TextAlign.Center) }
                }
            } else {
                Text(
                    "Cancel subscription",
                    style = fig(14, FontWeight.Bold, Tokens.Muted),
                    modifier = Modifier.tap { confirming = true }.padding(horizontal = 12.dp, vertical = 14.dp),
                )
            }
        }
    }
}
