package com.dailyworks.apnalaundry.ui.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppCard
import com.dailyworks.apnalaundry.ui.components.BottomNav
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.NavTab
import com.dailyworks.apnalaundry.ui.components.PillChip
import com.dailyworks.apnalaundry.ui.components.SectionLabel
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.theme.Tokens

private fun reminderLabel(closeTime: String): String {
    val parts = closeTime.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 21
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    var total = h * 60 + m - 30
    if (total < 0) total += 24 * 60
    val hh = total / 60
    val mm = total % 60
    val disp = if (hh % 12 == 0) 12 else hh % 12
    val ap = if (hh >= 12) "PM" else "AM"
    return "Reminder at $disp:${mm.toString().padStart(2, '0')} $ap to update the day"
}

@Composable
fun SettingsScreen(shopVm: ShopViewModel, navigator: AppNavigator, onLogout: () -> Unit) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val shop = state.shop

    var name by remember(shop.name) { mutableStateOf(shop.name) }
    LaunchedEffect(shop.name) { if (name.isBlank()) name = shop.name }

    val closeChips = listOf("20:00" to "8 PM", "21:00" to "9 PM", "22:00" to "10 PM", "23:00" to "11 PM")

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Settings", style = bric(28, FontWeight.Bold))

            // Shop name
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Shop name")
                FieldBox(
                    value = name,
                    onValueChange = { name = it; shopVm.updateShop(it, shop.closeTime, shop.expressPct) },
                    height = 52.dp,
                )
            }

            // Rate card
            AppCard {
                Row(
                    Modifier.fillMaxWidth().tap { navigator.openRates("settings") }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Rate card", style = fig(16, FontWeight.Bold))
                        Text("Services and prices", style = fig(13, color = Tokens.Muted))
                    }
                    Text("Edit", style = fig(15, FontWeight.Bold, Tokens.Blue))
                }
            }

            // Closing time
            AppCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("When do you close the shop?", style = fig(15, FontWeight.Bold))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        closeChips.forEach { (value, label) ->
                            PillChip(label, shop.closeTime == value) { shopVm.updateShop(name, value, shop.expressPct) }
                        }
                    }
                    Text(reminderLabel(shop.closeTime), style = fig(13, color = Tokens.Muted))
                }
            }

            // Phone
            AppCard {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Phone", style = fig(15, FontWeight.SemiBold, Tokens.Muted))
                    Text("+91 ${shop.phone}", style = fig(15, FontWeight.Bold))
                }
            }

            // Coming later
            AppCard(bg = Tokens.NeutralFill, borderColor = Tokens.NeutralFill) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Coming later", style = fig(13, FontWeight.Bold, Tokens.Muted))
                    Text("GST on bills · shop logo · staff logins · Hindi", style = fig(14, color = Tokens.InkSecondary))
                }
            }

            Box(
                Modifier.fillMaxWidth().height(52.dp).tap { onLogout() },
                contentAlignment = Alignment.Center,
            ) {
                Text("Log out / restart", style = fig(15, FontWeight.Bold, Tokens.OrangeText))
            }
            Spacer(Modifier.height(8.dp))
        }

        BottomNav(current = NavTab.SETTINGS, onSelect = navigator::selectTab)
    }
}
