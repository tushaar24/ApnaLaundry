package com.dailyworks.apnalaundry.ui.screens.paywall

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
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens
import org.koin.androidx.compose.koinViewModel

private const val WAS_MONTHLY = 799
private const val WAS_ANNUAL = 8999

// backend TRIAL_DAYS (laundry-razorpay.js)
private const val TRIAL_DAYS = 1
private val TRIAL_PERIOD = if (TRIAL_DAYS == 1) "1 day" else "$TRIAL_DAYS days"

private data class TrialFeature(val icon: ImageVector, val title: String, val sub: String)

// The paywall lists features with a subtitle each (design 08b).
private val TRIAL_FEATURES = listOf(
    TrialFeature(Icons.AutoMirrored.Outlined.ReceiptLong, "Unlimited orders", "Walk-in, home pickup and delivery"),
    TrialFeature(Icons.AutoMirrored.Outlined.Chat, "Bills on WhatsApp", "Make and send a bill in one tap"),
    TrialFeature(Icons.AutoMirrored.Outlined.MenuBook, "Khata for every customer", "Always know who owes you money"),
    TrialFeature(Icons.Outlined.CheckCircle, "“Clothes ready” message", "Customers get a WhatsApp automatically"),
    TrialFeature(Icons.Outlined.BarChart, "Daily earnings", "Cash and UPI added up for you"),
)

/** "₹4,999" from paise. */
private fun rupees(paise: Int): String {
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

/**
 * The ₹2-trial paywall. [hardGate] is the post-login BillingGate (no close, Back
 * consumed); otherwise it's Settings → Subscription, which shows the active plan
 * (with cancel) or, if there's none, the same trial offer with a close button.
 * [onDone] fires once the subscription is confirmed active.
 */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    onDone: () -> Unit,
    hardGate: Boolean = false,
    vm: PaywallViewModel = koinViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity

    // Hard gate: the back button cannot dismiss the paywall.
    BackHandler(enabled = hardGate) { /* consume — no escape until subscribed */ }

    val annualR = ui.annualAmount / 100
    val monthlyR = ui.monthlyAmount / 100
    val perMonth = ui.annualAmount / 12 / 100
    val saveVsMonthly = (ui.monthlyAmount * 12 - ui.annualAmount) / 100

    Box(
        Modifier
            .fillMaxSize()
            .background(Tokens.Bg)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        when {
            // No success screen (handoff 08b) — the ₹2 only starts the trial, so
            // proceed straight through instead of a summary.
            ui.stage == PaywallStage.DONE -> LaunchedEffect(Unit) { onDone() }

            ui.stage == PaywallStage.WAITING -> WaitingView()

            // Already subscribed (e.g. opened from Settings): show the plan, not
            // a Pay button — Checkout can't re-authorize an active subscription.
            ui.hasActive -> ActiveView(ui = ui, onClose = onClose, onCancel = { vm.cancel() })

            else -> Column(Modifier.fillMaxSize()) {
                // Close — hidden on the hard gate (non-cancellable).
                if (!hardGate) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .padding(start = 8.dp, top = 4.dp)
                            .tap(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", style = fig(22, FontWeight.Normal, Tokens.Muted)) }
                } else {
                    Spacer(Modifier.height(12.dp))
                }

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    // ₹2 trial hero
                    Spacer(Modifier.height(4.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tokens.Blue).padding(20.dp),
                    ) {
                        Text("$TRIAL_DAYS-DAY FULL TRIAL", style = fig(12, FontWeight.Bold, Tokens.BlueBar))
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(rupees(ui.trialAmount), style = bric(52, FontWeight.Bold, Tokens.OnDark))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("That's all you pay today", style = fig(18, FontWeight.Bold, Tokens.OnDark))
                                Text("Every feature unlocked for $TRIAL_PERIOD", style = fig(14, FontWeight.Normal, Tokens.BlueBar))
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Text("EVERYTHING IN THE APP", style = fig(12, FontWeight.Bold, Tokens.Muted))
                    Spacer(Modifier.height(12.dp))
                    TRIAL_FEATURES.forEach { f ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Tokens.BlueLight),
                                contentAlignment = Alignment.Center,
                            ) { Icon(f.icon, null, tint = Tokens.Blue, modifier = Modifier.size(22.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text(f.title, style = fig(16, FontWeight.Bold))
                                Text(f.sub, style = fig(13, FontWeight.Normal, Tokens.Muted))
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Text("YOUR PLAN AFTER ${TRIAL_PERIOD.uppercase()}", style = fig(12, FontWeight.Bold, Tokens.Muted))
                    Spacer(Modifier.height(12.dp))
                    PlanCard(
                        selected = ui.plan == PaywallPlan.ANNUAL,
                        onSelect = { vm.selectPlan(PaywallPlan.ANNUAL) },
                        name = "Yearly",
                        note = "Only ${rupees(perMonth * 100)} a month",
                        price = rupees(annualR * 100),
                        per = "/year",
                        was = rupees(WAS_ANNUAL * 100),
                        badge = "BEST VALUE · SAVE ${rupees(saveVsMonthly * 100)}",
                    )
                    Spacer(Modifier.height(10.dp))
                    PlanCard(
                        selected = ui.plan == PaywallPlan.MONTHLY,
                        onSelect = { vm.selectPlan(PaywallPlan.MONTHLY) },
                        name = "Monthly",
                        note = "Pay every month",
                        price = rupees(monthlyR * 100),
                        per = "/month",
                        was = rupees(WAS_MONTHLY * 100),
                        badge = null,
                    )
                    ui.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = fig(13, FontWeight.SemiBold, Tokens.OrangeText))
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Tokens.Card)
                        .padding(20.dp),
                ) {
                    PrimaryButton(
                        text = if (ui.busy) "Starting…" else "Start trial for ${rupees(ui.trialAmount)}",
                        enabled = !ui.busy,
                        onClick = { vm.pay(activity) },
                    )
                    Spacer(Modifier.height(8.dp))
                    // The chosen plan kicks in after the trial.
                    val amt = if (ui.plan == PaywallPlan.ANNUAL) ui.annualAmount else ui.monthlyAmount
                    val per = if (ui.plan == PaywallPlan.ANNUAL) "year" else "month"
                    Text(
                        "Then ${rupees(amt)}/$per after the $TRIAL_DAYS-day trial · cancel anytime",
                        style = fig(12, FontWeight.Normal, Tokens.Muted),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Tokens.Card)
            .border(2.dp, if (selected) Tokens.Blue else Tokens.CardBorder, RoundedCornerShape(16.dp))
            .tap(onClick = onSelect)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(20.dp).clip(CircleShape)
                    .border(2.dp, if (selected) Tokens.Blue else Tokens.CardBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Tokens.Blue)) }
            Column(Modifier.weight(1f)) {
                Text(name, style = fig(16, FontWeight.Bold))
                Text(note, style = fig(13, FontWeight.Normal, Tokens.Muted))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(price, style = bric(20, FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(was, style = fig(12, FontWeight.Normal, Tokens.Muted).copy(textDecoration = TextDecoration.LineThrough))
                    Text(per, style = fig(12, FontWeight.Normal, Tokens.Muted))
                }
            }
        }
        if (badge != null) {
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Tokens.Orange)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) { Text(badge, style = fig(10, FontWeight.Bold, Tokens.OnDark)) }
        }
    }
}

