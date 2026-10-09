package com.dailyworks.apnalaundry.ui.screens.neworder

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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
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
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun NewOrderScreen(
    shopVm: ShopViewModel,
    navigator: AppNavigator,
    editId: Int?,
    presetCustId: String?,
    from: String,
) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val services = state.services
    val clothes = rememberClothesState(services)
    val editingOrder = editId?.let { Selectors.order(state, it) }

    var custId by remember { mutableStateOf(presetCustId) }
    var query by remember { mutableStateOf("") }
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
    var exOverride by remember { mutableStateOf<String?>(null) }
    var discountText by remember { mutableStateOf("") }
    var serialText by remember { mutableStateOf("") } // "" = the order id
    var serialTouched by remember { mutableStateOf(false) } // owner typed in it
    var quickAmt by remember { mutableStateOf("") }
    var quickPcs by remember { mutableStateOf("") }
    var showQuickBox by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<ActiveSheet?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Analytics.screen("new_order")
        Analytics.newOrderStarted(if (presetCustId != null) "customer" else if (from == "empty_home") "empty_home" else "home", editId != null)
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
        discountText = if (o.discount > 0) o.discount.toString() else ""
        serialText = o.serialNo
        // Same base as the live auto amount below: every line, quick amount included.
        val clothesTotalForEx = o.lines.sumOf { it.amt }
        exOverride = if (o.express && o.exAmt != LaundryMath.expressAuto(clothesTotalForEx, state.shop.expressPct)) o.exAmt.toString() else null
        o.lines.forEach { l ->
            when {
                l.isQuick -> { quickAmt = l.amt.toString(); quickPcs = if (l.qty > 0) l.qty.toString() else ""; showQuickBox = true }
                l.kg > 0 -> {
                    clothes.setWeight(l.serviceId, Selectors.trimKg(l.kg))
                    if (l.qty > 0) clothes.setPcs(l.serviceId, l.qty.toString())
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
    val pct = state.shop.expressPct

    val quickAmount = if (showQuickBox) quickAmt.toIntOrNull() ?: 0 else 0
    val clothesTotal = clothes.total(services) + quickAmount
    val exAuto = LaundryMath.expressAuto(clothesTotal, pct)
    val exAmt = if (express) exOverride.let { if (it == null) exAuto else it.toIntOrNull() ?: 0 } else 0
    val fee = if (anyHome) feeText.toIntOrNull() ?: 0 else 0
    // A new order's serial starts as the last serial + 1 (and keeps following
    // it while orders load) until the owner types in the field.
    val nextSerial = Selectors.nextSerial(state)
    LaunchedEffect(nextSerial) {
        if (editId == null && !serialTouched) serialText = nextSerial
    }
    val discount = discountText.toIntOrNull() ?: 0
    val grand = max(0, clothesTotal + exAmt + fee - discount)
    val empty = clothesTotal == 0

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

    val ready = cust != null && (!anyHome || (cust.address.isNotBlank()))
    val ctaLabel = when {
        cust == null -> "Choose a customer"
        anyHome && cust.address.isBlank() -> "Add address first"
        editId != null -> "Save changes"
        empty && pickup == Route.HOME -> "Schedule pickup"
        empty -> "Save order"
        else -> "Save & make bill"
    }

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.statusBars)) {
        TopBar(
            title = if (editId != null) "Edit order" else "New order",
            onBack = { navigator.back() },
            trailing = { if (editingOrder != null) Text("#${editingOrder.no()}", style = fig(14, color = Tokens.Muted)) },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            // ---- serial no. ----
            FieldBox(
                serialText, { serialTouched = true; serialText = it.filter { c -> c.isLetterOrDigit() && c.code < 128 || c in "_/-" }.take(12) },
                prefix = "#", placeholder = "Serial no.",
                suffix = "Serial no.", height = 48.dp,
            )
            // ---- customer ----
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Customer")
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
                                " · ${if (Selectors.orderCount(state, cust) > 0) Selectors.countNoun(Selectors.orderCount(state, cust), "order") else "New customer"}" +
                                (if (bal > 0) " · ${Money.rupees(bal)} baaki" else "")
                            Text(meta, style = fig(13, color = Tokens.InkSecondary))
                        }
                        if (editId == null) Text("Change", style = fig(14, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { custId = null; query = "" })
                    }
                } else {
                    val q = query.trim()
                    val qd = q.filter { it.isDigit() }
                    val results = if (q.isEmpty()) state.customers.sortedBy { it.agoRank }.take(3)
                    else state.customers.filter { it.name.contains(q, true) || (qd.length >= 3 && it.phone.contains(qd)) }.take(4)
                    Column(Modifier.fillMaxWidth().rounded(14.dp).background(Tokens.Card).border(2.dp, Tokens.Blue, RoundedCornerShape(14.dp))) {
                        FieldBox(query, { query = it }, placeholder = "Search name or phone number", height = 54.dp, borderColor = Color.Transparent, borderWidth = 0.dp)
                        if (q.isEmpty()) SectionLabel("Recent customers", Modifier.padding(start = 14.dp, top = 6.dp))
                        results.forEach { r ->
                            Row(Modifier.fillMaxWidth().tap { custId = r.id; query = "" }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Avatar(Selectors.initials(r.name), small = true)
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, style = fig(15, FontWeight.Bold))
                                    Text(Selectors.fmtPhone(r.phone), style = fig(13, color = Tokens.Muted))
                                }
                                Text(if (Selectors.orderCount(state, r) > 0) Selectors.countNoun(Selectors.orderCount(state, r), "order") else "New", style = fig(12, color = Tokens.Muted))
                            }
                        }
                        Box(Modifier.padding(10.dp)) {
                            val label = if (q.isEmpty()) "Add new customer" else "Add “$q” as new customer"
                            Box(
                                Modifier.fillMaxWidth().height(50.dp).rounded(12.dp).border(1.5.dp, Tokens.Blue, RoundedCornerShape(12.dp))
                                    .tap {
                                        val isPhone = q.isNotEmpty() && q.all { it.isDigit() || it == ' ' || it == '+' }
                                        active = ActiveSheet.CustomerForm(
                                            editId = null, ctx = "order",
                                            prefillName = if (isPhone) "" else q,
                                            prefillPhone = if (isPhone) qd.takeLast(10) else "",
                                            needAddress = anyHome,
                                            onSaved = { id -> custId = id; query = "" },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text(label, style = fig(15, FontWeight.Bold, Tokens.Blue)) }
                        }
                    }
                }
            }

            // ---- pickup / delivery ----
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RouteChoice("Pickup", pickup, "At shop", "From home") { pickup = it }
                RouteChoice("Delivery", delivery, "At shop", "To home") { delivery = it }
                if (anyHome) {
                    AppCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Address", style = fig(13, FontWeight.Bold, Tokens.InkSecondary))
                                    Text(cust?.address?.ifBlank { "No address saved yet" } ?: "Choose a customer first", style = fig(14, color = if (cust?.address?.isNotBlank() == true) Tokens.InkSecondary else Tokens.OrangeText))
                                }
                                if (cust != null) Text(if (cust.address.isNotBlank()) "Change" else "Add", style = fig(14, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap {
                                    active = ActiveSheet.CustomerForm(editId = cust.id, ctx = "order", needAddress = true, onSaved = { custId = it })
                                })
                            }
                            FieldBox(feeText, { feeText = it.filter { c -> c.isDigit() }.take(4) }, prefix = "₹", placeholder = "Pickup / delivery charge (optional)", height = 48.dp, keyboardType = KeyboardType.Number)
                        }
                    }
                }
            }

            // ---- dates ----
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DateRow(
                    label = "Pickup",
                    seedIso = pickupDate,
                    dateText = AppDate.short(pickupDate),
                    dateIsSet = true,
                    minIso = null, // past pickups allowed (orders entered after the fact)
                    time24 = pickupTime,
                    onPickDate = { pickupDate = it },
                    onPickTime = { pickupTime = it },
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateRow(
                        label = "Delivery",
                        seedIso = if (dropIso.isNotBlank()) dropIso else AppDate.add(pickupDate, 1),
                        dateText = if (dropIso.isNotBlank()) AppDate.short(dropIso) else "Add date (optional)",
                        dateIsSet = dropIso.isNotBlank(),
                        minIso = pickupDate,
                        time24 = deliveryTime,
                        onPickDate = { nDD = it },
                        onPickTime = { deliveryTime = it },
                    )
                    val hint = when {
                        cleared -> "No delivery date. You can set it later from the ⋯ menu."
                        manual -> "You set this date."
                        basis != null -> "Set automatically: ${basis.name} is ready ${tatText(basis.readyInDays ?: 0)}."
                        else -> "Optional — leave empty if you don't know yet."
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(hint, style = fig(13, color = Tokens.Muted), modifier = Modifier.weight(1f))
                        if (dropIso.isNotBlank()) Text("Remove date", style = fig(13, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { nDD = "none" })
                        else if (cleared) Text("Use automatic", style = fig(13, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { nDD = "" })
                    }
                }
            }

            // ---- clothes ----
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Clothes")
                ClothesEditor(services, clothes, editablePrice = true)
                if (showQuickBox) {
                    AppCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Quick bill", style = fig(15, FontWeight.Bold), modifier = Modifier.weight(1f))
                                Text("Remove", style = fig(13, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { showQuickBox = false; quickAmt = ""; quickPcs = "" })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FieldBox(quickAmt, { quickAmt = it.filter { c -> c.isDigit() }.take(6) }, prefix = "₹", placeholder = "Amount", modifier = Modifier.weight(1f), height = 48.dp, keyboardType = KeyboardType.Number)
                                FieldBox(quickPcs, { quickPcs = it.filter { c -> c.isDigit() }.take(3) }, placeholder = "Pieces", modifier = Modifier.weight(1f), height = 48.dp, keyboardType = KeyboardType.Number)
                            }
                        }
                    }
                }
            }

            // ---- extras ----
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("Extras")
                AppCard {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Express order", style = fig(15, FontWeight.Bold, Tokens.OrangeText))
                            Text("+$pct% on clothes" + (if (express && clothesTotal > 0) " = ${Money.rupees(exAuto)}" else "") + " · washed first", style = fig(13, color = Tokens.Muted))
                        }
                        Toggle(express, onColor = Tokens.Orange) { express = !express; exOverride = null }
                    }
                }
                if (express) {
                    FieldBox(exOverride ?: exAuto.toString(), { exOverride = it.filter { c -> c.isDigit() }.take(5) }, prefix = "₹", suffix = "express", height = 48.dp, keyboardType = KeyboardType.Number)
                }
                FieldBox(discountText, { discountText = it.filter { c -> c.isDigit() }.take(5) }, prefix = "₹", suffix = "discount (optional)", height = 48.dp, keyboardType = KeyboardType.Number)
            }

            // ---- live bill ----
            if (clothesTotal > 0) {
                AppCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BillLine("Clothes", Money.rupees(clothesTotal))
                        if (exAmt > 0) BillLine("Express", "+ ${Money.rupees(exAmt)}")
                        if (fee > 0) BillLine("Pickup / delivery", "+ ${Money.rupees(fee)}")
                        if (discount > 0) BillLine("Discount", "− ${Money.rupees(discount)}", Tokens.OrangeText)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total", style = fig(16, FontWeight.Bold))
                            Text(Money.rupees(grand), style = bric(22, FontWeight.Bold))
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // ---- sticky bottom bar ----
        Column(Modifier.fillMaxWidth().background(Tokens.Card).padding(16.dp).windowInsetsPadding(WindowInsets.navigationBars)) {
            PrimaryButton(ctaLabel, enabled = ready, height = 56.dp) {
                if (!ready) return@PrimaryButton
                shopVm.saveOrder(
                    editId = editId, custId = cust!!.id, pickup = pickup, delivery = delivery,
                    pickupDate = pickupDate, pickupTime24 = pickupTime, deliveryDate = dropIso, deliveryTime24 = deliveryTime,
                    ddAuto = ddAuto, fee = fee, express = express, exAmt = exAmt, discount = discount,
                    lines = clothes.lines(services), quickAmount = if (showQuickBox) quickAmt.toIntOrNull() ?: 0 else 0,
                    quickPieces = if (showQuickBox) quickPcs.toIntOrNull() ?: 0 else 0,
                    serialNo = serialText,
                ) { result ->
                    if (editId != null) navigator.back()
                    else if (result.goToBill) navigator.openBill(result.orderId, "new")
                    else navigator.openHome()
                }
            }
        }
    }

    SheetHost(active, state, shopVm, navigator, onOpen = { active = it }, onDismiss = { active = null })
}

