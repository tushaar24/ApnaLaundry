package com.dailyworks.apnalaundry.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.People
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailyworks.apnalaundry.core.i18n
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.ui.theme.Tokens

@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) {
            Box(Modifier.size(44.dp).tap(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back"), tint = Tokens.Ink, modifier = Modifier.size(24.dp))
            }
        }
        Text(title, style = bric(22, FontWeight.Bold), modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Tokens.CardBorder,
    borderWidth: androidx.compose.ui.unit.Dp = 1.dp,
    radius: androidx.compose.ui.unit.Dp = 16.dp,
    bg: Color = Tokens.Card,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .rounded(radius)
            .background(bg)
            .border(borderWidth, borderColor, RoundedCornerShape(radius)),
    ) { content() }
}

/** [label] is English; shown with tr(label). */
enum class NavTab(val label: String, val icon: ImageVector) {
    ORDERS(i18n("Orders"), Icons.AutoMirrored.Outlined.ReceiptLong),
    CUSTOMERS(i18n("Customers"), Icons.Outlined.People),
    /** The assistant — the black "Ask" button over the tabs (not a tab); only where the server has it on. */
    ASK(i18n("Ask"), Icons.Outlined.Mic),
    EARNINGS(i18n("Earnings"), Icons.Outlined.BarChart),
    SETTINGS(i18n("Settings"), Icons.Outlined.Settings),
}

