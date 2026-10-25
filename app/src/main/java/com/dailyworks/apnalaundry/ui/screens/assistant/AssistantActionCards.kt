package com.dailyworks.apnalaundry.ui.screens.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens

// The cards that change the shop: one tap each, through the same ShopViewModel
// commands as the app's own sheets (so Undo, the khata maths and the
// "Tell the customer?" WhatsApp question all work as usual).

@Composable
private fun Muted(text: String) = Text(text, style = fig(14, color = Tokens.Muted))

@Composable
private fun Bullet(text: String) = Text("• $text", style = fig(14, FontWeight.SemiBold))

/** Cash / UPI, switchable before the tap. */
@Composable
private fun MethodPicker(method: PayMethod, onPick: (PayMethod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(PayMethod.CASH to "Cash", PayMethod.UPI to "UPI").forEach { (m, label) ->
            val on = m == method
            Box(
                Modifier.heightIn(min = 40.dp).rounded(999.dp).background(if (on) Tokens.Ink else Tokens.Card)
                    .border(1.5.dp, if (on) Tokens.Ink else Tokens.CardBorder, RoundedCornerShape(999.dp))
                    .tap { onPick(m) }.padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = fig(14, FontWeight.Bold, if (on) Tokens.OnDark else Tokens.Ink)) }
        }
    }
}

private fun khataWords(bal: Int) = when {
    bal > 0 -> "${Money.rupees(bal)} baaki"
    bal < 0 -> "${Money.rupees(bal)} advance"
    else -> "all clear"
}

@Composable
internal fun StatusChangeCard(
    card: AssistantCard.StatusChange, key: String, used: Set<String>, state: LaundryState,
    vm: AssistantViewModel, shopVm: ShopViewModel,
) {
    val o = Selectors.order(state, card.orderId)
    var method by remember { mutableStateOf(card.payments.singleOrNull()?.first ?: PayMethod.CASH) }
    CardBox {
        if (o == null) { Muted("This order was deleted"); return@CardBox }
        val c = Selectors.customer(state, o.custId)
        val to = statusLabel(card.to)
        Text("#${o.no()} · ${c.name}", style = bric(18, FontWeight.Bold))
        Text("${statusLabel(o.status)} → $to", style = fig(15, FontWeight.Bold, Tokens.BlueText))
        val pays = if (card.payments.size == 1) listOf(method to card.payments[0].second) else card.payments
        if (card.to == OrderStatus.DELIVERED) {
            val total = LaundryMath.amtOf(o)
            val oldBal = LaundryMath.balance(o.custId, state.ledger, state.orders, o.id)
            val got = pays.sumOf { it.second }
            val alloc = LaundryMath.deliverAllocation(total, o.pre, oldBal, got)
            AmountRow("Bill", Money.rupees(total))
            if (o.pre > 0) AmountRow("Paid before", Money.rupees(o.pre))
            if (pays.isEmpty()) Muted("Nothing paid now — all on khata")
            else pays.forEach { (m, amt) -> AmountRow("Got now · ${if (m == PayMethod.UPI) "UPI" else "Cash"}", Money.rupees(amt), bold = true) }
            if (card.payments.size == 1) MethodPicker(method) { method = it }
            Muted("Khata after: ${khataWords(alloc.newBalance)}")
        }
        if (o.status == OrderStatus.DELIVERED) Muted("Its bill comes off the khata; payments stay as paid in advance.")
        if (card.to == OrderStatus.READY && card.deliveryDate.isNotBlank()) Muted("Delivery ${AppDate.short(card.deliveryDate)}")
        if (card.to == OrderStatus.CANCELLED && card.reason.isNotBlank()) Muted("Reason: ${card.reason}")
        when {
            key in used || o.status == card.to -> DoneText("Done ✓ $to")
            else -> PrimaryButton(
                AssistantTools.statusButton(o.status, card.to),
                Modifier.fillMaxWidth(), height = 48.dp, bg = if (card.to == OrderStatus.CANCELLED) Tokens.Orange else Tokens.Blue,
            ) {
                val now = Selectors.order(shopVm.state.value, o.id) ?: return@PrimaryButton
                val needsClothes = now.lines.isEmpty() && (card.to == OrderStatus.DELIVERED ||
                    (card.to == OrderStatus.READY && LaundryMath.isOpen(now)) || (card.to == OrderStatus.RECEIVED && now.status == OrderStatus.CREATED))
                if (needsClothes) { shopVm.showInfo("Add the clothes to order #${now.no()} first"); return@PrimaryButton }
                when {
                    card.to == OrderStatus.CANCELLED -> shopVm.cancelOrder(now.id, card.reason)
                    card.to == OrderStatus.DELIVERED -> {
                        val cash = pays.filter { it.first == PayMethod.CASH }.sumOf { it.second }
                        val upi = pays.filter { it.first == PayMethod.UPI }.sumOf { it.second }
                        when {
                            cash > 0 && upi > 0 -> shopVm.deliverSplit(now.id, cash, upi)
                            upi > 0 -> shopVm.deliver(now.id, upi, PayMethod.UPI)
                            else -> shopVm.deliver(now.id, cash, PayMethod.CASH)
                        }
                    }
                    card.to == OrderStatus.READY && LaundryMath.isOpen(now) -> shopVm.markReady(now.id, card.deliveryDate)
                    card.to == OrderStatus.RECEIVED && now.status == OrderStatus.CREATED -> shopVm.markPickedUp(now.id)
                    else -> shopVm.setStatus(now.id, card.to)
                }
                vm.used(key, "change_status", "Moved order #${now.no()} of ${c.name} to $to")
            }
        }
    }
}

