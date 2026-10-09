package com.dailyworks.apnalaundry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.OrderLine
import com.dailyworks.apnalaundry.domain.PricingMode
import com.dailyworks.apnalaundry.domain.Service
import com.dailyworks.apnalaundry.ui.theme.Tokens
import kotlin.math.max
import kotlin.math.roundToInt

/** Mutable holder for the clothes being counted / added on an order. */
class ClothesState(services: List<Service>) {
    // svcId -> (itemName -> qty)
    val qty = mutableStateMapOf<String, MutableMap<String, Int>>()
    // svcId -> weight text
    val weight = mutableStateMapOf<String, String>()
    // svcId -> optional count of clothes in a by-weight bag (stored as the line's qty)
    val pcs = mutableStateMapOf<String, String>()
    // svcId -> (itemName -> price override text)
    val price = mutableStateMapOf<String, MutableMap<String, String>>()
    var selected by mutableStateOf(
        (services.firstOrNull { it.mode == PricingMode.PIECE } ?: services.firstOrNull())?.id ?: "",
    )

    fun qtyOf(svc: String, item: String): Int = qty[svc]?.get(item) ?: 0
    fun bump(svc: String, item: String, d: Int) {
        val m = qty.getOrPut(svc) { mutableMapOf() }.toMutableMap()
        m[item] = max(0, (m[item] ?: 0) + d)
        qty[svc] = m
    }

    fun setWeight(svc: String, v: String) { weight[svc] = v }
    fun setPcs(svc: String, v: String) { pcs[svc] = v }
    fun priceText(svc: String, item: String): String? = price[svc]?.get(item)
    fun setPrice(svc: String, item: String, v: String) {
        val m = price.getOrPut(svc) { mutableMapOf() }.toMutableMap(); m[item] = v; price[svc] = m
    }

    fun priceFor(svc: Service, item: String, base: Int): Int {
        val ov = price[svc.id]?.get(item)
        return if (!ov.isNullOrBlank()) ov.toIntOrNull() ?: 0 else base
    }

    fun lines(services: List<Service>): List<OrderLine> {
        val out = mutableListOf<OrderLine>()
        for (s in services) {
            if (s.mode == PricingMode.WEIGHT) {
                val kg = weight[s.id]?.toDoubleOrNull() ?: 0.0
                if (kg > 0) {
                    val rate = s.ratePerKg ?: 0
                    val min = s.minKg ?: 0.0
                    // qty on a weight line = clothes in the bag (0 = not counted)
                    val clothes = pcs[s.id]?.toIntOrNull() ?: 0
                    out += OrderLine(s.id, s.name, "By weight", clothes, rate, rate, kg, (max(kg, min) * rate).roundToInt())
                }
            } else {
                val q = qty[s.id] ?: emptyMap()
                for (it in s.items) {
                    val base = it.price ?: 0
                    val p = priceFor(s, it.name, base)
                    val c = q[it.name] ?: 0
                    if (c > 0 && p > 0) out += OrderLine(s.id, s.name, it.name, c, p, base, 0.0, c * p)
                }
            }
        }
        return out
    }

    fun total(services: List<Service>): Int = lines(services).sumOf { it.amt }
}

@Composable
fun rememberClothesState(services: List<Service>): ClothesState =
    remember(services.map { it.id }) { ClothesState(services) }

private fun serviceStat(state: ClothesState, s: Service): Triple<Int, Double, Int> {
    return if (s.mode == PricingMode.WEIGHT) {
        val kg = state.weight[s.id]?.toDoubleOrNull() ?: 0.0
        val amt = if (kg > 0) (max(kg, s.minKg ?: 0.0) * (s.ratePerKg ?: 0)).roundToInt() else 0
        Triple(0, kg, amt)
    } else {
        val q = state.qty[s.id] ?: emptyMap()
        var n = 0; var a = 0
        for (it in s.items) {
            val base = it.price ?: 0
            val p = state.priceFor(s, it.name, base)
            val c = q[it.name] ?: 0
            if (p > 0) { n += c; a += c * p }
        }
        Triple(n, 0.0, a)
    }
}

