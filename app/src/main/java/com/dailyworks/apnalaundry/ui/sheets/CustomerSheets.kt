package com.dailyworks.apnalaundry.ui.sheets

import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.PayMethod
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppBottomSheet
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.PillChip
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.ui.tour.TourStage
import com.dailyworks.apnalaundry.ui.tour.TourSteps
import com.dailyworks.apnalaundry.ui.tour.TourTip
import kotlinx.coroutines.launch
import com.dailyworks.apnalaundry.data.PhoneContacts
import com.dailyworks.apnalaundry.data.PhoneContact
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.data.experiments.CustomerFlow
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.PackageManager
import android.Manifest
import kotlin.math.max

@Composable
fun CustomerFormSheet(state: LaundryState, form: ActiveSheet.CustomerForm, vm: ShopViewModel, onDismiss: () -> Unit) {
    val editing = form.editId != null
    val existing = form.editId?.let { id -> state.customers.firstOrNull { it.id == id } }
    var name by remember { mutableStateOf(existing?.name ?: form.prefillName) }
    var phone by remember { mutableStateOf(existing?.phone ?: form.prefillPhone) }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var oldBaaki by remember { mutableStateOf("") }

    val dup = if (phone.length == 10) state.customers.firstOrNull { it.phone == phone && !(editing && it.id == form.editId) } else null
    // v2 of the first-customer test: no address here (it's asked in its own sheet for home pickup / delivery).
    val addressShown = form.askAddress
    val addrNeeded = form.needAddress && form.ctx == "order" && addressShown
    val tour by vm.tour.collectAsStateWithLifecycle()
    val flow by vm.customerFlow.collectAsStateWithLifecycle()

    // ── analytics: what happens inside the sheet, and how it ends ──
    val openedAt = remember { System.currentTimeMillis() }
    val edited = remember { mutableSetOf<String>() }
    var how by remember { mutableStateOf("dismissed") } // saved | used_existing | dismissed
    var method by remember { mutableStateOf<String?>(null) } // where the saved number came from
    var pickedPhone by remember { mutableStateOf<String?>(null) }
    var contactsTapped by remember { mutableStateOf(false) }
    var blockedTaps by remember { mutableIntStateOf(0) }
    var phoneTrimLogged by remember { mutableStateOf(false) }
    val mode = if (editing) "edit" else "new"
    fun secs() = (System.currentTimeMillis() - openedAt) / 1000
    fun missing(): String = when {
        name.trim().isEmpty() -> "name"
        phone.isEmpty() -> "phone_empty"
        phone.length < 10 -> "phone_short"
        dup != null -> "duplicate"
        addrNeeded && address.trim().isEmpty() -> "address"
        else -> "none"
    }
    fun fieldEdited(field: String) { if (edited.add(field)) Analytics.customerSheetFieldEdited(field, form.ctx, secs()) }
    LaunchedEffect(Unit) {
        Analytics.customerSheetOpened(
            ctx = form.ctx, mode = mode, trigger = form.trigger, addressShown = addressShown, addressNeeded = addrNeeded,
            prefilled = when { form.prefillPhone.isNotBlank() -> "phone"; form.prefillName.isNotBlank() -> "name"; else -> "none" },
            customers = state.customers.size, guideOn = tour == TourStage.ORDER,
        )
    }
    LaunchedEffect(dup?.id) { if (dup != null) Analytics.customerDuplicateShown(form.ctx) }
    DisposableEffect(Unit) {
        onDispose {
            Analytics.customerSheetClosed(
                ctx = form.ctx, mode = mode, trigger = form.trigger, how = how, seconds = secs(),
                nameFilled = name.trim().isNotEmpty(), phoneDigits = phone.length, addressShown = addressShown,
                addressFilled = address.trim().isNotEmpty(), contactsTapped = contactsTapped, contactPicked = pickedPhone != null,
                blockedTaps = blockedTaps, missing = if (how == "dismissed") missing() else "none", method = method,
            )
        }
    }

    // ── add from the phone's contacts (manual entry below still works) ──
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    fun fill(c: PhoneContact) { name = c.name; phone = c.phone; pickedPhone = c.phone; picking = false }
    val savedPhones = remember(state.customers) { state.customers.map { it.phone }.toSet() }
    // Permission refused: Android's own contact picker needs none.
    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri == null) { Analytics.customerContactsClosed("system", null, false); return@rememberLauncherForActivityResult }
        scope.launch {
            val c = PhoneContacts.fromPickerUri(context, uri)
            Analytics.customerContactPicked("system", ok = c != null, alreadySaved = c != null && c.phone in savedPhones)
            c?.let(::fill) ?: vm.showInfo(tr("That contact has no 10-digit mobile number"))
        }
    }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Analytics.contactsPermissionAnswered(granted)
        if (granted) picking = true else systemPicker.launch(null)
    }
    fun chooseFromContacts() {
        contactsTapped = true
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        Analytics.customerContactsTapped(if (granted) "granted" else "asking")
        if (granted) picking = true else askContacts.launch(Manifest.permission.READ_CONTACTS)
    }
    if (picking) {
        ContactPickerSheet(
            state,
            onPick = { c -> Analytics.customerContactPicked("in_app", ok = true, alreadySaved = c.phone in savedPhones); fill(c) },
            onBack = { contacts, searched -> Analytics.customerContactsClosed("in_app", contacts, searched); picking = false },
        )
        return
    }
    val valid = missing() == "none"

    AppBottomSheet(title = if (editing) tr("Edit customer") else tr("New customer"), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!editing && form.ctx == "order" && tour == TourStage.ORDER) TourTip(
                when (flow) {
                    CustomerFlow.LEGACY, CustomerFlow.NO_ADDRESS -> TourSteps.customerForm
                    CustomerFlow.AUTO_SHEET, CustomerFlow.TAP_TO_ADD -> TourSteps.customerFormAddressOptional
                },
            )
            if (!editing) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).rounded(14.dp).background(Tokens.BlueLight).tap { chooseFromContacts() },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Contacts, null, tint = Tokens.Blue, modifier = Modifier.size(20.dp))
                    Text("  " + tr("Choose from contacts"), style = fig(15, FontWeight.Bold, Tokens.Blue))
                }
                Text(tr("or type the details"), style = fig(12, color = Tokens.Muted), modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Field(tr("Name"), name, { fieldEdited("name"); name = it }, tr("Customer name"))
            Field(tr("Phone"), phone, {
                fieldEdited("phone")
                val digits = it.filter { c -> c.isDigit() }
                if (digits.length > 10 && !phoneTrimLogged) {
                    phoneTrimLogged = true
                    Analytics.customerPhoneTrimmed(digits.length, when { digits.startsWith("91") -> "91"; digits.startsWith("0") -> "0"; else -> "other" })
                }
                phone = digits.take(10)
            }, tr("10-digit number"), prefix = "+91", kb = KeyboardType.Phone)
            if (dup != null) {
                Row(Modifier.fillMaxWidth().rounded(12.dp).background(Tokens.OrangeLight).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Already saved as {name}", "name" to dup.name), style = fig(14, FontWeight.SemiBold, Tokens.OrangeDeep), modifier = Modifier.weight(1f))
                    Text(tr("Use this"), style = fig(14, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { how = "used_existing"; form.onSaved(dup.id); onDismiss() })
                }
            }
            if (addressShown) Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Address"), style = fig(14, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Text(if (addrNeeded) tr("Needed for home pickup / delivery") else tr("Optional"), style = fig(12, color = if (addrNeeded) Tokens.OrangeText else Tokens.Muted), textAlign = TextAlign.End)
                }
                Box(Modifier.height(6.dp))
                FieldBox(address, { fieldEdited("address"); address = it.replace('\n', ' ') }, placeholder = tr("House, area, landmark"), height = 80.dp, singleLine = false,
                    borderColor = if (addrNeeded && address.isBlank()) Tokens.OrangeBorder else Tokens.FieldBorder)
            }
            if (form.ctx == "list" && !editing) {
                Field(tr("Old khata from your notebook"), oldBaaki, { fieldEdited("old_khata"); oldBaaki = it.filter { c -> c.isDigit() }.take(6) }, "0", prefix = "₹", kb = KeyboardType.Number)
            }
            PrimaryButton(
                if (editing) tr("Save changes") else tr("Save customer"), enabled = valid, height = 56.dp,
                onDisabledClick = {
                    blockedTaps++
                    Analytics.customerSaveBlocked(missing(), form.ctx, name.trim().isNotEmpty(), phone.length, address.trim().isNotEmpty())
                },
            ) {
                how = "saved"
                method = if (pickedPhone != null && pickedPhone == phone) "contacts" else "typed"
                vm.saveCustomer(form.editId, name.trim(), phone, address.trim(), oldBaaki.toIntOrNull() ?: 0, form.ctx == "list") { id ->
                    form.onSaved(id)
                }
                onDismiss()
            }
        }
    }
}

