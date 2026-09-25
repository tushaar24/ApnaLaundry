package com.dailyworks.apnalaundry.ui.screens.rates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.domain.PricingMode
import com.dailyworks.apnalaundry.domain.Service
import com.dailyworks.apnalaundry.domain.ServiceItem
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.PillChip
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.Segmented
import com.dailyworks.apnalaundry.ui.components.ServiceTile
import com.dailyworks.apnalaundry.ui.components.TopBar
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens

private fun readyLabel(n: Int?): String = when {
    n == null -> ""
    n == 0 -> "Same day"
    n == 1 -> "1 day"
    else -> "$n days"
}

private fun reminderLabel(closeTime: String): String {
    val parts = closeTime.split(":")
    val total = (parts.getOrNull(0)?.toIntOrNull() ?: 21) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0) - 30
    val hh = total / 60
    val disp = if (hh % 12 == 0) 12 else hh % 12
    val ap = if (hh >= 12) "PM" else "AM"
    return "Reminder at $disp:${(total % 60).toString().padStart(2, '0')} $ap to update the day"
}

@Composable
fun RatesScreen(shopVm: ShopViewModel, from: String, onDone: () -> Unit, onBack: () -> Unit) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val services = state.services
    val setup = from == "setup"

    var shopName by remember { mutableStateOf(state.shop.name) }
    var closeTime by remember { mutableStateOf(state.shop.closeTime) }
    var expressPct by remember { mutableStateOf(state.shop.expressPct.toString()) }
    var selectedId by remember { mutableStateOf(services.firstOrNull()?.id ?: "") }

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars)) {
        if (!setup) TopBar("Rate card", onBack = onBack)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (setup) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Check, null, tint = Tokens.Blue, modifier = Modifier.size(18.dp))
                    Text("Number verified · Last step", style = fig(13, FontWeight.SemiBold, Tokens.Muted))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SHOP NAME", style = fig(13, FontWeight.Bold, Tokens.InkSecondary))
                    FieldBox(shopName, { shopName = it }, height = 52.dp, borderColor = Tokens.Blue, borderWidth = 2.dp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your rates", style = bric(26, FontWeight.Bold))
                Text("Tap a service. Tap any price to change it.", style = fig(15, color = Tokens.Muted))
            }

            // service tiles grid
            val tiles = services + listOf<Service?>(null) // null = new-service tile
            tiles.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { s ->
                        if (s == null) {
                            NewServiceTile(selectedId == "new", Modifier.weight(1f)) { selectedId = "new" }
                        } else {
                            val sub = (if (s.mode == PricingMode.WEIGHT) (if (s.ratePerKg != null) "₹${s.ratePerKg}/kg" else "By weight") else "Per piece") +
                                (if (readyLabel(s.readyInDays).isNotEmpty()) " · ${readyLabel(s.readyInDays)}" else "")
                            ServiceTile(s.name, sub, selectedId == s.id, false, Modifier.weight(1f)) { selectedId = s.id }
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f)) {}
                }
            }

            if (selectedId == "new") {
                NewServiceForm(services) { newSvc ->
                    shopVm.upsertService(newSvc); selectedId = newSvc.id
                }
            } else {
                val cur = services.firstOrNull { it.id == selectedId } ?: services.firstOrNull()
                if (cur != null) ServiceEditor(cur, services.size > 1, shopVm)
            }

            if (setup) {
                Column(Modifier.fillMaxWidth().rounded(16.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("When do you close the shop?", style = fig(15, FontWeight.Bold))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("20:00" to "8 PM", "21:00" to "9 PM", "22:00" to "10 PM", "23:00" to "11 PM").forEach { (v, label) ->
                            PillChip(label, closeTime == v) { closeTime = v }
                        }
                    }
                    Text(reminderLabel(closeTime), style = fig(13, color = Tokens.Muted))
                }
            }

            Row(Modifier.fillMaxWidth().rounded(16.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Express charge", style = fig(15, FontWeight.Bold))
                    Text("Added when you switch on Express in an order", style = fig(13, color = Tokens.Muted))
                }
                FieldBox(expressPct, { expressPct = it.filter { c -> c.isDigit() }.take(3) }, prefix = "+", suffix = "%", modifier = Modifier.width(96.dp), height = 44.dp, keyboardType = KeyboardType.Number, textStyle = fig(17, FontWeight.Bold))
            }
        }

        Column(Modifier.fillMaxWidth().background(Tokens.Card).padding(16.dp)) {
            PrimaryButton(if (setup) "Save and start taking orders" else "Save rates", height = 56.dp) {
                shopVm.updateShop(shopName.trim().ifBlank { "My Shop" }, closeTime, expressPct.toIntOrNull() ?: 50)
                onDone()
            }
        }
    }
}

