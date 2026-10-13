package com.dailyworks.apnalaundry.ui.screens.shops

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.data.billing.BillingRepository
import com.dailyworks.apnalaundry.data.shops.ShopRef
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppBottomSheet
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.TopBar
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.screens.paywall.PaywallScreen
import com.dailyworks.apnalaundry.ui.theme.Tokens
import org.koin.compose.koinInject

/** A shop's name for lists: its own, else "My shop" (the main one before its first sync). */
fun shopLabel(s: ShopRef, n: Int): String = s.name.ifBlank { if (n == 0) "My shop" else "Shop ${n + 1}" }

/**
 * "Your shops": every shop, the one on screen ticked — tap one to switch to
 * it — and "Add a shop" (a paid feature: the add screen shows the paywall
 * first without a plan).
 */
@Composable
fun ShopsSheet(shopVm: ShopViewModel, navigator: AppNavigator, from: String, onDismiss: () -> Unit) {
    val shops by shopVm.shops.collectAsStateWithLifecycle()
    val activeId by shopVm.activeShopId.collectAsStateWithLifecycle()
    AppBottomSheet(title = "Your shops", subtitle = "Each shop has its own customers, khata and bills", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shops.forEachIndexed { i, s ->
                val on = s.id == activeId
                Row(
                    Modifier.fillMaxWidth().height(60.dp).rounded(14.dp)
                        .background(if (on) Tokens.BlueLight else Tokens.Card)
                        .border(1.5.dp, if (on) Tokens.Blue else Tokens.CardBorder, RoundedCornerShape(14.dp))
                        .tap { shopVm.switchShop(s.id); onDismiss(); navigator.openHome() }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.Storefront, null, tint = if (on) Tokens.Blue else Tokens.InkSecondary, modifier = Modifier.size(22.dp))
                    Text(shopLabel(s, i), style = fig(16, FontWeight.Bold), maxLines = 1, modifier = Modifier.weight(1f))
                    if (on) Icon(Icons.Filled.CheckCircle, "Open now", tint = Tokens.Blue, modifier = Modifier.size(22.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().height(56.dp).rounded(14.dp).border(1.5.dp, Tokens.Blue, RoundedCornerShape(14.dp))
                    .tap { onDismiss(); navigator.openAddShop(from) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Add, null, tint = Tokens.Blue, modifier = Modifier.size(22.dp))
                Text("Add a shop", style = fig(16, FontWeight.Bold, Tokens.Blue))
            }
        }
    }
}

/**
 * Add a shop (paid). No plan: the paywall first ("Add unlimited shops"),
 * then the name. The new shop starts with the current shop's services and
 * bill details (not its address), and opens on Home.
 */
@Composable
fun AddShopScreen(shopVm: ShopViewModel, navigator: AppNavigator, from: String) {
    val billing: BillingRepository = koinInject()
    val state by shopVm.state.collectAsStateWithLifecycle()
    // null = checking; the saved plan decides instantly, a refresh catches a plan bought elsewhere.
    var hasPlan by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        var plan = billing.savedActive()
        if (!plan) { billing.refreshSaved(); plan = billing.savedActive() }
        Analytics.addShopTapped(from, plan)
        hasPlan = plan
    }

    when (hasPlan) {
        null -> Box(Modifier.fillMaxSize().background(Tokens.Bg))
        false -> PaywallScreen(onClose = { navigator.back() }, onDone = { hasPlan = true }, from = "add_shop")
        true -> {
            var name by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            val clean = name.trim().replace(Regex("\\s+"), " ")
            Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars)) {
                TopBar(title = "Add a shop", onBack = { navigator.back() })
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("New shop's name", style = fig(15, FontWeight.Bold))
                    FieldBox(
                        name, { name = it.take(40); error = null }, placeholder = "e.g. Shine Laundry, Station Road",
                        height = 56.dp, capitalization = KeyboardCapitalization.Words,
                    )
                    Column(
                        Modifier.fillMaxWidth().rounded(14.dp).background(Tokens.Card)
                            .border(1.dp, Tokens.CardBorder, RoundedCornerShape(14.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("How it works", style = fig(14, FontWeight.Bold))
                        Text("• The new shop has its own customers, khata, orders and bills.", style = fig(14, color = Tokens.InkSecondary))
                        Text("• Your services and prices are copied from ${state.shop.name}. You can change them later.", style = fig(14, color = Tokens.InkSecondary))
                        Text("• Switch shops anytime from the shop name at the top of Home.", style = fig(14, color = Tokens.InkSecondary))
                    }
                    error?.let { Text(it, style = fig(14, FontWeight.SemiBold, Tokens.OrangeText)) }
                    Spacer(Modifier.height(4.dp))
                    PrimaryButton(if (busy) "Adding…" else "Add shop", enabled = clean.isNotEmpty() && !busy, height = 56.dp) {
                        busy = true
                        shopVm.addShop(clean) { err ->
                            busy = false
                            when (err) {
                                null -> { shopVm.showInfo("$clean added · you're now in this shop"); navigator.openHome() }
                                "plan" -> hasPlan = false
                                else -> error = err
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Now in <shop>" + switch / add, for Settings. */
@Composable
fun ShopsCard(shopVm: ShopViewModel, hasPlan: Boolean, onSwitch: () -> Unit, onAdd: () -> Unit) {
    val shops by shopVm.shops.collectAsStateWithLifecycle()
    val state by shopVm.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxWidth().rounded(16.dp).background(Tokens.Card)
            .border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Storefront, null, tint = Tokens.Blue, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(state.shop.name, style = fig(16, FontWeight.Bold), maxLines = 1)
                Text(if (shops.size > 1) "${shops.size} shops · you're in this one" else "Your shop", style = fig(13, color = Tokens.Muted))
            }
            if (shops.size > 1) Text("Switch", style = fig(15, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap(onClick = onSwitch).padding(6.dp))
        }
        Row(
            Modifier.fillMaxWidth().height(48.dp).rounded(12.dp).border(1.5.dp, Tokens.Blue, RoundedCornerShape(12.dp))
                .tap(onClick = onAdd).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Add, null, tint = Tokens.Blue, modifier = Modifier.size(20.dp))
            Text("Add another shop", style = fig(15, FontWeight.Bold, Tokens.Blue), modifier = Modifier.weight(1f))
            if (!hasPlan) Text("PLAN", style = fig(11, FontWeight.ExtraBold, Tokens.OrangeText))
        }
    }
}
