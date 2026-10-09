package com.dailyworks.apnalaundry.ui.screens.order

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.dailyworks.apnalaundry.ui.screens.bill.sendBillOnWhatsApp
import com.dailyworks.apnalaundry.ui.screens.bill.shareBillPdf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.OrderStatus
import com.dailyworks.apnalaundry.domain.Route
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppCard
import com.dailyworks.apnalaundry.ui.components.OutlineButton
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.TopBar
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.sheets.ActiveSheet
import com.dailyworks.apnalaundry.ui.sheets.SheetHost
import com.dailyworks.apnalaundry.ui.theme.Tokens

@Composable
fun OrderDetailScreen(shopVm: ShopViewModel, navigator: AppNavigator, orderId: Int, from: String) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val o = Selectors.order(state, orderId) ?: return
    val c = Selectors.customer(state, o.custId)
    val amt = LaundryMath.amtOf(o)
    var active by remember { mutableStateOf<ActiveSheet?>(null) }

    LaunchedEffect(Unit) { Analytics.screen("order_detail") }

    val cancelled = o.status == OrderStatus.CANCELLED
    val labels = if (o.pickup == Route.HOME) listOf("To pick up", "Received", "Ready", "Delivered") else listOf("Received", "Ready", "Delivered")
    val keys = if (o.pickup == Route.HOME) listOf(OrderStatus.CREATED, OrderStatus.RECEIVED, OrderStatus.READY, OrderStatus.DELIVERED) else listOf(OrderStatus.RECEIVED, OrderStatus.READY, OrderStatus.DELIVERED)
    val idx = if (cancelled) -1 else keys.indexOf(o.status)

    val actLabel = when (o.status) {
        OrderStatus.CREATED -> "Mark picked up"; OrderStatus.RECEIVED -> "Mark ready"; OrderStatus.READY -> "Mark delivered"; else -> ""
    }

    fun onAct() {
        when (o.status) {
            OrderStatus.CREATED -> if (o.lines.isEmpty()) active = ActiveSheet.Count(o.id, OrderStatus.RECEIVED) else shopVm.markPickedUp(o.id)
            OrderStatus.RECEIVED -> if (o.lines.isEmpty()) active = ActiveSheet.Count(o.id, OrderStatus.READY) else shopVm.markReady(o.id)
            OrderStatus.READY -> active = ActiveSheet.Pay(o.id)
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(Tokens.Bg)) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()),
        ) {
            TopBar(title = "#${o.id}", onBack = { navigator.back() }, trailing = {
                Box(Modifier.size(44.dp).tap { active = ActiveSheet.Menu(o.id) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MoreVert, "More", tint = Tokens.Ink, modifier = Modifier.size(22.dp))
                }
            })

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // customer header
                Row(Modifier.fillMaxWidth().tap { navigator.openCustomer(o.custId, "order") }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).rounded(999.dp).background(Tokens.BlueLight), contentAlignment = Alignment.Center) {
                        Text(Selectors.initials(c.name), style = fig(15, FontWeight.Bold, Tokens.BlueText))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = fig(18, FontWeight.Bold))
                        Text("+91 ${Selectors.fmtPhone(c.phone)}", style = fig(13, color = Tokens.Muted))
                    }
                }

                // stepper
                if (!cancelled) {
                    AppCard {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                            labels.forEachIndexed { i, label ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier.size(18.dp).rounded(999.dp)
                                            .background(if (i <= idx) Tokens.Blue else Tokens.Card)
                                            .then(if (i > idx) Modifier.borderRing() else Modifier),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(label, style = fig(11, FontWeight.SemiBold, if (i <= idx) Tokens.Ink else Tokens.MutedDot))
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().rounded(12.dp).background(Tokens.NeutralFill).padding(14.dp)) {
                        Text("Cancelled" + (if (o.cancelReason.isNotBlank()) " · ${o.cancelReason}" else ""), style = fig(15, FontWeight.SemiBold, Tokens.InkSecondary))
                    }
                }

                // action
                if (actLabel.isNotEmpty()) {
                    PrimaryButton(actLabel, height = 54.dp) { onAct() }
                } else if (o.status == OrderStatus.DELIVERED) {
                    Box(Modifier.fillMaxWidth().rounded(12.dp).background(Tokens.BlueLight).padding(14.dp)) {
                        Text("Delivered" + (if (o.doneAt.isNotBlank()) " · ${o.doneAt}" else "") + " · order closed", style = fig(14, FontWeight.SemiBold, Tokens.BlueText))
                    }
                }

                // items
                if (o.lines.isNotEmpty()) {
                    AppCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            o.lines.forEach { l ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(if (l.kg > 0) "${l.serviceName} · ${Selectors.trimKg(l.kg)} kg" else "${l.itemName} × ${l.qty}", style = fig(15, FontWeight.SemiBold))
                                        Text(l.serviceName + (if (l.kg > 0) "" else " · ₹${l.price} each"), style = fig(12, color = Tokens.Muted))
                                    }
                                    Text(Money.rupees(l.amt), style = fig(15, FontWeight.SemiBold))
                                }
                            }
                            if (o.express && o.exAmt > 0) DetailRow("Express", "+ ${Money.rupees(o.exAmt)}")
                            if (o.fee > 0) DetailRow("Pickup / delivery", "+ ${Money.rupees(o.fee)}")
                            if (o.discount > 0) DetailRow("Discount", "− ${Money.rupees(o.discount)}", Tokens.OrangeText)
                            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Tokens.Divider))
                            DetailRow("Total", Money.rupees(amt), bold = true)
                            Text(paymentText(o, amt), style = fig(13, FontWeight.SemiBold, if (o.status == OrderStatus.DELIVERED && o.paid < amt) Tokens.OrangeText else Tokens.BlueText))
                        }
                    }
                    // bill actions
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineButton("View bill", Modifier.weight(1f), height = 48.dp, border = Tokens.CardBorder, fg = Tokens.Ink) { active = ActiveSheet.BillView(o.id) }
                        OutlineButton("Download", Modifier.weight(1f), height = 48.dp, border = Tokens.CardBorder, fg = Tokens.Ink) { shareBillPdf(context, state, o) }
                        if (o.billSent) OutlineButton("Sent ✓", Modifier.weight(1f), height = 48.dp, border = Tokens.BlueBorder, fg = Tokens.BlueText) { sendBillOnWhatsApp(context, state, o); shopVm.sendBill(o.id) }
                        else PrimaryButton("WhatsApp", Modifier.weight(1f), height = 48.dp) { sendBillOnWhatsApp(context, state, o); shopVm.sendBill(o.id) }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().rounded(12.dp).background(Tokens.Card).padding(14.dp)) {
                        Text("Bill after clothes are counted", style = fig(14, FontWeight.SemiBold, Tokens.Muted))
                    }
                }

                // pickup / delivery info
                AppCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow("Pickup", (if (o.pickup == Route.HOME) "From home · " else "At shop · ") + AppDate.short(o.pickupDate) + (if (o.pickupTime.isNotBlank()) " · ${o.pickupTime}" else ""))
                        InfoRow("Delivery", (if (o.delivery == Route.HOME) "To home · " else "At shop · ") + (if (o.deliveryDate.isNotBlank()) AppDate.short(o.deliveryDate) + (if (o.deliveryTime.isNotBlank()) " · ${o.deliveryTime}" else "") else "date not set"))
                        if (o.pickup == Route.HOME || o.delivery == Route.HOME) InfoRow("Address", c.address.ifBlank { "No address saved" })
                    }
                }

                // manage
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("Edit", Modifier.weight(1f), height = 50.dp, border = Tokens.CardBorder, fg = Tokens.Ink) { navigator.openNewOrder(editId = o.id, from = "order") }
                    OutlineButton("More", Modifier.weight(1f), height = 50.dp, border = Tokens.CardBorder, fg = Tokens.Ink) { active = ActiveSheet.Menu(o.id) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        SheetHost(active, state, shopVm, navigator, onOpen = { active = it }, onDismiss = { active = null })
    }
}

private fun paymentText(o: com.dailyworks.apnalaundry.domain.Order, amt: Int): String = when {
    o.status == OrderStatus.DELIVERED -> {
        val k = amt - o.paid
        when {
            k <= 0 -> "Paid ${Money.rupees(amt)}"
            o.paid > 0 -> "Paid ${Money.rupees(o.paid)} · ${Money.rupees(k)} went to khata"
            else -> "${Money.rupees(amt)} went to khata"
        }
    }
    o.lines.isEmpty() -> "Bill after clothes are counted"
    o.pre >= amt -> "Paid in advance"
    else -> "To be paid at delivery"
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = Tokens.Ink, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = fig(if (bold) 16 else 14, if (bold) FontWeight.Bold else FontWeight.Normal, if (bold) Tokens.Ink else Tokens.InkSecondary))
        Text(value, style = fig(if (bold) 18 else 14, FontWeight.Bold, valueColor))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = fig(13, FontWeight.SemiBold, Tokens.Muted), modifier = Modifier.width(72.dp))
        Text(value, style = fig(14, FontWeight.SemiBold), modifier = Modifier.weight(1f))
    }
}

private fun Modifier.borderRing(): Modifier = border(1.5.dp, Tokens.DashBorder, CircleShape)
