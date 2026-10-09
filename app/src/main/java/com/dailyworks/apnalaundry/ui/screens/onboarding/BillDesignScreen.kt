package com.dailyworks.apnalaundry.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.domain.BillDetails
import com.dailyworks.apnalaundry.domain.BillDetails.withFields
import com.dailyworks.apnalaundry.domain.BillReceipt
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.OutlineButton
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.TopBar
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.screens.bill.rememberBillImages
import com.dailyworks.apnalaundry.ui.screens.bill.sendTestBillOnWhatsApp
import com.dailyworks.apnalaundry.ui.theme.Tokens
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.dailyworks.apnalaundry.R
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.launch

/**
 * Onboarding step 3 · "Your bill" (handoff §7) and, with [editing], Settings →
 * Bill design & details. The owner's real bill — their name, rates, phone —
 * in the three designs on a swipe strip. Onboarding saves every change
 * straight to the shop (a killed app resumes with it); Settings keeps a
 * draft until Save. Port twin: web/src/ui/onboarding/billDesign.tsx.
 */
@Composable
fun BillDesignScreen(shopVm: ShopViewModel, editing: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val state by shopVm.state.collectAsStateWithLifecycle()
    val shop = state.shop
    var loginPhone by remember { mutableStateOf(shop.phone) }
    LaunchedEffect(Unit) {
        if (editing) Analytics.screen("bill_design")
        loginPhone = shopVm.loginPhone()
    }

    var name by remember { mutableStateOf(shop.name) }
    var draft by remember { mutableStateOf(BillDetails.fieldsOf(shop)) }
    var sheet by remember { mutableStateOf(false) }
    var tested by remember { mutableStateOf(false) }

    fun change(f: BillDetails.Fields) {
        draft = f
        if (!editing) shopVm.updateShopDetails(details = f)
    }

    // ── the three bills ──
    val order = remember(state.services, shop.expressPct) { BillDetails.sampleOrder(state.services, shop.expressPct, 1001) }
    val previewShop = shop.withFields(draft).copy(name = if (editing) BillDetails.cleanName(name).ifEmpty { shop.name } else shop.name)
    val receipts = remember(previewShop, order) {
        BillDetails.TEMPLATES.map { (id, _) -> BillReceipt.sample(previewShop, order, id) }
    }
    val images by rememberBillImages(shopVm, receipts, equalHeight = true, debounceMs = 120)

    // ── strip <-> selected design ──
    val startIndex = remember { BillDetails.TEMPLATES.indexOfFirst { it.first == draft.billTemplate }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = startIndex) { BillDetails.TEMPLATES.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.settledPage) {
        val t = BillDetails.TEMPLATES[pager.settledPage].first
        if (t != draft.billTemplate) change(draft.copy(billTemplate = t))
    }
    val index = pager.currentPage

    val added = BillDetails.addedCount(draft, loginPhone)

    StepScaffold(
        bottomNote = if (editing) null else "You can change the design and details anytime in Settings.",
        top = if (editing) ({ TopBar("Bill design & details", onBack = onBack) }) else null,
        bottom = {
            if (editing) {
                PrimaryButton("Save", height = 56.dp, enabled = BillDetails.isNameOk(name)) {
                    shopVm.updateShopDetails(name = BillDetails.cleanName(name), details = draft)
                    shopVm.showInfo("Bill saved")
                    onDone()
                }
            } else {
                PrimaryButton("Start taking orders", height = 56.dp) { onDone() }
            }
        },
    ) {
        if (editing) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Laundry name", style = fig(13, FontWeight.Bold, Tokens.InkSecondary))
                FieldBox(name, { name = it.take(40) }, height = 56.dp, capitalization = KeyboardCapitalization.Words)
            }
        } else {
            StepBar(3, onBack)
            Text("Your bill is ready", style = bric(28, FontWeight.Bold))
            Text("Your name, rates and phone number are already on it.", style = fig(14, color = Tokens.Muted))
        }

        // design switcher
        Row(
            Modifier.fillMaxWidth().rounded(16.dp).background(Tokens.Card)
                .border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous design", enabled = index > 0) {
                scope.launch { pager.animateScrollToPage(index - 1) }
            }
            Column(
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("DESIGN", style = fig(11, FontWeight.Bold, Tokens.Faint))
                    Text(BillDetails.TEMPLATES[index].second, style = fig(14, FontWeight.Bold))
                }
                Text("${index + 1} of ${BillDetails.TEMPLATES.size} · swipe the bill to change", style = fig(12, color = Tokens.Muted))
            }
            ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next design", enabled = index < BillDetails.TEMPLATES.lastIndex) {
                scope.launch { pager.animateScrollToPage(index + 1) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.weight(1f).height(48.dp).rounded(14.dp).background(Tokens.BlueLight).tap { sheet = true },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, null, tint = Tokens.Blue, modifier = Modifier.size(18.dp))
                Text(" Add fields", style = fig(15, FontWeight.Bold, Tokens.Blue))
                if (added > 0) {
                    Box(
                        Modifier.padding(start = 6.dp).widthIn(min = 20.dp).height(20.dp).rounded(999.dp).background(Tokens.Blue),
                        contentAlignment = Alignment.Center,
                    ) { Text("$added", style = fig(12, FontWeight.Bold, Tokens.OnDark), modifier = Modifier.padding(horizontal = 6.dp)) }
                }
            }
            Row(
                Modifier.weight(1f).height(48.dp).rounded(14.dp).border(1.5.dp, Tokens.FieldBorder, RoundedCornerShape(14.dp)).tap {
                val receipt = receipts[index]
                scope.launch {
                    sendTestBillOnWhatsApp(context, receipt, shopVm.sampleBillUrl())
                    Analytics.testBillSent(BillDetails.TEMPLATES[index].first, editing)
                    tested = true
                    shopVm.showInfo("Opening WhatsApp · pick who gets the test bill")
                }
                },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_whatsapp), null, tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                Text("  " + if (tested) "Sent · again" else "Test on WhatsApp", style = fig(15, FontWeight.Bold, Tokens.Ink))
            }
        }

        // the bills: one page per design, the next one peeking in
        HorizontalPager(
            state = pager,
            pageSize = PageSize.Fixed(322.dp),
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(end = 16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val img = images?.getOrNull(page)
            if (img == null) {
                Box(Modifier.fillMaxWidth().height(520.dp).rounded(16.dp).background(Color.White))
            } else {
                Image(
                    img, contentDescription = "${BillDetails.TEMPLATES[page].second} bill design",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().rounded(16.dp)
                        .border(1.dp, if (page == index) Tokens.Blue else Tokens.CardBorder, RoundedCornerShape(16.dp)),
                )
            }
        }

        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Good to know: ") }
                append("Express charge and discount are sample lines. They show on a bill only when you add them to an order — as a % or a fixed amount.")
            },
            style = fig(13, color = Color(0xFF6B3A10)),
            modifier = Modifier.fillMaxWidth().rounded(14.dp).background(Color(0xFFFBEEDC)).padding(16.dp),
        )
    }

    if (sheet) {
        FieldsSheet(shopVm, draft, onChange = ::change, onClose = { sheet = false })
    }
}

@Composable
private fun ArrowButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).rounded(12.dp).then(if (enabled) Modifier.tap(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (enabled) Tokens.Ink else Color(0xFFC9C3B8), modifier = Modifier.size(26.dp))
    }
}
