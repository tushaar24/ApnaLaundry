package com.dailyworks.apnalaundry.ui.screens.shops

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import com.dailyworks.apnalaundry.data.MAIN_SHOP
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.data.billing.BillingRepository
import com.dailyworks.apnalaundry.data.shops.FREE_SHOPS
import com.dailyworks.apnalaundry.data.shops.ShopRef
import com.dailyworks.apnalaundry.domain.BillDetails
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
fun shopLabel(s: ShopRef, n: Int): String = s.name.ifBlank { if (n == 0) tr("My shop") else tr("Shop {n}", "n" to n + 1) }

/**
 * "Your shops" (Home: tap the shop name ▾): every shop as a card with its own
 * phone number, the one on screen ticked — tap a card to switch to it, its ✏️
 * to edit that shop's name, phone number and bill details — then "Add a shop"
 * (paid: the add screen shows the paywall first without a plan).
 */
@Composable
fun ShopsSheet(shopVm: ShopViewModel, navigator: AppNavigator, from: String, onDismiss: () -> Unit) {
    val shops by shopVm.shops.collectAsStateWithLifecycle()
    val activeId by shopVm.activeShopId.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Pair<ShopRef, String>?>(null) } // shop, its label
    // Each shop's number on its bills (from its own database).
    var phones by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(shops) { phones = shopVm.shopPhones(shops.map { it.id }) }
    AppBottomSheet(title = tr("Your shops"), subtitle = tr("Each shop has its own customers, khata and bills"), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shops.forEachIndexed { i, s ->
                val on = s.id == activeId
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).rounded(14.dp)
                        .background(if (on) Tokens.BlueLight else Tokens.Card)
                        .border(1.5.dp, if (on) Tokens.Blue else Tokens.CardBorder, RoundedCornerShape(14.dp))
                        .tap { shopVm.switchShop(s.id); onDismiss(); navigator.openHome() }
                        .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), // the ✏️ button keeps its own space
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Storefront, null, tint = if (on) Tokens.Blue else Tokens.InkSecondary, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(shopLabel(s, i), style = fig(16, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        phones[s.id]?.takeIf { it.isNotEmpty() }?.let {
                            Text(BillDetails.fmtBillPhone(it), style = fig(13, color = Tokens.Muted), maxLines = 1)
                        }
                        if (on) Text(tr("Open now"), style = fig(12, FontWeight.SemiBold, Tokens.Blue), maxLines = 1)
                    }
                    Row { // ✏️ and delete side by side, no gap
                        // Edit this shop (switches to it first if it isn't open).
                        Box(
                            Modifier.size(44.dp).rounded(999.dp).tap { shopVm.openShop(s.id) { onDismiss(); navigator.openBillDesign() } },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.Edit, tr("Edit {shop}", "shop" to shopLabel(s, i)), tint = Tokens.InkSecondary, modifier = Modifier.size(20.dp)) }
                        // Delete — extra shops only; the main shop is the account itself.
                        if (s.id != MAIN_SHOP) {
                            Box(
                                Modifier.size(44.dp).rounded(999.dp).tap { deleting = s to shopLabel(s, i) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.Delete, tr("Delete {shop}", "shop" to shopLabel(s, i)), tint = Tokens.OrangeText, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            SheetAction(Icons.Filled.Add, tr("Add a shop"), tr("Its own customers, khata and bills")) {
                onDismiss(); navigator.openAddShop(from)
            }
        }
    }
    deleting?.let { (shop, label) ->
        DeleteShopDialog(label, onKeep = { deleting = null }) {
            deleting = null
            shopVm.deleteShop(shop.id) { err ->
                shopVm.showInfo(err ?: tr("{shop} deleted", "shop" to label))
                if (err == null) { onDismiss(); navigator.openHome() }
            }
        }
    }
}

/** "Delete <shop>?" — what goes with it, Keep / Delete. */
@Composable
private fun DeleteShopDialog(label: String, onKeep: () -> Unit, onDelete: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onKeep) {
        Column(
            Modifier.fillMaxWidth().rounded(20.dp).background(Tokens.Card).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(tr("Delete {shop}?", "shop" to label), style = bric(22, FontWeight.Bold))
            Text(
                tr("Its customers, orders, khata and bills will be removed from the app on all your phones. Your other shops stay as they are."),
                style = fig(15, color = Tokens.InkSecondary),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).rounded(14.dp).background(Tokens.NeutralFill).tap(onClick = onKeep)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr("Keep"), style = fig(15, FontWeight.Bold, Tokens.InkSecondary), textAlign = TextAlign.Center) }
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).rounded(14.dp).background(Tokens.Orange)
                        .tap(enabled = !busy) { busy = true; onDelete() }.padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (busy) tr("Deleting…") else tr("Delete"), style = fig(15, FontWeight.Bold, Tokens.OnDark), textAlign = TextAlign.Center) }
            }
        }
    }
}

/** A blue action row at the bottom of the shops sheet. */
@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().rounded(14.dp).border(1.5.dp, Tokens.Blue, RoundedCornerShape(14.dp))
            .tap(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = Tokens.Blue, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = fig(16, FontWeight.Bold, Tokens.Blue), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = fig(13, color = Tokens.Muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Add a shop. The 2nd shop is free ([FREE_SHOPS]); from the 3rd, no plan =
 * the paywall first ("Add more shops"), then the name and the shop's own
 * phone number (printed on its bills; the login number stays the same) — the
 * free limit isn't spelled out anywhere in the app (owner's call). The new shop
 * starts with the current shop's services and bill details (not its
 * address), and opens on Home. The server enforces the same rule.
 */
