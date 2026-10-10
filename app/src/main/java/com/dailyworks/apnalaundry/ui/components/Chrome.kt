package com.dailyworks.apnalaundry.ui.components

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
import androidx.compose.material.icons.outlined.People
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Tokens.Ink, modifier = Modifier.size(24.dp))
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

enum class NavTab(val label: String, val icon: ImageVector) {
    ORDERS("Orders", Icons.AutoMirrored.Outlined.ReceiptLong),
    CUSTOMERS("Customers", Icons.Outlined.People),
    EARNINGS("Earnings", Icons.Outlined.BarChart),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

@Composable
fun BottomNav(current: NavTab, onSelect: (NavTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Tokens.Card)
            .border(1.dp, Tokens.Divider, RoundedCornerShape(0.dp))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavTab.entries.forEach { tab ->
            val selected = tab == current
            val color = if (selected) Tokens.Blue else Tokens.Muted
            Column(
                Modifier
                    .weight(1f)
                    .tap { onSelect(tab) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(tab.icon, tab.label, tint = color, modifier = Modifier.size(24.dp))
                Text(tab.label, style = fig(11, if (selected) FontWeight.Bold else FontWeight.SemiBold, color))
            }
        }
    }
}

@Composable
fun ToastBar(text: String, hasUndo: Boolean, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .rounded(14.dp)
            .background(Tokens.Ink)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = fig(14, FontWeight.SemiBold, Tokens.OnDark), modifier = Modifier.weight(1f))
        if (hasUndo) {
            Spacer(Modifier.width(12.dp))
            Text("Undo", style = fig(15, FontWeight.Bold, Tokens.BlueBar), modifier = Modifier.tap(onClick = onUndo))
        }
    }
}

/**
 * After a status change: "Tell the customer?" with the message it would send.
 * Send opens their WhatsApp chat with it typed in; Not now just closes.
 */
@Composable
fun WhatsAppPromptDialog(url: String, onSend: () -> Unit, onDismiss: () -> Unit) {
    val text = remember(url) { runCatching { android.net.Uri.parse(url).getQueryParameter("text") }.getOrNull().orEmpty().replace("*", "") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().rounded(20.dp).background(Tokens.Card).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Tell the customer?", style = bric(22, FontWeight.Bold))
            Text("Send this update on WhatsApp:", style = fig(14, color = Tokens.Muted))
            Box(
                Modifier.fillMaxWidth().heightIn(max = 260.dp).rounded(14.dp)
                    .background(androidx.compose.ui.graphics.Color(0xFFE7F8EE))
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(14.dp),
            ) { Text(text, style = fig(14, FontWeight.SemiBold)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(50.dp).rounded(14.dp).background(Tokens.NeutralFill).tap(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Text("Not now", style = fig(15, FontWeight.Bold, Tokens.InkSecondary)) }
                Box(
                    Modifier.weight(1.4f).height(50.dp).rounded(14.dp).background(androidx.compose.ui.graphics.Color(0xFF25D366)).tap(onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) { Text("Send on WhatsApp", style = fig(15, FontWeight.Bold, androidx.compose.ui.graphics.Color.White)) }
            }
        }
    }
}
