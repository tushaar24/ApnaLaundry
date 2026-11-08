package com.dailyworks.apnalaundry.ui.screens.staff

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.data.staff.StaffException
import com.dailyworks.apnalaundry.data.staff.StaffRepository
import com.dailyworks.apnalaundry.domain.ExpenseCategory
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.StaffActivity
import com.dailyworks.apnalaundry.domain.StaffActivityPage
import com.dailyworks.apnalaundry.domain.StaffMember
import com.dailyworks.apnalaundry.domain.StaffOccupation
import com.dailyworks.apnalaundry.domain.StaffPermission
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppCard
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.OutlineButton
import com.dailyworks.apnalaundry.ui.components.PillChip
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.TopBar
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.screens.bill.openWhatsApp
import com.dailyworks.apnalaundry.ui.theme.Tokens
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PLAY_LINK = "https://play.google.com/store/apps/details?id=com.dailyworks.apnalaundry"

/** "Ramesh can now log in": opens their WhatsApp chat with the app link and what to do. */
private fun shareAppLink(context: android.content.Context, m: StaffMember, shopName: String, from: String) {
    Analytics.staffAppLinkShared(m.id, from)
    val text = tr(
        "Hi {name}, please install MyLaundry: {link}\nLog in with your number {phone} to work at {shop}.",
        "name" to Selectors.firstName(m.name), "link" to PLAY_LINK, "phone" to m.phone, "shop" to shopName,
    )
    val digits = m.phone.filter(Char::isDigit)
    openWhatsApp(context, if (digits.length == 10) "https://wa.me/91$digits?text=${Uri.encode(text)}" else "https://wa.me/?text=${Uri.encode(text)}")
}

private fun lastActive(at: Long?): String {
    if (at == null) return tr("Not logged in yet")
    val mins = ((System.currentTimeMillis() - at) / 60_000).coerceAtLeast(0)
    return when {
        mins < 2 -> tr("Last active: just now")
        mins < 60 -> tr("Last active: {n} min ago", "n" to mins)
        mins < 24 * 60 -> tr("Last active: {n} h ago", "n" to mins / 60)
        else -> tr("Last active: {date}", "date" to AppDate.plain(isoDay(at)))
    }
}

private fun isoDay(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private fun clock(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(timeFmt).lowercase()

private fun occupationLabel(key: String) = StaffOccupation.of(key)?.let { tr(it.label) } ?: key

// ---------------- Staff list ----------------

/** Settings → Staff: the shop's staff, "Today: 6 orders · ₹1,240", and Add staff. */
@Composable
fun StaffListScreen(navigator: AppNavigator, from: String) {
    val repo: StaffRepository = koinInject()
    val list by repo.staff.collectAsStateWithLifecycle()
    val planActive by repo.planActive.collectAsStateWithLifecycle()
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load() = scope.launch {
        failed = runCatching { repo.refresh() }.isFailure
    }
    LaunchedEffect(Unit) {
        Analytics.screen("staff")
        load().join()
        Analytics.staffOpened(from, repo.staff.value?.size ?: -1)
        if (!repo.planActive.value) Analytics.staffPlanRequiredShown(from)
    }

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars)) {
        TopBar(tr("Staff"), onBack = { navigator.back() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!planActive) {
                AppCard(bg = Tokens.OrangeLight, borderColor = Tokens.OrangeLight) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("Staff needs a MyLaundry plan"), style = fig(16, FontWeight.Bold))
                        Text(tr("With a plan, your workers can use MyLaundry on their own phones."), style = fig(14, color = Tokens.InkSecondary))
                        PrimaryButton(tr("See plans"), height = 48.dp) { navigator.openSubscription() }
                    }
                }
            }
            when {
                list == null && failed -> Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("Could not load. Check the internet and try again."), style = fig(14, color = Tokens.Muted), textAlign = TextAlign.Center)
                    OutlineButton(tr("Try again"), height = 44.dp) { load() }
                }
                list == null -> Text(tr("Loading…"), style = fig(14, color = Tokens.Muted), modifier = Modifier.padding(top = 40.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                list!!.isEmpty() -> Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("👥", style = fig(40))
                    Text(tr("No staff yet"), style = bric(20, FontWeight.Bold))
                    Text(
                        tr("Add your workers so they can take orders, deliver and collect money from their own phones. You choose what each one can do."),
                        style = fig(14, color = Tokens.Muted), textAlign = TextAlign.Center,
                    )
                }
                else -> list!!.forEach { m -> StaffCard(m) { Analytics.staffRowTapped(m.id); navigator.openStaffMember(m.id) } }
            }
        }
        PrimaryButton(
            "+ " + tr("Add staff"),
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Analytics.staffAddTapped(list?.size ?: -1)
            if (!planActive) navigator.openSubscription() else navigator.openStaffForm("")
        }
    }
}