@Composable
fun AddShopScreen(shopVm: ShopViewModel, navigator: AppNavigator, from: String) {
    val billing: BillingRepository = koinInject()
    val state by shopVm.state.collectAsStateWithLifecycle()
    val shops by shopVm.shops.collectAsStateWithLifecycle()
    val free = shops.size < FREE_SHOPS
    // null = checking; the saved plan decides instantly, a refresh catches a plan bought elsewhere.
    var hasPlan by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        var plan = billing.savedActive()
        if (!plan && !free) { billing.refreshSaved(); plan = billing.savedActive() }
        Analytics.addShopTapped(from, plan, free)
        hasPlan = plan || free
    }

    when (hasPlan) {
        null -> Box(Modifier.fillMaxSize().background(Tokens.Bg))
        false -> PaywallScreen(onClose = { navigator.back() }, onDone = { hasPlan = true }, from = "add_shop")
        true -> {
            var name by remember { mutableStateOf("") }
            // Starts as the shop on screen's number; empty = the login number.
            var phone by remember { mutableStateOf(BillDetails.cleanPhone(state.shop.billPhone.ifBlank { state.shop.phone })) }
            var busy by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            val clean = name.trim().replace(Regex("\\s+"), " ")
            val phoneOk = phone.isEmpty() || BillDetails.isPhoneValid(phone)
            fun submit() {
                if (clean.isEmpty() || !phoneOk || busy) return
                busy = true
                shopVm.addShop(clean, phone) { err ->
                    busy = false
                    when (err) {
                        null -> { shopVm.showInfo(tr("{shop} added · you're now in this shop", "shop" to clean)); navigator.openHome() }
                        "plan" -> hasPlan = false
                        else -> error = err
                    }
                }
            }
            Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
                TopBar(title = tr("Add a shop"), onBack = { navigator.back() })
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(tr("New shop's name"), style = fig(15, FontWeight.Bold))
                    FieldBox(
                        name, { name = it.take(40); error = null }, placeholder = tr("e.g. Shine Laundry, Station Road"),
                        height = 56.dp, capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next,
                    )
                    Text(tr("Shop's phone number"), style = fig(15, FontWeight.Bold))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldBox(
                            phone, { phone = it.filter { c -> c.isDigit() }.take(10); error = null },
                            prefix = "+91", placeholder = "98765 43210", height = 56.dp, keyboardType = KeyboardType.Number,
                            borderColor = if (phoneOk) Tokens.FieldBorder else Tokens.Orange,
                            imeAction = ImeAction.Done, onImeAction = { submit() },
                        )
                        Text(
                            if (phoneOk) tr("Printed on this shop's bills. Customers call or WhatsApp you on it. You still log in with {phone}.", "phone" to BillDetails.fmtBillPhone(state.shop.phone))
                            else tr("Enter all 10 digits, or leave it empty"),
                            style = fig(13, if (phoneOk) FontWeight.Normal else FontWeight.SemiBold, if (phoneOk) Tokens.Muted else Tokens.OrangeText),
                        )
                    }
                    Column(
                        Modifier.fillMaxWidth().rounded(14.dp).background(Tokens.Card)
                            .border(1.dp, Tokens.CardBorder, RoundedCornerShape(14.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(tr("How it works"), style = fig(14, FontWeight.Bold))
                        Text("• " + tr("The new shop has its own customers, khata, orders and bills."), style = fig(14, color = Tokens.InkSecondary))
                        Text("• " + tr("Your services and prices are copied from {shop}. You can change them later.", "shop" to state.shop.name), style = fig(14, color = Tokens.InkSecondary))
                        Text("• " + tr("Switch shops anytime from the shop name at the top of Home."), style = fig(14, color = Tokens.InkSecondary))
                    }
                    error?.let { Text(it, style = fig(14, FontWeight.SemiBold, Tokens.OrangeText)) }
                }
                Box(Modifier.fillMaxWidth().background(Tokens.Card).padding(16.dp)) {
                    PrimaryButton(if (busy) tr("Adding…") else tr("Add shop"), enabled = clean.isNotEmpty() && phoneOk && !busy, height = 56.dp) { submit() }
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
                Text(state.shop.name, style = fig(16, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (shops.size > 1) tr("{n} shops · you're in this one", "n" to shops.size) else tr("Your shop"), style = fig(13, color = Tokens.Muted))
            }
            if (shops.size > 1) Box(
                Modifier.heightIn(min = 48.dp).rounded(10.dp).tap(onClick = onSwitch).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(tr("Switch"), style = fig(15, FontWeight.Bold, Tokens.Blue)) }
        }
        Row(
            Modifier.fillMaxWidth().height(48.dp).rounded(12.dp).border(1.5.dp, Tokens.Blue, RoundedCornerShape(12.dp))
                .tap(onClick = onAdd).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Add, null, tint = Tokens.Blue, modifier = Modifier.size(20.dp))
            Text(tr("Add another shop"), style = fig(15, FontWeight.Bold, Tokens.Blue), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}