@Composable
fun ClothesEditor(
    services: List<Service>,
    state: ClothesState,
    editablePrice: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // tiles (2 columns)
        val rows = services.chunked(2)
        rows.forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { s ->
                    val (n, kg, amt) = serviceStat(state, s)
                    val has = amt > 0
                    val sel = state.selected == s.id
                    val sub = when {
                        has && s.mode == PricingMode.WEIGHT -> "${trimK(kg)} kg · ${Money.rupees(amt)}"
                        has -> "$n ${if (n == 1) "item" else "items"} · ${Money.rupees(amt)}"
                        s.mode == PricingMode.WEIGHT -> "By weight"
                        else -> "Tap to add"
                    }
                    ServiceTile(
                        name = s.name, sub = sub, selected = sel, hasAmount = has,
                        modifier = Modifier.weight(1f),
                    ) { state.selected = s.id }
                }
                if (pair.size == 1) Box(Modifier.weight(1f)) {}
            }
        }

        val cur = services.firstOrNull { it.id == state.selected } ?: return@Column
        if (cur.mode == PricingMode.PIECE) {
            Column {
                cur.items.filter { (it.price ?: 0) > 0 }.forEach { item ->
                    val base = item.price ?: 0
                    val c = state.qtyOf(cur.id, item.name)
                    ItemRow(
                        name = item.name, base = base, qty = c,
                        editablePrice = editablePrice,
                        priceText = state.priceText(cur.id, item.name) ?: base.toString(),
                        onPrice = { state.setPrice(cur.id, item.name, it.filter { ch -> ch.isDigit() }.take(5)) },
                        onDec = { state.bump(cur.id, item.name, -1) },
                        onInc = { state.bump(cur.id, item.name, 1) },
                    )
                }
            }
        } else {
            val rate = cur.ratePerKg ?: 0
            val min = cur.minKg ?: 0.0
            val w = state.weight[cur.id] ?: ""
            val kg = w.toDoubleOrNull() ?: 0.0
            val amt = if (kg > 0) (max(kg, min) * rate).roundToInt() else 0
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FieldBox(
                        value = w, onValueChange = { state.setWeight(cur.id, it.filter { ch -> ch.isDigit() || ch == '.' }) },
                        modifier = Modifier.weight(1f), placeholder = "0", suffix = "kg", height = 56.dp,
                        keyboardType = KeyboardType.Decimal, prefix = null,
                    )
                    Spacer8()
                    Text(Money.rupees(amt), style = bric(22, FontWeight.Bold))
                }
                val pc = state.pcs[cur.id] ?: ""
                FieldBox(
                    value = pc, onValueChange = { state.setPcs(cur.id, it.filter { ch -> ch.isDigit() }.take(4)) },
                    placeholder = "Number of clothes (optional)", suffix = if (pc.isNotEmpty()) "clothes" else null,
                    height = 48.dp, keyboardType = KeyboardType.Number,
                )
                Text("₹$rate per kg. Bags under ${trimK(min)} kg are charged for ${trimK(min)} kg.", style = fig(13, color = Tokens.Muted))
            }
        }
    }
}

@Composable
private fun Spacer8() = Box(Modifier.width(12.dp))

@Composable
fun ServiceTile(name: String, sub: String, selected: Boolean, hasAmount: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bd = if (selected) Tokens.Ink else if (hasAmount) Tokens.Blue else Tokens.CardBorder
    val bg = if (selected) Tokens.Ink else if (hasAmount) Tokens.BlueLight else Tokens.Card
    val fg = if (selected) Tokens.OnDark else Tokens.Ink
    val subFg = if (selected) Tokens.OnDarkMuted else if (hasAmount) Tokens.BlueText else Tokens.Muted
    Column(
        modifier
            .height(64.dp)
            .rounded(14.dp)
            .background(bg)
            .border(2.dp, bd, RoundedCornerShape(14.dp))
            .tap(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(name, style = fig(15, FontWeight.Bold, fg), maxLines = 1)
        Text(sub, style = fig(12, FontWeight.SemiBold, subFg), maxLines = 1)
    }
}

@Composable
private fun ItemRow(
    name: String, base: Int, qty: Int, editablePrice: Boolean, priceText: String,
    onPrice: (String) -> Unit, onDec: () -> Unit, onInc: () -> Unit,
) {
    val changed = editablePrice && priceText.toIntOrNull() != base && priceText.isNotBlank()
    Row(
        Modifier.fillMaxWidth().height(56.dp).border(0.dp, Tokens.Divider).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = fig(16, FontWeight.SemiBold), maxLines = 1)
            if (changed) Text("rate ₹$base", style = fig(12, FontWeight.SemiBold, Tokens.OrangeText))
        }
        if (editablePrice) {
            FieldBox(
                value = priceText, onValueChange = onPrice, prefix = "₹", height = 42.dp,
                modifier = Modifier.width(88.dp), keyboardType = KeyboardType.Number,
                textStyle = fig(16, FontWeight.Bold),
            )
        } else {
            Text("₹$base", style = fig(15, FontWeight.SemiBold, Tokens.Muted))
        }
        Stepper(qty = qty, onDec = onDec, onInc = onInc)
    }
}

private fun trimK(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
