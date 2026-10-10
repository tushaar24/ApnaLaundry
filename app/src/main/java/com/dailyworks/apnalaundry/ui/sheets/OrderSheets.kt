package com.dailyworks.apnalaundry.ui.sheets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.BillReceipt
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.domain.OrderStatus
import com.dailyworks.apnalaundry.domain.Route
import com.dailyworks.apnalaundry.domain.PayMethod
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppBottomSheet
import com.dailyworks.apnalaundry.ui.components.ClothesEditor
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.PillChip
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rememberClothesState
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.screens.bill.rememberBillImages
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import com.dailyworks.apnalaundry.domain.LedgerKind
import com.dailyworks.apnalaundry.ui.components.showDatePicker
import com.dailyworks.apnalaundry.ui.theme.Tokens
import kotlin.math.max

@Composable
private fun MoneyRow(label: String, value: String, valueColor: Color = Tokens.Ink, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = fig(if (bold) 16 else 15, if (bold) FontWeight.Bold else FontWeight.Normal, if (bold) Tokens.Ink else Tokens.InkSecondary))
        Text(value, style = fig(if (bold) 18 else 15, FontWeight.Bold, valueColor))
    }
}

@Composable
fun CollectPaymentSheet(state: LaundryState, orderId: Int, vm: ShopViewModel, onDismiss: () -> Unit) {
    val o = Selectors.order(state, orderId) ?: return onDismiss()
    val c = Selectors.customer(state, o.custId)
    val total = LaundryMath.amtOf(o)
    val pre = o.pre
    val oldBal = Selectors.balance(state, o.custId, o.id)
    val billDue = total - pre
    val due = billDue + oldBal
    var payAmt by remember { mutableStateOf(max(0, due).toString()) }
    val got = payAmt.toIntOrNull() ?: 0
    val left = due - got

    AppBottomSheet(title = "Collect payment", subtitle = "From ${c.name} before handing over", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().rounded(14.dp).background(Tokens.Card).padding(14.dp)) {
                MoneyRow(
                    label = if (pre > 0) (if (billDue < 0) "Paid extra before (bill ${Money.rupees(total)})" else "This bill (after ${Money.rupees(pre)} paid)") else "This bill",
                    value = (if (billDue < 0) "− " else "") + Money.rupees(billDue),
                )
                if (oldBal != 0) MoneyRow(
                    label = if (oldBal > 0) "Old baaki" else "Advance already paid",
                    value = (if (oldBal > 0) "+ " else "− ") + Money.rupees(oldBal),
                    valueColor = if (oldBal > 0) Tokens.OrangeText else Tokens.BlueText,
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider).padding(vertical = 6.dp))
                MoneyRow("Total to collect", Money.rupees(max(0, due)), bold = true)
            }

            FieldBox(value = payAmt, onValueChange = { payAmt = it.filter { ch -> ch.isDigit() }.take(6) }, prefix = "₹", height = 56.dp, keyboardType = KeyboardType.Number, textStyle = bric(22, FontWeight.Bold))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PillChip("Full ${Money.rupees(max(0, due))}", payAmt == max(0, due).toString()) { payAmt = max(0, due).toString() }
                if (oldBal > 0 && billDue > 0) PillChip("Only this bill ${Money.rupees(billDue)}", payAmt == billDue.toString()) { payAmt = billDue.toString() }
            }

            val (prevBg, prevFg, prevText) = when {
                payAmt.isEmpty() -> Triple(Tokens.Bg, Tokens.Muted, "Type the amount you got")
                left > 0 -> Triple(Tokens.OrangeLight, Tokens.OrangeDeep, "${Money.rupees(left)} will stay in khata (baaki)")
                left < 0 -> Triple(Tokens.BlueLight, Tokens.BlueText, "${Money.rupees(left)} extra — kept as advance")
                else -> Triple(Tokens.NeutralFill, Tokens.Ink, "Full payment — all clear")
            }
            Box(Modifier.fillMaxWidth().rounded(12.dp).background(prevBg).padding(14.dp)) {
                Text(prevText, style = fig(14, FontWeight.SemiBold, prevFg))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Got cash", Modifier.weight(1f), enabled = got > 0, height = 54.dp) { vm.deliver(orderId, got, PayMethod.CASH); onDismiss() }
                PrimaryButton("Got UPI", Modifier.weight(1f), enabled = got > 0, height = 54.dp) { vm.deliver(orderId, got, PayMethod.UPI); onDismiss() }
            }
            Box(Modifier.fillMaxWidth().height(50.dp).rounded(14.dp).background(Tokens.NeutralFill).tap { vm.deliver(orderId, 0, PayMethod.NONE); onDismiss() }, contentAlignment = Alignment.Center) {
                Text("Nothing now · add all to khata", style = fig(15, FontWeight.Bold, Tokens.InkSecondary))
            }
        }
    }
}

