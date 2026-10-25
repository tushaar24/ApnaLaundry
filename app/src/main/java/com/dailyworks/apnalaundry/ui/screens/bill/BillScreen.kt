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
import kotlinx.coroutines.launch
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.domain.Gst
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.OrderStatus
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
import com.dailyworks.apnalaundry.ui.tour.Spotlight
import com.dailyworks.apnalaundry.ui.tour.TourStage
import com.dailyworks.apnalaundry.ui.tour.TourSteps
import com.dailyworks.apnalaundry.ui.tour.rememberSpotlightTargets
import com.dailyworks.apnalaundry.ui.tour.spotlightTarget
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.relocation.bringIntoViewRequester

@Composable
fun BillScreen(shopVm: ShopViewModel, navigator: AppNavigator, orderId: Int, from: String) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val o = Selectors.order(state, orderId) ?: return
    val c = Selectors.customer(state, o.custId)
    val gst = LaundryMath.gstOf(o)
    val amt = gst.total
    val fullyPaid = o.pre >= amt && amt > 0
    val due = amt - o.pre // still to collect before delivery
    val open = LaundryMath.isOpen(o)
    var active by remember { mutableStateOf<ActiveSheet?>(null) }
    // The guide: the bill of the order just saved — send it, then on to Home.
    val tour by shopVm.tour.collectAsStateWithLifecycle()
    val tourOn = tour == TourStage.BILL && from == "new"
    var sentInTour by rememberSaveable { mutableStateOf(false) }
    val targets = rememberSpotlightTargets()
    val sendInView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }

    LaunchedEffect(Unit) { Analytics.screen("bill") }
    // The bill page link for the WhatsApp message — fetched ahead, so Send is instant.
    LaunchedEffect(orderId) { shopVm.billUrl(orderId) }

    Box(Modifier.fillMaxSize().background(Tokens.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState()),
        ) {
            TopBar(title = tr("Bill"), onBack = { if (from == "new") navigator.openHome() else navigator.back() })

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // banner
                Column(Modifier.fillMaxWidth().rounded(16.dp).background(Tokens.Ink).padding(16.dp)) {
                    Text(if (from == "new") tr("Order #{no} saved", "no" to o.no()) else tr("Bill for order #{no}", "no" to o.no()), style = bric(20, FontWeight.Bold, Tokens.OnDark))
                    Text(
                        if (o.lines.isNotEmpty()) tr("{items} · Total {amount}", "items" to Selectors.itemsLabel(o), "amount" to Money.rupees(amt))
                        else tr("Total {amount}", "amount" to Money.rupees(amt)),
                        style = fig(13, FontWeight.SemiBold, Tokens.OnDarkFaint),
                    )
                }

                // summary
                AppCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.name, style = fig(16, FontWeight.Bold))
                        Text("+91 ${Selectors.fmtPhone(c.phone)}", style = fig(13, color = Tokens.Muted))
                        Text(routeLine(o.pickup, o.delivery), style = fig(13, color = Tokens.InkSecondary))
                        Divider()
                        o.lines.forEach { l ->
                            BillRow(
                                if (l.kg > 0) Selectors.weightLabel(l) + Selectors.clothesList(l).let { if (it.isEmpty()) "" else "\n$it" }
                                else "${l.itemName} × ${l.qty}",
                                Money.rupees(l.amt),
                            )
                        }
                        if (o.express && o.exAmt > 0) BillRow(tr("Express"), "+ ${Money.rupees(o.exAmt)}")
                        if (o.fee > 0) BillRow(tr("Pickup / delivery"), "+ ${Money.rupees(o.fee)}")
                        if (o.discount > 0) BillRow(tr("Discount"), "− ${Money.rupees(o.discount)}", Tokens.OrangeText)
                        if (gst.on && gst.mode == Gst.EXCL) BillRow(Gst.rowLabel(gst, markRounded = true), Gst.rowValue(gst))
                        Divider()
                        BillRow(tr("Total"), Money.rupees(amt), bold = true)
                        if (gst.on && gst.mode == Gst.INCL) Text(Gst.inclusiveLine(gst), style = fig(12, color = Tokens.Muted))
                        Text(
                            if (o.deliveryDate.isNotBlank()) {
                                val whenText = AppDate.short(o.deliveryDate) + (if (o.deliveryTime.isNotBlank()) " · ${o.deliveryTime}" else "")
                                if (o.delivery == Route.HOME) tr("Delivery {date}", "date" to whenText) else tr("Ready by {date}", "date" to whenText)
                            } else tr("Delivery date not set yet"),
                            style = fig(13, color = Tokens.Muted),
                        )
                    }
                }

                // send section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Bill"), style = fig(13, FontWeight.Bold, Tokens.Muted))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineButton(tr("View bill"), Modifier.weight(1f), height = 48.dp, border = Tokens.CardBorder, fg = Tokens.Ink) { active = ActiveSheet.BillView(o.id) }
                        OutlineButton(tr("Download"), Modifier.weight(1f), height = 48.dp, border = Tokens.CardBorder, fg = Tokens.Ink) { Analytics.billDownloaded(o.id, "bill"); scope.launch { shareBillPdf(context, state, o) } }
                    }
                    // The bill link can take a few seconds on a slow network: say so, and
                    // ignore more taps meanwhile (each would send it again).
                    var sending by remember { mutableStateOf(false) }
                    PrimaryButton(
                        when { sending -> tr("Opening WhatsApp…"); o.billSent -> tr("Send again"); else -> tr("Send on WhatsApp") },
                        Modifier.bringIntoViewRequester(sendInView).spotlightTarget(targets, "send"), enabled = !sending, height = 56.dp,
                    ) {
                        if (tourOn) { sentInTour = true; shopVm.setTour(TourStage.STATUS) }
                        sending = true
                        scope.launch {
                            try { sendBillOnWhatsApp(context, state, o, shopVm.billUrl(o.id)); shopVm.sendBill(o.id) } finally { sending = false }
                        }
                    }
                    Text(
                        if (o.billSent) tr("Sent to {name} ✓", "name" to Selectors.firstName(c.name)) else tr("Not sent yet"),
                        style = fig(13, FontWeight.Bold, if (o.billSent) Tokens.BlueText else Tokens.OrangeText),
                    )
                }

                // tags for the clothes, on the Bluetooth printer
                if (o.lines.isNotEmpty() && o.status != OrderStatus.CANCELLED) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("Tags"), style = fig(13, FontWeight.Bold, Tokens.Muted))
                        OutlineButton(tr("Print tags"), Modifier.fillMaxWidth(), height = 48.dp, border = Tokens.CardBorder, fg = Tokens.Ink) {
                            active = ActiveSheet.PrintTags(o.id, "bill")
                        }
                    }
                }

                // paying now
                if (open && due > 0) {
                    AppCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // The amount in the heading, so the two buttons stay short on any phone.
                            Text(tr("Paying now? {amount}", "amount" to Money.rupees(due)), style = fig(15, FontWeight.Bold))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlineButton(tr("Got cash"), Modifier.weight(1f), height = 48.dp) { shopVm.prepay(o.id, PayMethod.CASH) }
                                OutlineButton(tr("Got UPI"), Modifier.weight(1f), height = 48.dp) { shopVm.prepay(o.id, PayMethod.UPI) }
                            }
                        }
                    }
                } else if (open && fullyPaid) {
                    Box(Modifier.fillMaxWidth().rounded(12.dp).background(Tokens.BlueLight).padding(14.dp)) {
                        Text(tr("Fully paid — nothing to collect at delivery"), style = fig(14, FontWeight.SemiBold, Tokens.BlueText))
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton(tr("Edit bill"), Modifier.weight(1f), height = 56.dp, border = Tokens.CardBorder, fg = Tokens.Ink) { navigator.openNewOrder(editId = o.id, from = "bill") }
                    PrimaryButton(tr("Done"), Modifier.weight(1f), height = 56.dp) { if (from == "new") navigator.openHome() else navigator.back() }
                }
                Text(
                    tr("Delete bill"),
                    style = fig(14, FontWeight.Bold, Tokens.OrangeText),
                    modifier = Modifier.align(Alignment.CenterHorizontally).tap { active = ActiveSheet.DeleteOrder(o.id) }.padding(12.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        if (tourOn && active == null) {
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(120); sendInView.bringIntoView() }
            Spotlight(
                targets, "send", TourSteps.billSend,
                alt = tr("Later"), onAlt = { shopVm.setTour(TourStage.STATUS); navigator.openHome() },
                onSkip = { shopVm.tourSkip(TourSteps.billSend) },
            )
        } else if (sentInTour && tour == TourStage.STATUS && active == null) {
            // Back from WhatsApp: on to the order's status, on Home.
            Spotlight(
                targets, null, TourSteps.billNext,
                next = tr("Go to Home"), onNext = { navigator.openHome() },
                onSkip = { shopVm.tourSkip(TourSteps.billNext) },
            )
        }
        SheetHost(active, state, shopVm, navigator, onOpen = { active = it }, onDismiss = { active = null })
    }
}

private fun routeLine(pickup: Route, delivery: Route): String = when {
    pickup == Route.HOME && delivery == Route.HOME -> tr("Picked up from home → deliver to home")
    pickup == Route.HOME -> tr("Picked up from home → customer collects at shop")
    delivery == Route.HOME -> tr("Received at shop → deliver to home")
    else -> tr("Received at shop → customer collects at shop")
}

/** A bill line: the label takes the room left, the amount always stays whole on the right. */
@Composable
private fun BillRow(label: String, value: String, valueColor: Color = Tokens.Ink, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = fig(if (bold) 16 else 15, if (bold) FontWeight.Bold else FontWeight.Normal, if (bold) Tokens.Ink else Tokens.InkSecondary))
        Text(value, style = fig(if (bold) 18 else 15, FontWeight.Bold, valueColor), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Tokens.Divider))
}
