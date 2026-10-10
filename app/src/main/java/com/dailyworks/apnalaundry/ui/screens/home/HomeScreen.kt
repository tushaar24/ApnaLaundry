package com.dailyworks.apnalaundry.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.EarningsMath
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.domain.OrderStatus
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.BottomNav
import com.dailyworks.apnalaundry.ui.components.NavTab
import com.dailyworks.apnalaundry.ui.components.OrderCard
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.SectionLabel
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.screens.paywall.ShopLoader
import com.dailyworks.apnalaundry.ui.sheets.ActiveSheet
import com.dailyworks.apnalaundry.ui.sheets.SheetHost
import com.dailyworks.apnalaundry.ui.theme.Tokens

/**
 * Home = the work screen. One rule: an order never leaves this screen until
 * it is delivered (or cancelled). No date strip, no tabs — three stacks by
 * what to do next, late ones on top. Dates are labels on cards, never
 * something to navigate. Delivered and cancelled orders live in History.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(shopVm: ShopViewModel, navigator: AppNavigator) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val hidden by shopVm.hideAmounts.collectAsStateWithLifecycle()
    val loaded by shopVm.loaded.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The database hasn't answered yet: the state is a placeholder, and its zero
    // orders would flash "Your shop is ready!" for a second. Keep the gate's loader.
    if (!loaded) { ShopLoader(); return }

    // Past midnight (app left open overnight): "today" moves to the new day.
    var today by remember { mutableStateOf(AppDate.TODAY) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            if (AppDate.TODAY != today) today = AppDate.TODAY
        }
    }
    // Search (all orders, all dates). Saveable: Back from an order lands on the
    // same results; the bottom nav clears it, so coming back starts empty.
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()
    val typeQuery: (String) -> Unit = { v ->
        if (!searching && v.isNotBlank()) Analytics.orderSearchOpened()
        query = v
    }
    var active by remember { mutableStateOf<ActiveSheet?>(null) }

    val startNewOrder: (String) -> Unit = { from -> navigator.openNewOrder(from = from) }

    LaunchedEffect(Unit) { Analytics.screen("home") }

    fun act(o: Order) {
        when (o.status) {
            OrderStatus.CREATED -> if (o.lines.isEmpty()) active = ActiveSheet.Count(o.id, OrderStatus.RECEIVED) else shopVm.markPickedUp(o.id)
            OrderStatus.RECEIVED -> if (o.lines.isEmpty()) active = ActiveSheet.Count(o.id, OrderStatus.READY) else active = ActiveSheet.Ready(o.id) // asks the delivery date
            OrderStatus.READY -> active = ActiveSheet.Pay(o.id)
            else -> Unit
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        // Header
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.shop.name, style = bric(22, FontWeight.Bold), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.height(44.dp).rounded(999.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(999.dp)).tap { navigator.openRates("home") }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text("₹ Rates", style = fig(14, FontWeight.Bold))
            }
        }

        val noOrders = state.orders.isEmpty()
        if (!noOrders && !searching) {
            DashboardStrip(state, today, hidden, onEye = shopVm::toggleHideAmounts, onOpen = { navigator.openEarnings() })
        }

        // The three stacks. Late first (oldest date on top), then by date; no date = last.
        val cmp = compareBy<Order> { stageDate(it).ifBlank { "9999-99-99" } }.thenByDescending { it.express }.thenBy { it.id }
        val toPickUp = state.orders.filter { it.status == OrderStatus.CREATED }.sortedWith(cmp)
        val inShop = state.orders.filter { it.status == OrderStatus.RECEIVED }.sortedWith(cmp)
        val ready = state.orders.filter { it.status == OrderStatus.READY }.sortedWith(cmp)
        val deliveredToday = state.orders.count { it.status == OrderStatus.DELIVERED && it.doneDate == today }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (noOrders) {
                EmptyHome { startNewOrder("empty_home") }
                return@Box
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 170.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Sticky: stays on screen while scrolling orders.
                stickyHeader(key = "search") {
                    Box(Modifier.fillMaxWidth().background(Tokens.Bg).padding(top = 4.dp, bottom = 2.dp)) {
                        SearchBar(query, typeQuery)
                    }
                }
                if (searching) {
                    val results = searchResults(state, query)
                    item(key = "count") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${results.size} ${if (results.size == 1) "order" else "orders"} · all dates",
                                style = fig(14, color = Tokens.Muted), modifier = Modifier.weight(1f),
                            )
                            Text("Clear search", style = fig(14, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { query = "" }.padding(vertical = 8.dp))
                        }
                    }
                    if (results.isEmpty()) {
                        item(key = "none") {
                            val q = query.trim()
                            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("No order found for “$q”", style = fig(17, FontWeight.Bold), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text("Check the spelling, or take a new order for them.", style = fig(14, color = Tokens.Muted), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Row(
                                    Modifier.padding(top = 4.dp).height(48.dp).rounded(12.dp).background(Tokens.Blue)
                                        .tap { navigator.openNewOrder(from = "home", query = q) }.padding(horizontal = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Filled.Add, null, tint = Tokens.OnDark, modifier = Modifier.size(20.dp))
                                    Text("New order for “$q”", style = fig(15, FontWeight.Bold, Tokens.OnDark), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    items(results) { o -> OrderCard(state, o, o.status == OrderStatus.CREATED, false, { navigator.openOrder(o.id) }, { act(o) }, { active = ActiveSheet.Menu(o.id) }, { dial(context, o, state) }, showDate = true) }
                    return@LazyColumn
                }

                // A stack: header with count, then its cards. Hidden when empty.
                fun stage(title: String, list: List<Order>, pickupSection: Boolean) {
                    if (list.isEmpty()) return
                    item(key = "h-$title") { SectionLabel("$title · ${list.size}", modifier = Modifier.padding(top = 6.dp)) }
                    items(list) { o -> OrderCard(state, o, pickupSection, isLate(o, today), { navigator.openOrder(o.id) }, { act(o) }, { active = ActiveSheet.Menu(o.id) }, { dial(context, o, state) }, showDate = true) }
                }
                stage("To pick up", toPickUp, pickupSection = true)
                stage("In shop", inShop, pickupSection = false)
                stage("Ready to deliver", ready, pickupSection = false)

                if (toPickUp.isEmpty() && inShop.isEmpty() && ready.isEmpty()) {
                    item(key = "done") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("All caught up!", style = fig(17, FontWeight.Bold))
                            Text("Every order is delivered. Take the next one below.", style = fig(14, color = Tokens.Muted), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }

                // Finished work lives in History, out of the way.
                item(key = "history") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).rounded(14.dp).background(Tokens.Card)
                            .border(1.dp, Tokens.CardBorder, RoundedCornerShape(14.dp))
                            .tap { navigator.openHistory() }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (deliveredToday > 0) "Delivered today · $deliveredToday" else "Delivered & cancelled orders",
                            style = fig(15, FontWeight.SemiBold), modifier = Modifier.weight(1f),
                        )
                        Text("History ›", style = fig(14, FontWeight.Bold, Tokens.Blue))
                    }
                }
            }

            // New order FAB
            Row(Modifier.align(Alignment.BottomEnd).padding(16.dp).height(58.dp).rounded(999.dp).background(Tokens.Blue).tap { startNewOrder("home") }.padding(start = 18.dp, end = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Add, null, tint = Tokens.OnDark, modifier = Modifier.size(22.dp))
                Text("New order", style = fig(17, FontWeight.Bold, Tokens.OnDark))
            }
        }

        BottomNav(NavTab.ORDERS) { query = ""; navigator.selectTab(it) }
    }

    SheetHost(active, state, shopVm, navigator, onOpen = { active = it }, onDismiss = { active = null })
}

// ---------- search ----------
/** Search bar above the order cards: 48dp, white, blue border while a search is active. */
@Composable
private fun SearchBar(value: String, onChange: (String) -> Unit) {
    val on = value.isNotBlank()
    Row(
        Modifier.fillMaxWidth().height(48.dp).rounded(14.dp).background(Tokens.Card)
            .border(1.5.dp, if (on) Tokens.Blue else Tokens.FieldBorder, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = if (on) Tokens.Blue else Tokens.Muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("Search name, phone or order no.", style = fig(16, color = Tokens.Faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = fig(16, FontWeight.SemiBold),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Tokens.Blue),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(Modifier.size(36.dp).tap { onChange("") }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(24.dp).rounded(999.dp).background(Tokens.NeutralFill), contentAlignment = Alignment.Center) {
                    Text("✕", style = fig(13, FontWeight.Bold, Tokens.InkSecondary))
                }
            }
        }
    }
}

