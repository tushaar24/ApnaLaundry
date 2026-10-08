package com.dailyworks.mylaundry.support.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.fig
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ───────────── formatting ─────────────

private val IST = ZoneId.of("Asia/Kolkata")
private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dayYearFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

fun parseInstant(iso: String?): Instant? = iso?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** "just now", "5m ago", "3h ago", "2d ago", else "12 Sep". */
fun ago(iso: String?): String {
    val t = parseInstant(iso) ?: return "—"
    val d = Duration.between(t, Instant.now())
    return when {
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()}m ago"
        d.toHours() < 24 -> "${d.toHours()}h ago"
        d.toDays() < 30 -> "${d.toDays()}d ago"
        else -> day(iso)
    }
}

fun day(iso: String?): String {
    val t = parseInstant(iso) ?: return "—"
    val z = t.atZone(IST)
    return if (z.year == Instant.now().atZone(IST).year) dayFmt.format(z) else dayYearFmt.format(z)
}

fun dayTime(iso: String?): String {
    val t = parseInstant(iso) ?: return "—"
    return "${day(iso)}, ${timeFmt.format(t.atZone(IST))}"
}

fun rupees(paise: Int): String = "₹" + String.format(Locale.ENGLISH, "%,d", paise / 100)

fun duration(sec: Int?): String = when {
    sec == null -> "—"
    sec < 60 -> "${sec}s"
    else -> "${sec / 60}m ${sec % 60}s"
}

// ───────────── subscription state ─────────────

data class StateLook(val label: String, val bg: Color, val fg: Color)

fun stateLook(state: String): StateLook = when (state) {
    "paid" -> StateLook("Paid", Tokens.GreenLight, Tokens.GreenText)
    "trial" -> StateLook("Trial", Tokens.BlueLight, Tokens.BlueText)
    "halted" -> StateLook("Payment failing", Tokens.OrangeLight, Tokens.OrangeText)
    "cancelled" -> StateLook("Cancelled", Tokens.RedLight, Tokens.Red)
    "expired" -> StateLook("Expired", Tokens.Neutral, Tokens.InkSecondary)
    else -> StateLook("No subscription", Tokens.Neutral, Tokens.Muted)
}

val STATE_ORDER = listOf("none", "trial", "paid", "halted", "cancelled", "expired")

@Composable
fun StateBadge(state: String, modifier: Modifier = Modifier) {
    val look = stateLook(state)
    Text(
        look.label,
        style = fig(12, FontWeight.Bold, look.fg),
        modifier = modifier.background(look.bg, RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        maxLines = 1,
    )
}

// ───────────── tags ─────────────

fun parseColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Tokens.Muted)

val TAG_COLORS = listOf("#1D4ED8", "#16A34A", "#C2410C", "#B42318", "#7C3AED", "#0E7490", "#CA8A04", "#6B7178")

@Composable
fun TagChip(name: String, color: String, modifier: Modifier = Modifier) {
    val c = parseColor(color)
    Row(
        modifier
            .background(c.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(7.dp).background(c, CircleShape))
        Text(name, style = fig(12, FontWeight.SemiBold, c), maxLines = 1)
    }
}

// ───────────── containers & states ─────────────

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Tokens.Card, RoundedCornerShape(16.dp))
            .border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) Text(title.uppercase(), style = fig(12, FontWeight.Bold, Tokens.Muted))
        content()
    }
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = Tokens.Ink) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = fig(14, color = Tokens.Muted))
        Text(value, style = fig(14, FontWeight.SemiBold, valueColor), textAlign = TextAlign.End)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Tokens.Blue) }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = fig(15, color = Tokens.InkSecondary), textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text("Try again", style = fig(15, FontWeight.Bold, Tokens.Blue)) }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = fig(15, color = Tokens.Muted), textAlign = TextAlign.Center)
    }
}
