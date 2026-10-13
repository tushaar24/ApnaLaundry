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
import com.dailyworks.apnalaundry.data.shops.FREE_SHOPS
import com.dailyworks.apnalaundry.data.shops.ShopDirectory
import androidx.compose.runtime.mutableIntStateOf
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens
import org.koin.compose.koinInject

private enum class Access { OPEN, PAYWALL }

/**
 * Wraps the New order screen. Within the free orders, or with a plan, it's
 * just [content]; after that, straight to the paywall ("50 of 50 free orders
 * used", Yearly picked, charged in full today), then [content] once the plan
 * is active. ✕ / Back leave New order.
 *
 * Free orders: 5, or what the server lists for the owner's phone — counted
 * across all their shops (the 2nd shop is free, but doesn't double them),
 * orders not deleted, so deleting one frees a slot. More shops than the free
 * ones (a plan that ended): no new order in any shop until a plan again —
 * every shop stays open to see ("Your plan has ended").
 *
 * Decides from the local DBs + the subscription saved in Prefs — no network
 * wait. The saved one is refreshed in the background (app start, after paying,
 * and here when it says "not subscribed", in case they paid on the website).
 */
@Composable
fun OrderLimitGate(onBack: () -> Unit, content: @Composable () -> Unit) {
    val repo: LaundryRepository = koinInject()
    val billing: BillingRepository = koinInject()
    val shopDir: ShopDirectory = koinInject()
    var access by remember { mutableStateOf<Access?>(null) }
    var made by remember { mutableIntStateOf(0) }
    var limit by remember { mutableIntStateOf(0) }
    var planEnded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        made = repo.liveOrderCountAllShops()
        limit = billing.savedFreeOrders()
        planEnded = shopDir.list().size > FREE_SHOPS
        if (billing.savedActive() || (!planEnded && made < limit)) {
            access = Access.OPEN
            return@LaunchedEffect
        }
        access = Access.PAYWALL
        Analytics.freeOrdersOverShown(made)
        // Paid elsewhere (website, another phone) since the last save? Let them through.
        billing.refreshSaved()
        limit = billing.savedFreeOrders()
        if (billing.savedActive() || (!planEnded && made < limit)) access = Access.OPEN
    }

    when (access) {
        // Local reads — a blink, not a loader.
        null -> Box(Modifier.fillMaxSize().background(Tokens.Bg))
        Access.OPEN -> content()
        Access.PAYWALL -> PaywallScreen(
            onClose = onBack,
            onDone = { access = Access.OPEN },
            from = if (planEnded) "plan_ended" else "free_orders",
            orders = made,
            freeOrders = limit,
        )
    }
}
