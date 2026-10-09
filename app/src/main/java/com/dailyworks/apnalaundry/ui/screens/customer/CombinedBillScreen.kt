package com.dailyworks.apnalaundry.ui.screens.customer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.BillDetails
import com.dailyworks.apnalaundry.domain.CombinedBill
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppCard
import com.dailyworks.apnalaundry.ui.components.OutlineButton
import com.dailyworks.apnalaundry.ui.components.PillChip
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.StatusPill
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.showDatePicker
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.screens.bill.rememberCombinedBillImage
import com.dailyworks.apnalaundry.ui.screens.bill.sendCombinedBillOnWhatsApp
import com.dailyworks.apnalaundry.ui.screens.bill.shareCombinedBillPdf
import com.dailyworks.apnalaundry.ui.sheets.Toggle
import com.dailyworks.apnalaundry.ui.theme.Tokens

private val TickedRow = Color(0xFFF2F5FD)
private val BoxBorder = Color(0xFFB8B2A7)

/**
 * Customer → Combined bill: pick the orders (a date chip or by hand), then
 * preview the one bill in the shop's design and send it on WhatsApp as a
 * PDF. A document, not a charge — the khata is never touched.
 * Design: canvas "Laundry App — Orders & Billing", boards 3b–3d.
 */
@Composable
fun CombinedBillScreen(shopVm: ShopViewModel, navigator: AppNavigator, custId: String) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = Selectors.customer(state, custId)
    val nm = Selectors.firstName(c.name)
    val today = AppDate.TODAY
    val all = remember(state.orders, custId) { CombinedBill.ordersOf(state, custId) }

    var preview by remember { mutableStateOf(false) }
    // The chip in force (null once the owner ticks by hand), its custom dates,
    // and the hand-made selection (null = follow the chip over the live orders).
    var preset by remember { mutableStateOf<CombinedBill.Preset?>(CombinedBill.Preset.MONTH) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf<Set<Int>?>(null) }
    var showItems by remember { mutableStateOf(false) }

    val selected: Set<Int> = manual
        ?: preset?.let { CombinedBill.pick(all, CombinedBill.range(it, today, from, to)) }
        ?: emptySet()
    val picked = all.filter { it.id in selected }
    val totals = CombinedBill.totals(picked)
    val period = CombinedBill.period(preset, picked, today, from, to)

    LaunchedEffect(Unit) { Analytics.screen("combined_bill") }
    BackHandler(enabled = preview) { preview = false }

    fun pickChip(p: CombinedBill.Preset) {
        preset = p; manual = null
    }

    fun toggle(id: Int) {
        manual = if (id in selected) selected - id else selected + id
        preset = null
    }

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars)) {
        // header
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.size(44.dp).tap { if (preview) preview = false else navigator.back() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Tokens.Ink, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(if (preview) "Combined bill · preview" else "Combined bill", style = bric(22, FontWeight.Bold))
                Text("${c.name} · +91 ${Selectors.fmtPhone(c.phone)}", style = fig(14, color = Tokens.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (!preview) {
            PickOrders(
                all = all, selected = selected, picked = picked, nm = nm, preset = preset, from = from, to = to, today = today,
                onChip = ::pickChip,
                // Either date re-ticks the range; the other date follows so From never passes To.
                onFrom = { d ->
                    val cur = CombinedBill.range(CombinedBill.Preset.CUSTOM, today, from, to)
                    from = d; if (cur.second < d) to = d
                    preset = CombinedBill.Preset.CUSTOM; manual = null
                },
                onTo = { d ->
                    val cur = CombinedBill.range(CombinedBill.Preset.CUSTOM, today, from, to)
                    to = d; if (cur.first > d) from = d
                    preset = CombinedBill.Preset.CUSTOM; manual = null
                },
                onToggle = ::toggle,
                onAll = { on -> manual = if (on) all.map { it.id }.toSet() else emptySet(); preset = null },
                modifier = Modifier.weight(1f),
            )
            BottomBar {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (picked.isEmpty()) "Nothing ticked" else "${Selectors.countNoun(picked.size, "order")} ticked",
                        style = fig(12, FontWeight.SemiBold, Tokens.Muted),
                    )
                    Text(Money.rupees(totals.total), style = bric(22, FontWeight.Bold))
                    Text(
                        when {
                            picked.isEmpty() -> "Tick at least one order"
                            totals.due > 0 -> "${Money.rupees(totals.due)} to pay"
                            else -> "All paid"
                        },
                        style = fig(13, FontWeight.Bold, when {
                            picked.isEmpty() -> Tokens.Muted
                            totals.due > 0 -> Tokens.OrangeText
                            else -> Tokens.GreenText
                        }),
                    )
                }
                PrimaryButton("See bill", Modifier.width(140.dp), enabled = picked.isNotEmpty(), height = 52.dp) { preview = true }
            }
        } else {
            val receipt = remember(state, custId, selected, period, showItems) {
                CombinedBill.receipt(state, custId, picked, period, showItems)
            }
            val image by rememberCombinedBillImage(shopVm, receipt)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppCard {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Show every item", style = fig(15, FontWeight.Bold))
                            Text("Shirt × 4, Saree × 1… under each order", style = fig(13, color = Tokens.Muted))
                        }
                        Toggle(on = showItems) { showItems = !showItems }
                    }
                }
                val img = image
                if (img == null) {
                    Box(Modifier.fillMaxWidth().height(480.dp).rounded(16.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)))
                } else {
                    Image(
                        img, contentDescription = "Combined bill · ${receipt.totalLabel} ${receipt.total}",
                        modifier = Modifier.fillMaxWidth().rounded(16.dp).border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.FillWidth,
                    )
                }
                Text(
                    "Goes to $nm on WhatsApp as a PDF, in your ${BillDetails.templateLabel(state.shop.billTemplate)} bill design.",
                    style = fig(13, color = Tokens.Muted),
                )
            }
            BottomBar {
                OutlineButton("Download", Modifier.weight(1f), height = 54.dp, border = Tokens.CardBorder, fg = Tokens.Ink) {
                    shareCombinedBillPdf(context, receipt)
                }
                PrimaryButton("Send on WhatsApp", Modifier.weight(1f), height = 54.dp) {
                    sendCombinedBillOnWhatsApp(context, state, custId, receipt)
                    Analytics.combinedBillSent(custId, receipt.orders, receipt.totalAmount, receipt.dueAmount)
                    shopVm.showInfo(
                        "Opening WhatsApp · combined bill to $nm · ${Selectors.countNoun(receipt.orders, "order")}, " +
                            if (receipt.fullyPaid) "all paid" else "${receipt.due} to pay",
                    )
                    navigator.back()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickOrders(
    all: List<Order>, selected: Set<Int>, picked: List<Order>, nm: String,
    preset: CombinedBill.Preset?, from: String, to: String, today: String,
    onChip: (CombinedBill.Preset) -> Unit, onFrom: (String) -> Unit, onTo: (String) -> Unit,
    onToggle: (Int) -> Unit, onAll: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Put many orders in one bill and send it on WhatsApp. Good for customers who pay once a month.",
            style = fig(14, color = Tokens.InkSecondary),
        )
        Text("Which orders?", style = fig(13, FontWeight.Bold, Tokens.Muted))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CombinedBill.Preset.entries.forEach { p -> PillChip(p.label, selected = preset == p) { onChip(p) } }
        }
        if (preset == CombinedBill.Preset.CUSTOM) {
            val range = CombinedBill.range(CombinedBill.Preset.CUSTOM, today, from, to)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("From", range.first, Modifier.weight(1f)) { showDatePicker(context, range.first, null, onFrom) }
                DateField("To", range.second, Modifier.weight(1f)) { showDatePicker(context, range.second, null, onTo) }
            }
        }
        Text("Or tick the orders yourself below.", style = fig(13, color = Tokens.Muted))

        AppCard {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (all.isEmpty()) "Orders" else "${Selectors.countNoun(all.size, "order")} from $nm",
                        style = fig(15, FontWeight.Bold), modifier = Modifier.weight(1f),
                    )
                    if (all.isNotEmpty()) {
                        val allOn = picked.size == all.size
                        Text(
                            if (allOn) "Clear all" else "Select all",
                            style = fig(14, FontWeight.Bold, Tokens.Blue),
                            modifier = Modifier.tap { onAll(!allOn) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }
                }
                if (all.isEmpty()) {
                    Text(
                        "No counted orders yet",
                        style = fig(14, color = Tokens.Muted),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 20.dp),
                    )
                }
                // grouped by the month of pickup, newest month first (orders arrive sorted)
                all.groupBy { it.pickupDate.take(7) }.forEach { (_, orders) ->
                    Text(
                        CombinedBill.monthName(orders.first().pickupDate).uppercase(),
                        style = fig(11, FontWeight.Bold, Tokens.Muted).copy(letterSpacing = 0.06.em),
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 4.dp),
                    )
                    orders.forEach { o -> OrderTickRow(o, on = o.id in selected) { onToggle(o.id) } }
                }
            }
        }
        Text("Cancelled orders and orders with no clothes counted yet are not listed.", style = fig(12, color = Tokens.Muted))
    }
}

