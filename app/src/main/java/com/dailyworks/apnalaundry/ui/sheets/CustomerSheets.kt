package com.dailyworks.apnalaundry.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.PayMethod
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppBottomSheet
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.PillChip
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens
import kotlin.math.max

@Composable
fun CustomerFormSheet(state: LaundryState, form: ActiveSheet.CustomerForm, vm: ShopViewModel, onDismiss: () -> Unit) {
    val editing = form.editId != null
    val existing = form.editId?.let { id -> state.customers.firstOrNull { it.id == id } }
    var name by remember { mutableStateOf(existing?.name ?: form.prefillName) }
    var phone by remember { mutableStateOf(existing?.phone ?: form.prefillPhone) }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var oldBaaki by remember { mutableStateOf("") }

    val dup = if (phone.length == 10) state.customers.firstOrNull { it.phone == phone && !(editing && it.id == form.editId) } else null
    val addrNeeded = form.needAddress && form.ctx == "order"
    val valid = name.trim().isNotEmpty() && phone.length == 10 && dup == null && (!addrNeeded || address.trim().isNotEmpty())

    AppBottomSheet(title = if (editing) "Edit customer" else "New customer", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Name", name, { name = it }, "Customer name")
            Field("Phone", phone, { phone = it.filter { c -> c.isDigit() }.take(10) }, "10-digit number", prefix = "+91", kb = KeyboardType.Phone)
            if (dup != null) {
                Row(Modifier.fillMaxWidth().rounded(12.dp).background(Tokens.OrangeLight).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Already saved as ${dup.name}", style = fig(14, FontWeight.SemiBold, Tokens.OrangeDeep), modifier = Modifier.weight(1f))
                    Text("Use this", style = fig(14, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { form.onSaved(dup.id); onDismiss() })
                }
            }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Address", style = fig(14, FontWeight.SemiBold))
                    Text(if (addrNeeded) "Needed for home pickup / delivery" else "Optional", style = fig(12, color = if (addrNeeded) Tokens.OrangeText else Tokens.Muted))
                }
                Box(Modifier.height(6.dp))
                FieldBox(address, { address = it }, placeholder = "House, area, landmark", height = 52.dp,
                    borderColor = if (addrNeeded && address.isBlank()) Tokens.OrangeBorder else Tokens.FieldBorder)
            }
            if (form.ctx == "list" && !editing) {
                Field("Old baaki from your notebook", oldBaaki, { oldBaaki = it.filter { c -> c.isDigit() }.take(6) }, "0", prefix = "₹", kb = KeyboardType.Number)
            }
            PrimaryButton(if (editing) "Save changes" else "Save customer", enabled = valid, height = 56.dp) {
                vm.saveCustomer(form.editId, name.trim(), phone, address.trim(), oldBaaki.toIntOrNull() ?: 0, form.ctx == "list") { id ->
                    form.onSaved(id)
                }
                onDismiss()
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, placeholder: String, prefix: String? = null, kb: KeyboardType = KeyboardType.Text) {
    Column {
        Text(label, style = fig(14, FontWeight.SemiBold))
        Box(Modifier.height(6.dp))
        FieldBox(value, onChange, placeholder = placeholder, prefix = prefix, height = 52.dp, keyboardType = kb)
    }
}

@Composable
fun ReceivePaymentSheet(state: LaundryState, custId: String, vm: ShopViewModel, onDismiss: () -> Unit) {
    val c = Selectors.customer(state, custId)
    val bal = Selectors.balance(state, custId)
    var amt by remember { mutableStateOf(if (bal > 0) bal.toString() else "") }
    val got = amt.toIntOrNull() ?: 0
    val left = bal - got

    val subtitle = when {
        bal > 0 -> "${Selectors.firstName(c.name)} has to pay ${Money.rupees(bal)}"
        bal < 0 -> "${Selectors.firstName(c.name)} already has ${Money.rupees(bal)} advance"
        else -> "Nothing due. Anything you take becomes advance."
    }

    AppBottomSheet(title = "Receive payment", subtitle = subtitle, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FieldBox(amt, { amt = it.filter { ch -> ch.isDigit() }.take(6) }, prefix = "₹", height = 56.dp, keyboardType = KeyboardType.Number, textStyle = bric(22, FontWeight.Bold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (bal > 0) PillChip("Full ${Money.rupees(bal)}", amt == bal.toString()) { amt = bal.toString() }
                listOf(100, 200, 500).forEach { v -> if (v != bal) PillChip(Money.rupees(v), amt == v.toString()) { amt = v.toString() } }
            }
            val (bg, fg, text) = when {
                amt.isEmpty() -> Triple(Tokens.Bg, Tokens.Muted, "Type the amount you got")
                left > 0 -> Triple(Tokens.OrangeLight, Tokens.OrangeDeep, "${Money.rupees(left)} will still be baaki")
                left < 0 -> Triple(Tokens.BlueLight, Tokens.BlueText, "${Money.rupees(left)} extra — kept as advance for next bill")
                else -> Triple(Tokens.NeutralFill, Tokens.Ink, "Full payment — all clear")
            }
            Box(Modifier.fillMaxWidth().rounded(12.dp).background(bg).padding(14.dp)) { Text(text, style = fig(14, FontWeight.SemiBold, fg)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Got cash", Modifier.weight(1f), enabled = got > 0, height = 54.dp) { vm.receivePayment(custId, got, PayMethod.CASH); onDismiss() }
                PrimaryButton("Got UPI", Modifier.weight(1f), enabled = got > 0, height = 54.dp) { vm.receivePayment(custId, got, PayMethod.UPI); onDismiss() }
            }
        }
    }
}

@Composable
fun AddOldBaakiSheet(state: LaundryState, custId: String, vm: ShopViewModel, onDismiss: () -> Unit) {
    val c = Selectors.customer(state, custId)
    val bal = max(0, Selectors.balance(state, custId))
    var amt by remember { mutableStateOf("") }
    val got = amt.toIntOrNull() ?: 0
    AppBottomSheet(title = "Add old baaki", subtitle = "Money ${Selectors.firstName(c.name)} owed you before you started using the app", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FieldBox(amt, { amt = it.filter { ch -> ch.isDigit() }.take(6) }, prefix = "₹", height = 56.dp, keyboardType = KeyboardType.Number, textStyle = bric(22, FontWeight.Bold))
            Box(Modifier.fillMaxWidth().rounded(12.dp).background(Tokens.Bg).padding(14.dp)) {
                Text(if (amt.isEmpty()) "Baaki now: ${Money.rupees(bal)}" else "Baaki will become ${Money.rupees(bal + got)}", style = fig(14, FontWeight.SemiBold, Tokens.InkSecondary))
            }
            PrimaryButton("Add old baaki", enabled = got > 0, height = 54.dp) { vm.addOldBaaki(custId, got); onDismiss() }
        }
    }
}
