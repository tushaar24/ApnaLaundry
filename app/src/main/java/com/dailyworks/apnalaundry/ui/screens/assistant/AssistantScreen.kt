package com.dailyworks.apnalaundry.ui.screens.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.data.StatusMessage
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.OrderStatus
import com.dailyworks.apnalaundry.domain.Route
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppCard
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.OutlineButton
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.SectionLabel
import com.dailyworks.apnalaundry.ui.components.TopBar
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.screens.bill.openWhatsApp
import com.dailyworks.apnalaundry.ui.screens.bill.sendBillOnWhatsApp
import com.dailyworks.apnalaundry.ui.screens.bill.sendReminderOnWhatsApp
import com.dailyworks.apnalaundry.ui.theme.Tokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private val WaGreen = Color(0xFF25D366)
private val WaBubble = Color(0xFFE7F8EE)

/**
 * "Ask": the owner speaks or types (Hindi, Hinglish, English…) and the
 * assistant answers from the shop's data — sales, an order, a customer's
 * khata — or puts a card to tap: save a new order, send a bill / status
 * update / reminder / any message on WhatsApp, mark an order picked up or
 * ready. Nothing is saved or sent without that tap.
 */
@Composable
fun AssistantScreen(shopVm: ShopViewModel, navigator: AppNavigator, from: String) {
    val vm: AssistantViewModel = koinViewModel()
    val state by shopVm.state.collectAsStateWithLifecycle()
    val bubbles by vm.bubbles.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val voice by vm.voice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { Analytics.screen("assistant"); Analytics.assistantOpened(from) }
    // Newest message (or "Thinking…") in sight.
    LaunchedEffect(bubbles.size, busy) {
        val n = bubbles.size + if (busy) 1 else 0
        if (n > 0) listState.animateScrollToItem(n - 1)
    }
    // Leaving mid-note: the mic stops.
    DisposableEffect(Unit) { onDispose { vm.cancelVoice() } }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startVoice() else shopVm.showInfo(tr("Allow the microphone to speak — or type instead"))
    }
    val startMic = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startVoice()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val send = { via: String ->
        if (input.isNotBlank() && !busy) { vm.ask(input, via); input = "" }
    }

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) {
        TopBar(tr("Ask"), onBack = { navigator.back() }, trailing = {
            if (bubbles.isNotEmpty() && !busy) {
                Text(tr("New chat"), style = fig(15, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap { vm.clear() }.padding(horizontal = 8.dp, vertical = 12.dp))
            }
        })

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (bubbles.isEmpty() && !busy) {
                Welcome(
                    onAsk = { q -> vm.ask(q, "chip") },
                    onStart = { q -> input = q },
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(), state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(bubbles, key = { it.id }) { b -> BubbleView(b, state, vm, shopVm, navigator) }
                    if (busy) item(key = "thinking") { Thinking() }
                }
            }
        }

        InputBar(
            input = input, onInput = { input = it }, busy = busy, voice = voice,
            onSend = { send("text") }, onMic = startMic, onStop = vm::stopVoice, onCancel = vm::cancelVoice,
        )
    }
}

// ---------------- empty state ----------------

@Composable
private fun Welcome(onAsk: (String) -> Unit, onStart: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(64.dp).rounded(999.dp).background(Tokens.BlueLight), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Mic, null, tint = Tokens.Blue, modifier = Modifier.size(32.dp))
        }
        Text(tr("Ask about your shop"), style = bric(24, FontWeight.Bold))
        Text(tr("Tap the mic and speak. Or type below."), style = fig(16, color = Tokens.InkSecondary))
        Spacer(Modifier.height(4.dp))
        SectionLabel(tr("Try"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(tr("How much did I earn today?")) { onAsk(tr("How much did I earn today?")) }
            Chip(tr("Who has baaki?")) { onAsk(tr("Which customers have baaki?")) }
            Chip(tr("Which orders are ready?")) { onAsk(tr("Which orders are ready?")) }
            Chip(tr("This month's sales")) { onAsk(tr("How were sales this month?")) }
            Chip(tr("New order for…")) { onStart(tr("New order for") + " ") }
            Chip(tr("Send bill to…")) { onStart(tr("Send the bill of the last order of") + " ") }
            Chip(tr("Khata of…")) { onStart(tr("Show the khata of") + " ") }
        }
        Spacer(Modifier.height(4.dp))
        Text(tr("Orders are saved and messages sent only when you tap the button."), style = fig(13, color = Tokens.Muted))
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).rounded(999.dp).background(Tokens.Card)
            .border(1.5.dp, Tokens.CardBorder, RoundedCornerShape(999.dp)).tap(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = fig(14, FontWeight.Bold)) }
}