private fun tatText(n: Int): String = when (n) { 0 -> "same day"; 1 -> "in 1 day"; else -> "in $n days" }

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
        modifier.height(54.dp).rounded(14.dp)
            .background(if (selected) Tokens.BlueLight else Tokens.Card)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Tokens.Blue else Tokens.CardBorder, RoundedCornerShape(14.dp))
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = fig(15, FontWeight.Bold, if (selected) Tokens.Blue else Tokens.Ink))
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
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = fig(15, FontWeight.Bold))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateTimeBox(
                modifier = Modifier.weight(1.45f),
                icon = Icons.Outlined.CalendarMonth,
                iconTint = Tokens.Blue,
                text = dateText,
                isSet = dateIsSet,
            ) { showDatePicker(context, seedIso, minIso, onPickDate) }
            DateTimeBox(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Schedule,
                iconTint = if (time24.isNotBlank()) Tokens.InkSecondary else Tokens.Muted,
                text = if (time24.isNotBlank()) AppDate.to12h(time24) else "Time (optional)",
                isSet = time24.isNotBlank(),
            ) { showTimePicker(context, time24, onPickTime) }
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
        Text(text, style = fig(14, FontWeight.Bold, if (isSet) Tokens.Ink else Tokens.Muted), maxLines = 1)
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = fig(15, color = Tokens.InkSecondary))
        Text(value, style = fig(15, FontWeight.SemiBold, color))
    }
}