// ---------- empty state (brand-new shop) ----------
@Composable
private fun EmptyHome(onNewOrder: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().rounded(16.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(84.dp).rounded(999.dp).background(Tokens.BlueLight), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Checkroom, null, tint = Tokens.Blue, modifier = Modifier.size(40.dp))
            }
            Text("Your shop is ready!", style = bric(26, FontWeight.Bold))
            Text(
                "No orders yet. Take your first one — it takes less than a minute.",
                style = fig(15, color = Tokens.Muted), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            PrimaryButton("+ Take your first order", height = 56.dp, onClick = onNewOrder)
        }

        Column(
            Modifier.fillMaxWidth().rounded(16.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionLabel("How it works")
            HowStep(1, "Take the order", "Pick the customer and clothes. The bill is made for you.")
            HowStep(2, "Mark it ready", "The customer gets a WhatsApp that clothes are ready.")
            HowStep(3, "Hand over and collect money", "Cash, UPI or khata — it all adds up in Earnings.")
        }

        Text(
            "Your prices are already set. Change them anytime from ₹ Rates at the top.",
            style = fig(13, color = Tokens.Muted), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun HowStep(n: Int, title: String, desc: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).rounded(999.dp).background(Tokens.Ink), contentAlignment = Alignment.Center) {
            Text("$n", style = fig(14, FontWeight.Bold, Tokens.OnDark))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = fig(16, FontWeight.Bold))
            Text(desc, style = fig(13, color = Tokens.Muted))
        }
    }
}

