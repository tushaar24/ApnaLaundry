package com.dailyworks.apnalaundry.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.domain.OrderStatus
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.OrderCard
import com.dailyworks.apnalaundry.ui.components.SectionLabel
import com.dailyworks.apnalaundry.ui.components.TopBar
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.sheets.ActiveSheet
import com.dailyworks.apnalaundry.ui.sheets.SheetHost
import com.dailyworks.apnalaundry.ui.theme.Tokens

/**
 * Finished work: delivered orders by day (newest first), cancelled ones at
 * the end. Home only shows open orders — this is where the rest lives.
 * Everything here still opens, and the ⋯ menu (Change status included) works,
 * so a wrong "delivered" can be brought back from here too.
 */
@Composable
fun HistoryScreen(shopVm: ShopViewModel, navigator: AppNavigator) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var active by remember { mutableStateOf<ActiveSheet?>(null) }
    LaunchedEffect(Unit) { Analytics.screen("history") }

    val delivered = state.orders.filter { it.status == OrderStatus.DELIVERED }
        .groupBy { it.doneDate.ifBlank { it.deliveryDate }.ifBlank { it.pickupDate } }
        .toList().sortedByDescending { it.first }
    val cancelled = state.orders.filter { it.status == OrderStatus.CANCELLED }.sortedByDescending { it.id }

    Box(Modifier.fillMaxSize().background(Tokens.Bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            TopBar(title = "History", onBack = { navigator.back() })
            if (delivered.isEmpty() && cancelled.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nothing here yet", style = fig(17, FontWeight.Bold))
                    Text("Delivered and cancelled orders will show up here.", style = fig(14, color = Tokens.Muted))
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    delivered.forEach { (date, list) ->
                        item(key = "d-$date") { SectionLabel("${AppDate.long(date)} · ${list.size}", modifier = Modifier.padding(top = 6.dp)) }
                        items(list.sortedByDescending { it.id }) { o -> card(state, o, navigator, context, onMenu = { active = ActiveSheet.Menu(o.id) }) }
                    }
                    if (cancelled.isNotEmpty()) {
                        item(key = "c-head") { SectionLabel("Cancelled · ${cancelled.size}", modifier = Modifier.padding(top = 6.dp)) }
                        items(cancelled) { o -> card(state, o, navigator, context, onMenu = { active = ActiveSheet.Menu(o.id) }) }
                    }
                }
            }
        }
    }

    SheetHost(active, state, shopVm, navigator, onOpen = { active = it }, onDismiss = { active = null })
}

@Composable
private fun card(state: LaundryState, o: Order, navigator: AppNavigator, context: android.content.Context, onMenu: () -> Unit) {
    OrderCard(
        state, o, pickupTab = false, late = false,
        onOpen = { navigator.openOrder(o.id) }, onAct = {}, onMore = onMenu,
        onCall = {
            val c = Selectors.customer(state, o.custId)
            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:+91${c.phone}"))) }
        },
        showDate = true,
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(list: List<Order>, block: @Composable (Order) -> Unit) {
    items(count = list.size, key = { list[it].id }) { block(list[it]) }
}
