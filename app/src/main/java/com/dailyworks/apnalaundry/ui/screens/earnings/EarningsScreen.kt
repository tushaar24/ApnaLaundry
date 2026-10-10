package com.dailyworks.apnalaundry.ui.screens.earnings

import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.EarningsMath
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LedgerEntry
import com.dailyworks.apnalaundry.domain.LedgerKind
import com.dailyworks.apnalaundry.domain.PayMethod
import com.dailyworks.apnalaundry.domain.PayTag
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppCard
import com.dailyworks.apnalaundry.ui.components.BottomNav
import com.dailyworks.apnalaundry.ui.components.NavTab
import com.dailyworks.apnalaundry.ui.components.PillChip
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.components.showDatePicker
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.sheets.ActiveSheet
import com.dailyworks.apnalaundry.ui.sheets.SheetHost
import com.dailyworks.apnalaundry.ui.theme.Tokens
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import kotlin.math.max

@Composable
fun EarningsScreen(shopVm: ShopViewModel, navigator: AppNavigator) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val hidden by shopVm.hideAmounts.collectAsStateWithLifecycle()
    var period by remember { mutableStateOf("today") }
    var payFilter by remember { mutableStateOf("all") }
    var showAllOrders by remember { mutableStateOf(false) }
    // Custom range: last 7 days until the owner picks their own.
    var customFrom by remember { mutableStateOf(AppDate.add(AppDate.TODAY, -6)) }
    var customTo by remember { mutableStateOf(AppDate.TODAY) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var active by remember { mutableStateOf<ActiveSheet?>(null) }

    LaunchedEffect(Unit) { Analytics.screen("earnings") }

    val today = AppDate.TODAY
    // Monday-start week and the 1st of this month, from today (like the web).
    val weekStart = java.time.LocalDate.parse(today).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }.toString()
    val monthStart = today.substring(0, 8) + "01"
    val inRange: (String) -> Boolean = when (period) {
        "week" -> { iso -> iso in weekStart..today }
        "month" -> { iso -> iso in monthStart..today }
        "all" -> { _ -> true }
        "custom" -> { iso -> iso in customFrom..customTo }
        else -> { iso -> iso == today }
    }
    val e = EarningsMath.compute(state, inRange)
    fun m(n: Int): String = if (hidden) "₹ ••••" else Money.rupees(n)

    val periodLabel = when (period) {
        "week" -> "${AppDate.plain(weekStart)} – ${AppDate.plain(today)}"
        "month" -> "1 – ${AppDate.dayOfMonth(today)} ${AppDate.plain(today).split(" ").last()}"
        "all" -> "All time"
        "custom" -> if (customFrom == customTo) AppDate.plain(customFrom) else "${AppDate.plain(customFrom)} – ${AppDate.plain(customTo)}"
        else -> AppDate.plain(today)
    }
    val periodTitle = when (period) {
        "week" -> "This week"; "month" -> "This month"; "all" -> "All time"; "custom" -> periodLabel; else -> "Today"
    }

    // Orders in the period by their own date (same as Home's Orders / Sales
    // tiles); live orders only — a cancelled one is listed but never counted.
    val periodOrders = state.orders.filter { inRange(it.pickupDate) }
        .sortedWith(compareByDescending<com.dailyworks.apnalaundry.domain.Order> { it.pickupDate }.thenByDescending { it.id })
    val counted = periodOrders.filter { it.status != com.dailyworks.apnalaundry.domain.OrderStatus.CANCELLED }
    val sales = counted.sumOf { LaundryMath.amtOf(it) }

    val shareText = buildString {
        append("${state.shop.name} — $periodLabel\n")
        append("Money received: ₹${Money.grouping(e.received.toLong())}\n")
        append("  Cash ₹${Money.grouping(e.cash.toLong())} · UPI ₹${Money.grouping(e.upi.toLong())}\n")
        append("Orders delivered: ${e.ordersDelivered} (work ₹${Money.grouping(e.work.toLong())})\n")
        append("Baaki added: ₹${Money.grouping(e.baakiAdded.toLong())} · Old baaki collected: ₹${Money.grouping(e.oldIn.toLong())}\n")
        append("Total baaki in market: ₹${Money.grouping(e.baakiMarket.toLong())}")
    }

    Column(
        Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // header
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Earnings", style = bric(22, FontWeight.Bold), modifier = Modifier.weight(1f))
            IconChip(if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Toggle amounts") { shopVm.toggleHideAmounts() }
            Spacer(Modifier.width(8.dp))
            IconChip(Icons.Outlined.Share, "Share") { Analytics.summaryShared(period); active = ActiveSheet.Share(shareText) }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.orders.isEmpty() && state.ledger.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(84.dp).rounded(999.dp).background(Tokens.NeutralFill), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.BarChart, null, tint = Tokens.InkSecondary, modifier = Modifier.size(40.dp))
                    }
                    Text("No earnings yet", style = bric(22, FontWeight.Bold))
                    Text(
                        "Take and deliver your first order — the money you receive shows up here.",
                        style = fig(14, color = Tokens.Muted), textAlign = TextAlign.Center,
                    )
                }
                return@Column
            }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("today" to "Today", "week" to "This week", "month" to "This month", "all" to "All time", "custom" to "Custom").forEach { (v, label) ->
                    PillChip(label, period == v) { period = v; payFilter = "all"; showAllOrders = false; Analytics.earningsPeriodChanged(v) }
                }
            }
            if (period == "custom") {
                // From / To: any dates; To never before From.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateBox("From", customFrom, Modifier.weight(1f)) {
                        showDatePicker(context, customFrom, null) { d -> customFrom = d; if (customTo < d) customTo = d; showAllOrders = false }
                    }
                    DateBox("To", customTo, Modifier.weight(1f)) {
                        showDatePicker(context, customTo, customFrom) { d -> customTo = d; showAllOrders = false }
                    }
                }
            }

            // Sales — same numbers as Home's tiles
            Column(
                Modifier.fillMaxWidth().rounded(18.dp).background(Tokens.Blue).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Sales · $periodTitle", style = fig(13, FontWeight.SemiBold, Tokens.OnDark.copy(alpha = 0.8f)))
                Text(m(sales), style = bric(34, FontWeight.Bold, Tokens.OnDark))
                Text("${counted.size} ${if (counted.size == 1) "order" else "orders"} · total of their bills", style = fig(13, FontWeight.SemiBold, Tokens.OnDark.copy(alpha = 0.8f)))
            }

            // Every order in the period
            Text("Orders · ${periodOrders.size}", style = fig(15, FontWeight.Bold))
            AppCard {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    if (periodOrders.isEmpty()) {
                        Text("No orders in this period.", style = fig(14, color = Tokens.Muted), modifier = Modifier.padding(vertical = 16.dp))
                    }
                    val list = if (showAllOrders) periodOrders else periodOrders.take(5)
                    list.forEachIndexed { i, o ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
                        OrderRow(state, o, hidden) { navigator.openOrder(o.id, "earnings") }
                    }
                    if (periodOrders.size > 5) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
                        Text(
                            if (showAllOrders) "Show less" else "Show all ${periodOrders.size} orders",
                            style = fig(14, FontWeight.Bold, Tokens.Blue),
                            modifier = Modifier.fillMaxWidth().tap { showAllOrders = !showAllOrders }.padding(vertical = 14.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Money received
            AppCard(bg = Tokens.Ink, borderColor = Tokens.Ink) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Money received · $periodTitle", style = fig(13, FontWeight.SemiBold, Tokens.OnDarkMuted))
                    Text(m(e.received), style = bric(34, FontWeight.Bold, Tokens.OnDark))
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MoneySplit("Cash", m(e.cash), "Should be in your drawer", Modifier.weight(1f))
                        MoneySplit("UPI", m(e.upi), "In your bank account", Modifier.weight(1f))
                    }
                }
            }

            // How money came in
            AppCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("How the money came in", style = fig(15, FontWeight.Bold))
                    Spacer(Modifier.height(4.dp))
                    Line("Work done", m(e.work), null)
                    Line("Went to khata", m(e.baakiAdded), "−")
                    Line("Old baaki collected", m(e.oldIn), "+")
                    Line("Advance taken", m(e.advIn), "+")
                    Line(if (e.prepaid >= 0) "Paid before delivery" else "Paid on earlier days for these orders", m(e.prepaid), if (e.prepaid >= 0) "+" else "−")
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider).padding(vertical = 6.dp))
                    Line("Money received", m(e.received), null, bold = true)
                }
            }

            // Work done by service
            if (e.byService.isNotEmpty()) {
                AppCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Work done by service", style = fig(15, FontWeight.Bold))
                        val maxAmt = max(1, e.byService.maxOf { it.amount })
                        e.byService.forEach { s ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(s.label, style = fig(14, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                                    Text(m(s.amount) + if (!hidden) " · ${s.amount * 100 / max(1, e.work)}%" else "", style = fig(13, FontWeight.SemiBold, Tokens.Muted))
                                }
                                Box(Modifier.fillMaxWidth().height(8.dp).rounded(999.dp).background(Tokens.Divider)) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth((s.amount.toFloat() / maxAmt).coerceIn(0.03f, 1f))
                                            .height(8.dp).rounded(999.dp)
                                            .background(if (s.isExtra) Tokens.ServiceBarGrey else Tokens.Blue),
                                    )
                                }
                            }
                        }
                        if (e.discounts > 0) Text("− ${m(e.discounts)} discounts given", style = fig(13, FontWeight.SemiBold, Tokens.OrangeText))
                    }
                }
            }

            // stats
            AppCard {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Stat("Orders", e.ordersDelivered.toString(), Modifier.weight(1f))
                    Stat("Pieces", e.pieces.toString(), Modifier.weight(1f))
                    Stat("Weight", "${Selectors.trimKg(e.kg)} kg", Modifier.weight(1f))
                }
            }

            // baaki in market
            AppCard(borderColor = Tokens.OrangeBorder, bg = Tokens.OrangeLight) {
                Row(Modifier.fillMaxWidth().tap { navigator.openCustomers("baaki") }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Baaki in market ${m(e.baakiMarket)}", style = fig(16, FontWeight.Bold, Tokens.OrangeDeep))
                        Text("${e.baakiCustomers} ${if (e.baakiCustomers == 1) "customer still has to pay" else "customers still have to pay"}", style = fig(13, color = Tokens.OrangeText))
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Tokens.OrangeText, modifier = Modifier.size(24.dp))
                }
            }

            // payments received
            val pays = state.ledger
                .filter { it.kind == LedgerKind.GOT && inRange(it.date) }
                .sortedWith(compareByDescending<LedgerEntry> { it.date }.thenByDescending { it.ts })
            val shown = pays.filter { payFilter == "all" || it.method.name.equals(payFilter, true) }
            Text("Payments received", style = fig(15, FontWeight.Bold))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "All", "cash" to "Cash", "upi" to "UPI").forEach { (v, label) ->
                    val count = if (v == "all") pays.size else pays.count { it.method.name.equals(v, true) }
                    PillChip("$label $count", payFilter == v) { payFilter = v }
                }
            }
            AppCard {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    if (shown.isEmpty()) {
                        Text("No payments in this period.", style = fig(14, color = Tokens.Muted), modifier = Modifier.padding(vertical = 16.dp))
                    }
                    shown.forEachIndexed { i, entry ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
                        PaymentRow(state, entry, hidden) { navigator.openCustomer(entry.custId, "earnings") }
                    }
                }
            }
        }

        BottomNav(current = NavTab.EARNINGS, onSelect = navigator::selectTab)
    }

    SheetHost(active, state, shopVm, navigator, onOpen = { active = it }, onDismiss = { active = null })
}