// ---------- dashboard (today, fixed) ----------
@Composable
private fun DashboardStrip(state: LaundryState, today: String, hidden: Boolean, onEye: () -> Unit, onOpen: () -> Unit) {
    val newOrders = state.orders.count { it.createdOn == today && it.status != OrderStatus.CANCELLED }
    val collected = EarningsMath.collectedOn(today, state.ledger)
    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp).fillMaxWidth().rounded(14.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(14.dp)), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).tap(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("$newOrders", style = fig(20, FontWeight.Bold))
            Text((if (newOrders == 1) "New order " else "New orders ") + "today", style = fig(12, color = Tokens.Muted))
        }
        Column(Modifier.weight(1.25f).tap(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(if (hidden) "₹ ••••" else Money.rupees(collected), style = fig(20, FontWeight.Bold))
            Text("Collected today", style = fig(12, color = Tokens.Muted))
        }
        Box(Modifier.size(48.dp).tap(onClick = onEye), contentAlignment = Alignment.Center) {
            Icon(if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Toggle amounts", tint = Tokens.Muted, modifier = Modifier.size(22.dp))
        }
    }
}

// ---------- pure home logic ----------
/** The date an order is waiting on: pickup until it's with us, delivery after. */
private fun stageDate(o: Order): String =
    if (o.status == OrderStatus.CREATED) o.pickupDate else o.deliveryDate

private fun isLate(o: Order, today: String): Boolean {
    val d = stageDate(o)
    return d.isNotBlank() && d < today
}

private fun rank(s: OrderStatus) = when (s) {
    OrderStatus.CREATED -> 0; OrderStatus.RECEIVED -> 1; OrderStatus.READY -> 2; OrderStatus.DELIVERED -> 3; OrderStatus.CANCELLED -> 4
}

/**
 * Search every order, any date or status: name (any part), phone (3+ digits),
 * order no. (2+ digits, "#" optional). Open orders first, in work order.
 */
private fun searchResults(state: LaundryState, query: String): List<Order> {
    val qs = query.trim().lowercase()
    if (qs.isEmpty()) return emptyList()
    val qd = qs.filter { it.isDigit() }
    val qn = qs.removePrefix("#").trim()
    return state.orders.filter { o ->
        val c = Selectors.customer(state, o.custId)
        c.name.lowercase().contains(qs) || (qd.length >= 3 && c.phone.contains(qd)) ||
            (qd.length >= 2 && (o.id.toString().contains(qn) || o.no().lowercase().contains(qn)))
    }.sortedWith(compareBy<Order> { rank(it.status) }.thenByDescending { it.id })
}

private fun dial(context: android.content.Context, o: Order, state: LaundryState) {
    val c = Selectors.customer(state, o.custId)
    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:+91${c.phone}"))) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(list: List<Order>, block: @Composable (Order) -> Unit) {
    items(count = list.size, key = { list[it].id }) { block(list[it]) }
}