// ---------------- messages ----------------

@Composable
private fun BubbleView(b: AssistantViewModel.Bubble, state: LaundryState, vm: AssistantViewModel, shopVm: ShopViewModel, navigator: AppNavigator) {
    when (b.who) {
        AssistantViewModel.Who.OWNER -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier.widthIn(max = 300.dp).rounded(16.dp).background(Tokens.Blue).padding(horizontal = 14.dp, vertical = 10.dp),
            ) { Text(b.text, style = fig(15, FontWeight.SemiBold, Tokens.OnDark)) }
        }
        AssistantViewModel.Who.ASSISTANT -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.widthIn(max = 320.dp).rounded(16.dp).background(Tokens.Card)
                    .border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            ) { Text(b.text, style = fig(15)) }
            if (b.cards.isNotEmpty()) Text(tr("👇 Nothing changes until you tap the button"), style = fig(13, FontWeight.SemiBold, Tokens.Muted))
            b.cards.forEachIndexed { i, c -> CardView(c, "${b.id}:$i", state, vm, shopVm, navigator) }
        }
        AssistantViewModel.Who.ERROR -> Box(
            Modifier.fillMaxWidth().rounded(14.dp).background(Tokens.OrangeLight)
                .border(1.dp, Tokens.OrangeBorder, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        ) { Text(b.text, style = fig(15, FontWeight.SemiBold, Tokens.OrangeText)) }
    }
}

@Composable
private fun Thinking() {
    var dots by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) { while (true) { delay(400); dots = dots % 3 + 1 } }
    Box(
        Modifier.rounded(16.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) { Text(tr("Thinking") + ".".repeat(dots), style = fig(15, color = Tokens.Muted)) }
}

// ---------------- cards ----------------

@Composable
private fun CardView(card: AssistantCard, key: String, state: LaundryState, vm: AssistantViewModel, shopVm: ShopViewModel, navigator: AppNavigator) {
    val used by vm.used.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    when (card) {
        is AssistantCard.NewOrder -> NewOrderCard(card, state, vm, shopVm, navigator, scope)
        is AssistantCard.SendBill -> SendBillCard(card.orderId, key, used, state, vm, shopVm, navigator, scope)
        is AssistantCard.StatusUpdates -> StatusCard(card.orderIds, key, used, state, vm)
        is AssistantCard.Reminder -> ReminderCard(card.custId, key, used, state, vm, shopVm)
        is AssistantCard.WhatsAppText -> MessageCard(card, key, used, state, vm)
        is AssistantCard.StatusChange -> StatusChangeCard(card, key, used, state, vm, shopVm)
        is AssistantCard.Payment -> PaymentCard(card, key, used, state, vm, shopVm)
        is AssistantCard.EditOrder -> EditOrderCard(card, key, used, state, vm, shopVm)
        is AssistantCard.RateChange -> RateChangeCard(card, key, used, vm, shopVm)
        is AssistantCard.SaveCustomer -> SaveCustomerCard(card, key, used, vm, shopVm)
        is AssistantCard.OldBaaki -> OldBaakiCard(card, key, used, state, vm, shopVm)
        is AssistantCard.DeleteOrder -> DeleteOrderCard(card, key, used, state, vm, shopVm)
    }
}

@Composable
internal fun CardBox(content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
internal fun AmountRow(label: String, amount: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = fig(if (bold) 16 else 14, if (bold) FontWeight.Bold else FontWeight.Normal), modifier = Modifier.weight(1f))
        Text(amount, style = if (bold) bric(20, FontWeight.Bold) else fig(14, FontWeight.SemiBold))
    }
}