@Composable
private fun StaffCard(m: StaffMember, onClick: () -> Unit) {
    AppCard(Modifier.tap(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).rounded(999.dp).background(Tokens.BlueLight), contentAlignment = Alignment.Center) {
                Text(Selectors.initials(m.name), style = fig(15, FontWeight.Bold, Tokens.BlueText))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(m.name, style = fig(16, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(m.occupation.takeIf { it.isNotBlank() }?.let { occupationLabel(it) }, "+91 ${Selectors.fmtPhone(m.phone)}").joinToString(" · "),
                    style = fig(13, color = Tokens.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    tr("Today: {orders} orders · {amount} collected", "orders" to m.todayOrders, "amount" to Money.rupees(m.todayCollected)),
                    style = fig(13, FontWeight.SemiBold, Tokens.InkSecondary),
                )
                Text(lastActive(m.lastSeenAt), style = fig(12, color = Tokens.Faint))
            }
        }
    }
}

// ---------------- Add / edit ----------------

/**
 * Add a staff member ([id] empty) or change one: name, phone (when adding), occupation chips
 * that fill the switches, and the nine switches. After adding: "Share the app on WhatsApp".
 */
@Composable
fun StaffFormScreen(shopVm: ShopViewModel, navigator: AppNavigator, id: String) {
    val repo: StaffRepository = koinInject()
    val state by shopVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isNew = id.isEmpty()
    val mode = if (isNew) "new" else "edit"
    val existing = remember(id) { repo.staff.value?.firstOrNull { it.id == id } }
    if (!isNew && existing == null) {
        LaunchedEffect(Unit) { navigator.back() }
        return
    }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var phone by rememberSaveable { mutableStateOf("") }
    // A work chip's key, or "other" — then [otherWork] is what the owner typed (saved as the work).
    val known = existing?.occupation?.let { StaffOccupation.of(it) }
    var occupation by rememberSaveable {
        mutableStateOf(if (existing != null && existing.occupation.isNotBlank() && known == null) StaffOccupation.OTHER.key else existing?.occupation ?: "")
    }
    var otherWork by rememberSaveable { mutableStateOf(if (existing != null && known == null) existing.occupation else "") }
    val work = if (occupation == StaffOccupation.OTHER.key) otherWork.trim().ifBlank { StaffOccupation.OTHER.key } else occupation
    var perms by rememberSaveable { mutableStateOf<List<String>>(existing?.permissions?.map { it.key } ?: emptyList()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var added by remember { mutableStateOf<StaffMember?>(null) }
    val scope = rememberCoroutineScope()
    val canSave = name.isNotBlank() && (!isNew || phone.length == 10) && !saving

    LaunchedEffect(Unit) { Analytics.staffFormShown(mode) }
    fun close() {
        if (added == null) Analytics.staffFormClosed(mode, name.isNotBlank(), phone.isNotBlank())
        navigator.back()
    }
    BackHandler { close() }

    fun save() {
        saving = true
        error = null
        val set = perms.mapNotNull { StaffPermission.of(it) }.toSet()
        scope.launch {
            runCatching {
                if (isNew) repo.add(name.trim(), phone, work, set) else repo.update(id, name.trim(), work, set)
            }.onSuccess { m ->
                Analytics.staffSaved(isNew, m.id, work, set.map { it.key })
                if (isNew) added = m else navigator.back()
            }.onFailure { e ->
                val code = (e as? StaffException)?.code ?: "NETWORK"
                Analytics.staffSaveFailed(mode, code)
                error = when (code) {
                    "PHONE_TAKEN" -> tr("This number is already in your staff")
                    "OWN_NUMBER" -> tr("That's your own number")
                    "PHONE_INVALID" -> tr("Enter a valid 10-digit mobile number")
                    "STAFF_LIMIT" -> tr("You can add up to 10 staff in a shop")
                    "PLAN_REQUIRED" -> tr("Staff needs a MyLaundry plan")
                    else -> tr("Could not save. Check the internet and try again.")
                }
                if (code == "PLAN_REQUIRED") navigator.openSubscription()
            }
            saving = false
        }
    }

    added?.let { m ->
        StaffAddedView(m, state.shop.name, onShare = { shareAppLink(context, m, state.shop.name, "added") }, onDone = { navigator.back() })
        return
    }

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
        TopBar(if (isNew) tr("Add staff") else tr("Edit staff"), onBack = { close() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Labelled(tr("Name")) {
                FieldBox(name, { name = it.take(60) }, Modifier.fillMaxWidth(), placeholder = tr("e.g. Ramesh"), capitalization = KeyboardCapitalization.Words)
            }
            if (isNew) {
                Labelled(tr("Phone number")) {
                    FieldBox(phone, { phone = it.filter(Char::isDigit).take(10) }, Modifier.fillMaxWidth(), prefix = "+91", keyboardType = KeyboardType.Phone)
                    Text(tr("They log in to MyLaundry with this number."), style = fig(12, color = Tokens.Muted))
                }
            } else {
                Labelled(tr("Phone number")) { Text("+91 ${Selectors.fmtPhone(existing!!.phone)}", style = fig(16, FontWeight.SemiBold)) }
            }
            Labelled(tr("Work (optional)")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StaffOccupation.entries.forEach { o ->
                        PillChip(tr(o.label), occupation == o.key) {
                            occupation = o.key
                            if (o != StaffOccupation.OTHER) perms = o.preset.map { it.key }
                            Analytics.staffOccupationPicked(mode, o.key)
                        }
                    }
                }
                // "Other": the owner writes the work (e.g. Cashier); it shows on the staff card.
                if (occupation == StaffOccupation.OTHER.key) {
                    FieldBox(
                        otherWork, { otherWork = it.take(30) }, Modifier.fillMaxWidth(),
                        placeholder = tr("What work do they do? e.g. Cashier"), capitalization = KeyboardCapitalization.Sentences,
                    )
                }
                Text(tr("Picking one fills the switches below. You can change any."), style = fig(12, color = Tokens.Muted))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (name.isNotBlank()) tr("What can {name} do?", "name" to Selectors.firstName(name.trim())) else tr("What can they do?"),
                    style = fig(15, FontWeight.Bold),
                )
                AppCard {
                    Column(Modifier.fillMaxWidth()) {
                        StaffPermission.entries.forEachIndexed { i, p ->
                            if (i > 0) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(Tokens.Divider))
                            Row(
                                Modifier.fillMaxWidth().tap {
                                    val on = p.key !in perms
                                    perms = if (on) perms + p.key else perms - p.key
                                    Analytics.staffPermissionToggled(mode, p.key, on)
                                }.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr(p.label), style = fig(15, FontWeight.SemiBold))
                                    Text(tr(p.hint), style = fig(12, color = Tokens.Muted))
                                }
                                Switch(
                                    checked = p.key in perms,
                                    onCheckedChange = { on ->
                                        perms = if (on) perms + p.key else perms - p.key
                                        Analytics.staffPermissionToggled(mode, p.key, on)
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = Tokens.Blue, checkedThumbColor = Color.White),
                                )
                            }
                        }
                    }
                }
                Text(
                    tr("Everyone can see orders, mark picked up and ready, print tags and search customers."),
                    style = fig(12, color = Tokens.Muted),
                )
            }
        }
        Column(Modifier.fillMaxWidth().background(Tokens.Card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, style = fig(13, FontWeight.SemiBold, Tokens.ErrorRed), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            PrimaryButton(
                if (saving) tr("Saving…") else if (isNew) tr("Add staff") else tr("Save"),
                Modifier.fillMaxWidth(), enabled = canSave, height = 56.dp,
                onDisabledClick = { if (!saving) Analytics.staffSaveFailed(mode, if (name.isBlank()) "name" else "phone") },
            ) { save() }
        }
    }
}

