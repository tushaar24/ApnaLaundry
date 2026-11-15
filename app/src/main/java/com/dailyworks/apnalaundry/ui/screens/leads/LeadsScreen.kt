package com.dailyworks.apnalaundry.ui.screens.leads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.data.leads.GbpWire
import com.dailyworks.apnalaundry.data.leads.LeadWire
import com.dailyworks.apnalaundry.data.leads.LeadsRepository
import com.dailyworks.apnalaundry.data.leads.LeadsScreenWire
import com.dailyworks.apnalaundry.push.AskNotificationPermission
import com.dailyworks.apnalaundry.push.Push
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppBottomSheet
import com.dailyworks.apnalaundry.ui.components.AppCard
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.OutlineButton
import com.dailyworks.apnalaundry.ui.components.PillChip
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.SectionLabel
import com.dailyworks.apnalaundry.ui.components.TopBar
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.screens.bill.openWhatsApp
import com.dailyworks.apnalaundry.ui.screens.staff.LocalStaffAccess
import com.dailyworks.apnalaundry.ui.theme.Tokens
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Get customers (1.0.22, docs/specs/leads.md): the owner's wallet, their Google Business
// connection, and the leads the team sent — a waiting lead is paid for (wallet / Razorpay) and
// only then shows the customer's number.

private const val GBP_SCOPE = "https://www.googleapis.com/auth/business.manage"

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun rupees(paise: Int) = Money.rupees(paise / 100)

private fun timeLeft(seconds: Long): String = when {
    seconds <= 0 -> tr("Time's up")
    seconds < 60 -> tr("{n} sec left", "n" to seconds)
    seconds < 3600 -> tr("{n} min left", "n" to seconds / 60)
    else -> tr("{h} h {m} min left", "h" to seconds / 3600, "m" to (seconds % 3600) / 60)
}

private val clockFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private fun whenText(ms: Long): String {
    if (ms <= 0L) return ""
    val z = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return AppDate.short(z.toLocalDate().toString()) + " · " + z.format(clockFmt).lowercase()
}