@Composable
private fun WaButton(text: String, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 48.dp, onClick: () -> Unit) =
    PrimaryButton(text, modifier, height = height, bg = WaGreen, onClick = onClick)

@Composable
internal fun DoneText(text: String) = Text(text, style = fig(14, FontWeight.Bold, Tokens.GreenText))

@Composable
private fun NewOrderCard(
    card: AssistantCard.NewOrder, state: LaundryState, vm: AssistantViewModel, shopVm: ShopViewModel,
    navigator: AppNavigator, scope: CoroutineScope,
) {
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    val d = card.draft
    val existing = d.custId?.let { id -> state.customers.firstOrNull { it.id == id } }
        ?: state.customers.firstOrNull { d.custId == null && d.newPhone.length == 10 && it.phone == d.newPhone }
    val name = existing?.name ?: d.newName
    val phone = existing?.phone ?: d.newPhone
    val status = drafts[card.key]

    fun save() = scope.launch {
        if (!vm.orderAllowed()) { vm.draft(card.key, AssistantViewModel.Draft.NeedsPlan); return@launch }
        vm.draft(card.key, AssistantViewModel.Draft.Saving)
        val st = shopVm.state.value
        val saveOrder = { custId: String ->
            shopVm.saveOrder(
                editId = null, custId = custId, pickup = d.pickup, delivery = d.delivery,
                pickupDate = AppDate.TODAY, pickupTime24 = "", deliveryDate = d.deliveryDate, deliveryTime24 = "",
                ddAuto = d.ddAuto, fee = 0, express = d.express, exAmt = d.exAmt, discount = 0,
                lines = d.lines, quickAmount = 0, quickPieces = 0, serialNo = Selectors.nextSerial(st),
                exPct = d.exPct, discPct = 0,
                gstOn = st.shop.gstOn, gstPct = st.shop.gstPct, gstMode = st.shop.gstMode,
                note = d.note,
            ) { res -> vm.orderSaved(card.key, res.orderId, name, d.total) }
        }
        // A new customer saved meanwhile (their own card, or another phone) is taken, never added twice.
        val c = d.custId?.let { id -> st.customers.firstOrNull { it.id == id } }
            ?: st.customers.firstOrNull { d.custId == null && d.newPhone.length == 10 && it.phone == d.newPhone }
        when {
            c == null && d.custId == null -> shopVm.saveCustomer(null, d.newName, d.newPhone, d.address, 0, fromList = false) { id -> saveOrder(id) }
            c == null -> vm.draft(card.key, AssistantViewModel.Draft.Cancelled) // deleted on another phone meanwhile
            d.address.isNotBlank() && c.address.isBlank() ->
                shopVm.saveCustomer(c.id, c.name, c.phone, d.address, 0, fromList = false) { id -> saveOrder(id) }
            else -> saveOrder(c.id)
        }
    }

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("New order"), style = bric(18, FontWeight.Bold), modifier = Modifier.weight(1f))
            if (existing == null) Tag(tr("New customer"))
            if (d.express) Tag(tr("Express"))
        }
        Text(name + if (phone.isNotBlank()) " · ${Selectors.fmtPhone(phone)}" else "", style = fig(15, FontWeight.SemiBold))
        if (d.pickup == Route.HOME || d.delivery == Route.HOME) {
            val how = listOfNotNull(if (d.pickup == Route.HOME) tr("Home pickup") else null, if (d.delivery == Route.HOME) tr("Home delivery") else null).joinToString(" · ")
            Text(how + (d.address.ifBlank { existing?.address.orEmpty() }.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""), style = fig(13, color = Tokens.Muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
        if (d.lines.isEmpty()) Text(tr("Pickup — count the clothes when you get them"), style = fig(14, color = Tokens.Muted))
        d.lines.forEach { l ->
            if (l.kg > 0) AmountRow(tr("{service} · {kg} kg", "service" to l.serviceName, "kg" to Selectors.trimKg(l.kg)), Money.rupees(l.amt))
            else AmountRow("${l.itemName} × ${l.qty} · ${l.serviceName}", Money.rupees(l.amt))
        }
        if (d.express && d.exAmt > 0) AmountRow(tr("Express"), Money.rupees(d.exAmt))
        if (d.lines.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
            AmountRow(if (state.shop.gstOn && state.shop.gstPct > 0) tr("Total (with GST)") else tr("Total"), Money.rupees(d.total), bold = true)
        }
        if (d.deliveryDate.isNotBlank()) Text(tr("Ready by {date}", "date" to AppDate.short(d.deliveryDate)), style = fig(14, FontWeight.SemiBold, Tokens.InkSecondary))
        if (d.note.isNotBlank()) Text(tr("Note: {note}", "note" to d.note), style = fig(13, color = Tokens.Muted))

        when (status) {
            null -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton(tr("Cancel"), Modifier.weight(1f), height = 48.dp, border = Tokens.CardBorder, fg = Tokens.InkSecondary) {
                    vm.draft(card.key, AssistantViewModel.Draft.Cancelled)
                }
                PrimaryButton(tr("Save order"), Modifier.weight(1.6f), height = 48.dp) { save() }
            }
            AssistantViewModel.Draft.Saving -> Text(tr("Saving…"), style = fig(14, FontWeight.SemiBold, Tokens.Muted))
            AssistantViewModel.Draft.Cancelled -> Text(tr("Not saved"), style = fig(14, FontWeight.SemiBold, Tokens.Muted))
            AssistantViewModel.Draft.NeedsPlan -> {
                Text(tr("Your free orders are used up. Choose a plan to save more orders."), style = fig(14, FontWeight.SemiBold, Tokens.OrangeText))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton(tr("Try again"), Modifier.weight(1f), height = 48.dp) { save() }
                    PrimaryButton(tr("See plans"), Modifier.weight(1.4f), height = 48.dp) {
                        Analytics.freeOrdersOverPlansTapped()
                        navigator.openNewOrder(from = "assistant")
                    }
                }
            }
            is AssistantViewModel.Draft.Saved -> {
                val o = Selectors.order(state, status.orderId)
                DoneText(tr("Saved ✓ Order #{no}", "no" to (o?.no() ?: status.orderId)))
                if (o != null && o.lines.isNotEmpty()) {
                    SendBillButtons(o.id, "${card.key}:bill", state, vm, shopVm, navigator, scope)
                } else if (o != null) {
                    OutlineButton(tr("Open order"), Modifier.fillMaxWidth(), height = 48.dp) { navigator.openOrder(o.id, "assistant") }
                }
            }
        }
    }
}