/**
 * "When will it be delivered?" — Today / Tomorrow / Day after or any date (not
 * before pickup). Asked when an order is marked ready, the way the payment is
 * asked when it's delivered.
 */
@Composable
private fun DeliveryDateQuestion(o: Order, date: String, onDate: (String) -> Unit) {
    val context = LocalContext.current
    val today = AppDate.TODAY
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("When will it be delivered?", style = fig(15, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Today" to today, "Tomorrow" to AppDate.add(today, 1), "Day after" to AppDate.add(today, 2)).forEach { (label, iso) ->
                PillChip(label, date == iso) { onDate(iso) }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(52.dp).rounded(12.dp).background(Tokens.Card)
                .border(1.5.dp, Tokens.FieldBorder, RoundedCornerShape(12.dp))
                .tap { showDatePicker(context, date.ifBlank { today }, o.pickupDate, onDate) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.CalendarMonth, null, tint = Tokens.Blue, modifier = Modifier.size(20.dp))
            Text(
                if (date.isNotBlank()) "Delivery: ${AppDate.short(date)}" else "Pick a delivery date",
                style = fig(15, FontWeight.SemiBold, if (date.isNotBlank()) Tokens.Ink else Tokens.Muted),
                modifier = Modifier.weight(1f),
            )
            Text("Change", style = fig(14, FontWeight.Bold, Tokens.Blue))
        }
    }
}

/** Mark ready: asks when it'll be delivered (starts on the order's date, if any). */
@Composable
fun ReadySheet(state: LaundryState, orderId: Int, vm: ShopViewModel, onDismiss: () -> Unit) {
    val o = Selectors.order(state, orderId) ?: return onDismiss()
    val c = Selectors.customer(state, o.custId)
    var date by remember { mutableStateOf(o.deliveryDate) }
    AppBottomSheet(title = "Mark ready", subtitle = "${c.name} · #${o.no()}", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DeliveryDateQuestion(o, date) { date = it }
            PrimaryButton(if (date.isBlank()) "Pick a delivery date" else "Mark ready", enabled = date.isNotBlank(), height = 56.dp) {
                if (date.isNotBlank()) { vm.markReady(orderId, date); onDismiss() }
            }
        }
    }
}

@Composable
fun CountClothesSheet(state: LaundryState, orderId: Int, next: OrderStatus, vm: ShopViewModel, onDismiss: () -> Unit, onSaved: () -> Unit = onDismiss) {
    val o = Selectors.order(state, orderId) ?: return onDismiss()
    val c = Selectors.customer(state, o.custId)
    val clothes = rememberClothesState(state.services)
    val total = clothes.total(state.services)
    val askDate = next == OrderStatus.READY
    var date by remember { mutableStateOf(o.deliveryDate) }

    AppBottomSheet(title = "Count clothes", subtitle = "${c.name} · #${o.no()}. The bill is made after this.", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ClothesEditor(state.services, clothes, editablePrice = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Total", style = fig(15, FontWeight.SemiBold, Tokens.Muted))
                Text(Money.rupees(total), style = bric(26, FontWeight.Bold))
            }
            if (askDate) DeliveryDateQuestion(o, date) { date = it }
            PrimaryButton(
                when {
                    askDate && date.isBlank() -> "Pick a delivery date"
                    next == OrderStatus.READY -> "Mark ready · make bill"
                    else -> "Picked up · make bill"
                },
                enabled = total > 0 && (!askDate || date.isNotBlank()), height = 56.dp,
            ) { vm.saveCount(orderId, next, clothes.lines(state.services), if (askDate) date else ""); onSaved() }
        }
    }
}

