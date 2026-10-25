package com.dailyworks.apnalaundry.ui.sheets

import androidx.compose.foundation.layout.heightIn
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
import com.dailyworks.apnalaundry.core.i18n
import com.dailyworks.apnalaundry.core.tr
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.ui.tour.TourStage
import com.dailyworks.apnalaundry.ui.tour.TourSteps
import com.dailyworks.apnalaundry.ui.tour.TourTip
import kotlin.math.max

@Composable
private fun MoneyRow(label: String, value: String, valueColor: Color = Tokens.Ink, bold: Boolean = false) {
    // The label takes the room left (and wraps); the amount always stays whole.
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = fig(if (bold) 16 else 15, if (bold) FontWeight.Bold else FontWeight.Normal, if (bold) Tokens.Ink else Tokens.InkSecondary))
        Text(value, style = fig(if (bold) 18 else 15, FontWeight.Bold, valueColor), maxLines = 1, softWrap = false)
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
    // Part cash, part UPI: two boxes; UPI follows "amount − cash" until typed in.
    var split by remember { mutableStateOf(false) }
    var cashText by remember { mutableStateOf("") }
    var upiText by remember { mutableStateOf("") }
    var upiTouched by remember { mutableStateOf(false) }
    val cash = cashText.toIntOrNull() ?: 0
    val upi = upiText.toIntOrNull() ?: 0
    val got = if (split) cash + upi else payAmt.toIntOrNull() ?: 0
    val left = due - got
    val tour by vm.tour.collectAsStateWithLifecycle()

    AppBottomSheet(title = tr("Collect payment"), subtitle = tr("From {name} before handing over", "name" to c.name), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().rounded(14.dp).background(Tokens.Card).padding(14.dp)) {
                MoneyRow(
                    label = if (pre > 0) (if (billDue < 0) tr("Paid extra before (bill {amount})", "amount" to Money.rupees(total)) else tr("This bill (after {amount} paid)", "amount" to Money.rupees(pre))) else tr("This bill"),
                    value = (if (billDue < 0) "− " else "") + Money.rupees(billDue),
                )
                if (oldBal != 0) MoneyRow(
                    label = if (oldBal > 0) tr("Old baaki") else tr("Advance already paid"),
                    value = (if (oldBal > 0) "+ " else "− ") + Money.rupees(oldBal),
                    valueColor = if (oldBal > 0) Tokens.OrangeText else Tokens.BlueText,
                )
                Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(Tokens.Divider))
                MoneyRow(tr("Total to collect"), Money.rupees(max(0, due)), bold = true)
            }

            if (!split) {
                FieldBox(value = payAmt, onValueChange = { payAmt = it.filter { ch -> ch.isDigit() }.take(6) }, prefix = "₹", height = 56.dp, keyboardType = KeyboardType.Number, textStyle = bric(22, FontWeight.Bold))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillChip(tr("Full {amount}", "amount" to Money.rupees(max(0, due))), payAmt == max(0, due).toString()) { payAmt = max(0, due).toString() }
                    if (oldBal > 0 && billDue > 0) PillChip(tr("Only this bill {amount}", "amount" to Money.rupees(billDue)), payAmt == billDue.toString()) { payAmt = billDue.toString() }
                }
            } else {
                val target = payAmt.toIntOrNull() ?: max(0, due)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tr("Cash"), style = fig(13, FontWeight.Bold, Tokens.Muted))
                        FieldBox(
                            value = cashText,
                            onValueChange = {
                                cashText = it.filter { ch -> ch.isDigit() }.take(6)
                                if (!upiTouched) upiText = max(0, target - (cashText.toIntOrNull() ?: 0)).toString()
                            },
                            prefix = "₹", height = 52.dp, keyboardType = KeyboardType.Number, textStyle = bric(20, FontWeight.Bold),
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tr("UPI"), style = fig(13, FontWeight.Bold, Tokens.Muted))
                        FieldBox(
                            value = upiText,
                            onValueChange = { upiText = it.filter { ch -> ch.isDigit() }.take(6); upiTouched = true },
                            prefix = "₹", height = 52.dp, keyboardType = KeyboardType.Number, textStyle = bric(20, FontWeight.Bold),
                        )
                    }
                }
            }

            val (prevBg, prevFg, prevText) = when {
                (if (split) cashText.isEmpty() && upiText.isEmpty() else payAmt.isEmpty()) -> Triple(Tokens.Bg, Tokens.Muted, tr("Type the amount you got"))
                left > 0 -> Triple(Tokens.OrangeLight, Tokens.OrangeDeep, tr("{amount} will stay in khata (baaki)", "amount" to Money.rupees(left)))
                left < 0 -> Triple(Tokens.BlueLight, Tokens.BlueText, tr("{amount} extra — kept as advance", "amount" to Money.rupees(left)))
                else -> Triple(Tokens.NeutralFill, Tokens.Ink, tr("Full payment — all clear"))
            }
            Box(Modifier.fillMaxWidth().rounded(12.dp).background(prevBg).padding(14.dp)) {
                Text(prevText, style = fig(14, FontWeight.SemiBold, prevFg))
            }

            // The guide: what the three ways out mean, arrow at the buttons.
            if (tour == TourStage.STATUS && !split) TourTip(TourSteps.paySheet)

            if (!split) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(tr("Got cash"), Modifier.weight(1f), enabled = got > 0, height = 56.dp) { vm.deliver(orderId, got, PayMethod.CASH); onDismiss() }
                    PrimaryButton(tr("Got UPI"), Modifier.weight(1f), enabled = got > 0, height = 56.dp) { vm.deliver(orderId, got, PayMethod.UPI); onDismiss() }
                }
            } else {
                PrimaryButton(
                    if (got > 0) tr("Got {amount} (cash + UPI)", "amount" to Money.rupees(got)) else tr("Type cash and UPI"),
                    enabled = got > 0, height = 56.dp,
                ) { vm.deliverSplit(orderId, cash, upi); onDismiss() }
            }
            Text(
                if (split) tr("Back to one method") else tr("Part cash, part UPI"),
                style = fig(14, FontWeight.Bold, Tokens.Blue),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().tap {
                    if (!split) {
                        // Start with all of it as cash; UPI fills the rest as cash is typed.
                        cashText = (payAmt.toIntOrNull() ?: max(0, due)).toString(); upiText = "0"; upiTouched = false
                    }
                    split = !split
                }.padding(vertical = 4.dp),
            )
            Box(Modifier.fillMaxWidth().height(48.dp).rounded(14.dp).background(Tokens.NeutralFill).tap { vm.deliver(orderId, 0, PayMethod.NONE); onDismiss() }, contentAlignment = Alignment.Center) {
                Text(tr("Nothing now · add all to khata"), style = fig(15, FontWeight.Bold, Tokens.InkSecondary))
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
        Text(tr("When will it be delivered?"), style = fig(15, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(tr("Today") to today, tr("Tomorrow") to AppDate.add(today, 1), tr("Day after") to AppDate.add(today, 2)).forEach { (label, iso) ->
                PillChip(label, date == iso) { onDate(iso) }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).rounded(12.dp).background(Tokens.Card)
                .border(1.5.dp, Tokens.FieldBorder, RoundedCornerShape(12.dp))
                .tap { showDatePicker(context, date.ifBlank { today }, o.pickupDate, onDate) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.CalendarMonth, null, tint = Tokens.Blue, modifier = Modifier.size(20.dp))
            Text(
                if (date.isNotBlank()) tr("Delivery: {date}", "date" to AppDate.short(date)) else tr("Pick a delivery date"),
                style = fig(15, FontWeight.SemiBold, if (date.isNotBlank()) Tokens.Ink else Tokens.Muted),
                modifier = Modifier.weight(1f),
            )
            Text(tr("Change"), style = fig(14, FontWeight.Bold, Tokens.Blue))
        }
    }
}