@Composable
private fun IconChip(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).tap(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, cd, tint = Tokens.InkSecondary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun MoneySplit(label: String, value: String, note: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = fig(12, FontWeight.SemiBold, Tokens.OnDarkMuted))
        Text(value, style = fig(18, FontWeight.Bold, Tokens.OnDark))
        Text(note, style = fig(11, color = Tokens.OnDarkFaint))
    }
}

@Composable
private fun Line(label: String, value: String, sign: String?, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text((if (sign != null) "$sign " else "") + label, style = fig(if (bold) 16 else 14, if (bold) FontWeight.Bold else FontWeight.Normal, if (bold) Tokens.Ink else Tokens.InkSecondary))
        Text(value, style = fig(if (bold) 17 else 14, FontWeight.Bold, if (sign == "−") Tokens.OrangeText else Tokens.Ink))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = bric(22, FontWeight.Bold))
        Text(label, style = fig(12, color = Tokens.Muted))
    }
}

@Composable
private fun PaymentRow(state: com.dailyworks.apnalaundry.domain.LaundryState, e: LedgerEntry, hidden: Boolean, onClick: () -> Unit) {
    val c = Selectors.customer(state, e.custId)
    val what = when (e.tag) {
        PayTag.PRE -> "Paid before delivery · #${e.ref}"
        PayTag.DELIVER -> {
            val o = Selectors.order(state, e.ref ?: -1)
            val khata = if (o != null) LaundryMath.amtOf(o) - o.paid else 0
            "Order #${e.ref} delivered" + if (khata > 0) " · ${if (hidden) "₹ ••••" else Money.rupees(khata)} to khata" else ""
        }
        PayTag.RECEIVE -> when {
            e.toOld > 0 && e.toAdv > 0 -> "Old baaki + advance"
            e.toOld > 0 -> "Old baaki"
            else -> "Advance"
        }
        else -> "Payment"
    }
    Row(Modifier.fillMaxWidth().tap(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).rounded(999.dp).background(if (e.method == PayMethod.CASH) Tokens.NeutralFill else Tokens.BlueLight), contentAlignment = Alignment.Center) {
            Text(if (e.method == PayMethod.CASH) "₹" else "U", style = fig(14, FontWeight.Bold, if (e.method == PayMethod.CASH) Tokens.InkSecondary else Tokens.BlueText))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = fig(15, FontWeight.Bold), maxLines = 1)
            Text(what + (if (e.time.isNotBlank()) " · ${e.time}" else ""), style = fig(12, color = Tokens.Muted), maxLines = 1)
        }
        Text(if (hidden) "₹ ••••" else Money.rupees(e.amt), style = fig(15, FontWeight.Bold))
    }
}

