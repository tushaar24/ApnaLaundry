package com.dailyworks.apnalaundry.ui.screens.paywall

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Celebration
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.data.LaundryRepository
import com.dailyworks.apnalaundry.data.billing.BillingRepository
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens
import org.koin.compose.koinInject

private enum class Access { OPEN, FREE_OVER }

/**
 * Wraps the New order screen. Within the free orders, or with a plan, it's
 * just [content]. After that: "Your free orders are over" → the paywall
 * (Yearly picked, charged in full today) → [content] once the plan is active.
 * Back / ✕ leave New order.
 *
 * Decides from the local DB + the subscription saved in Prefs — no network
 * wait. The saved one is refreshed in the background (app start, after paying,
 * and here when it says "not subscribed", in case they paid on the website).
 */
@Composable
fun OrderLimitGate(onBack: () -> Unit, content: @Composable () -> Unit) {
    val repo: LaundryRepository = koinInject()
    val billing: BillingRepository = koinInject()
    val shopDir: com.dailyworks.apnalaundry.data.shops.ShopDirectory = koinInject()
    // Several shops are a paid feature: once the plan ends, no new order in
    // any shop (the shops and their data stay open to see). The free orders
    // are only for a one-shop owner.
    var planEnded by remember { mutableStateOf(false) }
    var access by remember { mutableStateOf<Access?>(null) }
    var plans by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Free orders: 5, or more for shops the server lists (saved from billing
        // status). Never shown in the app — the owner only learns of it when it
        // runs out. Counts orders not deleted, so deleting one frees a slot.
        val made = repo.liveOrderCount()
        val manyShops = shopDir.list().size > 1
        if (billing.savedActive() || (!manyShops && made < billing.savedFreeOrders())) {
            access = Access.OPEN
            return@LaunchedEffect
        }
        access = Access.FREE_OVER
        planEnded = manyShops
        Analytics.freeOrdersOverShown(made)
        // Paid elsewhere (website, another phone) since the last save? Let them through.
        billing.refreshSaved()
        if ((billing.savedActive() || (!manyShops && made < billing.savedFreeOrders())) && !plans) access = Access.OPEN
    }

    when (access) {
        // Two local reads — a blink, not a loader.
        null -> Box(Modifier.fillMaxSize().background(Tokens.Bg))
        Access.OPEN -> content()
        Access.FREE_OVER -> if (plans) {
            PaywallScreen(onClose = onBack, onDone = { access = Access.OPEN }, from = "free_orders")
        } else {
            FreeOrdersOver(
                planEnded = planEnded,
                onSeePlans = {
                    Analytics.freeOrdersOverPlansTapped()
                    plans = true
                },
                onBack = onBack,
            )
        }
    }
}

/** The notice before the paywall: short, plain words, one big button. */
@Composable
private fun FreeOrdersOver(planEnded: Boolean = false, onSeePlans: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars)) {
        Box(Modifier.padding(start = 4.dp, top = 4.dp).size(48.dp).rounded(999.dp).tap(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Tokens.Ink, modifier = Modifier.size(24.dp))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(96.dp).rounded(999.dp).background(Tokens.OrangeLight), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Celebration, null, tint = Tokens.Orange, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(if (planEnded) "Your plan has ended" else "Your free orders are over", style = bric(26, FontWeight.Bold), textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                if (planEnded) "You have more than one shop. To take new orders in any of them, choose a plan again. You can still open every shop and see its orders, khata and earnings."
                else "Good start! To keep taking orders and sending bills on WhatsApp, choose a plan.",
                style = fig(16, color = Tokens.Muted), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().rounded(14.dp).background(Tokens.GreenLight).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, null, tint = Tokens.Green, modifier = Modifier.size(22.dp))
                Text("Your customers, orders and khata are all safe.", style = fig(15, FontWeight.SemiBold, Tokens.GreenText))
            }
        }
        Column(
            Modifier.fillMaxWidth().background(Tokens.Card).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrimaryButton("See plans", height = 56.dp, onClick = onSeePlans)
            Text(
                "Not now",
                style = fig(15, FontWeight.Bold, Tokens.Muted),
                modifier = Modifier.padding(top = 4.dp).tap(onClick = onBack).padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}