/**
 * v2 of the first-customer test: the address alone, when the order is home
 * pickup / delivery and the customer has none (the New customer sheet doesn't ask).
 */
@Composable
fun AddressFormSheet(state: LaundryState, form: ActiveSheet.AddressForm, vm: ShopViewModel, onDismiss: () -> Unit) {
    val c = state.customers.firstOrNull { it.id == form.custId } ?: return
    var address by remember { mutableStateOf(c.address) }
    val tour by vm.tour.collectAsStateWithLifecycle()
    val openedAt = remember { System.currentTimeMillis() }
    var how by remember { mutableStateOf("dismissed") }
    var edited by remember { mutableStateOf(false) }
    var blockedTaps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { Analytics.addressSheetOpened(form.trigger, c.address.isNotBlank(), tour == TourStage.ORDER) }
    DisposableEffect(Unit) {
        onDispose {
            Analytics.addressSheetClosed(form.trigger, how, (System.currentTimeMillis() - openedAt) / 1000, edited, address.isNotBlank(), blockedTaps)
        }
    }
    AppBottomSheet(title = tr("Customer's address"), subtitle = tr("{name} · for home pickup / delivery", "name" to c.name), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (tour == TourStage.ORDER) TourTip(TourSteps.addressForm)
            FieldBox(
                address, { edited = true; address = it.replace('\n', ' ') }, placeholder = tr("House, area, landmark"), height = 96.dp, singleLine = false,
                borderColor = if (address.isBlank()) Tokens.OrangeBorder else Tokens.FieldBorder,
            )
            PrimaryButton(
                tr("Save address"), enabled = address.isNotBlank(), height = 56.dp,
                onDisabledClick = { blockedTaps++; Analytics.customerSaveBlocked("address", "address_sheet", true, c.phone.length, false) },
            ) {
                how = "saved"
                vm.saveCustomer(c.id, c.name, c.phone, address.trim(), 0, false) { id -> form.onSaved(id) }
                onDismiss()
            }
        }
    }
}