@Composable
internal fun Tag(text: String) {
    Box(Modifier.rounded(999.dp).background(Tokens.BlueLight).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = fig(12, FontWeight.Bold, Tokens.BlueText))
    }
}

/** "Open bill" + "Send bill on WhatsApp" — the bill picture into the customer's chat, as on the Bill screen. */
@Composable
private fun SendBillButtons(
    orderId: Int, key: String, state: LaundryState, vm: AssistantViewModel, shopVm: ShopViewModel,
    navigator: AppNavigator, scope: CoroutineScope,
) {
    val context = LocalContext.current
    val used by vm.used.collectAsStateWithLifecycle()
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(orderId) { shopVm.billUrl(orderId) } // the bill page link, fetched ahead
    val o = Selectors.order(state, orderId) ?: return
    if (key in used || o.billSent) DoneText(tr("Bill sent to {name} ✓", "name" to Selectors.firstName(Selectors.customer(state, o.custId).name)))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlineButton(tr("Open bill"), Modifier.weight(1f), height = 48.dp) { navigator.openBill(o.id, "assistant") }
        WaButton(if (sending) tr("Opening…") else tr("Send bill on WhatsApp"), Modifier.weight(1.6f)) {
            if (sending) return@WaButton
            sending = true
            scope.launch {
                try {
                    val st = shopVm.state.value
                    val now = Selectors.order(st, orderId) ?: return@launch
                    sendBillOnWhatsApp(context, st, now, shopVm.billUrl(orderId))
                    shopVm.sendBill(orderId)
                    vm.used(key, "send_bill", "Sent bill #${now.no()} to ${Selectors.customer(st, now.custId).name} on WhatsApp")
                } finally { sending = false }
            }
        }
    }
}

