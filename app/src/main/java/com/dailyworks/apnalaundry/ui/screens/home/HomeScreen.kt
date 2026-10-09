package com.dailyworks.apnalaundry.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.EarningsMath
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.domain.OrderStatus
import com.dailyworks.apnalaundry.domain.Route
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.BottomNav
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.NavTab
import com.dailyworks.apnalaundry.ui.components.OrderCard
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.SectionLabel
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.sheets.ActiveSheet
import com.dailyworks.apnalaundry.ui.sheets.SheetHost
import com.dailyworks.apnalaundry.ui.theme.Tokens
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.ui.platform.LocalDensity
import com.dailyworks.apnalaundry.ui.components.showDatePicker

@Composable
fun HomeScreen(shopVm: ShopViewModel, navigator: AppNavigator) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val hidden by shopVm.hideAmounts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var tabPickup by remember { mutableStateOf(true) }
    var selDate by remember { mutableStateOf(AppDate.TODAY) }
    var filter by remember { mutableStateOf("all") }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf<ActiveSheet?>(null) }

    val startNewOrder: () -> Unit = { navigator.openNewOrder(from = "home") }

    LaunchedEffect(Unit) { Analytics.screen("home") }

    fun act(o: Order) {
        when (o.status) {
            OrderStatus.CREATED -> if (o.lines.isEmpty()) active = ActiveSheet.Count(o.id, OrderStatus.RECEIVED) else shopVm.markPickedUp(o.id)
            OrderStatus.RECEIVED -> if (o.lines.isEmpty()) active = ActiveSheet.Count(o.id, OrderStatus.READY) else shopVm.markReady(o.id)
            OrderStatus.READY -> active = ActiveSheet.Pay(o.id)
            else -> Unit
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        // Header
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!searching) {
                Text(state.shop.name, style = bric(22, FontWeight.Bold), modifier = Modifier.weight(1f))
                Box(Modifier.height(44.dp).rounded(999.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(999.dp)).tap { navigator.openRates("home") }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text("₹ Rates", style = fig(14, FontWeight.Bold))
                }
                RoundIcon(Icons.Filled.Search, "Search") { searching = true; query = ""; Analytics.orderSearchOpened() }
            } else {
                FieldBox(query, { query = it }, placeholder = "Name, phone or order no.", modifier = Modifier.weight(1f), height = 50.dp, borderColor = Tokens.Blue, borderWidth = 2.dp, leading = { Icon(Icons.Filled.Search, null, tint = Tokens.Muted, modifier = Modifier.size(20.dp)) })
                Text("Close", style = fig(15, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { searching = false; query = "" })
            }
        }

        val noOrders = state.orders.isEmpty()

        if (searching) {
            // handled below
        } else if (noOrders) {
            // brand-new shop: the empty state below carries the whole screen
        } else {
            DashboardStrip(state, selDate, hidden, onEye = shopVm::toggleHideAmounts, onOpen = { navigator.openEarnings() })
            TabRow(state, tabPickup, selDate) { tabPickup = it; filter = "all" }
            DateStrip(state, tabPickup, selDate) { selDate = it }
            FilterTabs(state, tabPickup, selDate, filter) { filter = it }
        }

        if (searching) {
            val note = if (query.isBlank()) "Type a name, phone number or order number." else run {
                val n = searchResults(state, query).size
                "$n result${if (n == 1) "" else "s"} from all dates"
            }
            Text(note, style = fig(14, color = Tokens.Muted), modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (noOrders && !searching) {
                EmptyHome { startNewOrder() }
                return@Box
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 170.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (searching) {
                    val results = searchResults(state, query)
                    items(results) { o -> OrderCard(state, o, tabPickup, false, { navigator.openOrder(o.id) }, { act(o) }, { active = ActiveSheet.Menu(o.id) }, { dial(context, o, state) }) }
                    return@LazyColumn
                }

                // Pending group (today only)
                if (selDate == AppDate.TODAY) {
                    val pending = pendingOf(state, tabPickup).filter { pass(state, it, tabPickup, filter) }.sortedWith(byTodo())
                    if (pending.isNotEmpty()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
                                Box(Modifier.size(7.dp).rounded(999.dp).background(Tokens.Orange))
                                SectionLabel((if (tabPickup) "Pending from earlier · " else "Late or no delivery date · ") + pending.size, color = Tokens.OrangeText)
                            }
                        }
                        items(pending) { o -> OrderCard(state, o, tabPickup, true, { navigator.openOrder(o.id) }, { act(o) }, { active = ActiveSheet.Menu(o.id) }, { dial(context, o, state) }) }
                    }
                }

                val list = dayOrders(state, selDate, tabPickup).filter { pass(state, it, tabPickup, filter) }.sortedWith(byTodo())
                item { SectionLabel(AppDate.long(selDate), modifier = Modifier.padding(top = 6.dp)) }
                if (list.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (tabPickup) "No pickups" else "No deliveries", style = fig(17, FontWeight.Bold))
                            Text("Nothing here for ${AppDate.long(selDate)}.", style = fig(14, color = Tokens.Muted))
                        }
                    }
                }
                items(list) { o -> OrderCard(state, o, tabPickup, false, { navigator.openOrder(o.id) }, { act(o) }, { active = ActiveSheet.Menu(o.id) }, { dial(context, o, state) }) }
            }

            // New order FAB
            if (!searching) {
                Row(Modifier.align(Alignment.BottomEnd).padding(16.dp).height(58.dp).rounded(999.dp).background(Tokens.Blue).tap { startNewOrder() }.padding(start = 18.dp, end = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Add, null, tint = Tokens.OnDark, modifier = Modifier.size(22.dp))
                    Text("New order", style = fig(17, FontWeight.Bold, Tokens.OnDark))
                }
            }
        }

        BottomNav(NavTab.ORDERS, navigator::selectTab)
    }

    SheetHost(active, state, shopVm, navigator, onOpen = { active = it }, onDismiss = { active = null })
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