/**
 * Searchable list of the phone's contacts; tapping one fills the customer form.
 * [onBack] gets how many contacts it listed (null = still loading) and whether the search was typed in.
 */
@Composable
private fun ContactPickerSheet(state: LaundryState, onPick: (PhoneContact) -> Unit, onBack: (contacts: Int?, searched: Boolean) -> Unit) {
    val context = LocalContext.current
    var all by remember { mutableStateOf<List<PhoneContact>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { all = runCatching { PhoneContacts.load(context) }.getOrDefault(emptyList()) }
    val saved = remember(state.customers) { state.customers.map { it.phone }.toSet() }
    val q = query.trim().lowercase()
    val shown = all.orEmpty().filter { q.isEmpty() || it.name.lowercase().contains(q) || it.phone.contains(q.filter { c -> c.isDigit() }.ifEmpty { "\u0000" }) }
    AppBottomSheet(title = tr("Choose from contacts"), subtitle = tr("Tap a contact to fill the name and number."), onDismiss = { onBack(all?.size, query.isNotBlank()) }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldBox(query, { query = it }, placeholder = tr("Search name or number"), height = 48.dp)
            when {
                all == null -> Text(tr("Loading contacts…"), style = fig(14, color = Tokens.Muted), modifier = Modifier.padding(vertical = 24.dp))
                shown.isEmpty() -> Text(if (all!!.isEmpty()) tr("No contacts with a 10-digit mobile number") else tr("No match"), style = fig(14, color = Tokens.Muted), modifier = Modifier.padding(vertical = 24.dp))
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(shown, key = { it.phone }) { c ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).tap { onPick(c) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(40.dp).rounded(999.dp).background(Tokens.BlueLight), contentAlignment = Alignment.Center) {
                                Text(Selectors.initials(c.name), style = fig(14, FontWeight.Bold, Tokens.Blue))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = fig(15, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("+91 ${Selectors.fmtPhone(c.phone)}", style = fig(13, color = Tokens.Muted))
                            }
                            if (c.phone in saved) Text(tr("Saved"), style = fig(12, FontWeight.Bold, Tokens.Muted))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, placeholder: String, prefix: String? = null, kb: KeyboardType = KeyboardType.Text) {
    Column {
        Text(label, style = fig(14, FontWeight.SemiBold))
        Box(Modifier.height(6.dp))
        FieldBox(value, onChange, placeholder = placeholder, prefix = prefix, height = 52.dp, keyboardType = kb)
    }
}

@Composable
fun ReceivePaymentSheet(state: LaundryState, custId: String, vm: ShopViewModel, onDismiss: () -> Unit) {
    val c = Selectors.customer(state, custId)
    val bal = Selectors.balance(state, custId)
    var amt by remember { mutableStateOf(if (bal > 0) bal.toString() else "") }
    val got = amt.toIntOrNull() ?: 0
    val left = bal - got

    val subtitle = when {
        bal > 0 -> tr("{name} has to pay {amount}", "name" to Selectors.firstName(c.name), "amount" to Money.rupees(bal))
        bal < 0 -> tr("{name} already has {amount} advance", "name" to Selectors.firstName(c.name), "amount" to Money.rupees(bal))
        else -> tr("Nothing due. Anything you take becomes advance.")
    }

    AppBottomSheet(title = tr("Receive payment"), subtitle = subtitle, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FieldBox(amt, { amt = it.filter { ch -> ch.isDigit() }.take(6) }, prefix = "₹", height = 56.dp, keyboardType = KeyboardType.Number, textStyle = bric(22, FontWeight.Bold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (bal > 0) PillChip(tr("Full {amount}", "amount" to Money.rupees(bal)), amt == bal.toString()) { amt = bal.toString() }
                listOf(100, 200, 500).forEach { v -> if (v != bal) PillChip(Money.rupees(v), amt == v.toString()) { amt = v.toString() } }
            }
            val (bg, fg, text) = when {
                amt.isEmpty() -> Triple(Tokens.Bg, Tokens.Muted, tr("Type the amount you got"))
                left > 0 -> Triple(Tokens.OrangeLight, Tokens.OrangeDeep, tr("{amount} will stay in khata", "amount" to Money.rupees(left)))
                left < 0 -> Triple(Tokens.BlueLight, Tokens.BlueText, tr("{amount} extra — kept as advance for next bill", "amount" to Money.rupees(left)))
                else -> Triple(Tokens.NeutralFill, Tokens.Ink, tr("Full payment — all clear"))
            }
            Box(Modifier.fillMaxWidth().rounded(12.dp).background(bg).padding(14.dp)) { Text(text, style = fig(14, FontWeight.SemiBold, fg)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(tr("Got cash"), Modifier.weight(1f), enabled = got > 0, height = 56.dp) { vm.receivePayment(custId, got, PayMethod.CASH); onDismiss() }
                PrimaryButton(tr("Got UPI"), Modifier.weight(1f), enabled = got > 0, height = 56.dp) { vm.receivePayment(custId, got, PayMethod.UPI); onDismiss() }
            }
        }
    }
}

@Composable
fun AddOldBaakiSheet(state: LaundryState, custId: String, vm: ShopViewModel, onDismiss: () -> Unit) {
    val c = Selectors.customer(state, custId)
    val bal = max(0, Selectors.balance(state, custId))
    var amt by remember { mutableStateOf("") }
    val got = amt.toIntOrNull() ?: 0
    AppBottomSheet(title = tr("Add old khata"), subtitle = tr("Money {name} owed you before you started using the app", "name" to Selectors.firstName(c.name)), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FieldBox(amt, { amt = it.filter { ch -> ch.isDigit() }.take(6) }, prefix = "₹", height = 56.dp, keyboardType = KeyboardType.Number, textStyle = bric(22, FontWeight.Bold))
            Box(Modifier.fillMaxWidth().rounded(12.dp).background(Tokens.Bg).padding(14.dp)) {
                Text(if (amt.isEmpty()) tr("Khata now: {amount}", "amount" to Money.rupees(bal)) else tr("Khata will become {amount}", "amount" to Money.rupees(bal + got)), style = fig(14, FontWeight.SemiBold, Tokens.InkSecondary))
            }
            PrimaryButton(tr("Add old khata"), enabled = got > 0, height = 56.dp) { vm.addOldBaaki(custId, got); onDismiss() }
        }
    }
}
