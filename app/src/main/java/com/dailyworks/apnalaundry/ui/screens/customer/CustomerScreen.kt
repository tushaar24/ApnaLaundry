package com.dailyworks.apnalaundry.ui.screens.customer

import androidx.compose.foundation.layout.Spacer
import com.dailyworks.apnalaundry.ui.components.FitText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FileCopy
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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.domain.CombinedBill
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LedgerKind
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.domain.OrderStatus
import com.dailyworks.apnalaundry.domain.PayMethod
import com.dailyworks.apnalaundry.domain.PayTag
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppCard
import com.dailyworks.apnalaundry.ui.components.OutlineButton
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.Segmented
import com.dailyworks.apnalaundry.ui.components.StatusPill
import com.dailyworks.apnalaundry.ui.components.TopBar
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.screens.bill.sendReminderOnWhatsApp
import com.dailyworks.apnalaundry.ui.sheets.ActiveSheet
import com.dailyworks.apnalaundry.ui.sheets.SheetHost
import com.dailyworks.apnalaundry.ui.theme.Tokens
import kotlin.math.min

private data class KhataRow(
    val title: String, val sub: String, val amount: String, val amtColor: Color,
    val after: String, val isAdd: Boolean, val icBg: Color, val icFg: Color,
)

@Composable
fun CustomerScreen(shopVm: ShopViewModel, navigator: AppNavigator, custId: String, from: String) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableStateOf("khata") }
    var active by remember { mutableStateOf<ActiveSheet?>(null) }

    LaunchedEffect(Unit) { Analytics.screen("customer_khata") }

    val c = Selectors.customer(state, custId)
    val nm = Selectors.firstName(c.name)
    val bal = Selectors.balance(state, custId)
    val mine = state.orders.filter { it.custId == custId }.sortedByDescending { it.id }
    val inProgress = mine.filter { it.status in listOf(OrderStatus.CREATED, OrderStatus.RECEIVED, OrderStatus.READY) }

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars)) {
        TopBar(title = "", onBack = navigator::back)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(52.dp).rounded(999.dp).background(Tokens.NeutralFill), contentAlignment = Alignment.Center) {
                    Text(Selectors.initials(c.name), style = fig(18, FontWeight.Bold, Tokens.InkSecondary))
                }
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = bric(24, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val orderCount = Selectors.orderCount(state, c)
                    Text("+91 ${Selectors.fmtPhone(c.phone)} · " + (if (orderCount == 1) tr("{n} order", "n" to orderCount) else tr("{n} orders", "n" to orderCount)), style = fig(13, color = Tokens.Muted))
                    if (c.address.isNotBlank()) Text(c.address, style = fig(13, color = Tokens.Muted), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton(tr("New order"), Modifier.weight(1f), height = 48.dp) { navigator.openNewOrder(custId = custId, from = "customer") }
                OutlineButton(tr("Edit"), Modifier.weight(1f), height = 48.dp, border = Tokens.CardBorder, fg = Tokens.Ink) {
                    active = ActiveSheet.CustomerForm(editId = custId, ctx = "list", trigger = "customer_page")
                }
            }

            // balance card
            val balBg = if (bal > 0) Tokens.OrangeLight else if (bal < 0) Tokens.BlueLight else Tokens.Card
            val balBd = if (bal > 0) Tokens.OrangeBorder else if (bal < 0) Tokens.BlueBorder else Tokens.CardBorder
            val balFg = if (bal > 0) Tokens.OrangeDeep else if (bal < 0) Tokens.BlueText else Tokens.Ink
            Column(Modifier.fillMaxWidth().rounded(16.dp).background(balBg).border(1.dp, balBd, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (bal > 0) tr("{name} has to pay you", "name" to nm) else if (bal < 0) tr("{name} has paid extra (advance)", "name" to nm) else tr("All clear"), style = fig(14, FontWeight.SemiBold, balFg))
                FitText(Money.rupees(bal), style = bric(28, FontWeight.Bold, balFg))
                Text(
                    if (bal > 0) tr("Includes orders in progress at their current total.")
                    else if (bal < 0) tr("Used automatically on the next bill.")
                    else tr("Nothing to collect."),
                    style = fig(12, color = balFg),
                )
                if (bal > 0) {
                    Spacer(Modifier.height(2.dp))
                    PrimaryButton(tr("Receive payment"), height = 48.dp) { active = ActiveSheet.Receive(custId) }
                    OutlineButton(tr("Remind on WhatsApp"), Modifier.fillMaxWidth(), height = 48.dp, border = Tokens.OrangeBorder, fg = Tokens.OrangeText) {
                        Analytics.reminderSent(custId, bal)
                        sendReminderOnWhatsApp(context, state, custId, bal)
                        shopVm.showInfo(tr("Opening WhatsApp · reminder to {name} for {amount}", "name" to nm, "amount" to Money.rupees(bal)))
                    }
                }
            }
            // Combined bill — only once there is a counted, non-cancelled order to put on it.
            if (CombinedBill.ordersOf(state, custId).isNotEmpty()) {
                AppCard {
                    Row(
                        Modifier.fillMaxWidth().tap { navigator.openCombinedBill(custId) }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(42.dp).rounded(12.dp).background(Tokens.BlueLight), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.FileCopy, null, tint = Tokens.Blue, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(tr("Combined bill"), style = fig(15, FontWeight.Bold))
                            Text(tr("Many orders in one bill · pick a month or tick orders"), style = fig(13, color = Tokens.Muted))
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = Tokens.Faint, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Text(
                tr("+ Add old khata from notebook"), style = fig(14, FontWeight.Bold, Tokens.Blue),
                modifier = Modifier.tap { active = ActiveSheet.AddOld(custId) }.padding(vertical = 12.dp),
            )

            if (inProgress.isNotEmpty()) {
                Text(tr("ORDERS IN PROGRESS"), style = fig(12, FontWeight.Bold, Tokens.Muted))
                inProgress.forEach { o -> OrderMiniRow(state, o) { navigator.openOrder(o.id, "customer") } }
            }

            Segmented(listOf(tr("Khata") to (tab == "khata"), tr("Orders") to (tab == "orders"))) { tab = if (it == 0) "khata" else "orders" }

            if (tab == "khata") {
                val rows = remember(state, custId) { khataRows(state, custId) }
                if (rows.isEmpty()) Text(tr("No khata entries yet"), style = fig(14, color = Tokens.Muted))
                rows.forEach { KhataRowView(it) }
            } else {
                if (mine.isEmpty()) Text(tr("No orders yet"), style = fig(14, color = Tokens.Muted))
                mine.forEach { o -> OrderMiniRow(state, o) { navigator.openOrder(o.id, "customer") } }
            }
        }
    }

    SheetHost(active, state, shopVm, navigator, onOpen = { active = it }, onDismiss = { active = null })
}

private fun methodName(m: PayMethod) = when (m) { PayMethod.CASH -> tr("Cash"); PayMethod.UPI -> tr("UPI"); else -> "" }

private fun balWord(b: Int) = if (b > 0) tr("Khata {amount}", "amount" to Money.rupees(b)) else if (b < 0) tr("Advance {amount}", "amount" to Money.rupees(b)) else tr("All clear")

private fun khataRows(state: com.dailyworks.apnalaundry.domain.LaundryState, custId: String): List<KhataRow> {
    val led = state.ledger.filter { it.custId == custId }.sortedWith(compareBy({ it.date }, { it.ts }))
    var run = 0
    val out = led.map { e ->
        val before = run
        val whenStr = AppDate.plain(e.date).substringAfter(", ") + (if (e.time.isNotBlank()) " · ${e.time}" else "")
        var isAdd = false
        val title: String; val sub: String
        when (e.kind) {
            LedgerKind.BILL -> {
                isAdd = true; run += e.amt
                val o = Selectors.order(state, e.ref ?: -1)
                title = tr("Bill · Order #{no}", "no" to (o?.no() ?: e.ref))
                val desc = if (e.note.isNotBlank()) e.note else o?.let { "${Selectors.itemsLabel(it)} · ${Selectors.svcLabel(it)}" } ?: ""
                sub = whenStr + (if (desc.isNotBlank()) " · $desc" else "") + (if (before < 0) " · " + tr("{amount} advance used", "amount" to Money.rupees(min(-before, e.amt))) else "")
            }
            LedgerKind.OLD -> { isAdd = true; run += e.amt; title = tr("Old khata"); sub = tr("{date} · from notebook", "date" to whenStr) }
            LedgerKind.ADJ -> { isAdd = e.amt > 0; run += e.amt; title = tr("Bill changed · Order #{no}", "no" to (Selectors.order(state, e.ref ?: -1)?.no() ?: e.ref)); sub = tr("{date} · order edited after delivery", "date" to whenStr) }
            LedgerKind.GOT -> {
                run -= e.amt
                title = if (e.tag == PayTag.PRE) tr("Paid for order #{no} · {method}", "no" to e.ref, "method" to methodName(e.method)) else tr("Got {amount} · {method}", "amount" to Money.rupees(e.amt), "method" to methodName(e.method))
                sub = whenStr + (if (e.toAdv > 0) " · " + tr("{amount} kept as advance", "amount" to Money.rupees(e.toAdv)) else "")
            }
        }
        val fg = if (isAdd) Tokens.OrangeText else Tokens.BlueText
        KhataRow(
            title = title, sub = sub, amount = (if (isAdd) "+ " else "− ") + Money.rupees(e.amt), amtColor = fg,
            after = balWord(run), isAdd = isAdd,
            icBg = if (isAdd) Tokens.OrangeLight else Tokens.BlueLight, icFg = if (isAdd) Tokens.OrangeText else Tokens.BlueText,
        )
    }.toMutableList()
    // Open orders are already in the baaki at their current total (newest last).
    state.orders.filter { it.custId == custId && LaundryMath.isOpen(it) }.sortedBy { it.id }.forEach { o ->
        val amt = LaundryMath.amtOf(o)
        run += amt
        val desc = if (o.lines.isNotEmpty()) "${Selectors.itemsLabel(o)} · ${Selectors.svcLabel(o)}" else tr("clothes not counted yet")
        out += KhataRow(
            title = tr("Order #{no} · in progress", "no" to o.no()),
            sub = AppDate.plain(o.createdOn.ifBlank { o.pickupDate }).substringAfter(", ") + " · $desc",
            amount = "+ " + Money.rupees(amt), amtColor = Tokens.OrangeText,
            after = balWord(run), isAdd = true, icBg = Tokens.OrangeLight, icFg = Tokens.OrangeText,
        )
    }
    return out.reversed()
}

@Composable
private fun KhataRowView(r: KhataRow) {
    AppCard {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(r.title, style = fig(15, FontWeight.Bold))
                Text(r.sub, style = fig(12, color = Tokens.Muted))
                Text(r.after, style = fig(12, FontWeight.SemiBold, Tokens.Muted))
            }
            Text(r.amount, style = fig(16, FontWeight.Bold, r.amtColor), maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun OrderMiniRow(state: com.dailyworks.apnalaundry.domain.LaundryState, o: Order, onClick: () -> Unit) {
    val (label, bg, fg) = statusStyle(o.status)
    AppCard {
        Row(Modifier.fillMaxWidth().tap(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("#${o.no()}", style = fig(15, FontWeight.Bold), maxLines = 1, softWrap = false)
                    Text(if (o.lines.isNotEmpty()) Money.rupees(LaundryMath.amtOf(o)) else tr("Not counted"), style = fig(14, FontWeight.SemiBold, Tokens.InkSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    AppDate.plain(o.pickupDate).substringAfter(", ") + " · " + (if (o.lines.isNotEmpty()) "${Selectors.itemsLabel(o)} · ${Selectors.svcLabel(o)}" else tr("Clothes counted at pickup")),
                    style = fig(12, color = Tokens.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            StatusPill(label, bg, fg)
        }
    }
}

private fun statusStyle(s: OrderStatus): Triple<String, Color, Color> = when (s) {
    OrderStatus.CREATED -> Triple(tr("To pick up"), Tokens.OrangeLight, Tokens.OrangeText)
    OrderStatus.RECEIVED -> Triple(tr("Received"), Tokens.NeutralFill, Tokens.InkSecondary)
    OrderStatus.READY -> Triple(tr("Ready"), Tokens.BlueLight, Tokens.BlueText)
    OrderStatus.DELIVERED -> Triple(tr("Delivered"), Tokens.NeutralFill, Tokens.InkSecondary)
    OrderStatus.CANCELLED -> Triple(tr("Cancelled"), Tokens.NeutralFill, Tokens.InkSecondary)
}