// ---------- header pieces ----------
@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).rounded(999.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(999.dp)).tap(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, cd, tint = Tokens.Ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DashboardStrip(state: LaundryState, selDate: String, hidden: Boolean, onEye: () -> Unit, onOpen: () -> Unit) {
    val newOrders = state.orders.count { it.createdOn == selDate && it.status != OrderStatus.CANCELLED }
    val collected = EarningsMath.collectedOn(selDate, state.ledger)
    val rel = AppDate.rel(selDate)
    val whenText = if (rel != null) rel.lowercase() else "on ${AppDate.plain(selDate)}"
    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp).fillMaxWidth().rounded(14.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(14.dp)), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).tap(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("$newOrders", style = fig(20, FontWeight.Bold))
            Text((if (newOrders == 1) "New order " else "New orders ") + whenText, style = fig(12, color = Tokens.Muted))
        }
        Column(Modifier.weight(1.25f).tap(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(if (hidden) "₹ ••••" else Money.rupees(collected), style = fig(20, FontWeight.Bold))
            Text("Collected $whenText", style = fig(12, color = Tokens.Muted))
        }
        Box(Modifier.size(48.dp).tap(onClick = onEye), contentAlignment = Alignment.Center) {
            Icon(if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Toggle amounts", tint = Tokens.Muted, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun TabRow(state: LaundryState, pickup: Boolean, selDate: String, onSelect: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 0.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(true, false).forEach { pk ->
            val on = pickup == pk
            val n = tabTodo(state, pk, selDate)
            Row(
                Modifier.weight(1f).height(50.dp).rounded(14.dp).background(if (on) Tokens.Ink else Tokens.Card).border(1.5.dp, if (on) Tokens.Ink else Tokens.CardBorder, RoundedCornerShape(14.dp)).tap { onSelect(pk) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(if (pk) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, null, tint = if (on) Tokens.OnDark else Tokens.Ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (pk) "Pickups" else "Deliveries", style = fig(16, FontWeight.Bold, if (on) Tokens.OnDark else Tokens.Ink))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.height(22.dp).rounded(999.dp).background(if (n > 0) Tokens.Orange else if (on) Tokens.DarkChipTrack else Tokens.NeutralFill).padding(horizontal = 6.dp).width(if (n > 9) 26.dp else 22.dp), contentAlignment = Alignment.Center) {
                    Text("$n", style = fig(12, FontWeight.Bold, if (n > 0) Tokens.OnDark else if (on) Tokens.OnDark else Tokens.InkSecondary))
                }
            }
        }
    }
}

/** Days the strip shows around today (orders can be dated in the past). */
private const val STRIP_BACK = 60
private const val STRIP_AHEAD = 30