@Composable
fun RescheduleSheet(state: LaundryState, orderId: Int, kind: String, vm: ShopViewModel, onDismiss: () -> Unit) {
    val o = Selectors.order(state, orderId) ?: return onDismiss()
    val c = Selectors.customer(state, o.custId)
    val isPickup = kind == "pickup"
    // No delivery date yet: nothing is pre-selected — the owner picks one (an
    // order only takes today's date by itself when it's marked delivered).
    val base = if (isPickup) o.pickupDate else o.deliveryDate
    var date by remember { mutableStateOf(base) }
    var time by remember { mutableStateOf(if (isPickup) AppDate.to24h(o.pickupTime) else AppDate.to24h(o.deliveryTime)) }
    val title = if (isPickup) "Reschedule pickup" else if (o.deliveryDate.isNotBlank()) "Reschedule delivery" else "Set delivery date"
    val chips = listOf("Today" to AppDate.TODAY, "Tomorrow" to AppDate.add(AppDate.TODAY, 1), "Day after" to AppDate.add(AppDate.TODAY, 2))

    AppBottomSheet(title = title, subtitle = "${c.name} · #${o.no()}", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { (label, iso) -> PillChip(label, date == iso) { date = iso } }
            }
            // Any date, past ones included — e.g. entering an order after the fact.
            val context = LocalContext.current
            Row(
                Modifier.fillMaxWidth().height(52.dp).rounded(12.dp).background(Tokens.Card)
                    .border(1.5.dp, Tokens.FieldBorder, RoundedCornerShape(12.dp))
                    .tap { showDatePicker(context, date.ifBlank { AppDate.TODAY }, if (isPickup) null else o.pickupDate) { date = it } }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = Tokens.Blue, modifier = Modifier.size(20.dp))
                Text(if (date.isNotBlank()) "Selected: ${AppDate.short(date)}" else "Pick a date", style = fig(15, FontWeight.SemiBold, if (date.isNotBlank()) Tokens.Ink else Tokens.Muted), modifier = Modifier.weight(1f))
                Text("Change", style = fig(14, FontWeight.Bold, Tokens.Blue))
            }
            if (isPickup && date.isNotBlank() && o.deliveryDate.isNotBlank() && o.ddAuto) {
                val shift = AppDate.daysBetween(o.pickupDate, date)
                Text("Delivery moves too: ${AppDate.short(AppDate.add(o.deliveryDate, shift))}", style = fig(13, color = Tokens.Muted))
            }
            PrimaryButton(if (date.isBlank()) "Pick a date to save" else "Save", height = 56.dp, enabled = date.isNotBlank()) {
                if (date.isNotBlank()) { vm.reschedule(orderId, kind, date, time, notify = false); onDismiss() }
            }
        }
    }
}

/** Confirm deleting an order / bill (and its khata entries); undo comes in the toast. */
@Composable
fun DeleteOrderSheet(state: LaundryState, orderId: Int, vm: ShopViewModel, navigator: AppNavigator, onDismiss: () -> Unit) {
    val o = Selectors.order(state, orderId) ?: return onDismiss()
    val c = Selectors.customer(state, o.custId)
    val what = if (o.lines.isNotEmpty()) "bill" else "order"
    val paidAny = state.ledger.any { it.ref == o.id && it.kind == LedgerKind.GOT }
    AppBottomSheet(title = "Delete $what #${o.no()}?", subtitle = "${c.name} · ${Money.rupees(LaundryMath.amtOf(o))}", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "The $what is removed from orders, earnings and ${Selectors.firstName(c.name)}'s khata" +
                    (if (paidAny) ", along with the payments recorded on it" else "") + ". You can undo right after.",
                style = fig(14, FontWeight.SemiBold, Tokens.OrangeDeep),
                modifier = Modifier.fillMaxWidth().rounded(12.dp).background(Tokens.OrangeLight).padding(14.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(54.dp).rounded(14.dp).background(Tokens.NeutralFill).tap { onDismiss() }, contentAlignment = Alignment.Center) {
                    Text("Keep", style = fig(16, FontWeight.Bold, Tokens.InkSecondary))
                }
                PrimaryButton("Delete $what", Modifier.weight(1f), height = 54.dp, bg = Tokens.Orange) {
                    onDismiss()
                    vm.deleteOrder(orderId)
                    // The order's own screens would show "not found" — leave them.
                    val route = navigator.nav.currentDestination?.route.orEmpty()
                    if (route.startsWith("order/") || route.startsWith("bill/")) navigator.openHome()
                }
            }
        }
    }
}