@Composable
private fun NewServiceTile(selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(64.dp).rounded(14.dp)
            .background(if (selected) Tokens.BlueLight else Tokens.Bg)
            .border(2.dp, if (selected) Tokens.Blue else Tokens.DashBorder, RoundedCornerShape(14.dp))
            .tap(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Add, null, tint = if (selected) Tokens.Blue else Tokens.InkSecondary, modifier = Modifier.size(18.dp))
        Text("New service", style = fig(15, FontWeight.Bold, if (selected) Tokens.Blue else Tokens.InkSecondary))
    }
}

@Composable
private fun ReadyChips(current: Int?, onPick: (Int?) -> Unit) {
    var moreOpen by remember(current) { mutableStateOf(current != null && current >= 4) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ready in (optional)", style = fig(15, FontWeight.Bold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Same day", 1 to "1 day", 2 to "2 days", 3 to "3 days").forEach { (v, label) ->
                PillChip(label, current == v && !moreOpen) { onPick(if (current == v) null else v); moreOpen = false }
            }
            PillChip("More", moreOpen || (current != null && current >= 4)) {
                moreOpen = !moreOpen; if (!moreOpen) onPick(null)
            }
        }
        if (moreOpen || (current != null && current >= 4)) {
            FieldBox(
                if (current != null && current >= 4) current.toString() else "",
                { onPick(it.filter { c -> c.isDigit() }.take(2).toIntOrNull()) },
                placeholder = "4", suffix = "days", modifier = Modifier.width(120.dp), height = 44.dp, keyboardType = KeyboardType.Number,
            )
        }
    }
}

