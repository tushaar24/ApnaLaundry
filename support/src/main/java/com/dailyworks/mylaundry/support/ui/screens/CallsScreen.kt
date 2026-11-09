package com.dailyworks.mylaundry.support.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dailyworks.mylaundry.support.Graph
import com.dailyworks.mylaundry.support.data.ApiException
import com.dailyworks.mylaundry.support.data.CallDto
import com.dailyworks.mylaundry.support.data.QueueItem
import com.dailyworks.mylaundry.support.data.ReminderAlarms
import com.dailyworks.mylaundry.support.data.ReminderDto
import com.dailyworks.mylaundry.support.ui.components.CallCard
import com.dailyworks.mylaundry.support.ui.components.EmptyState
import com.dailyworks.mylaundry.support.ui.components.ErrorState
import com.dailyworks.mylaundry.support.ui.components.Loading
import com.dailyworks.mylaundry.support.ui.components.ReminderDialog
import com.dailyworks.mylaundry.support.ui.components.ReminderRow
import com.dailyworks.mylaundry.support.ui.components.parseInstant
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.bric
import com.dailyworks.mylaundry.support.ui.theme.fig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class CallsUi(
    val mine: Boolean = false,
    val agent: String = "",
    val items: List<CallDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    /** Open reminders due by the end of today (overdue ones included), soonest first. */
    val scheduled: List<ReminderDto> = emptyList(),
    val queue: List<QueueItem> = emptyList(),
    val editing: ReminderDto? = null,
    val saving: Boolean = false,
    val message: String? = null,
) {
    val shownScheduled get() = if (mine) scheduled.filter { it.agentName == agent } else scheduled
    /** Still to reach: next in line, or didn't pick up last time. */
    val queueLeft get() = queue.count { it.open }
}

class CallsViewModel(private val graph: Graph, private val appContext: android.content.Context) : ViewModel() {
    private val _ui = MutableStateFlow(CallsUi())
    val ui: StateFlow<CallsUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _ui.update { it.copy(agent = graph.prefs.settings.first().agentName) }
            load()
        }
        viewModelScope.launch { graph.prefs.queue.collect { q -> _ui.update { it.copy(queue = q) } } }
    }

    fun setMine(mine: Boolean) {
        _ui.update { it.copy(mine = mine) }
        load()
    }

    fun load(refreshing: Boolean = false) {
        _ui.update { it.copy(loading = it.items.isEmpty() || !refreshing, refreshing = refreshing, error = null) }
        viewModelScope.launch {
            val s = _ui.value
            try {
                val p = graph.api.calls(page = 1, agent = s.agent.takeIf { s.mine && it.isNotBlank() })
                _ui.update { it.copy(items = p.items, total = p.total, page = 1, loading = false, refreshing = false) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, refreshing = false, error = e.message) }
            }
        }
        loadScheduled()
    }

    private fun loadScheduled() {
        viewModelScope.launch {
            val endOfToday = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            runCatching {
                graph.api.reminders(fromIso = Instant.now().minus(60, ChronoUnit.DAYS).toString(), toIso = endOfToday.toString())
            }.onSuccess { list -> _ui.update { it.copy(scheduled = list) } }
        }
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loading || s.loadingMore || s.items.size >= s.total) return
        _ui.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val p = graph.api.calls(page = s.page + 1, agent = s.agent.takeIf { s.mine && it.isNotBlank() })
                _ui.update { it.copy(items = (it.items + p.items).distinctBy { c -> c.id }, page = p.page, total = p.total, loadingMore = false) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loadingMore = false) }
            }
        }
    }

    private fun change(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                _ui.update { it.copy(editing = null, saving = false, message = null) }
                loadScheduled()
                ReminderAlarms.syncSoon(appContext)
            } catch (e: ApiException) {
                _ui.update { it.copy(saving = false, message = e.message) }
            }
        }
    }

    fun done(r: ReminderDto) {
        _ui.update { s -> s.copy(scheduled = s.scheduled.filter { it.id != r.id }) }
        change { graph.api.updateReminder(r.id, done = true) }
    }

    fun delete(r: ReminderDto) {
        _ui.update { s -> s.copy(scheduled = s.scheduled.filter { it.id != r.id }) }
        change { graph.api.deleteReminder(r.id) }
    }

    fun edit(r: ReminderDto?) = _ui.update { it.copy(editing = r, message = null) }

    fun reschedule(r: ReminderDto, at: Instant, tagIds: List<String>, note: String) {
        _ui.update { it.copy(saving = true) }
        change { graph.api.updateReminder(r.id, dueAtIso = at.toString(), tagIds = tagIds, note = note) }
    }
}