@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = fig(14, FontWeight.SemiBold))
        content()
    }
}

@Composable
private fun StaffAddedView(m: StaffMember, shopName: String, onShare: () -> Unit, onDone: () -> Unit) {
    BackHandler { onDone() }
    Column(
        Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(72.dp).rounded(999.dp).background(Tokens.GreenText), contentAlignment = Alignment.Center) {
            Text("✓", style = bric(36, FontWeight.Bold, Color.White))
        }
        Text(tr("{name} can now use MyLaundry", "name" to Selectors.firstName(m.name)), style = bric(22, FontWeight.Bold), textAlign = TextAlign.Center)
        Text(
            tr("Ask {name} to install MyLaundry and log in with {phone}. They will see {shop} straight away.",
                "name" to Selectors.firstName(m.name), "phone" to "+91 ${Selectors.fmtPhone(m.phone)}", "shop" to shopName),
            style = fig(15, color = Tokens.InkSecondary), textAlign = TextAlign.Center,
        )
        PrimaryButton(tr("Share the app on WhatsApp"), Modifier.fillMaxWidth(), height = 56.dp) { onShare() }
        OutlineButton(tr("Done"), Modifier.fillMaxWidth(), height = 52.dp) { onDone() }
    }
}

// ---------------- One staff member ----------------

