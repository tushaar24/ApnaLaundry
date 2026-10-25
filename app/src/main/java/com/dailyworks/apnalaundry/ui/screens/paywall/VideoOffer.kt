package com.dailyworks.apnalaundry.ui.screens.paywall

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.view.WindowCompat
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.i18n
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.ui.components.AppBottomSheet
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens
import kotlin.math.roundToInt

// Struck-out price on the Monthly card: the Monthly price before ₹199.
private const val WAS_MONTHLY_PAISE = 49900

private data class OfferFeature(val icon: ImageVector, val label: String)

// Feature row under the video — four icons, two-line labels (English, shown through tr).
private val OFFER_FEATURES = listOf(
    OfferFeature(Icons.AutoMirrored.Outlined.Chat, i18n("Bills on WhatsApp")),
    OfferFeature(Icons.AutoMirrored.Outlined.MenuBook, i18n("Khata for customers")),
    OfferFeature(Icons.AutoMirrored.Outlined.ReceiptLong, i18n("Unlimited orders")),
    OfferFeature(Icons.Outlined.BarChart, i18n("Daily earnings")),
)

// UPI apps that support AutoPay mandates (text chips — no third-party logos).
private val UPI_APPS = listOf("GPay", "PhonePe", "Paytm", "BHIM")

// Dark paywall palette — navy with the brand blue/orange as accents.
internal object Pw {
    val Bg = Brush.verticalGradient(
        0f to Color(0xFF10255C),
        0.42f to Color(0xFF0B1838),
        1f to Color(0xFF08112A),
    )
    val Bar = Color(0xF208112A)
    val Text = Color(0xFFB4C3E9)
    val Accent = Color(0xFF8FB0FF)
    val Eyebrow = Color(0xFFFDBA74)
    val Surface = Color(0x0FFFFFFF)
    val Selected = Color(0xFF5B8CFF)
    val Cta = Brush.horizontalGradient(listOf(Color(0xFF4876FF), Tokens.Blue))
}

/**
 * The paywall new shops get (from 2026-10-14, app 1.0.12+): the dark design with
 * the intro video and the plans the server sets (PaywallConfig, 1.0.13+) at its
 * prices — one plan (no choice) or Yearly and Monthly with its default picked —
 * as a ₹2 trial or paid in full, worded to match; or, from an older server, the
 * Monthly-only ₹199 offer. [tag] is "1 of 1 free orders used" etc.
 * [PaywallScreen] paints [Pw.Bg] behind it (status bar included).
 */