private fun dial(context: Context, digits: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+91$digits")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// ---------------- the screen ----------------

/** Settings → Get customers, Home's card, and the push for one lead ([openLeadId]). */
@Composable
fun LeadsScreen(shopVm: ShopViewModel, navigator: AppNavigator, from: String, openLeadId: String = "") {
    val vm: LeadsViewModel = koinViewModel()
    val repo: LeadsRepository = koinInject()
    val st by vm.state.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val pay by vm.pay.collectAsStateWithLifecycle()
    val shopState by shopVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The countdowns tick once a second.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    var openLead by rememberSaveable { mutableStateOf<String?>(null) }
    var walletOpen by rememberSaveable { mutableStateOf(false) }
    var mapsOpen by rememberSaveable { mutableStateOf(false) }
    var askNotif by rememberSaveable { mutableStateOf(false) }
    var logged by rememberSaveable { mutableStateOf(false) }
    var pendingOpen by rememberSaveable { mutableStateOf(openLeadId) }
    var gbpBusy by remember { mutableStateOf(false) }
    var gbpError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { Analytics.screen("leads"); vm.refresh() }
    LaunchedEffect(st) {
        val s = st ?: return@LaunchedEffect
        if (!logged) {
            logged = true
            Analytics.leadsOpened(from, offered = s.leads.count { it.isOpen() }, paid = s.leads.count { it.isPaid }, balancePaise = s.wallet.balancePaise, gbp = s.gbp.status)
        }
        if (pendingOpen.isNotEmpty()) {
            val l = s.leads.firstOrNull { it.id == pendingOpen }
            pendingOpen = ""
            if (l != null) { vm.resetPay(); openLead = l.id; vm.seen(l); Analytics.leadViewed(l.id, l.pricePaise, from) }
        }
    }

    val connectGoogle = rememberGoogleBusinessConnect(
        clientId = st?.gbp?.googleClientId,
        onCode = { code ->
            gbpBusy = true; gbpError = null
            scope.launch {
                val r = runCatching { repo.connectGbp(code) }.getOrNull()
                gbpBusy = false
                if (r?.success == true && r.gbp != null) Analytics.gbpConnected("google", r.gbp.locations.size)
                else { gbpError = r?.message ?: tr("Couldn't connect Google — try again"); Analytics.gbpConnectFailed("google", r?.code ?: "offline") }
            }
        },
        onFailed = { reason -> gbpError = if (reason == null) null else tr("Couldn't connect Google — try again"); Analytics.gbpConnectFailed("google", reason) },
    )

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars)) {
        TopBar(tr("Get customers"), onBack = { navigator.back() })
        val s = st
        when {
            s == null && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Tokens.Blue) }
            s == null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (failed) tr("Couldn't load — check internet") else tr("Loading…"), style = fig(15, color = Tokens.Muted))
                if (failed) OutlineButton(tr("Try again"), Modifier.fillMaxWidth()) { vm.refresh() }
            }
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val open = s.leads.filter { it.isOpen(now) }
                val paid = s.leads.filter { it.isPaid }
                val past = s.leads.filter { !it.isOpen(now) && !it.isPaid }

                if (!Push.canNotify(context)) {
                    AppCard(bg = Tokens.OrangeLight, borderColor = Tokens.OrangeBorder) {
                        Row(Modifier.fillMaxWidth().tap { Analytics.leadsNotificationsNudgeTapped(); askNotif = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("Turn on notifications"), style = fig(15, FontWeight.Bold, Tokens.OrangeDeep))
                                Text(tr("A customer is sent to your phone the moment they call. Without notifications you may miss them."), style = fig(13, color = Tokens.OrangeText))
                            }
                            Text(tr("Turn on"), style = fig(14, FontWeight.Bold, Tokens.Blue))
                        }
                    }
                }

                WalletCard(s, onAdd = { vm.resetPay(); walletOpen = true })

                GoogleCard(
                    s.gbp, busy = gbpBusy, error = gbpError,
                    onConnect = { Analytics.gbpConnectTapped("google"); gbpError = null; connectGoogle() },
                    onMapsLink = { Analytics.gbpConnectTapped("maps_link"); mapsOpen = true },
                    onPick = { name -> scope.launch { runCatching { repo.pickLocation(name) } } },
                    onDisconnect = { scope.launch { runCatching { repo.disconnectGbp() }; Analytics.gbpDisconnected() } },
                )

                if (open.isNotEmpty()) {
                    SectionLabel(tr("Waiting for you"), Modifier.padding(top = 4.dp))
                    open.forEach { lead ->
                        OfferedLeadCard(
                            lead, now,
                            onOpen = { vm.resetPay(); openLead = lead.id; vm.seen(lead); Analytics.leadViewed(lead.id, lead.pricePaise, "list") },
                            onDecline = { vm.decline(lead, "not_now") },
                        )
                    }
                }

                if (paid.isNotEmpty()) {
                    SectionLabel(tr("Your customers"), Modifier.padding(top = 4.dp))
                    paid.forEach { lead ->
                        PaidLeadCard(lead, shopState.customers.any { it.phone.filter(Char::isDigit).endsWith(lead.phoneDigits) && lead.phoneDigits.isNotEmpty() },
                            onCall = { Analytics.leadCallTapped(lead.id); dial(context, lead.phoneDigits) },
                            onWhatsApp = { Analytics.leadWhatsAppTapped(lead.id); openWhatsApp(context, "https://wa.me/91${lead.phoneDigits}") },
                            onSave = { saveAsCustomer(shopVm, navigator, shopState.customers, lead) },
                        )
                    }
                }

                if (open.isEmpty() && paid.isEmpty()) HowItWorksCard(s)

                if (past.isNotEmpty()) {
                    SectionLabel(tr("Earlier"), Modifier.padding(top = 4.dp))
                    AppCard {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                            past.take(10).forEach { lead ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(listOf(lead.area, lead.service).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { tr("Customer") }, style = fig(14, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(whenText(lead.createdAtMs), style = fig(12, color = Tokens.Faint))
                                    }
                                    Text(
                                        when (lead.status) {
                                            "expired" -> tr("Missed"); "declined" -> tr("Skipped"); "refunded" -> tr("Refunded"); else -> tr("Closed")
                                        },
                                        style = fig(13, FontWeight.Bold, if (lead.status == "refunded") Tokens.GreenText else Tokens.Muted),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (askNotif) AskNotificationPermission("leads") { askNotif = false }

    val leadForSheet = openLead?.let { id -> st?.leads?.firstOrNull { it.id == id } }
    if (openLead != null) {
        LeadSheet(
            lead = leadForSheet, screen = st, pay = pay, now = now, vm = vm,
            customers = shopState.customers,
            onSaveCustomer = { lead -> saveAsCustomer(shopVm, navigator, shopState.customers, lead) },
            onClose = { openLead = null; vm.resetPay() },
        )
    }
    if (walletOpen) WalletSheet(st, pay, vm, onClose = { walletOpen = false; vm.resetPay() })
    if (mapsOpen) {
        MapsLinkSheet(
            current = st?.gbp?.mapsUrl,
            onSave = { url, done ->
                scope.launch {
                    val r = runCatching { repo.setMapsLink(url) }.getOrNull()
                    if (r?.success == true) { Analytics.gbpConnected("maps_link", 0); done(null) }
                    else { Analytics.gbpConnectFailed("maps_link", r?.code ?: "offline"); done(if (r?.code == "BAD_MAPS_URL") tr("That doesn't look like a Google Maps link") else tr("Couldn't save — check internet and try again")) }
                }
            },
            onClose = { mapsOpen = false },
        )
    }
}

private fun saveAsCustomer(shopVm: ShopViewModel, navigator: AppNavigator, customers: List<com.dailyworks.apnalaundry.domain.Customer>, lead: LeadWire) {
    val digits = lead.phoneDigits
    if (digits.isEmpty()) return
    val existing = customers.firstOrNull { it.phone.filter(Char::isDigit).endsWith(digits) }
    Analytics.leadSavedAsCustomer(lead.id, existing != null)
    if (existing != null) { navigator.openCustomer(existing.id, "leads"); return }
    shopVm.saveCustomer(
        editId = null, name = lead.customerName.ifBlank { tr("Customer") }, phone = digits, address = lead.area, oldBaaki = 0, fromList = false,
    ) { id -> navigator.openCustomer(id, "leads") }
}

// ---------------- cards ----------------

@Composable
private fun WalletCard(s: LeadsScreenWire, onAdd: () -> Unit) {
    AppCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Wallet"), style = fig(13, FontWeight.Bold, Tokens.Muted))
                Text(rupees(s.wallet.balancePaise), style = bric(28, FontWeight.Bold))
                Text(tr("Each customer costs what the lead cost us + {markup}. Money left stays for the next one.", "markup" to rupees(s.config.markupPaise)), style = fig(12, color = Tokens.Faint))
            }
            Spacer(Modifier.width(12.dp))
            OutlineButton(tr("Add money"), height = 44.dp, onClick = onAdd)
        }
    }
}

@Composable
private fun GoogleCard(
    gbp: GbpWire, busy: Boolean, error: String?,
    onConnect: () -> Unit, onMapsLink: () -> Unit, onPick: (String) -> Unit, onDisconnect: () -> Unit,
) {
    AppCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                gbp.isConnected -> {
                    Text("✓ " + tr("Google connected"), style = fig(16, FontWeight.Bold, Tokens.GreenText))
                    gbp.email?.let { Text(it, style = fig(13, color = Tokens.Muted)) }
                    when {
                        gbp.locationTitle != null -> Text(tr("Your shop on Google: {name}", "name" to gbp.locationTitle), style = fig(14, FontWeight.SemiBold))
                        gbp.locations.isEmpty() -> Text(tr("We'll match your shop's Google listing shortly."), style = fig(13, color = Tokens.Muted))
                    }
                    if (gbp.locations.size > 1) {
                        Text(tr("Which one is your shop?"), style = fig(13, FontWeight.Bold, Tokens.Muted))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            gbp.locations.forEach { l -> PillChip(l.title.ifBlank { l.address }, selected = l.name == gbp.locationName) { onPick(l.name) } }
                        }
                    }
                    if (gbp.hasMapsLink) Text(tr("Maps link saved too"), style = fig(12, color = Tokens.Faint))
                    Text(tr("Remove"), style = fig(13, FontWeight.Bold, Tokens.Muted), modifier = Modifier.tap(onClick = onDisconnect).padding(top = 2.dp))
                }
                gbp.hasMapsLink -> {
                    Text("✓ " + tr("Your shop's Google Maps link is saved"), style = fig(16, FontWeight.Bold, Tokens.GreenText))
                    Text(gbp.mapsUrl.orEmpty(), style = fig(13, color = Tokens.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr("We run ads on your shop's listing. New customers who call come to you here."), style = fig(13, color = Tokens.Muted))
                    if (gbp.configured && !gbp.googleClientId.isNullOrBlank()) {
                        OutlineButton(if (busy) tr("Connecting…") else tr("Connect with Google"), Modifier.fillMaxWidth(), height = 48.dp) { if (!busy) onConnect() }
                    }
                    Text(tr("Change link"), style = fig(13, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap(onClick = onMapsLink))
                }
                else -> {
                    Text(tr("Your shop on Google"), style = fig(16, FontWeight.Bold))
                    Text(tr("Connect your Google Business Profile. We run ads on your shop's Google listing — people searching nearby call, and we send them to you."), style = fig(13, color = Tokens.Muted))
                    if (gbp.configured && !gbp.googleClientId.isNullOrBlank()) {
                        PrimaryButton(if (busy) tr("Connecting…") else tr("Connect with Google"), height = 48.dp, enabled = !busy) { onConnect() }
                        Text(tr("Or paste your Google Maps link"), style = fig(14, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap(onClick = onMapsLink).padding(top = 2.dp))
                    } else {
                        PrimaryButton(tr("Add your Google Maps link"), height = 48.dp) { onMapsLink() }
                    }
                }
            }
            if (error != null) Text(error, style = fig(13, color = Tokens.ErrorRed))
        }
    }
}

