package com.dailyworks.apnalaundry.ui.screens.neworder

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.data.experiments.CustomerFlow
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.domain.BillDetails
import com.dailyworks.apnalaundry.domain.Gst
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.PricingMode
import com.dailyworks.apnalaundry.domain.Route
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppCard
import com.dailyworks.apnalaundry.ui.components.ClothesEditor
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.PillChip
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.SectionLabel
import com.dailyworks.apnalaundry.ui.components.TopBar
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rememberClothesState
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.sheets.ActiveSheet
import com.dailyworks.apnalaundry.ui.sheets.SheetHost
import com.dailyworks.apnalaundry.ui.sheets.Toggle
import com.dailyworks.apnalaundry.ui.components.showDatePicker
import com.dailyworks.apnalaundry.ui.components.dashedBorder
import com.dailyworks.apnalaundry.ui.theme.Tokens
import androidx.compose.material.icons.outlined.Edit
import com.dailyworks.apnalaundry.domain.ServiceItem
import com.dailyworks.apnalaundry.ui.screens.rates.RatesEditor
import androidx.activity.compose.BackHandler
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import com.dailyworks.apnalaundry.ui.tour.Spotlight
import com.dailyworks.apnalaundry.ui.tour.SpotlightTargets
import com.dailyworks.apnalaundry.ui.tour.TourStage
import com.dailyworks.apnalaundry.ui.tour.TourSteps
import com.dailyworks.apnalaundry.ui.tour.rememberSpotlightTargets
import com.dailyworks.apnalaundry.ui.tour.spotlightTarget
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun NewOrderScreen(
    shopVm: ShopViewModel,
    navigator: AppNavigator,
    editId: Int?,
    presetCustId: String?,
    from: String,
    presetQuery: String = "", // from Home "New order for …"
) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val services = state.services
    val clothes = rememberClothesState(services)
    val editingOrder = editId?.let { Selectors.order(state, it) }

    var custId by remember { mutableStateOf(presetCustId) }
    var query by remember { mutableStateOf(presetQuery) }
    var pickup by remember { mutableStateOf(Route.SHOP) }
    var delivery by remember { mutableStateOf(Route.SHOP) }
    var pickupDate by remember { mutableStateOf(AppDate.TODAY) }
    var pickupTime by remember { mutableStateOf("") }
    var nDD by remember { mutableStateOf("") } // "" = auto, "none" = cleared, else manual iso
    var deliveryTime by remember { mutableStateOf("") }
    var feeText by remember { mutableStateOf("") }
    var express by remember { mutableStateOf(false) }
    // null = follow the automatic amount (pct of clothes); a string = the owner
    // typed their own, which may be "" mid-edit (must NOT snap back to auto).
    // Express and discount: each a % of the clothes or a fixed ₹ amount.
    var exIsPct by remember { mutableStateOf(true) }
    var exPctText by remember { mutableStateOf(state.shop.expressPct.toString()) }
    var exAmtText by remember { mutableStateOf("") }
    var discOn by remember { mutableStateOf(false) }
    var discIsPct by remember { mutableStateOf(false) }
    var discountText by remember { mutableStateOf("") }
    // GST: pre-filled from the shop's last-used setting; on a NEW order every
    // change is saved back as the default (an edit changes this order only).
    var gstOn by remember { mutableStateOf(state.shop.gstOn) }
    var gstPctText by remember { mutableStateOf(if (state.shop.gstPct > 0) Gst.fmtPct(state.shop.gstPct) else "") }
    var gstMode by remember { mutableStateOf(state.shop.gstMode) }
    var serialText by remember { mutableStateOf("") } // "" = the order id
    var note by remember { mutableStateOf("") } // owner's note on the order
    var serialTouched by remember { mutableStateOf(false) } // owner typed in it
    var quickAmt by remember { mutableStateOf("") }
    var quickPcs by remember { mutableStateOf("") }
    var showQuickBox by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<ActiveSheet?>(null) }
    var editingRates by remember { mutableStateOf(false) } // rate list opened over this form
    var loaded by remember { mutableStateOf(false) }
    // For "New Order Abandoned": how far the owner got before leaving without saving.
    var saved by remember { mutableStateOf(false) }
    var custFormOpened by remember { mutableStateOf(false) }
    var custAdded by remember { mutableStateOf(false) }
    var custSheetOpens by remember { mutableIntStateOf(0) }
    var saveBlockedTaps by remember { mutableIntStateOf(0) }
    var addressSheetOpened by remember { mutableStateOf(false) }
    var searchLogged by remember { mutableStateOf(false) }
    val openedAt = remember { System.currentTimeMillis() }
    val entryPoint = if (presetCustId != null) "customer" else if (from == "empty_home" || from == "push") from else "home"
    LaunchedEffect(active) {
        when (val a = active) {
            is ActiveSheet.CustomerForm -> if (a.editId == null) { custFormOpened = true; custSheetOpens++ }
            is ActiveSheet.AddressForm -> addressSheetOpened = true
            else -> Unit
        }
    }

    // The first-customer test (server-assigned, new accounts on 1.0.15+); LEGACY = the 1.0.14 flow.
    val flow by shopVm.customerFlow.collectAsStateWithLifecycle()
    val noAddress = flow == CustomerFlow.NO_ADDRESS

    // The guide, part 1 on this screen: customer → shop/home → dates → clothes → save.
    val tour by shopVm.tour.collectAsStateWithLifecycle()
    val tourOn = tour == TourStage.ORDER && editId == null
    var tourStep by rememberSaveable { mutableIntStateOf(TOUR_CUSTOMER) }
    val targets = rememberSpotlightTargets()
    val tourScroll = remember { List(TOUR_SAVE) { BringIntoViewRequester() } }

    // v2: a customer set while the order is home pickup / delivery and they have no address →
    // the address sheet, once the customer sheet has closed and the new customer has loaded.
    var pendingAddress by remember { mutableStateOf<Pair<String, String>?>(null) } // customer id to trigger
    fun openCustomerForm(trigger: String, prefillName: String = "", prefillPhone: String = "") {
        active = ActiveSheet.CustomerForm(
            editId = null, ctx = "order", prefillName = prefillName, prefillPhone = prefillPhone,
            needAddress = pickup == Route.HOME || delivery == Route.HOME,
            trigger = trigger, askAddress = !noAddress,
            onSaved = { id ->
                custId = id; query = ""; custAdded = true
                if (noAddress) pendingAddress = id to "customer_saved"
            },
        )
    }

    // No customers yet: there's nobody to search, so open "Add customer" straight away.
    val vmLoaded by shopVm.loaded.collectAsStateWithLifecycle()
    var autoAdded by remember { mutableStateOf(false) }
    LaunchedEffect(vmLoaded, flow) {
        if (!vmLoaded || autoAdded || editId != null || presetCustId != null || state.customers.isNotEmpty()) return@LaunchedEffect
        when (flow) {
            // 1.0.14: the guide shows the "Add new customer" button instead.
            CustomerFlow.LEGACY -> if (tourOn) return@LaunchedEffect
            // v3: never by itself — the owner taps "Add new customer" (the guide points at it).
            CustomerFlow.TAP_TO_ADD -> return@LaunchedEffect
            // v1 / v2: always, guide or not; the guide's tip shows inside the sheet.
            CustomerFlow.AUTO_SHEET, CustomerFlow.NO_ADDRESS -> Unit
        }
        autoAdded = true
        openCustomerForm("auto")
    }

    LaunchedEffect(Unit) {
        Analytics.screen("new_order")
        Analytics.newOrderStarted(entryPoint, editId != null)
    }

    // Prefill for edit mode.
    LaunchedEffect(editingOrder?.id, services.size) {
        val o = editingOrder ?: return@LaunchedEffect
        if (loaded || services.isEmpty()) return@LaunchedEffect
        custId = o.custId
        pickup = o.pickup; delivery = o.delivery
        pickupDate = o.pickupDate; pickupTime = AppDate.to24h(o.pickupTime)
        deliveryTime = AppDate.to24h(o.deliveryTime)
        nDD = when {
            o.deliveryDate.isBlank() -> "none"
            o.ddAuto -> ""
            else -> o.deliveryDate
        }
        feeText = if (o.fee > 0) o.fee.toString() else ""
        express = o.express
        discOn = o.discPct > 0 || o.discount > 0
        if (o.discPct > 0) { discIsPct = true; discountText = o.discPct.toString() }
        else { discIsPct = false; discountText = if (o.discount > 0) o.discount.toString() else "" }
        // An order from before GST (or with it off) shows the shop's current rate / type, switched off.
        gstOn = o.gstOn
        gstPctText = if (o.gstPct > 0) Gst.fmtPct(o.gstPct) else if (state.shop.gstPct > 0) Gst.fmtPct(state.shop.gstPct) else ""
        gstMode = if (o.gstOn) o.gstMode else state.shop.gstMode
        serialText = o.no() // the number it shows today (its serial, else the order id)
        note = o.note
        when {
            o.express && o.exPct > 0 -> { exIsPct = true; exPctText = o.exPct.toString() }
            o.express && o.exAmt > 0 -> { exIsPct = false; exAmtText = o.exAmt.toString() }
            else -> { exIsPct = true; exPctText = state.shop.expressPct.toString() }
        }
        o.lines.forEach { l ->
            when {
                l.isQuick -> { quickAmt = l.amt.toString(); quickPcs = if (l.qty > 0) l.qty.toString() else ""; showQuickBox = true }
                l.kg > 0 -> {
                    clothes.setWeight(l.serviceId, Selectors.trimKg(l.kg))
                    l.clothes.forEach { c -> clothes.bumpBag(l.serviceId, c.name, c.qty) }
                    if (l.qty > 0 && l.clothes.isEmpty()) clothes.setPcs(l.serviceId, l.qty.toString())
                }
                else -> {
                    clothes.bump(l.serviceId, l.itemName, l.qty)
                    val base = services.firstOrNull { it.id == l.serviceId }?.items?.firstOrNull { it.name == l.itemName }?.price ?: l.base
                    if (l.price != base) clothes.setPrice(l.serviceId, l.itemName, l.price.toString())
                }
            }
        }
        o.lines.firstOrNull { !it.isQuick }?.let { clothes.selected = it.serviceId }
        loaded = true
    }

    val cust = custId?.let { Selectors.customer(state, it) }
    val anyHome = pickup == Route.HOME || delivery == Route.HOME

    // v2: the address sheet for [c] when the order goes to / comes from their home and they have none.
    fun askAddress(custIdToAsk: String, trigger: String) {
        active = ActiveSheet.AddressForm(custIdToAsk, trigger, onSaved = { custId = it })
    }
    LaunchedEffect(pendingAddress, active, state.customers) {
        val (pid, trigger) = pendingAddress ?: return@LaunchedEffect
        if (active != null) return@LaunchedEffect // wait for the customer sheet to close
        val c = state.customers.firstOrNull { it.id == pid } ?: return@LaunchedEffect // a new customer still loading
        pendingAddress = null
        if (noAddress && (pickup == Route.HOME || delivery == Route.HOME) && c.address.isBlank()) askAddress(c.id, trigger)
    }
    val exPct = exPctText.toIntOrNull() ?: 0
    val discPct = if (discOn && discIsPct) minOf(100, discountText.toIntOrNull() ?: 0) else 0

    val quickAmount = if (showQuickBox) quickAmt.toIntOrNull() ?: 0 else 0
    val clothesTotal = clothes.total(services) + quickAmount
    val exAuto = LaundryMath.expressAuto(clothesTotal, exPct)
    val exAmt = if (express) (if (exIsPct) exAuto else exAmtText.toIntOrNull() ?: 0) else 0
    val fee = if (anyHome) feeText.toIntOrNull() ?: 0 else 0
    // A new order's serial starts as the last serial + 1 (and keeps following
    // it while orders load) until the owner types in the field.
    val nextSerial = Selectors.nextSerial(state)
    LaunchedEffect(nextSerial) {
        if (editId == null && !serialTouched) serialText = nextSerial
    }
    val discount = when {
        !discOn -> 0
        discIsPct -> (clothesTotal * discPct / 100.0).roundToInt()
        else -> discountText.toIntOrNull() ?: 0
    }
    val gstPct = Gst.pctFrom(gstPctText)
    // The live total goes through the same maths as every saved order (Gst.calc → amtOf).
    val gst = Gst.calc(max(0, clothesTotal + exAmt + fee - discount), gstOn, gstPct, gstMode)
    val grand = gst.total
    val empty = clothesTotal == 0

    /** One GST change on the form; on a new order it also becomes the shop's default straight away. */
    fun changeGst(on: Boolean = gstOn, pctText: String = gstPctText, mode: String = gstMode) {
        gstOn = on; gstPctText = pctText; gstMode = mode
        if (editId == null) shopVm.setGstDefaults(on, Gst.pctFrom(pctText), mode)
    }

    // delivery date resolution
    val usedWithTat = services.filter { s ->
        s.readyInDays != null && (
            (s.mode == PricingMode.WEIGHT && (clothes.weight[s.id]?.toDoubleOrNull() ?: 0.0) > 0) ||
                (s.mode == PricingMode.PIECE && (clothes.qty[s.id]?.values?.any { it > 0 } == true))
            )
    }
    val basis = usedWithTat.maxByOrNull { it.readyInDays ?: 0 }
    val autoDrop = basis?.let { AppDate.add(pickupDate, it.readyInDays ?: 0) } ?: ""
    val cleared = nDD == "none"
    val manual = !cleared && nDD.isNotBlank() && nDD >= pickupDate
    val dropIso = if (cleared) "" else if (manual) nDD else autoDrop
    val ddAuto = !cleared && !manual && autoDrop.isNotBlank()

    // A new order needs at least one service (or a quick-bill amount). Edits of older
    // orders saved empty ("count the clothes later") still go through.
    val needsClothes = editId == null && empty
    val ready = cust != null && (!anyHome || (cust.address.isNotBlank())) && !needsClothes
    val ctaLabel = when {
        cust == null -> tr("Choose a customer")
        anyHome && cust.address.isBlank() -> tr("Add address first")
        editId != null -> tr("Save changes")
        needsClothes -> tr("Add clothes first")
        else -> tr("Save & make bill")
    }

    // Leaving without saving (back, or Home from a notification) → "New Order Abandoned".
    // Not fired on a rotation / theme change (the screen comes straight back), nor if the
    // app is killed from the background (nothing is disposed then).
    val pieces = clothes.qty.values.sumOf { it.values.sum() } + (if (showQuickBox) quickPcs.toIntOrNull() ?: 0 else 0)
    val kg = clothes.weight.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
    val abandon by rememberUpdatedState {
        val stage = when {
            cust == null -> "customer"
            empty -> "clothes"
            else -> "save"
        }
        Analytics.newOrderAbandoned(
            entryPoint = entryPoint, isEdit = editId != null, stage = stage,
            customerSet = cust != null, customerAdded = custAdded, customerFormOpened = custFormOpened,
            clothesAdded = !empty, pieces = pieces, kg = kg, total = grand,
            seconds = (System.currentTimeMillis() - openedAt) / 1000,
            customerSheetOpens = custSheetOpens, saveBlockedTaps = saveBlockedTaps,
            addressNeeded = anyHome && cust != null && cust.address.isBlank(), addressSheetOpened = addressSheetOpened,
        )
    }
    val activity = LocalContext.current.findActivity()
    DisposableEffect(Unit) {
        onDispose { if (!saved && activity?.isChangingConfigurations != true) abandon() }
    }

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        TopBar(
            title = if (editId != null) tr("Edit order") else tr("New order"),
            onBack = { navigator.back() },
            trailing = { if (editingOrder != null) Text("#${editingOrder.no()}", style = fig(14, color = Tokens.Muted)) },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            // ---- serial no. ----
            val serialFocus = remember { FocusRequester() }
            FieldBox(
                serialText, { serialTouched = true; serialText = it.filter { c -> c.isLetterOrDigit() && c.code < 128 || c in "_/-" }.take(12) },
                prefix = tr("# Serial no."), height = 48.dp,
                fieldModifier = Modifier.focusRequester(serialFocus),
                trailing = { EditIcon(tr("Edit serial no.")) { serialFocus.requestFocus() } },
            )
            // ---- customer ----
            Column(Modifier.tourTarget(targets, tourScroll, TOUR_CUSTOMER), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(tr("Customer"))
                if (cust != null) {
                    Row(
                        Modifier.fillMaxWidth().rounded(14.dp).background(Tokens.BlueLight)
                            .border(1.5.dp, Tokens.Blue, RoundedCornerShape(14.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Avatar(Selectors.initials(cust.name))
                        Column(Modifier.weight(1f)) {
                            Text(cust.name, style = fig(16, FontWeight.Bold))
                            val bal = Selectors.balance(state, cust.id)
                            val meta = Selectors.fmtPhone(cust.phone) +
                                " · ${if (Selectors.orderCount(state, cust) > 0) ordersText(Selectors.orderCount(state, cust)) else tr("New customer")}" +
                                (if (bal > 0) " · ${tr("{amount} baaki", "amount" to Money.rupees(bal))}" else "")
                            Text(meta, style = fig(13, color = Tokens.InkSecondary))
                        }
                        if (editId == null) Text(tr("Change"), style = fig(14, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { custId = null; query = "" })
                    }
                } else {
                    val q = query.trim()
                    val qd = q.filter { it.isDigit() }
                    val results = if (q.isEmpty()) state.customers.sortedBy { it.agoRank }.take(3)
                    else state.customers.filter { it.name.contains(q, true) || (qd.length >= 3 && it.phone.contains(qd)) }.take(4)
                    Column(Modifier.fillMaxWidth().rounded(14.dp).background(Tokens.Card).border(2.dp, Tokens.Blue, RoundedCornerShape(14.dp))) {
                        FieldBox(query, {
                            if (!searchLogged && it.isNotBlank()) { searchLogged = true; Analytics.customerSearchUsed() }
                            query = it
                        }, placeholder = tr("Search name or phone number"), height = 52.dp, borderColor = Color.Transparent, borderWidth = 0.dp)
                        if (q.isEmpty()) SectionLabel(tr("Recent customers"), Modifier.padding(start = 14.dp, top = 6.dp))
                        results.forEach { r ->
                            Row(Modifier.fillMaxWidth().tap {
                                Analytics.customerSelected(if (q.isEmpty()) "recent" else "search", q.length)
                                custId = r.id; query = ""
                                if (noAddress && anyHome && r.address.isBlank()) askAddress(r.id, "customer_picked")
                            }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Avatar(Selectors.initials(r.name), small = true)
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, style = fig(15, FontWeight.Bold))
                                    Text(Selectors.fmtPhone(r.phone), style = fig(13, color = Tokens.Muted))
                                }
                                Text(if (Selectors.orderCount(state, r) > 0) ordersText(Selectors.orderCount(state, r)) else tr("New"), style = fig(12, color = Tokens.Muted))
                            }
                        }
                        Box(Modifier.padding(10.dp)) {
                            val label = if (q.isEmpty()) tr("Add new customer") else tr("Add “{name}” as new customer", "name" to q)
                            Box(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).rounded(12.dp).border(1.5.dp, Tokens.Blue, RoundedCornerShape(12.dp))
                                    .tap {
                                        val isPhone = q.isNotEmpty() && q.all { it.isDigit() || it == ' ' || it == '+' }
                                        openCustomerForm(
                                            trigger = if (q.isEmpty()) "add_button" else "add_with_query",
                                            prefillName = if (isPhone) "" else q,
                                            prefillPhone = if (isPhone) qd.takeLast(10) else "",
                                        )
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(label, style = fig(15, FontWeight.Bold, Tokens.Blue), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }

            // ---- pickup / delivery ----
            Column(Modifier.tourTarget(targets, tourScroll, TOUR_ROUTE), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // v2: switching to home asks for the customer's address right away (if they have none).
                fun routeChanged(leg: String, to: Route) {
                    Analytics.orderRouteChanged(leg, if (to == Route.HOME) "home" else "shop", cust != null, cust?.address?.isNotBlank() == true)
                    if (noAddress && to == Route.HOME && cust != null && cust.address.isBlank()) askAddress(cust.id, "route")
                }
                RouteChoice(tr("Pickup"), pickup, tr("At shop"), tr("From home")) { if (it != pickup) { pickup = it; routeChanged("pickup", it) } }
                RouteChoice(tr("Delivery"), delivery, tr("At shop"), tr("To home")) { if (it != delivery) { delivery = it; routeChanged("delivery", it) } }
                if (anyHome) {
                    AppCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr("Address"), style = fig(13, FontWeight.Bold, Tokens.InkSecondary))
                                    Text(cust?.address?.ifBlank { tr("No address saved yet") } ?: tr("Choose a customer first"), style = fig(14, color = if (cust?.address?.isNotBlank() == true) Tokens.InkSecondary else Tokens.OrangeText))
                                }
                                if (cust != null) Text(if (cust.address.isNotBlank()) tr("Change") else tr("Add"), style = fig(14, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap {
                                    if (noAddress) askAddress(cust.id, "address_card")
                                    else active = ActiveSheet.CustomerForm(editId = cust.id, ctx = "order", needAddress = true, trigger = "address_card", onSaved = { custId = it })
                                })
                            }
                            FieldBox(feeText, { feeText = it.filter { c -> c.isDigit() }.take(4) }, prefix = "₹", placeholder = tr("Pickup / delivery charge (optional)"), height = 48.dp, keyboardType = KeyboardType.Number)
                        }
                    }
                }
            }

            // ---- dates ----
            Column(Modifier.tourTarget(targets, tourScroll, TOUR_DATES), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DateRow(
                    label = tr("Pickup"),
                    seedIso = pickupDate,
                    dateText = AppDate.short(pickupDate),
                    dateIsSet = true,
                    dateEditIcon = true,
                    minIso = null, // past pickups allowed (orders entered after the fact)
                    time24 = pickupTime,
                    onPickDate = { pickupDate = it },
                    onPickTime = { pickupTime = it },
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateRow(
                        label = tr("Delivery"),
                        seedIso = if (dropIso.isNotBlank()) dropIso else AppDate.add(pickupDate, 1),
                        dateText = if (dropIso.isNotBlank()) AppDate.short(dropIso) else tr("Add date (optional)"),
                        dateIsSet = dropIso.isNotBlank(),
                        minIso = pickupDate,
                        time24 = deliveryTime,
                        onPickDate = { nDD = it },
                        onPickTime = { deliveryTime = it },
                    )
                    val hint = when {
                        cleared -> tr("No delivery date. You can set it later from the ⋯ menu.")
                        manual -> tr("You set this date.")
                        basis != null -> autoDateHint(basis.name, basis.readyInDays ?: 0)
                        else -> tr("Optional — leave empty if you don't know yet.")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(hint, style = fig(13, color = Tokens.Muted), modifier = Modifier.weight(1f))
                        if (dropIso.isNotBlank()) Text(tr("Remove date"), style = fig(13, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { nDD = "none" })
                        else if (cleared) Text(tr("Use automatic"), style = fig(13, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { nDD = "" })
                    }
                }
            }

            // ---- clothes ----
            Column(Modifier.tourTarget(targets, tourScroll, TOUR_CLOTHES), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel(tr("Clothes"))
                    Row(
                        Modifier.height(36.dp).rounded(999.dp).background(Tokens.BlueLight).tap { editingRates = true }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.Edit, null, tint = Tokens.Blue, modifier = Modifier.size(16.dp))
                        Text(tr("Edit services"), style = fig(13, FontWeight.Bold, Tokens.Blue))
                    }
                }
                ClothesEditor(services, clothes, editablePrice = true, onAddItem = { svc, name, price ->
                    // Save to the rate list (or price an item it didn't offer), then put 1 in this order.
                    val has = svc.items.any { it.name == name }
                    val items = if (has) svc.items.map { if (it.name == name && (it.price ?: 0) <= 0) it.copy(price = price) else it }
                    else svc.items + ServiceItem(name, price)
                    shopVm.upsertService(svc.copy(items = items))
                    clothes.bump(svc.id, name, 1)
                    shopVm.showInfo(tr("{item} · {amount} added to {service}", "item" to name, "amount" to "₹$price", "service" to svc.name))
                })
                if (showQuickBox) {
                    AppCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Quick bill"), style = fig(15, FontWeight.Bold), modifier = Modifier.weight(1f))
                                Text(tr("Remove"), style = fig(13, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { showQuickBox = false; quickAmt = ""; quickPcs = "" })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FieldBox(quickAmt, { quickAmt = it.filter { c -> c.isDigit() }.take(6) }, prefix = "₹", placeholder = tr("Amount"), modifier = Modifier.weight(1f), height = 48.dp, keyboardType = KeyboardType.Number)
                                FieldBox(quickPcs, { quickPcs = it.filter { c -> c.isDigit() }.take(3) }, placeholder = tr("Pieces"), modifier = Modifier.weight(1f), height = 48.dp, keyboardType = KeyboardType.Number)
                            }
                        }
                    }
                }
            }

            // ---- extras ----
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(tr("Extras"))
                ExtraCard(
                    title = tr("Express order"), sub = tr("Washed first · charge extra"), accent = Tokens.Orange, titleColor = Tokens.OrangeText,
                    on = express, onToggle = { express = !express },
                    isPct = exIsPct, onMode = { exIsPct = it },
                    value = if (exIsPct) exPctText else exAmtText,
                    onChange = { if (exIsPct) exPctText = it else exAmtText = it },
                    effect = when {
                        exAmt > 0 -> tr("Adds {amount} to the bill", "amount" to Money.rupees(exAmt))
                        exIsPct && clothesTotal == 0 -> tr("Worked out from the clothes once they're counted")
                        else -> tr("Type the express charge")
                    },
                )
                ExtraCard(
                    title = tr("Discount"), sub = tr("Take money off this bill"), accent = Green, titleColor = Green,
                    on = discOn, onToggle = { discOn = !discOn; discountText = "" },
                    isPct = discIsPct, onMode = { discIsPct = it; discountText = "" },
                    value = discountText, onChange = { discountText = it },
                    effect = when {
                        discount > 0 -> tr("Takes {amount} off the bill", "amount" to Money.rupees(discount))
                        discIsPct && clothesTotal == 0 -> tr("Worked out from the clothes once they're counted")
                        else -> tr("Type the discount")
                    },
                )
                GstCard(
                    on = gstOn, onToggle = { changeGst(on = !gstOn) },
                    mode = gstMode, onMode = { changeGst(mode = it) },
                    pctText = gstPctText, onPct = { changeGst(pctText = it) },
                    hint = Gst.hint(gstOn, gstPct, gstMode),
                    noGstin = gstOn && !BillDetails.isGstinValid(state.shop.gstin),
                    footnote = if (editId != null) tr("Changes here apply to this order only.") else tr("Saved for your next orders. You can change it on any order."),
                )
            }

            // ---- note ----
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(tr("Note"))
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).rounded(12.dp).background(Tokens.Card)
                        .border(1.5.dp, Tokens.FieldBorder, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (note.isEmpty()) Text(tr("Optional — e.g. Starch the shirts, stain on the collar"), style = fig(16, color = Tokens.Faint))
                    androidx.compose.foundation.text.BasicTextField(
                        value = note,
                        onValueChange = { note = it.take(500) },
                        textStyle = fig(16, FontWeight.SemiBold),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Tokens.Blue),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ---- live bill ----
            if (clothesTotal > 0) {
                AppCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BillLine(tr("Clothes"), Money.rupees(clothesTotal))
                        if (exAmt > 0) BillLine(tr("Express"), "+ ${Money.rupees(exAmt)}")
                        if (fee > 0) BillLine(tr("Pickup / delivery"), "+ ${Money.rupees(fee)}")
                        if (discount > 0) BillLine(tr("Discount"), "− ${Money.rupees(discount)}", Tokens.OrangeText)
                        if (gst.on && gst.mode == Gst.EXCL) {
                            BillLine(Gst.rowLabel(gst), Gst.rowValue(gst))
                            if (gst.roundOffPaise != 0L) BillLine(tr("Round off"), Gst.roundOffValue(gst))
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tr("Total"), style = fig(16, FontWeight.Bold))
                            Text(Money.rupees(grand), style = bric(22, FontWeight.Bold))
                        }
                        if (gst.on && gst.mode == Gst.INCL) Text(Gst.inclusiveLine(gst), style = fig(12, color = Tokens.Muted))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // ---- sticky bottom bar ----
        Column(Modifier.fillMaxWidth().background(Tokens.Card).padding(16.dp).windowInsetsPadding(WindowInsets.navigationBars)) {
            PrimaryButton(
                ctaLabel, Modifier.spotlightTarget(targets, "tour_$TOUR_SAVE"), enabled = ready, height = 56.dp,
                onDisabledClick = {
                    saveBlockedTaps++
                    val reason = when {
                        cust == null -> "customer"
                        anyHome && cust.address.isBlank() -> "address"
                        else -> "clothes"
                    }
                    Analytics.orderSaveBlocked(reason, editId != null)
                    // v2: "Add address first" opens the address sheet.
                    if (noAddress && reason == "address" && cust != null) askAddress(cust.id, "save_button")
                },
            ) {
                if (!ready) return@PrimaryButton
                shopVm.saveOrder(
                    editId = editId, custId = cust!!.id, pickup = pickup, delivery = delivery,
                    pickupDate = pickupDate, pickupTime24 = pickupTime, deliveryDate = dropIso, deliveryTime24 = deliveryTime,
                    ddAuto = ddAuto, fee = fee, express = express, exAmt = exAmt, discount = discount,
                    exPct = if (express && exIsPct) exPct else 0, discPct = discPct,
                    gstOn = gstOn, gstPct = gstPct, gstMode = gstMode,
                    lines = clothes.lines(services), quickAmount = if (showQuickBox) quickAmt.toIntOrNull() ?: 0 else 0,
                    quickPieces = if (showQuickBox) quickPcs.toIntOrNull() ?: 0 else 0,
                    serialNo = serialText,
                    note = note,
                ) { result ->
                    saved = true
                    if (tourOn) shopVm.tourOrderSaved(result.orderId)
                    if (editId != null) navigator.back()
                    else if (result.goToBill) navigator.openBill(result.orderId, "new")
                    else navigator.openHome()
                }
            }
        }
    }

    if (tourOn) {
        LaunchedEffect(Unit) { shopVm.tourAutoStart() }
        // A customer picked (or added) ends the customer step by itself.
        LaunchedEffect(cust?.id) { if (tourStep == TOUR_CUSTOMER && cust != null) tourStep = TOUR_ROUTE }
        LaunchedEffect(tourStep) {
            if (tourStep < TOUR_SAVE) { kotlinx.coroutines.delay(120); tourScroll[tourStep].bringIntoView() }
        }
        if (vmLoaded && active == null && !editingRates) {
            val step = when (tourStep) {
                TOUR_CUSTOMER -> TourSteps.customer(hasCustomers = state.customers.isNotEmpty())
                TOUR_ROUTE -> if (noAddress) TourSteps.routeAsksAddress else TourSteps.route
                TOUR_DATES -> TourSteps.dates
                TOUR_CLOTHES -> TourSteps.clothes
                else -> TourSteps.save(
                    ctaLabel, needsCustomer = cust == null, needsClothes = needsClothes,
                    needsAddress = cust != null && anyHome && cust.address.isBlank(),
                )
            }
            Spotlight(
                targets, "tour_$tourStep", step,
                next = when (tourStep) {
                    TOUR_CUSTOMER -> null // the owner taps the real thing
                    TOUR_SAVE -> if (ready) null else tr("Go back")
                    TOUR_CLOTHES -> if (empty) null else tr("Done") // an order needs clothes
                    else -> tr("Next")
                },
                onNext = {
                    tourStep = when {
                        tourStep != TOUR_SAVE -> tourStep + 1
                        cust == null -> TOUR_CUSTOMER
                        needsClothes -> TOUR_CLOTHES
                        else -> TOUR_ROUTE
                    }
                },
                onSkip = { shopVm.tourSkip(step) },
            )
        }
    }

    SheetHost(active, state, shopVm, navigator, onOpen = { active = it }, onDismiss = { active = null })
    if (editingRates) {
        // The rate list drawn over this form, in the same screen (not a Dialog), so
        // nothing typed here is lost and back returns here: a Dialog's back could
        // reach the NavHost and pop New order to Home. Its Add / Edit pages register
        // their own BackHandlers later, so they still go back to the list first.
        BackHandler { editingRates = false }
        Box(Modifier.fillMaxSize().background(Tokens.Bg).tap { /* keep taps off the form below */ }) {
            RatesEditor(shopVm, setupHeader = null, onDone = { editingRates = false }, onBack = { editingRates = false })
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// The guide's steps on this screen.
private const val TOUR_CUSTOMER = 0
private const val TOUR_ROUTE = 1
private const val TOUR_DATES = 2
private const val TOUR_CLOTHES = 3
private const val TOUR_SAVE = 4

/** A tour target inside the form: the spotlight finds it, and the form scrolls to it. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.tourTarget(targets: SpotlightTargets, scroll: List<BringIntoViewRequester>, step: Int): Modifier =
    bringIntoViewRequester(scroll[step]).spotlightTarget(targets, "tour_$step")

/** The delivery date hint when a service's "Ready in" set it: whole sentences, so each language can order the words. */
private fun autoDateHint(service: String, n: Int): String = when (n) {
    0 -> tr("Set automatically: {service} is ready same day.", "service" to service)
    1 -> tr("Set automatically: {service} is ready in 1 day.", "service" to service)
    else -> tr("Set automatically: {service} is ready in {n} days.", "service" to service, "n" to n)
}

/** "1 order" / "5 orders". */
private fun ordersText(n: Int): String = if (n == 1) tr("{n} order", "n" to n) else tr("{n} orders", "n" to n)

@Composable
private fun Avatar(initials: String, small: Boolean = false) {
    val s = if (small) 34.dp else 38.dp
    Box(Modifier.size(s).rounded(999.dp).background(if (small) Tokens.NeutralFill else Tokens.Blue), contentAlignment = Alignment.Center) {
        Text(initials, style = fig(if (small) 13 else 14, FontWeight.Bold, if (small) Tokens.InkSecondary else Tokens.OnDark))
    }
}

@Composable
private fun RouteChoice(label: String, value: Route, shopLabel: String, homeLabel: String, onChange: (Route) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = fig(14, FontWeight.SemiBold))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RouteButton(shopLabel, value == Route.SHOP, Modifier.weight(1f)) { onChange(Route.SHOP) }
            RouteButton(homeLabel, value == Route.HOME, Modifier.weight(1f)) { onChange(Route.HOME) }
        }
    }
}

