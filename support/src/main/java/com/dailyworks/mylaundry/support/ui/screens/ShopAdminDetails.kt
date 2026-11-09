package com.dailyworks.mylaundry.support.ui.screens

import com.dailyworks.mylaundry.support.data.showPhone
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.mylaundry.support.SupportApp
import com.dailyworks.mylaundry.support.data.AdminOwner
import com.dailyworks.mylaundry.support.data.ApiException
import com.dailyworks.mylaundry.support.data.WaSignedOut
import com.dailyworks.mylaundry.support.ui.components.KeyValue
import com.dailyworks.mylaundry.support.ui.components.SectionCard
import com.dailyworks.mylaundry.support.ui.components.ago
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.fig
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import com.dailyworks.mylaundry.support.data.OwnerSummary
import com.dailyworks.mylaundry.support.ui.components.StateBadge
import com.dailyworks.mylaundry.support.ui.theme.bric
import java.util.Locale
import kotlin.math.roundToLong

private fun inr(v: Double) = "₹" + String.format(Locale.ENGLISH, "%,d", v.roundToLong())

private val STATUS = listOf("CREATED" to "New", "RECEIVED" to "Received", "READY" to "Ready", "DELIVERED" to "Delivered", "CANCELLED" to "Cancelled")

/**
 * What admin.mylaundry.work's owner panel shows (8 Nov 2026): totals, earnings, customers
 * and baaki, recent orders, services, shop settings and CleverTap — read with the admin
 * login the WhatsApp tab signs in with.
 */