@Composable
private fun OfferedLeadCard(lead: LeadWire, now: Long, onOpen: () -> Unit, onDecline: () -> Unit) {
    val left = (lead.expiresAtMs - now) / 1000
    AppCard(bg = Tokens.BlueLight, borderColor = Tokens.BlueBorder) {
        Column(Modifier.fillMaxWidth().tap(onClick = onOpen).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("New customer"), style = fig(13, FontWeight.Bold, Tokens.BlueText), modifier = Modifier.weight(1f))
                Text(timeLeft(left), style = fig(13, FontWeight.Bold, if (left < 300) Tokens.Orange else Tokens.BlueText))
            }
            Text(lead.area.ifBlank { tr("Near you") }, style = bric(22, FontWeight.Bold))
            if (lead.service.isNotBlank()) Text(lead.service, style = fig(15, FontWeight.SemiBold))
            if (lead.note.isNotBlank()) Text(lead.note, style = fig(13, color = Tokens.Muted))
            Text(tr("Number: {masked}", "masked" to lead.customerPhone), style = fig(13, color = Tokens.Muted))
            Spacer(Modifier.height(4.dp))
            PrimaryButton(tr("Pay {amount} · get the number", "amount" to rupees(lead.pricePaise)), height = 52.dp) { onOpen() }
            Text(tr("Not now"), style = fig(14, FontWeight.Bold, Tokens.Muted), modifier = Modifier.align(Alignment.CenterHorizontally).tap(onClick = onDecline).padding(6.dp))
        }
    }
}