@Composable
private fun RouteButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).rounded(14.dp)
            .background(if (selected) Tokens.BlueLight else Tokens.Card)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Tokens.Blue else Tokens.CardBorder, RoundedCornerShape(14.dp))
            .tap(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = fig(15, FontWeight.Bold, if (selected) Tokens.Blue else Tokens.Ink), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DateRow(
    label: String,
    seedIso: String,
    dateText: String,
    dateIsSet: Boolean,
    minIso: String?,
    time24: String,
    onPickDate: (String) -> Unit,
    onPickTime: (String) -> Unit,
    dateEditIcon: Boolean = false,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = fig(15, FontWeight.Bold))
        val date: @Composable (Modifier) -> Unit = { m ->
            DateTimeBox(
                modifier = m,
                icon = Icons.Outlined.CalendarMonth,
                iconTint = Tokens.Blue,
                text = dateText,
                isSet = dateIsSet,
                editIcon = dateEditIcon,
            ) { showDatePicker(context, seedIso, minIso, onPickDate) }
        }
        val time: @Composable (Modifier) -> Unit = { m ->
            DateTimeBox(
                modifier = m,
                icon = Icons.Outlined.Schedule,
                iconTint = if (time24.isNotBlank()) Tokens.InkSecondary else Tokens.Muted,
                text = if (time24.isNotBlank()) AppDate.to12h(time24) else tr("Add time"),
                isSet = time24.isNotBlank(),
            ) { showTimePicker(context, time24, onPickTime) }
        }
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // "Tomorrow, 14 Oct" + "10:30 AM" need ~320dp side by side at normal text size.
            if (maxWidth >= 320.dp * fontScale) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    date(Modifier.weight(1.7f))
                    time(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    date(Modifier.fillMaxWidth())
                    time(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DateTimeBox(
    modifier: Modifier,
    icon: ImageVector,
    iconTint: Color,
    text: String,
    isSet: Boolean,
    editIcon: Boolean = false,
    onClick: () -> Unit,
) {
    val borderMod = if (isSet) Modifier.border(1.5.dp, Tokens.FieldBorder, RoundedCornerShape(12.dp))
    else Modifier.dashedBorder(Tokens.DashBorder, 1.5.dp, 12.dp)
    Row(
        modifier
            .height(52.dp)
            .rounded(12.dp)
            .background(Tokens.Card)
            .then(borderMod)
            .tap(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        Text(text, style = fig(14, FontWeight.Bold, if (isSet) Tokens.Ink else Tokens.Muted), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (editIcon) Icon(Icons.Outlined.Edit, null, tint = Tokens.Blue, modifier = Modifier.size(18.dp))
    }
}

/** Pencil at the right end of a field: says "you can change this". */
@Composable
private fun EditIcon(label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).rounded(999.dp).tap(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Edit, label, tint = Tokens.Blue, modifier = Modifier.size(18.dp))
    }
}


private fun showTimePicker(context: Context, time24: String, onSet: (String) -> Unit) {
    val parts = time24.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    TimePickerDialog(context, { _, hh, mm -> onSet(String.format(Locale.US, "%02d:%02d", hh, mm)) }, h, m, false).show()
}

@Composable
private fun BillLine(label: String, value: String, color: Color = Tokens.Ink) {
    // The label takes the room left; the amount always stays whole.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = fig(15, color = Tokens.InkSecondary), modifier = Modifier.weight(1f))
        Text(value, style = fig(15, FontWeight.SemiBold, color), maxLines = 1, softWrap = false)
    }
}

private val Green = Color(0xFF15803D)

/**
 * Express charge / discount on an order: a card with an on/off switch; when on,
 * pick "Perc %" or "Fixed ₹", type the number, and see what it does to
 * the bill in plain words.
 */
@Composable
private fun ExtraCard(
    title: String, sub: String, accent: Color, titleColor: Color,
    on: Boolean, onToggle: () -> Unit,
    isPct: Boolean, onMode: (Boolean) -> Unit,
    value: String, onChange: (String) -> Unit, effect: String,
) {
    AppCard {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = fig(15, FontWeight.Bold, titleColor))
                    Text(sub, style = fig(13, color = Tokens.Muted))
                }
                Toggle(on, onColor = accent) { onToggle() }
            }
            if (on) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().rounded(12.dp).background(Tokens.SegTrack).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(true to tr("Perc %"), false to tr("Fixed ₹")).forEach { (pct, label) ->
                            val sel = isPct == pct
                            Box(
                                Modifier.weight(1f).height(40.dp).rounded(9.dp).background(if (sel) Tokens.Card else Color.Transparent).tap { onMode(pct) },
                                contentAlignment = Alignment.Center,
                            ) { Text(label, style = fig(14, FontWeight.Bold, if (sel) Tokens.Ink else Tokens.InkSecondary)) }
                        }
                    }
                    FieldBox(
                        value, { onChange(it.filter { c -> c.isDigit() }.take(if (isPct) 3 else 5)) },
                        prefix = if (isPct) null else "₹", suffix = if (isPct) "%" else null,
                        placeholder = if (isPct) tr("e.g. 50") else tr("e.g. 100"),
                        height = 48.dp, keyboardType = KeyboardType.Number,
                    )
                    Text(effect, style = fig(13, FontWeight.SemiBold, if (value.isNotEmpty()) accent else Tokens.Muted))
                }
            }
        }
    }
}

