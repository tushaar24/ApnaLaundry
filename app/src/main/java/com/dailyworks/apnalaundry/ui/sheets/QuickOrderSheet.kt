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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.PayMethod
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppBottomSheet
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.PillChip
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens

@Composable
fun QuickOrderSheet(state: LaundryState, day: String, onCreated: (Int) -> Unit, vm: ShopViewModel, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var pickedId by remember { mutableStateOf<String?>(null) }
    var amt by remember { mutableStateOf("") }
    var pcs by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf("") } // "", "Cash", "UPI"

    val q = input.trim()
    val qd = q.filter { it.isDigit() }
    val isPhone = q.isNotEmpty() && q.all { it.isDigit() || it == ' ' || it == '+' }
    val picked = pickedId?.let { id -> state.customers.firstOrNull { it.id == id } }
    val matches = if (picked == null && q.isNotEmpty())
        state.customers.filter { it.name.contains(q, true) || (qd.length >= 3 && it.phone.contains(qd)) }.take(3)
    else emptyList()
    val amount = amt.toIntOrNull() ?: 0
    val valid = picked != null || (if (isPhone) qd.length >= 10 else q.length >= 2)

    AppBottomSheet(title = "Quick order", subtitle = "Just the customer. Amount and clothes can be added later.", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (picked != null) {
                Row(Modifier.fillMaxWidth().rounded(14.dp).background(Tokens.BlueLight).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(picked.name, style = fig(16, FontWeight.Bold))
                        Text(if (picked.phone.isNotBlank()) "+91 ${Selectors.fmtPhone(picked.phone)}" else "No phone", style = fig(13, color = Tokens.InkSecondary))
                    }
                    Text("Change", style = fig(14, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { pickedId = null; input = "" })
                }
            } else {
                FieldBox(input, { input = it; pickedId = null }, placeholder = "Phone number or name", height = 54.dp)
                matches.forEach { c ->
                    Row(Modifier.fillMaxWidth().tap { pickedId = c.id; input = c.name }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = fig(15, FontWeight.Bold))
                            Text(Selectors.fmtPhone(c.phone), style = fig(13, color = Tokens.Muted))
                        }
                    }
                }
                if (picked == null && valid && matches.isEmpty()) {
                    Text(
                        if (isPhone) "New customer · +91 ${Selectors.fmtPhone(qd.takeLast(10))} (add name later)"
                        else "New customer · ${q.replaceFirstChar { it.uppercase() }}",
                        style = fig(13, FontWeight.SemiBold, Tokens.Blue),
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldBox(amt, { amt = it.filter { ch -> ch.isDigit() }.take(6) }, prefix = "₹", placeholder = "Amount", modifier = Modifier.weight(1f), height = 52.dp, keyboardType = KeyboardType.Number)
                FieldBox(pcs, { pcs = it.filter { ch -> ch.isDigit() }.take(3) }, placeholder = "Pieces", modifier = Modifier.weight(1f), height = 52.dp, keyboardType = KeyboardType.Number)
            }

            if (amount > 0) {
                Text("Paid now?", style = fig(14, FontWeight.SemiBold))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("" to "Not yet", "Cash" to "Cash", "UPI" to "UPI").forEach { (v, label) ->
                        PillChip(label, paid == v) { paid = v }
                    }
                }
            }

            PrimaryButton("Save order", enabled = valid, height = 56.dp) {
                val method = when (paid) { "Cash" -> PayMethod.CASH; "UPI" -> PayMethod.UPI; else -> null }
                vm.createQuick(pickedId, input.trim(), amount, pcs.toIntOrNull() ?: 0, if (amount > 0) method else null, day) { id -> onCreated(id) }
                onDismiss()
            }
        }
    }
}