@Composable
private fun SendBillCard(
    orderId: Int, key: String, used: Set<String>, state: LaundryState, vm: AssistantViewModel, shopVm: ShopViewModel,
    navigator: AppNavigator, scope: CoroutineScope,
) {
    val o = Selectors.order(state, orderId)
    CardBox {
        if (o == null) { Text(tr("This order was deleted"), style = fig(14, color = Tokens.Muted)); return@CardBox }
        val c = Selectors.customer(state, o.custId)
        Text(tr("Bill #{no}", "no" to o.no()), style = bric(18, FontWeight.Bold))
        AmountRow(c.name, Money.rupees(LaundryMath.amtOf(o)), bold = true)
        Text(Selectors.itemsLabel(o) + " · " + Selectors.svcLabel(o), style = fig(13, color = Tokens.Muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
        SendBillButtons(o.id, key, state, vm, shopVm, navigator, scope)
    }
}

@Composable
private fun StatusCard(orderIds: List<Int>, key: String, used: Set<String>, state: LaundryState, vm: AssistantViewModel) {
    val context = LocalContext.current
    CardBox {
        Text(if (orderIds.size == 1) tr("Status update") else tr("Status updates · {n}", "n" to orderIds.size), style = bric(18, FontWeight.Bold))
        orderIds.forEachIndexed { i, id ->
            val o = Selectors.order(state, id) ?: return@forEachIndexed
            val c = Selectors.customer(state, o.custId)
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
            Text("#${o.no()} · ${c.name} · ${statusLabel(o.status)}", style = fig(15, FontWeight.Bold))
            Text(StatusMessage.text(state.shop.name, c.name, o).replace("*", ""), style = fig(13, color = Tokens.InkSecondary), maxLines = 3, overflow = TextOverflow.Ellipsis)
            val k = "$key:$id"
            if (k in used) DoneText(tr("WhatsApp opened ✓"))
            WaButton(if (k in used) tr("Send again") else tr("Send to {name} on WhatsApp", "name" to Selectors.firstName(c.name)), Modifier.fillMaxWidth()) {
                openWhatsApp(context, StatusMessage.link(state.shop.name, c, o))
                vm.used(k, "status_update", "Opened WhatsApp with the status update of #${o.no()} for ${c.name}")
            }
        }
    }
}

@Composable
private fun ReminderCard(custId: String, key: String, used: Set<String>, state: LaundryState, vm: AssistantViewModel, shopVm: ShopViewModel) {
    val context = LocalContext.current
    val c = state.customers.firstOrNull { it.id == custId }
    CardBox {
        if (c == null) { Text(tr("This customer was deleted"), style = fig(14, color = Tokens.Muted)); return@CardBox }
        val bal = Selectors.balance(state, custId)
        Text(tr("Khata reminder"), style = bric(18, FontWeight.Bold))
        AmountRow(c.name, if (bal > 0) tr("{amount} baaki", "amount" to Money.rupees(bal)) else tr("All clear"), bold = true)
        if (key in used) DoneText(tr("WhatsApp opened ✓"))
        if (bal > 0) WaButton(tr("Remind on WhatsApp"), Modifier.fillMaxWidth()) {
            Analytics.reminderSent(custId, bal)
            sendReminderOnWhatsApp(context, state, custId, bal)
            shopVm.showInfo(tr("Opening WhatsApp · reminder to {name} for {amount}", "name" to Selectors.firstName(c.name), "amount" to Money.rupees(bal)))
            vm.used(key, "reminder", "Opened WhatsApp with a ${Money.rupees(bal)} baaki reminder for ${c.name}")
        }
    }
}

@Composable
private fun MessageCard(card: AssistantCard.WhatsAppText, key: String, used: Set<String>, state: LaundryState, vm: AssistantViewModel) {
    val context = LocalContext.current
    val c = card.custId?.let { id -> state.customers.firstOrNull { it.id == id } }
    CardBox {
        Text(tr("WhatsApp message"), style = bric(18, FontWeight.Bold))
        Text(if (c != null) tr("To {name}", "name" to c.name) else tr("WhatsApp will ask: a chat, or My Status"), style = fig(13, color = Tokens.Muted))
        Box(
            Modifier.fillMaxWidth().heightIn(max = 220.dp).rounded(12.dp).background(WaBubble)
                .verticalScroll(rememberScrollState()).padding(12.dp),
        ) { Text(card.text.replace("*", ""), style = fig(14, FontWeight.SemiBold)) }
        if (key in used) DoneText(tr("WhatsApp opened ✓"))
        WaButton(if (key in used) tr("Open again") else tr("Open WhatsApp"), Modifier.fillMaxWidth()) {
            val digits = c?.phone?.filter { it.isDigit() }?.takeLast(10).orEmpty()
            val text = Uri.encode(card.text)
            openWhatsApp(context, if (digits.length == 10) "https://wa.me/91$digits?text=$text" else "https://wa.me/?text=$text")
            vm.used(key, "whatsapp_message", "Opened WhatsApp with the message" + (c?.let { " for ${it.name}" } ?: ""))
        }
    }
}

internal fun statusLabel(s: OrderStatus) = when (s) {
    OrderStatus.CREATED -> tr("To pick up"); OrderStatus.RECEIVED -> tr("Received"); OrderStatus.READY -> tr("Ready")
    OrderStatus.DELIVERED -> tr("Delivered"); OrderStatus.CANCELLED -> tr("Cancelled")
}

// ---------------- input ----------------

@Composable
private fun InputBar(
    input: String, onInput: (String) -> Unit, busy: Boolean, voice: AssistantViewModel.Voice,
    onSend: () -> Unit, onMic: () -> Unit, onStop: () -> Unit, onCancel: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().background(Tokens.Card).border(1.dp, Tokens.Divider, RoundedCornerShape(0.dp)).padding(12.dp)) {
        when (voice) {
            is AssistantViewModel.Voice.Recording -> {
                var secs by remember { mutableIntStateOf(0) }
                LaunchedEffect(voice.startedAt) {
                    while (true) { secs = ((SystemClock.elapsedRealtime() - voice.startedAt) / 1000).toInt(); delay(250) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(12.dp).rounded(999.dp).background(Tokens.ErrorRed))
                    Text(tr("Listening… {time}", "time" to "0:" + secs.toString().padStart(2, '0')), style = fig(16, FontWeight.Bold), modifier = Modifier.weight(1f))
                    Text(tr("Cancel"), style = fig(15, FontWeight.Bold, Tokens.InkSecondary), modifier = Modifier.tap(onClick = onCancel).padding(horizontal = 8.dp, vertical = 12.dp))
                    RoundButton(Tokens.ErrorRed, onStop) { Icon(Icons.Filled.Stop, tr("Stop"), tint = Tokens.OnDark, modifier = Modifier.size(26.dp)) }
                }
            }
            AssistantViewModel.Voice.Hearing -> Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Getting your words…"), style = fig(16, FontWeight.SemiBold, Tokens.Muted))
            }
            AssistantViewModel.Voice.Idle -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldBox(
                    input, onInput, Modifier.weight(1f), placeholder = if (busy) tr("Wait for the answer…") else tr("Type here…"),
                    height = 56.dp, capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send, onImeAction = onSend,
                )
                if (input.isBlank()) {
                    RoundButton(if (busy) Tokens.BlueDisabled else Tokens.Blue, { if (!busy) onMic() }) {
                        Icon(Icons.Filled.Mic, tr("Speak"), tint = Tokens.OnDark, modifier = Modifier.size(28.dp))
                    }
                } else {
                    RoundButton(if (busy) Tokens.BlueDisabled else Tokens.Blue, { if (!busy) onSend() }) {
                        Icon(Icons.AutoMirrored.Filled.Send, tr("Send"), tint = Tokens.OnDark, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundButton(bg: Color, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(Modifier.size(56.dp).rounded(999.dp).background(bg).tap(onClick = onClick), contentAlignment = Alignment.Center) { icon() }
}