@Composable
private fun PaidLeadCard(lead: LeadWire, isCustomer: Boolean, onCall: () -> Unit, onWhatsApp: () -> Unit, onSave: () -> Unit) {
    AppCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(lead.customerName.ifBlank { tr("Customer") }, style = fig(17, FontWeight.Bold), modifier = Modifier.weight(1f))
                Text(whenText(lead.paidAtMs), style = fig(12, color = Tokens.Faint))
            }
            Text(lead.customerPhone, style = bric(22, FontWeight.Bold))
            Text(listOf(lead.area, lead.service).filter { it.isNotBlank() }.joinToString(" · "), style = fig(14, color = Tokens.Muted))
            if (lead.note.isNotBlank()) Text(lead.note, style = fig(13, color = Tokens.Muted))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(tr("Call"), Modifier.weight(1f), height = 46.dp) { onCall() }
                OutlineButton(tr("WhatsApp"), Modifier.weight(1f), height = 46.dp, border = Tokens.Green, fg = Tokens.GreenText) { onWhatsApp() }
            }
            Text(
                if (isCustomer) tr("Open customer") else tr("Save as customer"),
                style = fig(14, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap(onClick = onSave).padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun HowItWorksCard(s: LeadsScreenWire) {
    AppCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("How it works"), style = fig(16, FontWeight.Bold))
            Step("1", tr("We run Google ads on your shop's listing. People nearby see it and call."))
            Step("2", tr("Our team talks to them first and sends real customers to this screen."))
            Step("3", tr("Pay per customer (the lead's cost + {markup}) — from your wallet or UPI — and you get their number. No monthly fee.", "markup" to rupees(s.config.markupPaise)))
            Text(tr("Nothing yet — when a customer calls for your shop, it shows up here."), style = fig(13, color = Tokens.Faint))
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(24.dp).background(Tokens.BlueLight, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
            Text(n, style = fig(12, FontWeight.Bold, Tokens.BlueText))
        }
        Text(text, style = fig(14), modifier = Modifier.weight(1f))
    }
}