/** A staff member: what they can do, the last 7 days (tiles + log), Edit, Remove. */
@Composable
fun StaffMemberScreen(shopVm: ShopViewModel, navigator: AppNavigator, id: String) {
    val repo: StaffRepository = koinInject()
    val list by repo.staff.collectAsStateWithLifecycle()
    val state by shopVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val m = list?.firstOrNull { it.id == id }
    var page by remember { mutableStateOf<StaffActivityPage?>(null) }
    var failed by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) {
        Analytics.screen("staff_member")
        runCatching { repo.activity(id) }
            .onSuccess { page = it; Analytics.staffActivityViewed(id, it.rows.size) }
            .onFailure { failed = true }
    }
    if (m == null) {
        LaunchedEffect(Unit) { navigator.back() }
        return
    }

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars)) {
        TopBar(m.name, onBack = { navigator.back() }, trailing = {
            Text(tr("Edit"), style = fig(15, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { navigator.openStaffForm(m.id) }.padding(12.dp))
        })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        listOfNotNull(m.occupation.takeIf { it.isNotBlank() }?.let { occupationLabel(it) }, "+91 ${Selectors.fmtPhone(m.phone)}").joinToString(" · "),
                        style = fig(15, FontWeight.SemiBold),
                    )
                    Text(lastActive(m.lastSeenAt), style = fig(13, color = Tokens.Muted))
                    Text(tr("Can do"), style = fig(13, FontWeight.Bold, Tokens.Muted), modifier = Modifier.padding(top = 8.dp))
                    if (m.permissions.isEmpty()) {
                        Text(tr("Only the basics: see orders, mark picked up and ready, print tags"), style = fig(14, color = Tokens.InkSecondary))
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            StaffPermission.entries.filter { it in m.permissions }.forEach { p ->
                                Box(Modifier.rounded(999.dp).background(Tokens.BlueLight).padding(horizontal = 10.dp, vertical = 5.dp)) {
                                    Text(tr(p.label), style = fig(13, FontWeight.SemiBold, Tokens.BlueText))
                                }
                            }
                        }
                    }
                }
            }
            if (m.lastSeenAt == null) {
                OutlineButton(tr("Share the app on WhatsApp"), Modifier.fillMaxWidth(), height = 48.dp) { shareAppLink(context, m, state.shop.name, "member") }
            }

            Text(tr("Last 7 days"), style = fig(15, FontWeight.Bold))
            val p = page
            when {
                p == null && failed -> Text(tr("Could not load. Check the internet and try again."), style = fig(14, color = Tokens.Muted))
                p == null -> Text(tr("Loading…"), style = fig(14, color = Tokens.Muted))
                else -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile(tr("Orders taken"), p.ordersTaken.toString(), Modifier.weight(1f))
                        Tile(tr("Delivered"), p.delivered.toString(), Modifier.weight(1f))
                        Tile(tr("Collected"), Money.rupees(p.collected), Modifier.weight(1f))
                    }
                    if (p.rows.isEmpty()) {
                        Text(tr("Nothing yet. What {name} does in the app shows here.", "name" to Selectors.firstName(m.name)), style = fig(14, color = Tokens.Muted))
                    } else {
                        val today = AppDate.TODAY
                        p.rows.groupBy { isoDay(it.at) }.forEach { (day, rows) ->
                            Text(
                                when (day) { today -> tr("Today"); AppDate.add(today, -1) -> tr("Yesterday"); else -> AppDate.plain(day) },
                                style = fig(13, FontWeight.Bold, Tokens.Muted), modifier = Modifier.padding(top = 4.dp),
                            )
                            AppCard {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                                    rows.forEach { a ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(clock(a.at), style = fig(13, color = Tokens.Muted), modifier = Modifier.width(64.dp))
                                            Text(activityLine(a, state), style = fig(14, FontWeight.SemiBold, if (a.action == "refused") Tokens.OrangeText else Tokens.Ink), modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text(
                tr("Remove staff"), style = fig(15, FontWeight.Bold, Tokens.DeleteRed), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).tap { Analytics.staffRemoveTapped(m.id); confirmRemove = true }.padding(12.dp),
            )
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false; Analytics.staffRemoveCancelled(m.id) },
            title = { Text(tr("Remove {name}?", "name" to Selectors.firstName(m.name)), style = fig(18, FontWeight.Bold)) },
            text = { Text(tr("{name} will be logged out of {shop} on their phone. The orders they made stay.", "name" to Selectors.firstName(m.name), "shop" to state.shop.name), style = fig(15)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        runCatching { repo.remove(m.id) }
                            .onSuccess { Analytics.staffRemoved(m.id); shopVm.showInfo(tr("{name} removed", "name" to Selectors.firstName(m.name))); navigator.back() }
                            .onFailure { shopVm.showInfo(tr("Could not remove. Check the internet and try again.")) }
                    }
                }) { Text(tr("Remove"), style = fig(15, FontWeight.Bold, Tokens.DeleteRed)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false; Analytics.staffRemoveCancelled(m.id) }) { Text(tr("Cancel"), style = fig(15, FontWeight.Bold)) } },
            containerColor = Tokens.Card,
        )
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    AppCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = bric(20, FontWeight.Bold), maxLines = 1)
            Text(label, style = fig(12, color = Tokens.Muted), maxLines = 2)
        }
    }
}

