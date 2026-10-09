package com.dailyworks.apnalaundry.ui.screens.bill

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.Gst
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.PayMethod
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
fun BillScreen(shopVm: ShopViewModel, navigator: AppNavigator, orderId: Int, from: String) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val o = Selectors.order(state, orderId) ?: return
    val c = Selectors.customer(state, o.custId)
    val gst = LaundryMath.gstOf(o)
    val amt = gst.total
    val fullyPaid = o.pre >= amt && amt > 0
    val due = amt - o.pre // still to collect before delivery
    val open = LaundryMath.isOpen(o)
    var active by remember { mutableStateOf<ActiveSheet?>(null) }

    LaunchedEffect(Unit) { Analytics.screen("bill") }

    Box(Modifier.fillMaxSize().background(Tokens.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState()),
        ) {
            TopBar(title = "Bill", onBack = { if (from == "new") navigator.openHome() else navigator.back() })

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // banner
                Column(Modifier.fillMaxWidth().rounded(16.dp).background(Tokens.Ink).padding(16.dp)) {
                    Text(if (from == "new") "Order #${o.no()} saved" else "Bill for order #${o.no()}", style = bric(20, FontWeight.Bold, Tokens.OnDark))
                    Text(
                        (if (o.lines.isNotEmpty()) "${Selectors.itemsLabel(o)} · " else "") + "Total ${Money.rupees(amt)}",
                        style = fig(13, FontWeight.SemiBold, Tokens.OnDarkFaint),
                    )
                }

                // summary
                AppCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.name, style = fig(17, FontWeight.Bold))
                        Text("+91 ${Selectors.fmtPhone(c.phone)}", style = fig(13, color = Tokens.Muted))
                        Text(routeLine(o.pickup, o.delivery), style = fig(13, color = Tokens.InkSecondary))
                        Divider()
                        o.lines.forEach { l ->
                            BillRow(
                                if (l.kg > 0) Selectors.weightLabel(l) else "${l.itemName} × ${l.qty}",
                                Money.rupees(l.amt),
                            )
                        }
                        if (o.express && o.exAmt > 0) BillRow("Express", "+ ${Money.rupees(o.exAmt)}")
                        if (o.fee > 0) BillRow("Pickup / delivery", "+ ${Money.rupees(o.fee)}")
                        if (o.discount > 0) BillRow("Discount", "− ${Money.rupees(o.discount)}", Tokens.OrangeText)
                        if (gst.on && gst.mode == Gst.EXCL) BillRow(Gst.rowLabel(gst, markRounded = true), Gst.rowValue(gst))
                        Divider()
                        BillRow("Total", Money.rupees(amt), bold = true)
                        if (gst.on && gst.mode == Gst.INCL) Text(Gst.inclusiveLine(gst), style = fig(12, color = Tokens.Muted))
                        Text(
                            if (o.deliveryDate.isNotBlank())
                                (if (o.delivery == Route.HOME) "Delivery " else "Ready by ") + AppDate.short(o.deliveryDate) + (if (o.deliveryTime.isNotBlank()) " · ${o.deliveryTime}" else "")
                            else "Delivery date not set yet",
                            style = fig(13, color = Tokens.Muted),
                        )
                    }
                }

                // send section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bill", style = fig(13, FontWeight.Bold, Tokens.Muted))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineButton("View bill", Modifier.weight(1f), height = 50.dp, border = Tokens.CardBorder, fg = Tokens.Ink) { active = ActiveSheet.BillView(o.id) }
                        OutlineButton("Download", Modifier.weight(1f), height = 50.dp, border = Tokens.CardBorder, fg = Tokens.Ink) { Analytics.billDownloaded(o.id, "bill"); shareBillPdf(context, state, o) }
                    }
                    PrimaryButton(if (o.billSent) "Send again" else "Send on WhatsApp", height = 54.dp) { sendBillOnWhatsApp(context, state, o); shopVm.sendBill(o.id) }
                    Text(
                        if (o.billSent) "Sent to ${Selectors.firstName(c.name)} ✓" else "Not sent yet",
                        style = fig(13, FontWeight.Bold, if (o.billSent) Tokens.BlueText else Tokens.OrangeText),
                    )
                }

                // paying now
                if (open && due > 0) {
                    AppCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Paying now?", style = fig(15, FontWeight.Bold))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlineButton("Got cash ${Money.rupees(due)}", Modifier.weight(1f), height = 50.dp) { shopVm.prepay(o.id, PayMethod.CASH) }
                                OutlineButton("Got UPI ${Money.rupees(due)}", Modifier.weight(1f), height = 50.dp) { shopVm.prepay(o.id, PayMethod.UPI) }
                            }
                        }
                    }
                } else if (open && fullyPaid) {
                    Box(Modifier.fillMaxWidth().rounded(12.dp).background(Tokens.BlueLight).padding(14.dp)) {
                        Text("Fully paid — nothing to collect at delivery", style = fig(14, FontWeight.SemiBold, Tokens.BlueText))
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("Edit bill", Modifier.weight(1f), height = 52.dp, border = Tokens.CardBorder, fg = Tokens.Ink) { navigator.openNewOrder(editId = o.id, from = "bill") }
                    PrimaryButton("Done", Modifier.weight(1f), height = 52.dp) { if (from == "new") navigator.openHome() else navigator.back() }
                }
                Text(
                    "Delete bill",
                    style = fig(14, FontWeight.Bold, Tokens.OrangeText),
                    modifier = Modifier.align(Alignment.CenterHorizontally).tap { active = ActiveSheet.DeleteOrder(o.id) }.padding(12.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        SheetHost(active, state, shopVm, navigator, onOpen = { active = it }, onDismiss = { active = null })
    }
}

private fun routeLine(pickup: Route, delivery: Route): String =
    (if (pickup == Route.HOME) "Picked up from home" else "Received at shop") + " → " +
        (if (delivery == Route.HOME) "deliver to home" else "customer collects at shop")

@Composable
private fun BillRow(label: String, value: String, valueColor: Color = Tokens.Ink, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = fig(if (bold) 16 else 15, if (bold) FontWeight.Bold else FontWeight.Normal, if (bold) Tokens.Ink else Tokens.InkSecondary))
        Text(value, style = fig(if (bold) 18 else 15, FontWeight.Bold, valueColor))
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Tokens.Divider))
}
