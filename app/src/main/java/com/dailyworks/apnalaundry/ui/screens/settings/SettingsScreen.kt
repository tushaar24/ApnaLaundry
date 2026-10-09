package com.dailyworks.apnalaundry.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppCard
import com.dailyworks.apnalaundry.ui.components.BottomNav
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.NavTab
import com.dailyworks.apnalaundry.ui.components.SectionLabel
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.screens.paywall.PaywallViewModel
import com.dailyworks.apnalaundry.ui.theme.Tokens
import org.koin.androidx.compose.koinViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size

@Composable
fun SettingsScreen(shopVm: ShopViewModel, navigator: AppNavigator, onLogout: () -> Unit) {
    val paywallVm: PaywallViewModel = koinViewModel()
    val state by shopVm.state.collectAsStateWithLifecycle()
    val shop = state.shop

    var name by remember(shop.name) { mutableStateOf(shop.name) }
    LaunchedEffect(shop.name) { if (name.isBlank()) name = shop.name }
    LaunchedEffect(Unit) { Analytics.screen("settings") }

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
                    onValueChange = { name = it; shopVm.updateShop(it, shop.expressPct) },
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

            // Bill design & details (same screen as onboarding step 3)
            AppCard {
                Row(
                    Modifier.fillMaxWidth().tap { navigator.openBillDesign() }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Bill design & details", style = fig(16, FontWeight.Bold))
                        Text("Design, logo, UPI QR, address, GSTIN, terms", style = fig(13, color = Tokens.Muted))
                    }
                    Text("Edit", style = fig(15, FontWeight.Bold, Tokens.Blue))
                }
            }

            // Subscription row (manage / cancel the plan) — only with an active plan.
            val billing by paywallVm.ui.collectAsStateWithLifecycle()
            if (billing.hasActive) {
                AppCard {
                    Row(
                        Modifier.fillMaxWidth().tap { navigator.openSubscription() }.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (billing.status?.subscription?.plan == "annual") "Yearly plan" else "Monthly plan",
                                style = fig(16, FontWeight.Bold),
                            )
                            Text("Unlimited orders", style = fig(13, color = Tokens.Muted))
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Tokens.Muted, modifier = Modifier.size(20.dp))
                    }
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
                    Text("Staff logins · Hindi", style = fig(14, color = Tokens.InkSecondary))
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