/** One log line in plain words, with the shop's own order numbers and customer names. */
private fun activityLine(a: StaffActivity, state: LaundryState): String {
    val no = a.orderId?.let { id -> state.orders.firstOrNull { it.id == id }?.no() ?: id.toString() }
    val cust = a.custId?.let { c -> state.customers.firstOrNull { it.id == c }?.name }
    val amount = a.amount?.let { Money.rupees(it) }
    val order = no?.let { "#$it" }
    val head = when (a.action) {
        "order_new" -> tr("New order {order}", "order" to (order ?: ""))
        "order_picked_up" -> tr("Picked up {order}", "order" to (order ?: ""))
        "order_ready" -> tr("Marked ready {order}", "order" to (order ?: ""))
        "order_delivered" -> tr("Delivered {order}", "order" to (order ?: ""))
        "order_cancelled" -> tr("Cancelled {order}", "order" to (order ?: ""))
        "order_status_changed" -> tr("Changed the status of {order}", "order" to (order ?: ""))
        "order_edited" -> tr("Edited {order}", "order" to (order ?: ""))
        "order_deleted" -> tr("Deleted {order}", "order" to (order ?: ""))
        "discount_given" -> tr("Discount {amount} on {order}", "amount" to (amount ?: ""), "order" to (order ?: ""))
        "money_received" -> if (a.method == "UPI") tr("Got {amount} UPI", "amount" to (amount ?: "")) else tr("Got {amount} cash", "amount" to (amount ?: ""))
        "payment_removed" -> tr("Removed a payment of {amount}", "amount" to (amount ?: ""))
        "old_baaki_added" -> tr("Added old baaki {amount}", "amount" to (amount ?: ""))
        "customer_added" -> tr("Added customer {name}", "name" to a.detail)
        "customer_edited" -> tr("Changed customer {name}", "name" to a.detail)
        "rate_added" -> tr("Added rate: {name}", "name" to a.detail)
        "rate_changed" -> tr("Changed rate: {name}", "name" to a.detail)
        "rate_deleted" -> tr("Deleted rate: {name}", "name" to a.detail)
        "expense_added" -> tr("Added expense {amount} · {what}", "amount" to (amount ?: ""), "what" to ExpenseCategory.label(a.detail))
        "expense_edited" -> tr("Changed expense {amount} · {what}", "amount" to (amount ?: ""), "what" to ExpenseCategory.label(a.detail))
        "expense_deleted" -> tr("Deleted expense {amount} · {what}", "amount" to (amount ?: ""), "what" to ExpenseCategory.label(a.detail))
        "login" -> tr("Opened the shop")
        "refused" -> tr("Tried something they are not allowed to")
        else -> a.action
    }
    val tail = when (a.action) {
        "order_new", "order_edited" -> listOfNotNull(cust, amount)
        "money_received", "payment_removed" -> listOfNotNull(order, cust)
        "old_baaki_added" -> listOfNotNull(cust)
        "order_cancelled" -> listOfNotNull(cust, a.detail.takeIf { it.isNotBlank() })
        "order_picked_up", "order_ready", "order_delivered", "order_deleted", "order_status_changed", "discount_given" -> listOfNotNull(cust)
        else -> emptyList()
    }
    return (listOf(head.trim()) + tail).joinToString(" · ")
}