@Composable
fun BottomNav(
    current: NavTab,
    /** Per tab — e.g. the guide pointing at "Customers". */
    itemModifier: (NavTab) -> Modifier = { Modifier },
    /** The "New order" button's — the guide points at it on Home. */
    newOrderModifier: Modifier = Modifier,
    /** Orders only: "Ask" + "+ New order" over the tabs. null (other tabs) = Ask in the bar's middle instead. */
    onNewOrder: (() -> Unit)? = null,
    onSelect: (NavTab) -> Unit,
) {
    // Ask only where the server has the assistant on (else New order alone, full width).
    val askOn by org.koin.compose.koinInject<com.dailyworks.apnalaundry.data.Prefs>().assistantOn
        .collectAsStateWithLifecycle(initialValue = false)
    // Customers / Earnings / Settings: no action buttons — Ask sits in the middle of the
    // bar, the big black button in its notch (owner, 2026-10-25). Orders: the panel below.
    if (onNewOrder == null) {
        NotchedTabs(current, itemModifier, askOn, onSelect)
        return
    }
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Column(Modifier.fillMaxWidth().shadow(14.dp, shape).background(Tokens.Card, shape)) {
        // The two big actions (owner's mock, 2026-10-25): black "Ask", blue "+ New order".
        if (onNewOrder != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (askOn) {
                    Row(
                        Modifier.weight(1f).height(56.dp).then(itemModifier(NavTab.ASK)).rounded(999.dp).background(Tokens.Ink)
                            .tap { onSelect(NavTab.ASK) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    ) {
                        Box(Modifier.size(40.dp).rounded(999.dp).background(Color(0xFF3A3F46)), contentAlignment = Alignment.Center) {
                            Icon(NavTab.ASK.icon, null, tint = Tokens.OnDark, modifier = Modifier.size(22.dp))
                        }
                        Text(tr(NavTab.ASK.label), style = fig(18, FontWeight.Bold, Tokens.OnDark), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(
                    Modifier.weight(1f).height(56.dp).then(newOrderModifier).rounded(999.dp).background(Tokens.Blue)
                        .tap(onClick = onNewOrder).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Add, null, tint = Tokens.OnDark, modifier = Modifier.size(24.dp))
                    Text(tr("New order"), style = fig(18, FontWeight.Bold, Tokens.OnDark), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab.entries.filter { it != NavTab.ASK }.forEach { tab ->
                val selected = tab == current
                val color = if (selected) Tokens.Blue else Tokens.Muted
                Column(
                    Modifier
                        .weight(1f)
                        .then(itemModifier(tab))
                        .tap { onSelect(tab) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(tab.icon, tr(tab.label), tint = color, modifier = Modifier.size(24.dp))
                    Text(tr(tab.label), style = fig(11, if (selected) FontWeight.Bold else FontWeight.SemiBold, color), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The other tabs' bar: Orders · Customers · [Ask] · Earnings · Settings, Ask in the notch (flat 4 tabs without it). */
@Composable
private fun NotchedTabs(current: NavTab, itemModifier: (NavTab) -> Modifier, askOn: Boolean, onSelect: (NavTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = BAR_H)
            .then(
                if (askOn) Modifier.drawBehind { drawNotchedBar() }
                else Modifier.background(Tokens.Card).border(1.dp, Tokens.Divider, RoundedCornerShape(0.dp)),
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavTab.entries.filter { it != NavTab.ASK || askOn }.forEach { tab ->
            if (tab == NavTab.ASK) {
                AskNavButton(Modifier.weight(1f).then(itemModifier(tab))) { onSelect(tab) }
                return@forEach
            }
            val selected = tab == current
            val color = if (selected) Tokens.Blue else Tokens.Muted
            Column(
                Modifier
                    .weight(1f)
                    .then(itemModifier(tab))
                    .tap { onSelect(tab) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(tab.icon, tr(tab.label), tint = color, modifier = Modifier.size(24.dp))
                Text(tr(tab.label), style = fig(11, if (selected) FontWeight.Bold else FontWeight.SemiBold, color), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// The bar with Ask: a notch in the middle of its top edge cradles the round button.
private val BAR_H = 72.dp
private val ASK_SIZE = 60.dp
private val NOTCH_GAP = 6.dp // between the button and the notch
private val NOTCH_SHOULDER = 12.dp // the soft curve into the notch

/**
 * The white bar with a round notch at the top middle (the Ask button sits in
 * it, half above the bar) and its 1 dp top line following the notch.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNotchedBar() {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val r = ASK_SIZE.toPx() / 2f + NOTCH_GAP.toPx()
    val s = NOTCH_SHOULDER.toPx()
    val d = 25.0 // degrees: where the shoulders meet the notch
    val rad = Math.toRadians(d)
    val ax = (cx - r * kotlin.math.cos(rad)).toFloat()
    val bx = (cx + r * kotlin.math.cos(rad)).toFloat()
    val y = (r * kotlin.math.sin(rad)).toFloat()
    val top = androidx.compose.ui.graphics.Path().apply {
        moveTo(0f, 0f)
        lineTo(cx - r - s, 0f)
        quadraticBezierTo(cx - r, 0f, ax, y)
        arcTo(androidx.compose.ui.geometry.Rect(cx - r, -r, cx + r, r), (180 - d).toFloat(), -(180 - 2 * d).toFloat(), false)
        quadraticBezierTo(cx + r, 0f, cx + r + s, 0f)
        lineTo(w, 0f)
    }
    val bar = androidx.compose.ui.graphics.Path().apply {
        addPath(top)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(bar, Tokens.Card)
    drawPath(top, Tokens.Divider, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
}

/**
 * Ask, the middle of the bottom bar: a big black mic button sitting in the
 * bar's notch, half above it, with a soft pulse so it's noticed; "Ask" under it
 * (owner: bigger, highlighted, like a docked button — in black).
 */
@Composable
private fun AskNavButton(modifier: Modifier, onClick: () -> Unit) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "ask")
    val t by pulse.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        ),
        label = "ring",
    )
    Box(modifier.height(BAR_H).tap(onClick = onClick)) {
        // Centred on the bar's top edge: half above it, in the notch.
        Box(Modifier.align(Alignment.TopCenter).offset(y = -ASK_SIZE / 2).size(ASK_SIZE), contentAlignment = Alignment.Center) {
            // The pulse: a ring that grows and fades, again and again.
            Box(
                Modifier.size(ASK_SIZE)
                    .graphicsLayer { scaleX = 1f + 0.3f * t; scaleY = 1f + 0.3f * t; alpha = 0.3f * (1f - t) }
                    .background(Tokens.Ink, androidx.compose.foundation.shape.CircleShape),
            )
            Box(
                Modifier.size(ASK_SIZE)
                    .shadow(8.dp, androidx.compose.foundation.shape.CircleShape)
                    .background(Tokens.Ink, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(NavTab.ASK.icon, tr(NavTab.ASK.label), tint = Tokens.OnDark, modifier = Modifier.size(30.dp))
            }
        }
        Text(
            tr(NavTab.ASK.label), style = fig(12, FontWeight.ExtraBold, Tokens.Ink),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        )
    }
}

@Composable
fun ToastBar(text: String, hasUndo: Boolean, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .rounded(14.dp)
            .background(Tokens.Ink)
            .heightIn(min = 52.dp)
            .padding(start = 16.dp, end = if (hasUndo) 4.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = fig(14, FontWeight.SemiBold, Tokens.OnDark), modifier = Modifier.weight(1f).padding(vertical = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (hasUndo) {
            Box(
                Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp).rounded(10.dp).tap(onClick = onUndo).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(tr("Undo"), style = fig(15, FontWeight.Bold, Tokens.BlueBar), maxLines = 1, softWrap = false) }
        }
    }
}

/**
 * After a status change: "Tell the customer?" with the message it would send.
 * Send opens their WhatsApp chat with it typed in; Not now just closes.
 */
@Composable
fun WhatsAppPromptDialog(
    url: String,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    /** The guide's note above the buttons, while it's on. */
    tip: com.dailyworks.apnalaundry.ui.tour.TourStep? = null,
) {
    val text = remember(url) { runCatching { android.net.Uri.parse(url).getQueryParameter("text") }.getOrNull().orEmpty().replace("*", "") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().rounded(20.dp).background(Tokens.Card).padding(20.dp)) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(tr("Tell the customer?"), style = bric(22, FontWeight.Bold))
                Text(tr("Send this update on WhatsApp:"), style = fig(14, color = Tokens.Muted))
                Box(
                    Modifier.fillMaxWidth().heightIn(max = if (tip != null) 140.dp else 260.dp).rounded(14.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFFE7F8EE))
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(14.dp),
                ) { Text(text, style = fig(14, FontWeight.SemiBold)) }
                if (tip != null) com.dailyworks.apnalaundry.ui.tour.TourTip(tip)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).rounded(14.dp).background(Tokens.NeutralFill).tap(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr("Not now"), style = fig(15, FontWeight.Bold, Tokens.InkSecondary), textAlign = TextAlign.Center, maxLines = 2) }
                Box(
                    Modifier.weight(1.4f).fillMaxHeight().heightIn(min = 48.dp).rounded(14.dp).background(androidx.compose.ui.graphics.Color(0xFF25D366)).tap(onClick = onSend)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr("Send on WhatsApp"), style = fig(15, FontWeight.Bold, androidx.compose.ui.graphics.Color.White), textAlign = TextAlign.Center, maxLines = 2) }
            }
        }
    }
}