/** Mark ready: asks when it'll be delivered (starts on the order's date, if any). */
@Composable
fun ReadySheet(state: LaundryState, orderId: Int, vm: ShopViewModel, onDismiss: () -> Unit) {
    val o = Selectors.order(state, orderId) ?: return onDismiss()
    val c = Selectors.customer(state, o.custId)
    var date by remember { mutableStateOf(o.deliveryDate) }
    val tour by vm.tour.collectAsStateWithLifecycle()
    AppBottomSheet(title = tr("Mark ready"), subtitle = "${c.name} · #${o.no()}", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (tour == TourStage.STATUS) TourTip(TourSteps.readySheet)
            DeliveryDateQuestion(o, date) { date = it }
            PrimaryButton(if (date.isBlank()) tr("Pick a delivery date") else tr("Mark ready"), enabled = date.isNotBlank(), height = 56.dp) {
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

    AppBottomSheet(title = tr("Count clothes"), subtitle = tr("{name} · #{no}. The bill is made after this.", "name" to c.name, "no" to o.no()), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ClothesEditor(state.services, clothes, editablePrice = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Total"), style = fig(15, FontWeight.SemiBold, Tokens.Muted))
                Text(Money.rupees(total), style = bric(24, FontWeight.Bold))
            }
            if (askDate) DeliveryDateQuestion(o, date) { date = it }
            PrimaryButton(
                when {
                    askDate && date.isBlank() -> tr("Pick a delivery date")
                    next == OrderStatus.READY -> tr("Mark ready · make bill")
                    else -> tr("Picked up · make bill")
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
    val title = if (isPickup) tr("Reschedule pickup") else if (o.deliveryDate.isNotBlank()) tr("Reschedule delivery") else tr("Set delivery date")
    val chips = listOf(tr("Today") to AppDate.TODAY, tr("Tomorrow") to AppDate.add(AppDate.TODAY, 1), tr("Day after") to AppDate.add(AppDate.TODAY, 2))

    AppBottomSheet(title = title, subtitle = "${c.name} · #${o.no()}", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { (label, iso) -> PillChip(label, date == iso) { date = iso } }
            }
            // Any date, past ones included — e.g. entering an order after the fact.
            val context = LocalContext.current
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).rounded(12.dp).background(Tokens.Card)
                    .border(1.5.dp, Tokens.FieldBorder, RoundedCornerShape(12.dp))
                    .tap { showDatePicker(context, date.ifBlank { AppDate.TODAY }, if (isPickup) null else o.pickupDate) { date = it } }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = Tokens.Blue, modifier = Modifier.size(20.dp))
                Text(if (date.isNotBlank()) tr("Selected: {date}", "date" to AppDate.short(date)) else tr("Pick a date"), style = fig(15, FontWeight.SemiBold, if (date.isNotBlank()) Tokens.Ink else Tokens.Muted), modifier = Modifier.weight(1f))
                Text(tr("Change"), style = fig(14, FontWeight.Bold, Tokens.Blue))
            }
            if (isPickup && date.isNotBlank() && o.deliveryDate.isNotBlank() && o.ddAuto) {
                val shift = AppDate.daysBetween(o.pickupDate, date)
                Text(tr("Delivery moves too: {date}", "date" to AppDate.short(AppDate.add(o.deliveryDate, shift))), style = fig(13, color = Tokens.Muted))
            }
            PrimaryButton(if (date.isBlank()) tr("Pick a date to save") else tr("Save"), height = 56.dp, enabled = date.isNotBlank()) {
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
    val isBill = o.lines.isNotEmpty()
    val paidAny = state.ledger.any { it.ref == o.id && it.kind == LedgerKind.GOT }
    val first = Selectors.firstName(c.name)
    AppBottomSheet(title = if (isBill) tr("Delete bill #{no}?", "no" to o.no()) else tr("Delete order #{no}?", "no" to o.no()), subtitle = "${c.name} · ${Money.rupees(LaundryMath.amtOf(o))}", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                when {
                    isBill && paidAny -> tr("The bill is removed from orders, earnings and {name}'s khata, along with the payments recorded on it. You can undo right after.", "name" to first)
                    isBill -> tr("The bill is removed from orders, earnings and {name}'s khata. You can undo right after.", "name" to first)
                    paidAny -> tr("The order is removed from orders, earnings and {name}'s khata, along with the payments recorded on it. You can undo right after.", "name" to first)
                    else -> tr("The order is removed from orders, earnings and {name}'s khata. You can undo right after.", "name" to first)
                },
                style = fig(14, FontWeight.SemiBold, Tokens.OrangeDeep),
                modifier = Modifier.fillMaxWidth().rounded(12.dp).background(Tokens.OrangeLight).padding(14.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(56.dp).rounded(14.dp).background(Tokens.NeutralFill).tap { onDismiss() }, contentAlignment = Alignment.Center) {
                    Text(tr("Keep"), style = fig(16, FontWeight.Bold, Tokens.InkSecondary))
                }
                PrimaryButton(if (isBill) tr("Delete bill") else tr("Delete order"), Modifier.weight(1f), height = 56.dp, bg = Tokens.Orange) {
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
    // Saved on the order in English (cancelReason); shown in the app's language.
    val reasons = listOf(i18n("Customer not home"), i18n("Customer cancelled"), i18n("Wrong address"), i18n("Other"))
    val isPickup = o.status == OrderStatus.CREATED

    AppBottomSheet(title = if (isPickup) tr("Cancel pickup?") else tr("Cancel order?"), subtitle = "${c.name} · #${o.no()}", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reasons.forEach { r -> PillChip(tr(r), reason == r) { reason = if (reason == r) "" else r } }
            }
            if (o.status == OrderStatus.DELIVERED) {
                Text(tr("The bill comes off the khata. Money already taken stays as {name}'s advance.", "name" to Selectors.firstName(c.name)), style = fig(13, color = Tokens.Muted))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(56.dp).rounded(14.dp).background(Tokens.NeutralFill).tap { onDismiss() }, contentAlignment = Alignment.Center) {
                    Text(tr("Keep order"), style = fig(16, FontWeight.Bold, Tokens.InkSecondary))
                }
                PrimaryButton(if (isPickup) tr("Cancel pickup") else tr("Cancel order"), Modifier.weight(1f), height = 56.dp, bg = Tokens.Orange) { vm.cancelOrder(orderId, reason); onDismiss() }
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
    val images by rememberBillImages(vm, listOf(com.dailyworks.apnalaundry.core.I18n.english { BillReceipt.of(state, o) }), equalHeight = false)
    AppBottomSheet(title = tr("Bill #{no}", "no" to o.no()), subtitle = tr("{name} · this is what the customer sees", "name" to c.name), onDismiss = onDismiss) {
        val img = images?.firstOrNull()
        if (img == null) {
            Box(Modifier.fillMaxWidth().height(420.dp).rounded(16.dp).background(Tokens.Card))
        } else {
            Image(
                img, contentDescription = tr("Bill #{no} · total {amount}", "no" to o.no(), "amount" to Money.rupees(total)),
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
        if (o.pickup == Route.HOME) add(OrderStatus.CREATED to tr("To pick up"))
        add(OrderStatus.RECEIVED to tr("Received · clothes at shop"))
        add(OrderStatus.READY to tr("Ready"))
        add(OrderStatus.DELIVERED to tr("Delivered"))
        add(OrderStatus.CANCELLED to tr("Cancelled"))
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

    AppBottomSheet(title = tr("Change status"), subtitle = "${c.name} · #${o.no()}", onDismiss = onDismiss) {
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
                    if (current) Text(tr("Current"), style = fig(12, FontWeight.Bold, Tokens.Muted))
                }
            }
            if (o.status == OrderStatus.DELIVERED && sel != OrderStatus.DELIVERED) {
                Text(
                    tr("Going back takes the bill off the khata. Money already taken stays with the order and counts when you deliver again."),
                    style = fig(13, color = Tokens.Muted),
                )
            }
            PrimaryButton(
                when (sel) {
                    OrderStatus.DELIVERED -> if (o.lines.isEmpty()) tr("Count clothes · deliver") else tr("Mark delivered")
                    OrderStatus.CANCELLED -> tr("Cancel order")
                    OrderStatus.CREATED -> tr("Move to To pick up")
                    OrderStatus.RECEIVED -> tr("Move to Received")
                    OrderStatus.READY -> tr("Move to Ready")
                },
                height = 56.dp, bg = if (sel == OrderStatus.CANCELLED) Tokens.Orange else Tokens.Blue,
                enabled = sel != o.status,
            ) { apply(sel) }
        }
    }
}