@Composable
private fun ServiceEditor(cur: Service, canDelete: Boolean, shopVm: ShopViewModel) {
    var renaming by remember(cur.id) { mutableStateOf(false) }
    var addName by remember(cur.id) { mutableStateOf("") }
    var addPrice by remember(cur.id) { mutableStateOf("") }

    Column(
        Modifier.fillMaxWidth().rounded(18.dp).background(Tokens.Card).border(2.dp, Tokens.Ink, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!renaming) {
                Text(cur.name, style = bric(22, FontWeight.Bold), modifier = Modifier.weight(1f))
                Text("Change name", style = fig(14, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { renaming = true })
            } else {
                FieldBox(cur.name, { shopVm.upsertService(cur.copy(name = it)) }, modifier = Modifier.weight(1f), height = 44.dp, borderColor = Tokens.Blue, borderWidth = 2.dp, textStyle = fig(18, FontWeight.Bold))
                Text("Done", style = fig(14, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { renaming = false }.padding(start = 8.dp))
            }
        }

        if (cur.lockedToPiece) {
            Row(Modifier.fillMaxWidth().height(44.dp).rounded(11.dp).background(Tokens.Bg).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Lock, null, tint = Tokens.InkSecondary, modifier = Modifier.size(16.dp))
                Text("Dry cleaning is always per piece", style = fig(14, FontWeight.SemiBold, Tokens.InkSecondary))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Charge by", style = fig(14, FontWeight.Bold, Tokens.InkSecondary))
                Segmented(listOf("Per piece" to (cur.mode == PricingMode.PIECE), "By weight (kg)" to (cur.mode == PricingMode.WEIGHT))) { i ->
                    shopVm.upsertService(cur.copy(mode = if (i == 0) PricingMode.PIECE else PricingMode.WEIGHT))
                }
            }
        }

        ReadyChips(cur.readyInDays) { shopVm.upsertService(cur.copy(readyInDays = it)) }

        if (cur.mode == PricingMode.PIECE) {
            Column {
                cur.items.forEachIndexed { idx, item ->
                    Row(Modifier.fillMaxWidth().height(54.dp).border(0.dp, Tokens.Divider), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.name, style = fig(16, FontWeight.SemiBold, if (item.price != null) Tokens.Ink else Tokens.Faint), modifier = Modifier.weight(1f))
                        FieldBox(
                            item.price?.toString() ?: "",
                            { v -> shopVm.upsertService(cur.copy(items = cur.items.mapIndexed { j, it -> if (j == idx) it.copy(price = v.filter { c -> c.isDigit() }.toIntOrNull()) else it })) },
                            prefix = "₹", placeholder = "–", modifier = Modifier.width(84.dp), height = 42.dp, keyboardType = KeyboardType.Number, textStyle = fig(16, FontWeight.Bold),
                        )
                        Box(Modifier.size(40.dp).tap { shopVm.upsertService(cur.copy(items = cur.items.filterIndexed { j, _ -> j != idx })) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "Remove", tint = Tokens.Faint, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FieldBox(addName, { addName = it }, placeholder = "Add item, e.g. Curtain", modifier = Modifier.weight(1f), height = 48.dp, borderColor = Tokens.DashBorder, textStyle = fig(15))
                    FieldBox(addPrice, { addPrice = it.filter { c -> c.isDigit() }.take(4) }, prefix = "₹", placeholder = "0", modifier = Modifier.width(80.dp), height = 48.dp, borderColor = Tokens.DashBorder, keyboardType = KeyboardType.Number, textStyle = fig(16, FontWeight.Bold))
                    val canAdd = addName.isNotBlank() && addPrice.isNotBlank()
                    Box(
                        Modifier.width(72.dp).height(48.dp).rounded(10.dp).background(if (canAdd) Tokens.Blue else Tokens.BlueDisabled)
                            .tap(enabled = canAdd) {
                                shopVm.upsertService(cur.copy(items = cur.items + ServiceItem(addName.trim(), addPrice.toIntOrNull())))
                                addName = ""; addPrice = ""
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text("Add", style = fig(15, FontWeight.Bold, Tokens.OnDark)) }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FieldBox(cur.ratePerKg?.toString() ?: "", { shopVm.upsertService(cur.copy(ratePerKg = it.filter { c -> c.isDigit() }.toIntOrNull())) }, prefix = "₹", suffix = "per kg", height = 64.dp, borderColor = Tokens.Blue, borderWidth = 2.dp, keyboardType = KeyboardType.Number, textStyle = bric(28, FontWeight.Bold))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Minimum weight", style = fig(15, FontWeight.Bold))
                        Text("Small bags are charged for this much", style = fig(13, color = Tokens.Muted))
                    }
                    FieldBox((cur.minKg ?: 0.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }, { shopVm.upsertService(cur.copy(minKg = it.filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull())) }, suffix = "kg", modifier = Modifier.width(96.dp), height = 48.dp, keyboardType = KeyboardType.Decimal, textStyle = fig(17, FontWeight.Bold))
                }
            }
        }

        if (canDelete) {
            Text("Delete this service", style = fig(14, FontWeight.Bold, Tokens.DeleteRed), modifier = Modifier.tap { shopVm.deleteService(cur.id) })
        }
    }
}

@Composable
private fun NewServiceForm(services: List<Service>, onCreate: (Service) -> Unit) {
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(PricingMode.PIECE) }
    var ready by remember { mutableStateOf<Int?>(null) }
    val suggestions = listOf("Steam Press", "Shoe Cleaning", "Curtain Wash", "Carpet Cleaning", "Starch")

    Column(
        Modifier.fillMaxWidth().rounded(18.dp).background(Tokens.Card).border(2.dp, Tokens.Blue, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Service name", style = fig(15, FontWeight.Bold))
            FieldBox(name, { name = it }, placeholder = "Type a name", height = 52.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { s -> PillChip(s, name == s) { name = s } }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Charge by", style = fig(15, FontWeight.Bold))
            Segmented(listOf("Per piece" to (mode == PricingMode.PIECE), "By weight (kg)" to (mode == PricingMode.WEIGHT))) { i -> mode = if (i == 0) PricingMode.PIECE else PricingMode.WEIGHT }
        }
        ReadyChips(ready) { ready = it }
        PrimaryButton("Add service and set prices", enabled = name.isNotBlank(), height = 52.dp) {
            val baseItems = if (mode == PricingMode.PIECE)
                (services.firstOrNull { it.mode == PricingMode.PIECE }?.items?.map { ServiceItem(it.name, null) } ?: emptyList())
            else emptyList()
            onCreate(
                Service(
                    id = "s${System.currentTimeMillis()}", name = name.trim(), mode = mode,
                    ratePerKg = if (mode == PricingMode.WEIGHT) 0 else null, minKg = if (mode == PricingMode.WEIGHT) 0.0 else null,
                    readyInDays = ready, lockedToPiece = false, items = baseItems, sortOrder = services.size,
                )
            )
        }
    }
}
