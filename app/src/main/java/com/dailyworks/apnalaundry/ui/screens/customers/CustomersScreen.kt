package com.dailyworks.apnalaundry.ui.screens.customers

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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.Customer
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppCard
import com.dailyworks.apnalaundry.ui.components.BottomNav
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.NavTab
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.sheets.ActiveSheet
import com.dailyworks.apnalaundry.ui.sheets.SheetHost
import com.dailyworks.apnalaundry.ui.theme.Tokens

@Composable
fun CustomersScreen(shopVm: ShopViewModel, navigator: AppNavigator, initialFilter: String) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(initialFilter.ifBlank { "all" }) }
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf<ActiveSheet?>(null) }

    val withBal = state.customers.map { it to Selectors.balance(state, it.id) }
    val owe = withBal.filter { it.second > 0 }
    val adv = withBal.filter { it.second < 0 }
    val sumOwe = owe.sumOf { it.second }
    val sumAdv = adv.sumOf { it.second }

    val q = query.trim()
    val qd = q.filter { it.isDigit() }
    var list = withBal.filter { (_, b) -> when (filter) { "baaki" -> b > 0; "advance" -> b < 0; else -> true } }
    if (q.isNotEmpty()) list = list.filter { (c, _) -> c.name.contains(q, true) || (qd.length >= 3 && c.phone.contains(qd)) }
    list = when (filter) {
        "baaki" -> list.sortedByDescending { it.second }
        "advance" -> list.sortedBy { it.second }
        else -> list.sortedBy { it.first.agoRank }
    }

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.statusBars)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Customers", style = bric(24, FontWeight.Bold))
                Text("${state.customers.size} customers", style = fig(13, color = Tokens.Muted), modifier = Modifier.padding(bottom = 3.dp))
            }
            FieldBox(query, { query = it }, placeholder = "Search name or phone", height = 48.dp, leading = {
                Icon(Icons.Outlined.Search, null, tint = Tokens.Muted, modifier = Modifier.size(20.dp))
            })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterTile("All", state.customers.size.toString(), "customers", Tokens.Ink, filter == "all", Modifier.weight(1f)) { filter = "all" }
                FilterTile("Baaki", Money.rupees(sumOwe), "${owe.size} to collect", Tokens.OrangeText, filter == "baaki", Modifier.weight(1f)) { filter = if (filter == "baaki") "all" else "baaki" }
                FilterTile("Advance", Money.rupees(sumAdv), "${adv.size} paid extra", Tokens.BlueText, filter == "advance", Modifier.weight(1f)) { filter = if (filter == "advance") "all" else "advance" }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.first.id }) { (c, bal) -> CustomerRow(state, c, bal) { navigator.openCustomer(c.id, "customers") } }
                if (list.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (q.isNotEmpty()) "No customer found for “$q”" else "No customers here", style = fig(16, FontWeight.SemiBold))
                            val label = if (q.isNotEmpty()) (if (qd.length >= 3) "Add customer with this number" else "Add “${q.replaceFirstChar { it.uppercase() }}” as customer") else "Add customer"
                            Box(Modifier.rounded(12.dp).background(Tokens.Blue).tap {
                                active = ActiveSheet.CustomerForm(editId = null, ctx = "list", prefillName = if (qd.length >= 3) "" else q.replaceFirstChar { it.uppercase() }, prefillPhone = if (qd.length >= 3) qd.takeLast(10) else "")
                            }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                                Text(label, style = fig(15, FontWeight.Bold, Tokens.OnDark))
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(16.dp).height(52.dp).rounded(999.dp).background(Tokens.Blue)
                    .tap { active = ActiveSheet.CustomerForm(editId = null, ctx = "list") }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.Add, null, tint = Tokens.OnDark, modifier = Modifier.size(22.dp))
                Text("Add customer", style = fig(16, FontWeight.Bold, Tokens.OnDark))
            }
        }
        BottomNav(current = NavTab.CUSTOMERS, onSelect = navigator::selectTab)
    }

    SheetHost(active, state, shopVm, navigator, onOpen = { active = it }, onDismiss = { active = null })
}

@Composable
private fun FilterTile(label: String, value: String, sub: String, tone: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (selected) Tokens.Ink else Tokens.Card
    Column(
        modifier.height(74.dp).rounded(14.dp).background(bg).border(1.dp, if (selected) Tokens.Ink else Tokens.CardBorder, RoundedCornerShape(14.dp)).tap(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start,
    ) {
        Text(label, style = fig(12, FontWeight.Bold, if (selected) Tokens.OnDarkMuted else tone))
        Text(value, style = bric(18, FontWeight.Bold, if (selected) Tokens.OnDark else tone), maxLines = 1)
        Text(sub, style = fig(11, color = if (selected) Tokens.OnDarkMuted else Tokens.Muted), maxLines = 1)
    }
}

@Composable
private fun CustomerRow(state: com.dailyworks.apnalaundry.domain.LaundryState, c: Customer, bal: Int, onClick: () -> Unit) {
    val avBg = if (bal > 0) Tokens.OrangeLight else if (bal < 0) Tokens.BlueLight else Tokens.NeutralFill
    val avFg = if (bal > 0) Tokens.OrangeText else if (bal < 0) Tokens.BlueText else Tokens.InkSecondary
    val count = Selectors.orderCount(state, c)
    AppCard {
        Row(Modifier.fillMaxWidth().tap(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).rounded(999.dp).background(avBg), contentAlignment = Alignment.Center) {
                Text(Selectors.initials(c.name), style = fig(14, FontWeight.Bold, avFg))
            }
            Column(Modifier.weight(1f)) {
                Text(c.name, style = fig(16, FontWeight.Bold), maxLines = 1)
                Text(
                    (if (count > 0) "Last order ${c.lastLabel} · $count orders" else "New customer") + " · " + Selectors.fmtPhone(c.phone),
                    style = fig(13, color = Tokens.Muted), maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (bal != 0) Text(Money.rupees(bal), style = fig(16, FontWeight.Bold, avFg))
                Text(if (bal > 0) "baaki" else if (bal < 0) "advance" else "All clear", style = fig(12, color = avFg))
            }
        }
    }
}