@Composable
fun CallsScreen(
    vm: CallsViewModel,
    onOpenCall: (String) -> Unit,
    onOpenShop: (String) -> Unit,
    onCallReminder: (ReminderDto) -> Unit,
    onQueueReminders: (List<ReminderDto>) -> Unit,
    onOpenQueue: () -> Unit,
    onAllReminders: () -> Unit = {},
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }
    Column(Modifier.fillMaxSize()) {
        Text("Calls", style = bric(26), modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to "Everyone", true to "Mine").forEach { (mine, label) ->
                FilterChip(
                    selected = ui.mine == mine,
                    onClick = { vm.setMine(mine) },
                    label = { Text(label, style = fig(14, FontWeight.SemiBold, Color.Unspecified)) },
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Tokens.Card, selectedContainerColor = Tokens.Ink, selectedLabelColor = Tokens.OnDark, labelColor = Tokens.Ink,
                    ),
                )
            }
        }
        PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.load(refreshing = true) }, modifier = Modifier.weight(1f)) {
            when {
                ui.loading && ui.items.isEmpty() && ui.scheduled.isEmpty() -> Loading()
                ui.error != null && ui.items.isEmpty() -> ErrorState(ui.error!!, onRetry = { vm.load() })
                else -> {
                    val list = rememberLazyListState()
                    val nearEnd by remember {
                        derivedStateOf {
                            (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 5
                        }
                    }
                    LaunchedEffect(nearEnd, ui.items.size) { if (nearEnd) vm.loadMore() }
                    val scheduled = ui.shownScheduled
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Any queue, even one called through: open it to call again or clear it.
                        if (ui.queue.isNotEmpty()) {
                            item(key = "queue") {
                                Row(
                                    Modifier.fillMaxWidth().background(Tokens.Ink, RoundedCornerShape(14.dp)).clickable(onClick = onOpenQueue).padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Call queue", style = fig(16, FontWeight.Bold, Tokens.OnDark))
                                        Text(
                                            if (ui.queueLeft > 0) "${ui.queueLeft} left to call" else "Everyone called · open to clear",
                                            style = fig(13, color = Tokens.OnDark.copy(alpha = 0.8f)),
                                        )
                                    }
                                    Text(if (ui.queueLeft > 0) "Continue →" else "Open →", style = fig(15, FontWeight.Bold, Tokens.OnDark))
                                }
                            }
                        }
                        item(key = "scheduled-head") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Scheduled today · ${scheduled.size}", style = fig(17, FontWeight.Bold), modifier = Modifier.weight(1f))
                                if (scheduled.size > 1) {
                                    TextButton(onClick = { onQueueReminders(scheduled) }) {
                                        Text("Call all (${scheduled.size})", style = fig(14, FontWeight.Bold, Tokens.Green))
                                    }
                                }
                            }
                        }
                        item(key = "all-reminders") {
                            TextButton(onClick = onAllReminders, contentPadding = PaddingValues(0.dp)) {
                                Text("All reminders — upcoming, by date, done →", style = fig(14, FontWeight.Bold, Tokens.Blue))
                            }
                        }
                        if (scheduled.isEmpty()) {
                            item(key = "scheduled-empty") {
                                Text(
                                    "Nothing scheduled for today. Use Remind me on a shop, or after a call.",
                                    style = fig(14, color = Tokens.Muted),
                                )
                            }
                        }
                        items(scheduled, key = { "r-" + it.id }) { r ->
                            ReminderRow(
                                r, showShop = true, showAgent = !ui.mine && r.agentName != ui.agent,
                                onCall = { onCallReminder(r) },
                                onDone = { vm.done(r) },
                                onEdit = { vm.edit(r) },
                                onDelete = { vm.delete(r) },
                                onClick = { onOpenShop(r.userId) },
                            )
                        }
                        ui.message?.let { m -> item(key = "msg") { Text(m, style = fig(13, FontWeight.SemiBold, Tokens.Red)) } }
                        item(key = "log-head") {
                            Text("Call log", style = fig(17, FontWeight.Bold), modifier = Modifier.padding(top = 8.dp))
                        }
                        if (ui.items.isEmpty()) item(key = "log-empty") { EmptyState("No calls yet.") }
                        items(ui.items, key = { it.id }) { c -> CallCard(c, showShop = true, onClick = { onOpenCall(c.id) }) }
                    }
                }
            }
        }
    }
    ui.editing?.let { r ->
        ReminderDialog(
            title = "Reschedule · ${r.title}",
            saving = ui.saving,
            error = ui.message,
            initialTagIds = r.tags.map { it.id },
            initialNote = r.note,
            initialAt = parseInstant(r.dueAt)?.takeIf { it.isAfter(Instant.now()) },
            onDismiss = { vm.edit(null) },
            onSave = { at, tagIds, note -> vm.reschedule(r, at, tagIds, note) },
        )
    }
}