// ---------------- sheets ----------------

@Composable
private fun LeadSheet(
    lead: LeadWire?, screen: LeadsScreenWire?, pay: LeadPayUi, now: Long, vm: LeadsViewModel,
    customers: List<com.dailyworks.apnalaundry.domain.Customer>,
    onSaveCustomer: (LeadWire) -> Unit, onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val paidLead = pay.paidLead ?: lead?.takeIf { it.isPaid }
    val balance = screen?.wallet?.balancePaise ?: 0
    val price = lead?.pricePaise ?: 0
    var addOpen by remember { mutableStateOf(false) }

    val title = when {
        paidLead != null -> tr("You got the customer!")
        pay.confirming -> tr("Payment received")
        pay.leadGone -> tr("Money added to your wallet")
        lead == null -> tr("Lead")
        !lead.isOpen(now) -> tr("This lead has expired")
        else -> tr("Get this customer")
    }
    AppBottomSheet(title = title, onDismiss = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                paidLead != null -> {
                    val isCustomer = paidLead.phoneDigits.isNotEmpty() && customers.any { it.phone.filter(Char::isDigit).endsWith(paidLead.phoneDigits) }
                    Text(paidLead.customerName.ifBlank { tr("Customer") }, style = fig(17, FontWeight.Bold))
                    Text(paidLead.customerPhone, style = bric(30, FontWeight.Bold))
                    Text(listOf(paidLead.area, paidLead.service).filter { it.isNotBlank() }.joinToString(" · "), style = fig(14, color = Tokens.Muted))
                    if (paidLead.note.isNotBlank()) Text(paidLead.note, style = fig(13, color = Tokens.Muted))
                    pay.toppedUpPaise?.let { if (it > paidLead.pricePaise) Text(tr("{amount} added to your wallet; {left} is left for the next customer.", "amount" to rupees(it), "left" to rupees(balance)), style = fig(13, color = Tokens.GreenText)) }
                    Text(tr("Call now — they're waiting for a laundry."), style = fig(14, FontWeight.SemiBold, Tokens.BlueText))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(tr("Call"), Modifier.weight(1f)) { Analytics.leadCallTapped(paidLead.id); dial(context, paidLead.phoneDigits) }
                        OutlineButton(tr("WhatsApp"), Modifier.weight(1f), border = Tokens.Green, fg = Tokens.GreenText) {
                            Analytics.leadWhatsAppTapped(paidLead.id); openWhatsApp(context, "https://wa.me/91${paidLead.phoneDigits}")
                        }
                    }
                    OutlineButton(if (isCustomer) tr("Open customer") else tr("Save as customer"), Modifier.fillMaxWidth(), height = 48.dp) { onSaveCustomer(paidLead); onClose() }
                    Text(tr("Done"), style = fig(15, FontWeight.Bold, Tokens.Muted), modifier = Modifier.align(Alignment.CenterHorizontally).tap(onClick = onClose).padding(8.dp))
                }
                pay.confirming -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Tokens.Blue, strokeWidth = 2.5.dp)
                        Text(tr("Confirming with the bank… the number appears here in a moment. If you close this, it's in Get customers."), style = fig(14, color = Tokens.Muted), modifier = Modifier.weight(1f))
                    }
                    OutlineButton(tr("Close"), Modifier.fillMaxWidth(), height = 48.dp, onClick = onClose)
                }
                pay.leadGone -> {
                    Text(tr("The payment went through, but this lead closed while you were paying. The money ({amount}) is in your wallet for the next customer.", "amount" to rupees(pay.toppedUpPaise ?: price)), style = fig(14))
                    PrimaryButton(tr("OK"), height = 48.dp, onClick = onClose)
                }
                lead == null -> {
                    Text(tr("This lead isn't here any more."), style = fig(14, color = Tokens.Muted))
                    OutlineButton(tr("Close"), Modifier.fillMaxWidth(), height = 48.dp, onClick = onClose)
                }
                !lead.isOpen(now) -> {
                    Text(tr("The time to take it ran out. The next customer who calls for your shop will show up here."), style = fig(14, color = Tokens.Muted))
                    OutlineButton(tr("Close"), Modifier.fillMaxWidth(), height = 48.dp, onClick = onClose)
                }
                else -> {
                    Text(lead.area.ifBlank { tr("Near you") }, style = bric(24, FontWeight.Bold))
                    if (lead.service.isNotBlank()) Text(lead.service, style = fig(16, FontWeight.SemiBold))
                    if (lead.note.isNotBlank()) Text(lead.note, style = fig(14, color = Tokens.Muted))
                    Text(timeLeft((lead.expiresAtMs - now) / 1000), style = fig(13, FontWeight.Bold, Tokens.Orange))
                    Text(tr("Pay {amount} and the customer's name and number appear here. Call them and take the order.", "amount" to rupees(price)), style = fig(14))
                    Text(tr("Wallet: {balance}", "balance" to rupees(balance)), style = fig(13, color = Tokens.Muted))
                    val short = pay.shortForLead != null || balance < price
                    if (!short) {
                        PrimaryButton(if (pay.busy) tr("Paying…") else tr("Pay {amount} from wallet", "amount" to rupees(price)), enabled = !pay.busy) { vm.payFromWallet(lead) }
                        Text(tr("{left} stays in your wallet", "left" to rupees(balance - price)), style = fig(12, color = Tokens.Faint), modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        if (screen?.config?.razorpayConfigured == true && activity != null) {
                            PrimaryButton(if (pay.busy) tr("Opening payment…") else tr("Pay {amount} now", "amount" to rupees(price)), enabled = !pay.busy) { vm.payNow(activity, lead) }
                            Text(tr("UPI, card or net banking"), style = fig(12, color = Tokens.Faint), modifier = Modifier.align(Alignment.CenterHorizontally))
                            OutlineButton(tr("Add money & pay"), Modifier.fillMaxWidth(), height = 48.dp) { addOpen = !addOpen }
                            if (addOpen) {
                                Text(tr("Add to your wallet now — this customer is paid from it, the rest stays for the next ones."), style = fig(13, color = Tokens.Muted))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    screen.config.topupChoicesPaise.filter { it >= price }.forEach { amt ->
                                        PillChip(rupees(amt), selected = false) { if (!pay.busy) vm.payNow(activity, lead, amt) }
                                    }
                                }
                            }
                        } else {
                            Text(tr("Payments aren't available right now — try again later"), style = fig(13, color = Tokens.ErrorRed))
                        }
                    }
                    pay.error?.let { Text(it, style = fig(13, color = Tokens.ErrorRed)) }
                    Text(tr("Not now"), style = fig(15, FontWeight.Bold, Tokens.Muted), modifier = Modifier.align(Alignment.CenterHorizontally).tap { vm.decline(lead, "not_now"); onClose() }.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun WalletSheet(screen: LeadsScreenWire?, pay: LeadPayUi, vm: LeadsViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val choices = screen?.config?.topupChoicesPaise ?: listOf(10000, 20000, 50000, 100000)
    var chosen by rememberSaveable { mutableStateOf(choices.getOrElse(1) { 20000 }) }
    var custom by rememberSaveable { mutableStateOf("") }
    val amount = custom.toIntOrNull()?.times(100) ?: chosen
    val min = screen?.config?.topupMinPaise ?: 5000
    val max = screen?.config?.topupMaxPaise ?: 1000000
    val ok = amount in min..max
    val done = pay.toppedUpPaise != null && pay.paidLead == null
    AppBottomSheet(title = if (done) tr("Money added") else tr("Add money"), onDismiss = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                done -> {
                    Text(tr("{amount} added. Wallet: {balance}", "amount" to rupees(pay.toppedUpPaise ?: 0), "balance" to rupees(screen?.wallet?.balancePaise ?: 0)), style = fig(16, FontWeight.Bold, Tokens.GreenText))
                    Text(tr("The next customers are paid from it with one tap."), style = fig(14, color = Tokens.Muted))
                    PrimaryButton(tr("Done"), height = 48.dp, onClick = onClose)
                }
                pay.confirming -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Tokens.Blue, strokeWidth = 2.5.dp)
                        Text(tr("Confirming with the bank… your wallet updates in a moment."), style = fig(14, color = Tokens.Muted), modifier = Modifier.weight(1f))
                    }
                    OutlineButton(tr("Close"), Modifier.fillMaxWidth(), height = 48.dp, onClick = onClose)
                }
                else -> {
                    Text(tr("Wallet: {balance}", "balance" to rupees(screen?.wallet?.balancePaise ?: 0)), style = fig(14, color = Tokens.Muted))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        choices.forEach { amt -> PillChip(rupees(amt), selected = custom.isBlank() && chosen == amt) { chosen = amt; custom = "" } }
                    }
                    FieldBox(value = custom, onValueChange = { v -> custom = v.filter(Char::isDigit).take(5) }, placeholder = tr("Other amount"), prefix = "₹", keyboardType = KeyboardType.Number)
                    if (!ok && custom.isNotBlank()) Text(tr("Between {min} and {max}", "min" to rupees(min), "max" to rupees(max)), style = fig(12, color = Tokens.ErrorRed))
                    if (screen?.config?.razorpayConfigured == true && activity != null) {
                        PrimaryButton(if (pay.busy) tr("Opening payment…") else tr("Add {amount}", "amount" to rupees(amount)), enabled = ok && !pay.busy) { vm.topup(activity, amount) }
                        Text(tr("UPI, card or net banking · the money is only for customers here, no withdrawals"), style = fig(12, color = Tokens.Faint), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text(tr("Payments aren't available right now — try again later"), style = fig(13, color = Tokens.ErrorRed))
                    }
                    pay.error?.let { Text(it, style = fig(13, color = Tokens.ErrorRed)) }
                }
            }
        }
    }
}

