package com.dailyworks.mylaundry.support.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dailyworks.mylaundry.support.Graph
import com.dailyworks.mylaundry.support.data.ApiException
import com.dailyworks.mylaundry.support.data.OwnerSummary
import com.dailyworks.mylaundry.support.data.ShopDetail
import com.dailyworks.mylaundry.support.ui.components.CallCard
import com.dailyworks.mylaundry.support.ui.components.EmptyState
import com.dailyworks.mylaundry.support.ui.components.ErrorState
import com.dailyworks.mylaundry.support.ui.components.KeyValue
import com.dailyworks.mylaundry.support.ui.components.Loading
import com.dailyworks.mylaundry.support.ui.components.SectionCard
import com.dailyworks.mylaundry.support.ui.components.StateBadge
import com.dailyworks.mylaundry.support.ui.components.ago
import com.dailyworks.mylaundry.support.ui.components.day
import com.dailyworks.mylaundry.support.ui.components.dayTime
import com.dailyworks.mylaundry.support.ui.components.rupees
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.bric
import com.dailyworks.mylaundry.support.ui.theme.fig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShopDetailUi(
    val detail: ShopDetail? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class ShopDetailViewModel(private val graph: Graph, private val userId: String) : ViewModel() {
    private val _ui = MutableStateFlow(ShopDetailUi())
    val ui: StateFlow<ShopDetailUi> = _ui.asStateFlow()

    fun load(refreshing: Boolean = false) {
        _ui.update { it.copy(loading = it.detail == null, refreshing = refreshing, error = null) }
        viewModelScope.launch {
            try {
                val d = graph.api.shop(userId)
                _ui.update { it.copy(detail = d, loading = false, refreshing = false) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, refreshing = false, error = e.message) }
            }
        }
    }
}

private val ORDER_STATUS = listOf("CREATED" to "New", "RECEIVED" to "Received", "READY" to "Ready", "DELIVERED" to "Delivered", "CANCELLED" to "Cancelled")

@Composable
fun ShopDetailScreen(
    vm: ShopDetailViewModel,
    onBack: () -> Unit,
    onCall: (OwnerSummary) -> Unit,
    onOpenCall: (String) -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Reload whenever the screen comes back (e.g. after a call's notes were saved).
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(ui.detail?.owner?.let { it.shopName ?: it.name } ?: "Shop owner", style = fig(18, FontWeight.Bold)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Tokens.Bg),
        )
        val d = ui.detail
        when {
            ui.loading && d == null -> Loading()
            d == null -> ErrorState(ui.error ?: "Couldn't load", onRetry = { vm.load() })
            else -> PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.load(refreshing = true) }) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Header(d, onCall = { onCall(d.owner) }) }
                    item { Billing(d) }
                    item { Usage(d) }
                    item {
                        Text(
                            "Calls · ${d.calls.size}",
                            style = bric(20),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (d.calls.isEmpty()) item { EmptyState("No calls yet. Tap Call to make the first one.") }
                    items(d.calls, key = { it.id }) { c -> CallCard(c, showShop = false, onClick = { onOpenCall(c.id) }) }
                }
            }
        }
    }
}

@Composable
private fun Header(d: ShopDetail, onCall: () -> Unit) {
    val o = d.owner
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(o.shopName ?: "Shop not set up yet", style = bric(22, color = if (o.shopName == null) Tokens.Muted else Tokens.Ink))
                o.name?.let { Text(it, style = fig(14, color = Tokens.InkSecondary)) }
                Text("+91 ${o.phone.take(5)} ${o.phone.drop(5)}", style = fig(15, FontWeight.SemiBold))
            }
            StateBadge(o.subscription.state)
        }
        Button(
            onClick = onCall,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Tokens.Green),
        ) {
            Icon(Icons.Filled.Call, null)
            Spacer(Modifier.size(8.dp))
            Text("Call +91 ${o.phone.take(5)} ${o.phone.drop(5)}", style = fig(16, FontWeight.Bold, Tokens.OnDark))
        }
    }
}

@Composable
private fun Billing(d: ShopDetail) {
    SectionCard("Subscription") {
        val sub = d.subscriptions.firstOrNull()
        if (sub == null) {
            Text("Never started a subscription.", style = fig(14, color = Tokens.Muted))
        } else {
            KeyValue("Plan", "${sub.plan.replaceFirstChar(Char::uppercase)} · ${rupees(sub.amount)}")
            KeyValue("Razorpay status", sub.status)
            KeyValue("Payments made", sub.paidCount.toString())
            KeyValue("Started", day(sub.createdAt))
            sub.currentEnd?.let { KeyValue("Paid until", day(it)) }
            sub.chargeAt?.let { KeyValue("Next charge", day(it)) }
            sub.endedAt?.let { KeyValue("Ended", day(it)) }
        }
        if (d.payments.isNotEmpty()) {
            Text("RECENT PAYMENTS", style = fig(12, FontWeight.Bold, Tokens.Muted))
            d.payments.take(5).forEach { p ->
                KeyValue(
                    "${dayTime(p.createdAt)}",
                    "${rupees(p.amount)} · ${p.status}",
                    valueColor = when (p.status) {
                        "captured" -> Tokens.GreenText
                        "failed" -> Tokens.Red
                        else -> Tokens.Ink
                    },
                )
                p.errorDescription?.takeIf { p.status == "failed" }?.let { Text(it, style = fig(12, color = Tokens.Red)) }
            }
        }
    }
}

@Composable
private fun Usage(d: ShopDetail) {
    val o = d.owner
    SectionCard("Usage") {
        KeyValue("Joined", "${day(o.joinedAt)} (${ago(o.joinedAt)})")
        KeyValue("Last active", ago(o.lastActiveAt))
        KeyValue("Last login", ago(o.lastLoginAt))
        KeyValue("Customers", o.customers.toString())
        KeyValue("Orders", o.orders.toString())
        val byStatus = d.usage.ordersByStatus
        if (byStatus.isNotEmpty()) {
            Text(
                ORDER_STATUS.mapNotNull { (k, label) -> byStatus[k]?.let { "$label $it" } }.joinToString(" · "),
                style = fig(13, color = Tokens.Muted),
            )
        }
        KeyValue("Services set up", d.usage.services.toString())
        d.shop?.let { KeyValue("Closes at", it.closeTime) }
    }
}