@Composable
fun ShopAdminDetails(userId: String) {
    val graph = (LocalContext.current.applicationContext as SupportApp).graph
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(null)
    val signedIn = settings?.waCookie != null
    var owner by remember(userId) { mutableStateOf<AdminOwner?>(null) }
    var ct by remember(userId) { mutableStateOf<Map<String, String>?>(null) }
    var ctLoaded by remember(userId) { mutableStateOf(false) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(userId, signedIn, tick) {
        if (!signedIn) return@LaunchedEffect
        error = null
        try {
            owner = graph.wa.owner(userId)
        } catch (e: ApiException) {
            error = e.message
        } catch (_: WaSignedOut) {
        }
        ct = runCatching { graph.wa.clevertap(userId) }.getOrNull()
        ctLoaded = true
    }

    if (!signedIn) {
        SectionCard("Shop details") {
            Text(
                "Sign in on the WhatsApp tab (the admin.mylaundry.work login) to see this shop's orders, sales, " +
                    "customers and CleverTap details here.",
                style = fig(14, color = Tokens.Muted),
            )
        }
        return
    }
    val o = owner
    if (o == null) {
        SectionCard("Shop details") {
            if (error != null) {
                Text(error!!, style = fig(14, FontWeight.SemiBold, Tokens.Red))
                TextButton(onClick = { tick++ }) { Text("Try again", style = fig(14, FontWeight.Bold, Tokens.Blue)) }
            } else {
                Text("Loading…", style = fig(14, color = Tokens.Muted))
            }
        }
        return
    }
    AdminSections(o, ct, ctLoaded, showTotals = true)
}

/** The owner-panel sections (totals optional — the shop card shows its own). */
@Composable
fun AdminSections(o: AdminOwner, ct: Map<String, String>?, ctLoaded: Boolean, showTotals: Boolean) {
    // Totals
    if (showTotals) SectionCard("Totals") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Total orders", "%,d".format(Locale.ENGLISH, o.totals?.orders ?: 0), Modifier.weight(1f))
            Stat("Total sales", inr(o.totals?.sales ?: 0.0), Modifier.weight(1f))
            Stat("Delivered", "${o.totals?.delivered ?: 0}", Modifier.weight(1f))
        }
        Text("Sales = value of their orders (cancelled left out; deleted not counted).", style = fig(12, color = Tokens.Faint))
    }

    // Earnings by period (as the owner's Earnings screen counts them)
    var period by remember { mutableStateOf("all") }
    SectionCard("Earnings") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("today" to "Today", "week" to "Week", "month" to "Month", "all" to "All time").forEach { (k, label) ->
                FilterChip(
                    selected = period == k, onClick = { period = k },
                    label = { Text(label, style = fig(13, FontWeight.SemiBold, Color.Unspecified)) },
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Tokens.Ink, selectedLabelColor = Tokens.OnDark, labelColor = Tokens.Ink),
                )
            }
        }
        val e = o.earnings[period]
        if (e == null) {
            Text("No data", style = fig(14, color = Tokens.Muted))
        } else {
            KeyValue("Work done (delivered)", "${inr(e.work)} · ${e.ordersDelivered} orders")
            KeyValue("Money received", "${inr(e.received)} (cash ${inr(e.cash)} · UPI ${inr(e.upi)})")
            KeyValue("Clothes", "${e.pieces.roundToLong()} pieces" + if (e.kg > 0) " · ${e.kg} kg" else "")
        }
    }

    // Customers + baaki
    SectionCard("Customers · ${o.customers.size}") {
        KeyValue("Baaki (dues)", if (o.baakiCustomers > 0) "${inr(o.baakiMarket)} with ${o.baakiCustomers}" else "None", if (o.baakiCustomers > 0) Tokens.OrangeText else Tokens.Ink)
        o.customers.take(5).forEach { c ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(c.name ?: showPhone(c.phone).ifBlank { "Customer" }, style = fig(14, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${c.orders} orders · ${inr(c.billed)}" + if (c.balance > 0) " · owes ${inr(c.balance)}" else "", style = fig(13, color = Tokens.Muted))
            }
        }
        if (o.customers.size > 5) Text("Top 5 by billing", style = fig(12, color = Tokens.Faint))
    }

    // Orders
    SectionCard("Orders") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            STATUS.forEach { (k, label) ->
                val n = o.statusCounts[k] ?: 0
                if (n > 0) {
                    Text(
                        "$label $n", style = fig(13, FontWeight.SemiBold, Tokens.InkSecondary),
                        modifier = Modifier.background(Tokens.Neutral, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        if (o.orders.isEmpty()) Text("No orders yet.", style = fig(14, color = Tokens.Muted))
        o.orders.take(8).forEach { ord ->
            Column {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(ord.customer ?: "Customer", style = fig(14, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(inr(ord.amount), style = fig(14, FontWeight.Bold))
                }
                Text(
                    listOfNotNull(ord.createdOn, STATUS.firstOrNull { it.first == ord.status }?.second ?: ord.status, ord.services?.ifBlank { null })
                        .joinToString(" · "),
                    style = fig(12, color = Tokens.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (o.orders.size > 8) Text("Latest 8 of ${o.orders.size}", style = fig(12, color = Tokens.Faint))
    }

    // Shop + account
    SectionCard("Shop") {
        o.shop?.name?.let { KeyValue("Shop name", it) }
        o.shop?.phone?.takeIf { it != o.user.phone }?.let { KeyValue("Shop phone", showPhone(it)) }
        o.shop?.closeTime?.let { KeyValue("Closes at", it) }
        o.shop?.expressPct?.let { KeyValue("Express charge", "${it.roundToLong()}%") }
        KeyValue("Services", o.services.mapNotNull { it.name }.joinToString(", ").ifBlank { "None" })
        KeyValue("Day closes", "${o.dayCloses}")
        KeyValue("Joined", ago(o.user.createdAt))
        KeyValue("Last login", ago(o.user.lastLoginAt))
    }

    // CleverTap
    SectionCard("CleverTap") {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyValue("Identity", o.user.id)
                val props = ct
                when {
                    !ctLoaded -> Text("Loading…", style = fig(14, color = Tokens.Muted))
                    props == null -> Text("No CleverTap profile found.", style = fig(14, color = Tokens.Muted))
                    else -> {
                        props["clevertap_id"]?.let { KeyValue("CleverTap ID", it) }
                        props.filterKeys { it != "clevertap_id" }.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (k, v) -> KeyValue(k, v) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Tokens.Bg, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(label, style = fig(12, FontWeight.SemiBold, Tokens.Muted))
        Text(value, style = fig(18, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The shop at a glance for the calls flow (call notes, the queue): who it is, plan, usage,
 * last call, total orders + sales, and "All details" with everything the shop page shows
 * (admin login for the sales / details parts).
 */
@Composable
fun ShopCard(userId: String, onOpenShop: ((String) -> Unit)? = null) {
    val graph = (LocalContext.current.applicationContext as SupportApp).graph
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(null)
    val signedIn = settings?.waCookie != null
    var summary by remember(userId) { mutableStateOf<OwnerSummary?>(null) }
    var admin by remember(userId) { mutableStateOf<AdminOwner?>(null) }
    var ct by remember(userId) { mutableStateOf<Map<String, String>?>(null) }
    var ctLoaded by remember(userId) { mutableStateOf(false) }
    var expanded by rememberSaveable(userId) { mutableStateOf(false) }
    LaunchedEffect(userId) { summary = runCatching { graph.api.shop(userId).owner }.getOrNull() }
    LaunchedEffect(userId, signedIn) { if (signedIn) admin = runCatching { graph.wa.owner(userId) }.getOrNull() }
    LaunchedEffect(userId, signedIn, expanded) {
        if (signedIn && expanded && !ctLoaded) {
            ct = runCatching { graph.wa.clevertap(userId) }.getOrNull()
            ctLoaded = true
        }
    }

    SectionCard {
        val o = summary
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(o?.shopName ?: admin?.shop?.name ?: "Shop", style = bric(20), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val who = listOfNotNull(o?.name, o?.phone?.let { showPhone(it) }).joinToString(" · ")
                if (who.isNotEmpty()) Text(who, style = fig(13, color = Tokens.InkSecondary))
            }
            o?.let { StateBadge(it.subscription.state) }
        }
        if (o != null) {
            Text(
                "${o.orders} orders · ${o.customers} customers · active ${ago(o.lastActiveAt)} · joined ${ago(o.joinedAt)}",
                style = fig(13, color = Tokens.Muted),
            )
            val c = o.calls
            Text(
                if (c.count == 0) "Never called before"
                else "Called ${c.count}× · last ${ago(c.lastAt)}" + (c.lastAgent?.let { " by $it" } ?: ""),
                style = fig(13, FontWeight.SemiBold, if (c.count == 0) Tokens.Orange else Tokens.BlueText),
            )
        }
        val a = admin
        if (a?.totals != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Total orders", "%,d".format(Locale.ENGLISH, a.totals.orders), Modifier.weight(1f))
                Stat("Total sales", inr(a.totals.sales), Modifier.weight(1f))
                Stat("Baaki", if (a.baakiCustomers > 0) inr(a.baakiMarket) else "—", Modifier.weight(1f))
            }
        } else if (!signedIn) {
            Text("Sign in on the WhatsApp tab to see sales and all the shop's details here.", style = fig(12, color = Tokens.Faint))
        }
        Row {
            if (signedIn) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide details ▴" else "All details ▾", style = fig(14, FontWeight.Bold, Tokens.Blue))
                }
            }
            onOpenShop?.let { open ->
                TextButton(onClick = { open(userId) }) { Text("Open shop page", style = fig(14, FontWeight.Bold, Tokens.Blue)) }
            }
        }
    }
    if (expanded) {
        val a = admin
        if (a != null) AdminSections(a, ct, ctLoaded, showTotals = false)
        else SectionCard { Text("Loading…", style = fig(14, color = Tokens.Muted)) }
    }
}