@Composable
fun CancelSheet(state: LaundryState, orderId: Int, vm: ShopViewModel, onDismiss: () -> Unit) {
    val o = Selectors.order(state, orderId) ?: return onDismiss()
    val c = Selectors.customer(state, o.custId)
    var reason by remember { mutableStateOf("") }
    val reasons = listOf("Customer not home", "Customer cancelled", "Wrong address", "Other")
    val word = if (o.status == OrderStatus.CREATED) "pickup" else "order"

    AppBottomSheet(title = "Cancel $word?", subtitle = "${c.name} · #${o.no()}", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reasons.forEach { r -> PillChip(r, reason == r) { reason = if (reason == r) "" else r } }
            }
            if (o.status == OrderStatus.DELIVERED) {
                Text("The bill comes off the khata. Money already taken stays as ${Selectors.firstName(c.name)}'s advance.", style = fig(13, color = Tokens.Muted))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(54.dp).rounded(14.dp).background(Tokens.NeutralFill).tap { onDismiss() }, contentAlignment = Alignment.Center) {
                    Text("Keep order", style = fig(16, FontWeight.Bold, Tokens.InkSecondary))
                }
                PrimaryButton("Cancel $word", Modifier.weight(1f), height = 54.dp, bg = Tokens.Orange) { vm.cancelOrder(orderId, reason); onDismiss() }
            }
        }
    }
}