/** One order in the Earnings list: name, number · date, amount and status. */
@Composable
private fun OrderRow(state: com.dailyworks.apnalaundry.domain.LaundryState, o: com.dailyworks.apnalaundry.domain.Order, hidden: Boolean, onClick: () -> Unit) {
    val c = Selectors.customer(state, o.custId)
    val (label, bg, fg) = statusChip(o.status)
    Row(Modifier.fillMaxWidth().tap(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(c.name, style = fig(15, FontWeight.Bold), maxLines = 1)
            Text("#${o.no()} · ${AppDate.plain(o.pickupDate)}", style = fig(12, color = Tokens.Muted), maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when {
                    o.lines.isEmpty() -> "No bill yet"
                    hidden -> "₹ ••••"
                    else -> Money.rupees(LaundryMath.amtOf(o))
                },
                style = fig(if (o.lines.isEmpty()) 12 else 15, if (o.lines.isEmpty()) FontWeight.SemiBold else FontWeight.Bold, if (o.lines.isEmpty()) Tokens.Muted else Tokens.Ink),
            )
            Box(Modifier.rounded(999.dp).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(label, style = fig(11, FontWeight.Bold, fg))
            }
        }
    }
}

private fun statusChip(s: com.dailyworks.apnalaundry.domain.OrderStatus): Triple<String, Color, Color> = when (s) {
    com.dailyworks.apnalaundry.domain.OrderStatus.CREATED -> Triple("To pick up", Tokens.OrangeLight, Tokens.OrangeText)
    com.dailyworks.apnalaundry.domain.OrderStatus.RECEIVED -> Triple("Received", Tokens.NeutralFill, Tokens.InkSecondary)
    com.dailyworks.apnalaundry.domain.OrderStatus.READY -> Triple("Ready", Tokens.BlueLight, Tokens.BlueText)
    com.dailyworks.apnalaundry.domain.OrderStatus.DELIVERED -> Triple("Delivered", Tokens.NeutralFill, Tokens.InkSecondary)
    com.dailyworks.apnalaundry.domain.OrderStatus.CANCELLED -> Triple("Cancelled", Tokens.NeutralFill, Tokens.Muted)
}

/** A From / To box for the custom range; tap opens the date picker. */
@Composable
private fun DateBox(label: String, iso: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(52.dp).rounded(12.dp).background(Tokens.Card)
            .border(1.5.dp, Tokens.FieldBorder, RoundedCornerShape(12.dp)).tap(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.CalendarMonth, null, tint = Tokens.Blue, modifier = Modifier.size(20.dp))
        Column {
            Text(label, style = fig(11, FontWeight.SemiBold, Tokens.Muted))
            Text(AppDate.plain(iso), style = fig(14, FontWeight.Bold), maxLines = 1)
        }
    }
}