@Composable
private fun MapsLinkSheet(current: String?, onSave: (String, (String?) -> Unit) -> Unit, onClose: () -> Unit) {
    var url by rememberSaveable { mutableStateOf(current.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AppBottomSheet(title = tr("Your shop's Google Maps link"), subtitle = tr("Google Maps → your shop → Share → Copy link"), onDismiss = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldBox(value = url, onValueChange = { url = it.trim(); error = null }, placeholder = "https://maps.app.goo.gl/…", keyboardType = KeyboardType.Uri)
            error?.let { Text(it, style = fig(13, color = Tokens.ErrorRed)) }
            PrimaryButton(if (busy) tr("Saving…") else tr("Save"), enabled = url.isNotBlank() && !busy) {
                busy = true
                onSave(url) { err -> busy = false; if (err == null) onClose() else error = err }
            }
        }
    }
}

// ---------------- Home ----------------

/** Home: the "ride request" card while a lead waits for the owner. */
@Composable
fun LeadsHomeCard(navigator: AppNavigator) {
    val access = LocalStaffAccess.current
    if (!access.isOwner) return
    val repo: LeadsRepository = koinInject()
    val st by repo.state.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val lead = st?.leads?.firstOrNull { it.isOpen(now) } ?: return
    LaunchedEffect(lead.id) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        AppCard(bg = Tokens.BlueLight, borderColor = Tokens.BlueBorder) {
            Row(
                Modifier.fillMaxWidth().tap { Analytics.leadsEntryTapped("home_card"); navigator.openLead(lead.id, "home_card") }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("New customer waiting"), style = fig(13, FontWeight.Bold, Tokens.BlueText))
                    Text(listOf(lead.area, lead.service).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { tr("Near you") }, style = fig(16, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr("Pay {amount} to get the number · {time}", "amount" to rupees(lead.pricePaise), "time" to timeLeft((lead.expiresAtMs - now) / 1000)), style = fig(13, color = Tokens.Muted))
                }
                Text(tr("Open"), style = fig(15, FontWeight.Bold, Tokens.Blue))
            }
        }
    }
}