/**
 * GST on an order: a switch; when on, Exclusive (added on top) or Inclusive
 * (already in the price), the rate as quick chips or a typed %, a nudge to add
 * the GSTIN when the bill has none, and where the setting is saved.
 */
@Composable
private fun GstCard(
    on: Boolean, onToggle: () -> Unit,
    mode: String, onMode: (String) -> Unit,
    pctText: String, onPct: (String) -> Unit,
    hint: String, noGstin: Boolean, footnote: String,
) {
    val pct = Gst.pctFrom(pctText)
    AppCard {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("GST"), style = fig(15, FontWeight.Bold))
                    Text(hint, style = fig(13, color = Tokens.Muted))
                }
                Toggle(on) { onToggle() }
            }
            if (on) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Exclusive / Inclusive
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple(Gst.EXCL, tr("Exclusive"), tr("Add GST on top")),
                            Triple(Gst.INCL, tr("Inclusive"), tr("GST is in my price")),
                        ).forEach { (m, label, sub) ->
                            val sel = mode == m
                            Column(
                                Modifier.weight(1f).heightIn(min = 56.dp).rounded(12.dp)
                                    .background(if (sel) Tokens.BlueLight else Tokens.Card)
                                    .border(2.dp, if (sel) Tokens.Blue else Tokens.FieldBorder, RoundedCornerShape(12.dp))
                                    .tap { onMode(m) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(label, style = fig(15, FontWeight.Bold, if (sel) Tokens.Blue else Tokens.Ink))
                                Text(sub, style = fig(12, FontWeight.SemiBold, Tokens.Muted))
                            }
                        }
                    }
                    // Rate: quick chips or a typed %
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr("GST rate"), style = fig(14, FontWeight.Bold), modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp))
                        Gst.RATE_CHIPS.forEach { r -> PillChip("${Gst.fmtPct(r)}%", selected = pct == r) { onPct(Gst.fmtPct(r)) } }
                        FieldBox(
                            pctText, { onPct(it.filter { c -> c.isDigit() || c == '.' }.take(5)) },
                            modifier = Modifier.width(86.dp), suffix = "%", keyboardType = KeyboardType.Decimal,
                            height = 44.dp, textStyle = fig(16, FontWeight.Bold), textAlign = TextAlign.End,
                        )
                    }
                    if (noGstin) {
                        Text(
                            tr("Your GSTIN is not on the bill yet. Add it in Settings → Bill design & details."),
                            style = fig(13, color = Tokens.OrangeDeep),
                            modifier = Modifier.fillMaxWidth().rounded(10.dp).background(Tokens.OrangeLight).padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                    Text(footnote, style = fig(12, color = Tokens.DisabledFg))
                }
            }
        }
    }
}