@Composable
private fun DateStrip(state: LaundryState, pickup: Boolean, selDate: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val today = AppDate.TODAY
    // Stretch the range when the date picker jumps outside it.
    val back = maxOf(STRIP_BACK, -AppDate.daysBetween(today, selDate))
    val ahead = maxOf(STRIP_AHEAD, AppDate.daysBetween(today, selDate))
    val days = (-back..ahead).toList()
    val list = rememberLazyListState()
    val density = LocalDensity.current
    var first by remember { mutableStateOf(true) }
    // Keep the selected day in view: centred on open, smoothly after a pick.
    LaunchedEffect(selDate, back) {
        val idx = days.indexOf(AppDate.daysBetween(today, selDate)).coerceAtLeast(0)
        val half = list.layoutInfo.viewportSize.width / 2
        val cell = with(density) { 52.dp.roundToPx() }
        val offset = -(half - cell / 2).coerceAtLeast(0)
        if (first) list.scrollToItem(idx, offset) else list.animateScrollToItem(idx, offset)
        first = false
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            state = list,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(days, key = { it }) { i ->
                val iso = AppDate.add(today, i)
                val on = selDate == iso
                val n = state.orders.count { onDate(it, iso, pickup) && it.status != OrderStatus.CANCELLED }
                Column(
                    Modifier.width(50.dp).height(58.dp).rounded(12.dp).background(if (on) Tokens.Ink else Color.Transparent).border(1.5.dp, if (on) Tokens.Ink else Color.Transparent, RoundedCornerShape(12.dp)).tap { onPick(iso) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text(if (i == 0) "Today" else AppDate.dayName(iso), style = fig(11, FontWeight.Bold, if (on) Tokens.OnDarkMuted else if (i == 0) Tokens.Blue else Tokens.Muted))
                    Text("${AppDate.dayOfMonth(iso)}", style = fig(17, FontWeight.Bold, if (on) Tokens.OnDark else Tokens.Ink))
                    Text(if (n > 0) "$n" else "", style = fig(11, FontWeight.Bold, if (on) Tokens.BlueBar else Tokens.Blue))
                }
            }
        }
        // Jump to any date (past orders included).
        Box(
            Modifier.padding(end = 8.dp).size(44.dp).rounded(12.dp).tap { showDatePicker(context, selDate, null, onPick) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.CalendarMonth, "Pick a date", tint = Tokens.Blue, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun FilterTabs(state: LaundryState, pickup: Boolean, selDate: String, filter: String, onSelect: (String) -> Unit) {
    val defs = if (pickup) listOf("all" to "All", "created" to "To pick up", "received" to "Received", "cancelled" to "Cancelled")
    else listOf("all" to "All", "notready" to "Not ready", "ready" to "Ready", "delivered" to "Delivered")
    val day = dayOrders(state, selDate, pickup)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        defs.forEach { (key, label) ->
            val on = filter == key
            val n = if (key == "all") day.count { it.status != OrderStatus.CANCELLED } else day.count { groupOf(it, pickup) == key }
            Column(Modifier.tap { onSelect(key) }.height(44.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(label, style = fig(14, FontWeight.Bold, if (on) Tokens.Ink else Tokens.Muted))
                    Text("$n", style = fig(14, FontWeight.SemiBold, Tokens.Faint))
                }
                Box(Modifier.padding(top = 4.dp).height(2.5.dp).width(if (on) 40.dp else 0.dp).background(Tokens.Ink))
            }
        }
    }
}

// ---------- pure home logic (ported from prototype vHome) ----------
private fun onDate(o: Order, iso: String, pickup: Boolean): Boolean =
    if (pickup) o.pickupDate == iso else (o.deliveryDate == iso && o.status != OrderStatus.CANCELLED)

private fun isTodo(o: Order, pickup: Boolean): Boolean =
    if (pickup) o.status == OrderStatus.CREATED else o.status in listOf(OrderStatus.CREATED, OrderStatus.RECEIVED, OrderStatus.READY)

private fun groupOf(o: Order, pickup: Boolean): String =
    if (pickup) when (o.status) { OrderStatus.CREATED -> "created"; OrderStatus.CANCELLED -> "cancelled"; else -> "received" }
    else if (o.status == OrderStatus.CREATED || o.status == OrderStatus.RECEIVED) "notready" else o.status.name.lowercase()

private fun rank(s: OrderStatus) = when (s) {
    OrderStatus.CREATED -> 0; OrderStatus.RECEIVED -> 1; OrderStatus.READY -> 2; OrderStatus.DELIVERED -> 3; OrderStatus.CANCELLED -> 4
}

private fun byTodo(): Comparator<Order> = compareBy<Order> { rank(it.status) }.thenByDescending { it.express }.thenBy { it.id }

private fun pendingOf(state: LaundryState, pickup: Boolean): List<Order> = state.orders.filter { o ->
    if (pickup) o.status == OrderStatus.CREATED && o.pickupDate < AppDate.TODAY
    else o.status in listOf(OrderStatus.CREATED, OrderStatus.RECEIVED, OrderStatus.READY) && ((o.deliveryDate.isNotBlank() && o.deliveryDate < AppDate.TODAY) || o.deliveryDate.isBlank())
}

private fun dayOrders(state: LaundryState, iso: String, pickup: Boolean): List<Order> =
    state.orders.filter { onDate(it, iso, pickup) }

private fun pass(state: LaundryState, o: Order, pickup: Boolean, filter: String): Boolean =
    if (filter == "all") o.status != OrderStatus.CANCELLED else groupOf(o, pickup) == filter

private fun tabTodo(state: LaundryState, pickup: Boolean, selDate: String): Int {
    val base = state.orders.count { onDate(it, selDate, pickup) && isTodo(it, pickup) }
    return base + if (selDate == AppDate.TODAY) pendingOf(state, pickup).size else 0
}

private fun searchResults(state: LaundryState, query: String): List<Order> {
    val qs = query.trim().lowercase()
    if (qs.isEmpty()) return emptyList()
    val qd = qs.filter { it.isDigit() }
    return state.orders.filter { o ->
        val c = Selectors.customer(state, o.custId)
        c.name.lowercase().contains(qs) || (qd.length >= 3 && c.phone.contains(qd)) || o.id.toString().contains(qs)
    }.sortedWith(byTodo())
}

private fun dial(context: android.content.Context, o: Order, state: LaundryState) {
    val c = Selectors.customer(state, o.custId)
    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:+91${c.phone}"))) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(list: List<Order>, block: @Composable (Order) -> Unit) {
    items(count = list.size, key = { list[it].id }) { block(list[it]) }
}
