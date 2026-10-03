package com.dailyworks.apnalaundry.ui.sheets

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.OrderStatus
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
    val oldBal = Selectors.balance(state, o.custId)
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

@Composable
fun CountClothesSheet(state: LaundryState, orderId: Int, next: OrderStatus, vm: ShopViewModel, onDismiss: () -> Unit) {
    val o = Selectors.order(state, orderId) ?: return onDismiss()
    val c = Selectors.customer(state, o.custId)
    val clothes = rememberClothesState(state.services)
    val total = clothes.total(state.services)

    AppBottomSheet(title = "Count clothes", subtitle = "${c.name} · #${o.id}. The bill is made after this.", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ClothesEditor(state.services, clothes, editablePrice = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Total", style = fig(15, FontWeight.SemiBold, Tokens.Muted))
                Text(Money.rupees(total), style = bric(26, FontWeight.Bold))
            }
            PrimaryButton(
                if (next == OrderStatus.READY) "Mark ready · make bill" else "Picked up · make bill",
                enabled = total > 0, height = 56.dp,
            ) { vm.saveCount(orderId, next, clothes.lines(state.services)); onDismiss() }
        }
    }
}

@Composable
fun RescheduleSheet(state: LaundryState, orderId: Int, kind: String, vm: ShopViewModel, onDismiss: () -> Unit) {
    val o = Selectors.order(state, orderId) ?: return onDismiss()
    val c = Selectors.customer(state, o.custId)
    val isPickup = kind == "pickup"
    val base = if (isPickup) o.pickupDate else o.deliveryDate.ifBlank { AppDate.TODAY }
    var date by remember { mutableStateOf(base) }
    var time by remember { mutableStateOf(if (isPickup) AppDate.to24h(o.pickupTime) else AppDate.to24h(o.deliveryTime)) }
    var notify by remember { mutableStateOf(true) }

    val title = if (isPickup) "Reschedule pickup" else if (o.deliveryDate.isNotBlank()) "Reschedule delivery" else "Set delivery date"
    val chips = listOf("Today" to AppDate.TODAY, "Tomorrow" to AppDate.add(AppDate.TODAY, 1), "Day after" to AppDate.add(AppDate.TODAY, 2))

    AppBottomSheet(title = title, subtitle = "${c.name} · #${o.id}", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { (label, iso) -> PillChip(label, date == iso) { date = iso } }
            }
            Text("Selected: ${AppDate.short(date)}", style = fig(15, FontWeight.SemiBold))
            if (isPickup && o.deliveryDate.isNotBlank() && o.ddAuto) {
                val shift = AppDate.daysBetween(o.pickupDate, date)
                Text("Delivery moves too: ${AppDate.short(AppDate.add(o.deliveryDate, shift))}", style = fig(13, color = Tokens.Muted))
            }
            Row(Modifier.fillMaxWidth().rounded(12.dp).background(Tokens.Card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tell customer on WhatsApp", style = fig(15, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Toggle(notify) { notify = !notify }
            }
            PrimaryButton("Save", height = 56.dp) { vm.reschedule(orderId, kind, date, time, notify); onDismiss() }
        }
    }
}

@Composable
fun CancelSheet(state: LaundryState, orderId: Int, vm: ShopViewModel, onDismiss: () -> Unit) {
    val o = Selectors.order(state, orderId) ?: return onDismiss()
    val c = Selectors.customer(state, o.custId)
    var reason by remember { mutableStateOf("") }
    val reasons = listOf("Customer not home", "Customer cancelled", "Wrong address", "Other")

    AppBottomSheet(title = "Cancel pickup?", subtitle = "${c.name} · #${o.id}", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reasons.forEach { r -> PillChip(r, reason == r) { reason = if (reason == r) "" else r } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(54.dp).rounded(14.dp).background(Tokens.NeutralFill).tap { onDismiss() }, contentAlignment = Alignment.Center) {
                    Text("Keep order", style = fig(16, FontWeight.Bold, Tokens.InkSecondary))
                }
                PrimaryButton("Cancel pickup", Modifier.weight(1f), height = 54.dp, bg = Tokens.Orange) { vm.cancelOrder(orderId, reason); onDismiss() }
            }
        }
    }
}

@Composable
fun BillViewSheet(state: LaundryState, orderId: Int, onDismiss: () -> Unit) {
    val o = Selectors.order(state, orderId) ?: return onDismiss()
    val c = Selectors.customer(state, o.custId)
    val total = LaundryMath.amtOf(o)
    LaunchedEffect(orderId) { Analytics.billViewed(orderId) }
    AppBottomSheet(title = "Bill #${o.id}", subtitle = "${c.name} · this is what the customer sees", onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().rounded(16.dp).background(Tokens.Card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.shop.name, style = bric(22, FontWeight.Bold))
            Text("+91 ${Selectors.fmtPhone(state.shop.phone)}", style = fig(13, color = Tokens.Muted))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider).padding(vertical = 4.dp))
            Text("Bill #${o.id} · ${AppDate.plain(o.createdOn)}", style = fig(13, FontWeight.SemiBold, Tokens.Muted))
            Text("To: ${c.name}", style = fig(15, FontWeight.SemiBold))
            o.lines.forEach { l ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (l.kg > 0) "${l.serviceName} · ${Selectors.trimKg(l.kg)} kg" else "${l.itemName} × ${l.qty}", style = fig(15))
                    Text(Money.rupees(l.amt), style = fig(15, FontWeight.SemiBold))
                }
            }
            if (o.express && o.exAmt > 0) MoneyRow("Express", "+ ${Money.rupees(o.exAmt)}")
            if (o.fee > 0) MoneyRow("Pickup / delivery", "+ ${Money.rupees(o.fee)}")
            if (o.discount > 0) MoneyRow("Discount", "− ${Money.rupees(o.discount)}", Tokens.OrangeText)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider).padding(vertical = 4.dp))
            MoneyRow("Total", Money.rupees(total), bold = true)
            Spacer(Modifier.height(4.dp))
            Text("Thank you!", style = fig(15, FontWeight.Bold, Tokens.Blue))
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