// ---------------- Google sign-in for the Business Profile ----------------

/**
 * Google's Authorization flow for the business.manage scope with offline access for our server
 * client id: the owner picks an account, grants, and a server auth code comes back, which the
 * server trades for tokens (routes/laundry-leads.js /gbp/connect). [onFailed] gets null when the
 * owner cancelled.
 */
@Composable
private fun rememberGoogleBusinessConnect(clientId: String?, onCode: (String) -> Unit, onFailed: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data) }
                .onSuccess { r -> val code = r.serverAuthCode; if (code.isNullOrBlank()) onFailed("no_code") else onCode(code) }
                .onFailure { onFailed(it.message ?: "error") }
        } else onFailed(null)
    }
    return remember(clientId) {
        {
            if (clientId.isNullOrBlank()) onFailed("not_configured") else {
                val request = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(Scope(GBP_SCOPE)))
                    .requestOfflineAccess(clientId, true)
                    .build()
                Identity.getAuthorizationClient(context).authorize(request)
                    .addOnSuccessListener { r ->
                        if (r.hasResolution()) {
                            val pi = r.pendingIntent
                            if (pi != null) launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build()) else onFailed("no_intent")
                        } else {
                            val code = r.serverAuthCode
                            if (code.isNullOrBlank()) onFailed("no_code") else onCode(code)
                        }
                    }
                    .addOnFailureListener { onFailed(it.message ?: "error") }
            }
        }
    }
}