@Composable
internal fun PaymentCard(
    card: AssistantCard.Payment, key: String, used: Set<String>, state: LaundryState,
    vm: AssistantViewModel, shopVm: ShopViewModel,
) {
    var method by remember { mutableStateOf(card.method) }
    val c = state.customers.firstOrNull { it.id == card.custId }
    CardBox {
        if (c == null) { Muted("This customer was deleted"); return@CardBox }
        val o = card.orderId?.let { Selectors.order(state, it) }
        Text("Payment · ${c.name}", style = bric(18, FontWeight.Bold))
        AmountRow(if (o != null) "On order #${o.no()} (paid in advance)" else "To khata", Money.rupees(card.amount), bold = true)
        MethodPicker(method) { method = it }
        Muted("Khata after: ${khataWords(Selectors.balance(state, c.id) - card.amount)}")
        if (key in used) DoneText("Recorded ✓")
        else PrimaryButton("Got ${Money.rupees(card.amount)} ${if (method == PayMethod.UPI) "UPI" else "cash"}", Modifier.fillMaxWidth(), height = 48.dp) {
            val now = card.orderId?.let { Selectors.order(shopVm.state.value, it) }
            if (now != null && LaundryMath.isOpen(now)) shopVm.prepayPart(now.id, card.amount, method)
            else shopVm.receivePayment(c.id, card.amount, method)
            vm.used(key, "payment", "Recorded ${Money.rupees(card.amount)} ${method.name.lowercase()} from ${c.name}")
        }
    }
}