@Composable
private fun OrderTickRow(o: Order, on: Boolean, onToggle: () -> Unit) {
    val tag = CombinedBill.tag(o)
    val (tagBg, tagFg) = when (tag.kind) {
        CombinedBill.TagKind.PAID -> Tokens.GreenLight to Tokens.GreenText
        CombinedBill.TagKind.DUE -> Tokens.OrangeLight to Tokens.OrangeText
        CombinedBill.TagKind.OPEN -> Tokens.BlueLight to Tokens.BlueText
    }
    Column {
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Tokens.Divider))
        Row(
            Modifier.fillMaxWidth().background(if (on) TickedRow else Tokens.Card).tap(onClick = onToggle)
                .defaultMinSize(minHeight = 66.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(22.dp).rounded(6.dp).background(if (on) Tokens.Blue else Tokens.Card)
                    .border(1.5.dp, if (on) Tokens.Blue else BoxBorder, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (on) Icon(Icons.Outlined.Check, null, tint = Tokens.OnDark, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("#${o.no()} · ${CombinedBill.dayMonth(o.pickupDate)}", style = fig(15, FontWeight.Bold))
                Text("${Selectors.itemsLabel(o)} · ${Selectors.svcLabel(o)}", style = fig(13, color = Tokens.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(Money.rupees(LaundryMath.amtOf(o)), style = fig(16, FontWeight.Bold))
                StatusPill(tag.label, tagBg, tagFg)
            }
        }
    }
}

@Composable
private fun DateField(label: String, iso: String, modifier: Modifier = Modifier, onTap: () -> Unit) {
    Column(
        modifier.height(56.dp).rounded(12.dp).background(Tokens.Card).border(1.5.dp, Tokens.FieldBorder, RoundedCornerShape(12.dp))
            .tap(onClick = onTap).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = fig(11, FontWeight.SemiBold, Tokens.Muted))
        Text(CombinedBill.dayMonthYear(iso), style = fig(15, FontWeight.SemiBold))
    }
}

/** The fixed bar under the content, with a hairline above it. */
@Composable
private fun BottomBar(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Tokens.Card)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        Spacer(Modifier.height(4.dp))
    }
}