@Composable
internal fun VideoOffer(
    ui: PaywallUiState,
    tag: String?,
    hardGate: Boolean,
    onClose: () -> Unit,
    onPay: () -> Unit,
    onSelectPlan: (PaywallPlan) -> Unit,
) {
    val activity = LocalContext.current as Activity
    var faq by remember { mutableStateOf(false) }
    val yearly = ui.effectivePlan == PaywallPlan.ANNUAL
    val price = rupees(if (yearly) ui.annualAmount else ui.monthlyAmount)
    // Trial (paywall test, hard_trial): ₹2 today (refunded), the plan from [planFrom].
    val trial = ui.hasTrial
    val setup = rupees(ui.trialAmount)
    val days = ui.trialDays
    val planFrom = remember(days) {
        java.time.LocalDate.now().plusDays(days.toLong()).let { "${it.dayOfMonth} ${monthName(it)}" }
    }

    // Light status-bar icons over the navy offer; restore on leave.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val wasLight = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = wasLight }
    }

    Column(Modifier.fillMaxSize()) {
        // Close (hidden on the non-cancellable hard gate) + FAQs.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!hardGate) {
                Box(
                    Modifier.size(44.dp).tap(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", style = fig(22, FontWeight.Normal, Pw.Text)) }
            }
            Spacer(Modifier.weight(1f))
            Text(
                tr("FAQs"),
                style = fig(14, FontWeight.Bold, Pw.Text),
                modifier = Modifier
                    .tap {
                        faq = true
                        Analytics.paywallFaqOpened()
                    }
                    .padding(12.dp),
            )
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            tag?.let {
                Text(
                    it,
                    style = fig(12, FontWeight.Bold, Tokens.OnDark),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Tokens.Orange)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                tr("• YOUR LAUNDRY SHOP APP •"),
                style = fig(12, FontWeight.ExtraBold, Pw.Eyebrow).copy(letterSpacing = 0.16.em),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    !trial -> tr("Take unlimited orders")
                    days == 1 -> tr("Start your 1-day FREE trial")
                    else -> tr("Start your {n}-day FREE trial", "n" to days)
                },
                style = fig(19, FontWeight.Bold, Tokens.OnDark), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    trial && yearly -> tr("{setup} today, refunded instantly · then {price} a year", "setup" to setup, "price" to price)
                    trial -> tr("{setup} today, refunded instantly · then {price} a month", "setup" to setup, "price" to price)
                    yearly -> tr("Keep your shop running on the app for {price} a year", "price" to price)
                    else -> tr("Keep your shop running on the app for {price} a month", "price" to price)
                },
                style = fig(14, FontWeight.Normal, Pw.Text),
                textAlign = TextAlign.Center,
            )

            ui.videoUrl?.let { url ->
                Spacer(Modifier.height(20.dp))
                PaywallVideo(url = url, paused = ui.busy || faq || ui.retrySheet)
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth()) {
                OFFER_FEATURES.forEach { f ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Pw.Surface),
                            contentAlignment = Alignment.Center,
                        ) { Icon(f.icon, null, tint = Pw.Accent, modifier = Modifier.size(22.dp)) }
                        Spacer(Modifier.height(8.dp))
                        Text(tr(f.label), style = fig(12, FontWeight.SemiBold, Tokens.OnDark), textAlign = TextAlign.Center)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            val plans = ui.offeredPlans
            Text(if (plans.size > 1) tr("Pick your plan") else tr("Your plan"), style = fig(16, FontWeight.Bold, Tokens.OnDark))
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                plans.forEach { p ->
                    val selected = p == ui.effectivePlan
                    val onClick = if (plans.size > 1) ({ onSelectPlan(p) }) else null
                    if (p == PaywallPlan.ANNUAL) {
                        // ₹4,999 a year ≈ ₹417 a month — the per-month figure makes the yearly price easy to judge.
                        val perMonth = rupees((ui.annualAmount / 1200.0).roundToInt() * 100)
                        val save = ui.monthlyAmount * 12 - ui.annualAmount
                        PlanCard(
                            title = tr("Yearly"), sub = tr("Pay once a year · just {amount} a month", "amount" to perMonth),
                            price = rupees(ui.annualAmount), slash = tr("/year"), was = null,
                            // Next to Monthly, say what Yearly saves (only when it does).
                            badge = if (PaywallPlan.MONTHLY in plans && save > 0) tr("SAVE {amount}", "amount" to rupees(save)) else null,
                            selected = selected, onClick = onClick,
                        )
                    } else {
                        PlanCard(
                            title = tr("Monthly"), sub = tr("Pay every month"),
                            price = rupees(ui.monthlyAmount), slash = tr("/month"),
                            // Strike out the old price only while this one is lower.
                            was = if (ui.monthlyAmount < WAS_MONTHLY_PAISE) rupees(WAS_MONTHLY_PAISE) else null,
                            badge = null, selected = selected, onClick = onClick,
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(tr("Works with your UPI app"), style = fig(16, FontWeight.Bold, Tokens.OnDark))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UPI_APPS.forEach { app ->
                    Text(
                        app,
                        style = fig(13, FontWeight.Bold, Tokens.Ink),
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Tokens.OnDark)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(tr("and more"), style = fig(13, FontWeight.Normal, Pw.Text))

            ui.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(tr(it), style = fig(13, FontWeight.SemiBold, Pw.Eyebrow), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
        }

        // Fixed bottom bar so the video never pushes the button off-screen.
        Column(
            Modifier.fillMaxWidth().background(Pw.Bar).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when {
                    trial && yearly -> tr("{setup} today (refunded) · {price}/year from {date} by UPI AutoPay · cancel anytime before",
                        "setup" to setup, "price" to price, "date" to planFrom)
                    trial -> tr("{setup} today (refunded) · {price}/month from {date} by UPI AutoPay · cancel anytime before",
                        "setup" to setup, "price" to price, "date" to planFrom)
                    yearly -> tr("Pays {price} today, then every year by UPI AutoPay · cancel anytime", "price" to price)
                    else -> tr("Pays {price} today, then every month by UPI AutoPay · cancel anytime", "price" to price)
                },
                style = fig(12, FontWeight.Normal, Pw.Text),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                    .background(Pw.Cta, alpha = if (ui.busy) 0.6f else 1f)
                    .tap(enabled = !ui.busy, onClick = onPay)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        ui.busy -> tr("Starting…")
                        trial -> tr("START FREE TRIAL")
                        else -> tr("PAY {price} & CONTINUE", "price" to price)
                    },
                    style = fig(16, FontWeight.ExtraBold, Tokens.OnDark),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (faq) {
        AppBottomSheet(title = tr("Questions"), onDismiss = { faq = false }) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (trial) {
                    FaqItem(
                        tr("Why do I pay {amount} today?", "amount" to setup),
                        if (days == 1) tr("It sets up UPI AutoPay for your plan, and it is refunded to you. You get the full app free for 1 day.")
                        else tr("It sets up UPI AutoPay for your plan, and it is refunded to you. You get the full app free for {n} days.", "n" to days),
                    )
                    FaqItem(
                        tr("When is my plan charged?"),
                        when {
                            days == 1 && yearly -> tr("On {date}, when the free day ends: {price}, then every year, by UPI AutoPay.", "date" to planFrom, "price" to price)
                            days == 1 -> tr("On {date}, when the free day ends: {price}, then every month, by UPI AutoPay.", "date" to planFrom, "price" to price)
                            yearly -> tr("On {date}, when the free days end: {price}, then every year, by UPI AutoPay.", "date" to planFrom, "price" to price)
                            else -> tr("On {date}, when the free days end: {price}, then every month, by UPI AutoPay.", "date" to planFrom, "price" to price)
                        },
                    )
                } else {
                    FaqItem(
                        tr("When is my plan charged?"),
                        if (yearly) tr("Today, then {price} every year by UPI AutoPay.", "price" to price)
                        else tr("Today, then {price} every month by UPI AutoPay.", "price" to price),
                    )
                }
                FaqItem(
                    tr("Can I cancel?"),
                    if (yearly) tr("Yes, anytime from Settings. You keep the app until the year you paid for ends.")
                    else tr("Yes, anytime from Settings. You keep the app until the month you paid for ends."),
                )
                FaqItem(tr("Which UPI apps work?"), tr("Any UPI app that supports AutoPay — GPay, PhonePe, Paytm, BHIM and more."))
            }
        }
    }
}

/** A plan on the dark paywall; [onClick] null = the only plan (always selected). */
@Composable
private fun PlanCard(
    title: String, sub: String, price: String, slash: String, was: String?,
    badge: String?, selected: Boolean, onClick: (() -> Unit)?,
) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) Pw.Selected.copy(alpha = 0.15f) else Pw.Surface)
            .border(2.dp, if (selected) Pw.Selected else Pw.Surface, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) Pw.Accent else Pw.Text, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Pw.Accent)) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = fig(16, FontWeight.Bold, Tokens.OnDark))
                    badge?.let {
                        Text(
                            it, style = fig(11, FontWeight.ExtraBold, Tokens.OnDark), maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Tokens.Orange).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(sub, style = fig(13, FontWeight.Normal, Pw.Text))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(price, style = bric(20, FontWeight.Bold, Tokens.OnDark), maxLines = 1, softWrap = false)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    was?.let { Text(it, style = fig(12, FontWeight.Normal, Pw.Text).copy(textDecoration = TextDecoration.LineThrough), maxLines = 1, softWrap = false) }
                    Text(slash, style = fig(12, FontWeight.Normal, Pw.Text), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun FaqItem(q: String, a: String) {
    Column {
        Text(q, style = fig(15, FontWeight.Bold))
        Spacer(Modifier.height(4.dp))
        Text(a, style = fig(14, FontWeight.Normal, Tokens.Muted))
    }
}