@Composable
internal fun EditOrderCard(
    card: AssistantCard.EditOrder, key: String, used: Set<String>, state: LaundryState,
    vm: AssistantViewModel, shopVm: ShopViewModel,
) {
    val o = Selectors.order(state, card.orderId)
    CardBox {
        if (o == null) { Muted("This order was deleted"); return@CardBox }
        Text("Edit order #${o.no()} · ${Selectors.customer(state, o.custId).name}", style = bric(18, FontWeight.Bold))
        card.changes.forEach { Bullet(it) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
        AmountRow("Total", "${Money.rupees(card.before)} → ${Money.rupees(card.after)}", bold = true)
        if (o.status == OrderStatus.DELIVERED && card.after != card.before) {
            Muted("Khata ${if (card.after > card.before) "+" else "−"}${Money.rupees(kotlin.math.abs(card.after - card.before))} (delivered order)")
        }
        if (key in used) DoneText("Saved ✓")
        else PrimaryButton("Save changes", Modifier.fillMaxWidth(), height = 48.dp) {
            // Worked out again on the order as it is now, so an earlier change isn't undone.
            val plan = runCatching { AssistantTools(shopVm.state.value).planEdit(card.args) }.getOrElse {
                shopVm.showInfo(it.message ?: "Couldn't change the order"); return@PrimaryButton
            }
            val n = plan.order; val e = plan.edit
            shopVm.saveOrder(
                editId = n.id, custId = n.custId, pickup = n.pickup, delivery = n.delivery,
                pickupDate = e.pickupDate, pickupTime24 = AppDate.to24h(n.pickupTime),
                deliveryDate = e.deliveryDate, deliveryTime24 = AppDate.to24h(n.deliveryTime), ddAuto = e.ddAuto,
                fee = n.fee, express = e.express, exAmt = e.exAmt, discount = e.discount,
                lines = e.lines, quickAmount = e.quickAmount, quickPieces = e.quickPieces, serialNo = n.serialNo,
                exPct = e.exPct, discPct = e.discPct, gstOn = n.gstOn, gstPct = n.gstPct, gstMode = n.gstMode, note = e.note,
            ) { vm.used(key, "edit_order", "Changed order #${n.no()}: " + plan.changes.joinToString("; ")) }
        }
    }
}

@Composable
internal fun RateChangeCard(
    card: AssistantCard.RateChange, key: String, used: Set<String>, vm: AssistantViewModel, shopVm: ShopViewModel,
) {
    val deleting = card.args["action"]?.toString()?.contains("delete_service") == true
    CardBox {
        Text(if (card.isNew) "New service" else "Rate card", style = bric(18, FontWeight.Bold))
        card.summary.forEach { Bullet(it) }
        if (!card.isNew && !deleting) Muted("Old orders keep their prices.")
        if (key in used) DoneText("Saved ✓")
        else PrimaryButton(
            when { deleting -> "Delete service"; card.isNew -> "Add service"; else -> "Save to rate card" },
            Modifier.fillMaxWidth(), height = 48.dp, bg = if (deleting) Tokens.Orange else Tokens.Blue,
        ) {
            val plan = runCatching { AssistantTools(shopVm.state.value).planRate(card.args) }.getOrElse {
                shopVm.showInfo(it.message ?: "Couldn't change the rate card"); return@PrimaryButton
            }
            plan.service?.let { s ->
                shopVm.upsertService(s)
                if (plan.isNew) Analytics.serviceAdded(s.mode.name) else Analytics.serviceEdited(s.id)
                shopVm.showInfo(if (plan.isNew) "${s.name} added to your services" else "${s.name} updated")
            }
            plan.deleteId?.let { shopVm.deleteService(it) }
            vm.used(key, "rate_card", "Rate card: " + plan.summary.joinToString("; "))
        }
    }
}

@Composable
internal fun SaveCustomerCard(
    card: AssistantCard.SaveCustomer, key: String, used: Set<String>, vm: AssistantViewModel, shopVm: ShopViewModel,
) {
    CardBox {
        Text(if (card.editId == null) "New customer" else "Customer · ${card.name}", style = bric(18, FontWeight.Bold))
        card.changes.forEach { Bullet(it) }
        if (key in used) DoneText("Saved ✓")
        else PrimaryButton(if (card.editId == null) "Add customer" else "Save", Modifier.fillMaxWidth(), height = 48.dp) {
            val dup = shopVm.state.value.customers.firstOrNull { it.phone == card.phone && it.id != card.editId }
            if (dup != null) { shopVm.showInfo("${Selectors.fmtPhone(card.phone)} is already saved for ${dup.name}"); return@PrimaryButton }
            shopVm.saveCustomer(card.editId, card.name, card.phone, card.address, card.oldBaaki, fromList = card.editId == null) {
                vm.used(key, "customer", (if (card.editId == null) "Added customer " else "Updated customer ") + card.name)
            }
        }
    }
}

@Composable
internal fun OldBaakiCard(
    card: AssistantCard.OldBaaki, key: String, used: Set<String>, state: LaundryState, vm: AssistantViewModel, shopVm: ShopViewModel,
) {
    val c = state.customers.firstOrNull { it.id == card.custId }
    CardBox {
        if (c == null) { Muted("This customer was deleted"); return@CardBox }
        Text("Old baaki · ${c.name}", style = bric(18, FontWeight.Bold))
        AmountRow("Add to khata", "+ ${Money.rupees(card.amount)}", bold = true)
        Muted("Khata after: ${khataWords(Selectors.balance(state, c.id) + if (key in used) 0 else card.amount)}")
        if (key in used) DoneText("Added ✓")
        else PrimaryButton("Add ${Money.rupees(card.amount)} baaki", Modifier.fillMaxWidth(), height = 48.dp) {
            shopVm.addOldBaaki(c.id, card.amount)
            vm.used(key, "old_baaki", "Added ${Money.rupees(card.amount)} old baaki for ${c.name}")
        }
    }
}

@Composable
internal fun DeleteOrderCard(
    card: AssistantCard.DeleteOrder, key: String, used: Set<String>, state: LaundryState, vm: AssistantViewModel, shopVm: ShopViewModel,
) {
    val o = Selectors.order(state, card.orderId)
    CardBox {
        if (o == null) { DoneText(if (key in used) "Deleted ✓" else "Already deleted"); return@CardBox }
        val c = Selectors.customer(state, o.custId)
        Text("Delete order #${o.no()} · ${c.name}?", style = bric(18, FontWeight.Bold))
        AmountRow("${Selectors.itemsLabel(o).ifBlank { "No clothes" }} · ${statusLabel(o.status)}", Money.rupees(LaundryMath.amtOf(o)))
        Muted("Its bill and payments come off the khata. You can Undo right after.")
        PrimaryButton("Delete order", Modifier.fillMaxWidth(), height = 48.dp, bg = Tokens.DeleteRed) {
            shopVm.deleteOrder(o.id)
            vm.used(key, "delete_order", "Deleted order #${o.no()} of ${c.name}")
        }
    }
}