@Composable
private fun WaitingView() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Tokens.Blue)
        Spacer(Modifier.height(16.dp))
        Text("Confirming your subscription…", style = bric(22, FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Text(
            "We're confirming the UPI AutoPay approval. This updates automatically — it only takes a moment.",
            style = fig(14, FontWeight.Normal, Tokens.Muted),
        )
    }
}

@Composable
private fun ActiveView(ui: PaywallUiState, onClose: () -> Unit, onCancel: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val sub = ui.status?.subscription
    val planName = if (sub?.plan == "annual") "Yearly" else "Monthly"
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(44.dp)
                .padding(start = 8.dp, top = 4.dp)
                .tap(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Text("✕", style = fig(22, FontWeight.Normal, Tokens.Muted)) }
        Column(
            Modifier.weight(1f).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Tokens.Blue),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Check, null, tint = Tokens.OnDark, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("$planName plan is active", style = bric(24, FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            Text("Unlimited orders. Nothing to do here.", style = fig(14, FontWeight.Normal, Tokens.Muted))
            Spacer(Modifier.height(6.dp))
            Text(
                "${rupees(sub?.amount ?: 0)}${if (sub?.plan == "annual") "/year" else "/month"}" +
                    if (sub?.status == "pending") " · payment retrying" else "",
                style = fig(14, FontWeight.SemiBold, Tokens.Muted),
            )
            ui.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = fig(13, FontWeight.SemiBold, Tokens.OrangeText))
            }
            Spacer(Modifier.weight(1f))
            if (confirming) {
                Text("Cancel the plan? New orders stop when it ends.", style = fig(14, FontWeight.SemiBold))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                            .border(1.5.dp, Tokens.Orange, RoundedCornerShape(14.dp))
                            .tap { if (!ui.busy) onCancel() },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (ui.busy) "Cancelling…" else "Yes, cancel", style = fig(15, FontWeight.Bold, Tokens.OrangeText)) }
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(Tokens.Blue)
                            .tap { confirming = false },
                        contentAlignment = Alignment.Center,
                    ) { Text("Keep plan", style = fig(15, FontWeight.Bold, Tokens.OnDark)) }
                }
            } else {
                Text(
                    "Cancel subscription",
                    style = fig(14, FontWeight.Bold, Tokens.Muted),
                    modifier = Modifier.tap { confirming = true }.padding(12.dp),
                )
            }
        }
    }
}