@Composable
fun BillViewSheet(state: LaundryState, orderId: Int, vm: ShopViewModel, onDismiss: () -> Unit) {
    val o = Selectors.order(state, orderId) ?: return onDismiss()
    val c = Selectors.customer(state, o.custId)
    val total = LaundryMath.amtOf(o)
    LaunchedEffect(orderId) { Analytics.billViewed(orderId) }
    // The exact image customers get, in the shop's chosen design.
    val images by rememberBillImages(vm, listOf(BillReceipt.of(state, o)), equalHeight = false)
    AppBottomSheet(title = "Bill #${o.no()}", subtitle = "${c.name} · this is what the customer sees", onDismiss = onDismiss) {
        val img = images?.firstOrNull()
        if (img == null) {
            Box(Modifier.fillMaxWidth().height(420.dp).rounded(16.dp).background(Tokens.Card))
        } else {
            Image(
                img, contentDescription = "Bill #${o.no()} · total ${Money.rupees(total)}",
                modifier = Modifier.fillMaxWidth().rounded(16.dp).border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}

@Composable
fun Toggle(on: Boolean, onColor: Color = Tokens.Blue, onToggle: () -> Unit) {
    Box(
        Modifier
            .width(46.dp).height(28.dp).rounded(999.dp)
            .background(if (on) onColor else Color(0xFFC9C3B8))
            .tap(onClick = onToggle)
            .padding(2.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.width(24.dp).height(24.dp).rounded(999.dp).background(Color.White))
    }
}

@Suppress("UNUSED_PARAMETER")
val forwardIcon = Icons.AutoMirrored.Filled.ArrowForward

/**
 * Change status: every state the order can be in, the next one pre-selected.
 * Forward steps that need input reuse the usual sheets (count clothes,
 * delivery date, collect payment, cancel reason); everything else is a
 * direct move, going backward included.
 */
@Composable
fun ChangeStatusSheet(
    state: LaundryState, orderId: Int, vm: ShopViewModel,
    onOpen: (ActiveSheet) -> Unit, onDismiss: () -> Unit,
) {
    val o = Selectors.order(state, orderId) ?: return onDismiss()
    val c = Selectors.customer(state, o.custId)
    val steps = buildList {
        if (o.pickup == Route.HOME) add(OrderStatus.CREATED to "To pick up")
        add(OrderStatus.RECEIVED to "Received · clothes at shop")
        add(OrderStatus.READY to "Ready")
        add(OrderStatus.DELIVERED to "Delivered")
        add(OrderStatus.CANCELLED to "Cancelled")
    }
    var sel by remember {
        mutableStateOf(
            when (o.status) {
                OrderStatus.CREATED -> OrderStatus.RECEIVED
                OrderStatus.RECEIVED -> OrderStatus.READY
                OrderStatus.READY -> OrderStatus.DELIVERED
                OrderStatus.DELIVERED -> OrderStatus.READY // only way is back
                OrderStatus.CANCELLED -> OrderStatus.RECEIVED
            },
        )
    }

    fun apply(target: OrderStatus) {
        when {
            target == o.status -> onDismiss()
            target == OrderStatus.CANCELLED -> onOpen(ActiveSheet.Cancel(o.id))
            target == OrderStatus.DELIVERED ->
                // No bill yet: count the clothes first, then collect.
                onOpen(if (o.lines.isEmpty()) ActiveSheet.Count(o.id, OrderStatus.READY, thenPay = true) else ActiveSheet.Pay(o.id))
            target == OrderStatus.READY && o.status != OrderStatus.DELIVERED && o.status != OrderStatus.CANCELLED ->
                // Forward to ready asks the delivery date; back from delivered doesn't.
                onOpen(if (o.lines.isEmpty()) ActiveSheet.Count(o.id, OrderStatus.READY) else ActiveSheet.Ready(o.id))
            target == OrderStatus.RECEIVED && o.status == OrderStatus.CREATED && o.lines.isEmpty() ->
                onOpen(ActiveSheet.Count(o.id, OrderStatus.RECEIVED))
            else -> { vm.setStatus(o.id, target); onDismiss() }
        }
    }

    AppBottomSheet(title = "Change status", subtitle = "${c.name} · #${o.no()}", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            steps.forEach { (st, label) ->
                val current = st == o.status
                val on = sel == st && !current
                val danger = st == OrderStatus.CANCELLED
                Row(
                    Modifier.fillMaxWidth().rounded(14.dp)
                        .background(if (on) Tokens.BlueLight else Tokens.Card)
                        .border(1.5.dp, if (on) Tokens.Blue else Tokens.CardBorder, RoundedCornerShape(14.dp))
                        .tap(enabled = !current) { sel = st }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(18.dp).rounded(999.dp)
                            .background(if (on) Tokens.Blue else Tokens.Card)
                            .border(1.5.dp, if (on) Tokens.Blue else Tokens.FieldBorder, RoundedCornerShape(999.dp)),
                    )
                    Text(
                        label,
                        style = fig(15, if (on) FontWeight.Bold else FontWeight.SemiBold,
                            if (current) Tokens.Muted else if (danger) Tokens.OrangeText else Tokens.Ink),
                        modifier = Modifier.weight(1f),
                    )
                    if (current) Text("Current", style = fig(12, FontWeight.Bold, Tokens.Muted))
                }
            }
            if (o.status == OrderStatus.DELIVERED && sel != OrderStatus.DELIVERED) {
                Text(
                    "Going back takes the bill off the khata. Money already taken stays with the order and counts when you deliver again.",
                    style = fig(13, color = Tokens.Muted),
                )
            }
            PrimaryButton(
                when (sel) {
                    OrderStatus.DELIVERED -> if (o.lines.isEmpty()) "Count clothes · deliver" else "Mark delivered"
                    OrderStatus.CANCELLED -> "Cancel order"
                    else -> "Move to " + steps.first { it.first == sel }.second.substringBefore(" ·")
                },
                height = 54.dp, bg = if (sel == OrderStatus.CANCELLED) Tokens.Orange else Tokens.Blue,
                enabled = sel != o.status,
            ) { apply(sel) }
        }
    }
}
