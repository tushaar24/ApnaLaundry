package com.dailyworks.mylaundry.support.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dailyworks.mylaundry.support.Graph
import com.dailyworks.mylaundry.support.data.ApiException
import com.dailyworks.mylaundry.support.data.ReminderAlarms
import com.dailyworks.mylaundry.support.data.ReminderDto
import com.dailyworks.mylaundry.support.ui.components.EmptyState
import com.dailyworks.mylaundry.support.ui.components.ErrorState
import com.dailyworks.mylaundry.support.ui.components.Loading
import com.dailyworks.mylaundry.support.ui.components.ReminderDialog
import com.dailyworks.mylaundry.support.ui.components.ReminderRow
import com.dailyworks.mylaundry.support.ui.components.parseInstant
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.fig
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import com.dailyworks.mylaundry.support.data.TagDto
import com.dailyworks.mylaundry.support.ui.components.parseColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Which reminders: by when they're due, or the ones already done. */
enum class RemindersWhen(val label: String) {
    OVERDUE("Overdue"),
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    WEEK("Next 7 days"),
    UPCOMING("All upcoming"),
    DATE("Pick a date"),
    DONE("Done"),
}

data class RemindersUi(
    val agent: String = "",
    val mine: Boolean = true,
    val whenFilter: RemindersWhen = RemindersWhen.TODAY,
    /** For [RemindersWhen.DATE]. */
    val date: LocalDate? = null,
    /** Tag filter: reminders with any of these tags; empty = all. */
    val tagIds: Set<String> = emptySet(),
    val tags: List<TagDto> = emptyList(),
    val items: List<ReminderDto> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val editing: ReminderDto? = null,
    val saving: Boolean = false,
    val message: String? = null,
) {
    val shown get() = items.filter { r -> (tagIds.isEmpty() || r.tags.any { it.id in tagIds }) && (!mine || r.agentName == agent) }
}

class RemindersViewModel(private val graph: Graph, private val appContext: Context) : ViewModel() {
    private val _ui = MutableStateFlow(RemindersUi())
    val ui: StateFlow<RemindersUi> = _ui.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            _ui.update { it.copy(agent = graph.prefs.settings.first().agentName) }
            load()
        }
        loadTags()
    }

    fun setWhen(w: RemindersWhen, date: LocalDate? = null) {
        _ui.update { it.copy(whenFilter = w, date = date ?: it.date, items = emptyList()) }
        load()
    }

    fun setMine(mine: Boolean) = _ui.update { it.copy(mine = mine) }
    fun toggleTag(id: String?) = _ui.update {
        it.copy(tagIds = if (id == null) emptySet() else if (id in it.tagIds) it.tagIds - id else it.tagIds + id)
    }

    fun loadTags() {
        viewModelScope.launch { runCatching { graph.api.reminderTags() }.onSuccess { t -> _ui.update { it.copy(tags = t) } } }
    }

    fun load(refreshing: Boolean = false) {
        loadJob?.cancel()
        _ui.update { it.copy(loading = it.items.isEmpty(), refreshing = refreshing, error = null) }
        loadJob = viewModelScope.launch {
            val s = _ui.value
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val now = Instant.now()
            fun day(d: LocalDate) = d.atStartOfDay(zone).toInstant()
            val (from, to) = when (s.whenFilter) {
                RemindersWhen.OVERDUE -> day(today.minusDays(365)) to now
                RemindersWhen.TODAY -> day(today) to day(today.plusDays(1))
                RemindersWhen.TOMORROW -> day(today.plusDays(1)) to day(today.plusDays(2))
                RemindersWhen.WEEK -> day(today) to day(today.plusDays(8))
                RemindersWhen.UPCOMING -> now to day(today.plusDays(366))
                RemindersWhen.DATE -> (s.date ?: today).let { day(it) to day(it.plusDays(1)) }
                RemindersWhen.DONE -> day(today.minusDays(30)) to day(today.plusDays(366))
            }
            try {
                val list = graph.api.reminders(fromIso = from.toString(), toIso = to.toString(), open = s.whenFilter != RemindersWhen.DONE)
                val items = if (s.whenFilter == RemindersWhen.DONE) list.filter { it.doneAt != null }.sortedByDescending { it.doneAt } else list
                _ui.update { it.copy(items = items, loading = false, refreshing = false) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, refreshing = false, error = e.message) }
            }
        }
    }

    private fun change(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                _ui.update { it.copy(editing = null, saving = false, message = null) }
                ReminderAlarms.syncSoon(appContext)
                load()
            } catch (e: ApiException) {
                _ui.update { it.copy(saving = false, message = e.message) }
            }
        }
    }

    fun done(r: ReminderDto) {
        _ui.update { s -> s.copy(items = s.items.filter { it.id != r.id }) }
        change { graph.api.updateReminder(r.id, done = true) }
    }

    /** A done reminder back to open. */
    fun reopen(r: ReminderDto) {
        _ui.update { s -> s.copy(items = s.items.filter { it.id != r.id }) }
        change { graph.api.updateReminder(r.id, done = false) }
    }

    fun delete(r: ReminderDto) {
        _ui.update { s -> s.copy(items = s.items.filter { it.id != r.id }) }
        change { graph.api.deleteReminder(r.id) }
    }

    fun edit(r: ReminderDto?) = _ui.update { it.copy(editing = r, message = null) }

    fun reschedule(r: ReminderDto, at: Instant, tagIds: List<String>, note: String) {
        _ui.update { it.copy(saving = true) }
        change { graph.api.updateReminder(r.id, dueAtIso = at.toString(), tagIds = tagIds, note = note, done = false) }
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** "Overdue" / "Today · Sat 8 Nov" / "Tomorrow · Sun 9 Nov" / "Mon 10 Nov". */
private fun dayHeader(r: ReminderDto, today: LocalDate): String {
    val at = parseInstant(r.dueAt) ?: return ""
    if (r.doneAt == null && at.isBefore(Instant.now()) && at.atZone(ZoneId.systemDefault()).toLocalDate() != today) return "Overdue"
    val d = at.atZone(ZoneId.systemDefault()).toLocalDate()
    return when (d) {
        today -> "Today · ${d.format(DAY)}"
        today.plusDays(1) -> "Tomorrow · ${d.format(DAY)}"
        else -> d.format(DAY)
    }
}

@Composable
fun RemindersScreen(
    vm: RemindersViewModel,
    onBack: () -> Unit,
    onOpenShop: (String) -> Unit,
    onCallReminder: (ReminderDto) -> Unit,
    onQueueReminders: (List<ReminderDto>) -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        vm.load()
        vm.loadTags()
        onPauseOrDispose { }
    }
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = Tokens.Card, selectedContainerColor = Tokens.Ink, selectedLabelColor = Tokens.OnDark, labelColor = Tokens.Ink,
    )
    fun pickDate() {
        val start = ui.date ?: LocalDate.now()
        DatePickerDialog(context, { _, y, m, d -> vm.setWhen(RemindersWhen.DATE, LocalDate.of(y, m + 1, d)) }, start.year, start.monthValue - 1, start.dayOfMonth).show()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Reminders", style = fig(18, FontWeight.Bold)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Tokens.Bg),
        )
        // When
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RemindersWhen.entries.toList()) { w ->
                val label = if (w == RemindersWhen.DATE && ui.date != null && ui.whenFilter == w) ui.date!!.format(DAY) else w.label
                FilterChip(
                    selected = ui.whenFilter == w,
                    onClick = { if (w == RemindersWhen.DATE) pickDate() else vm.setWhen(w) },
                    label = { Text(label, style = fig(14, FontWeight.SemiBold, Color.Unspecified)) },
                    shape = RoundedCornerShape(999.dp), colors = chipColors,
                )
            }
        }
        // Whose + what
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = ui.mine, onClick = { vm.setMine(true) }, label = { Text("Mine", style = fig(13, FontWeight.SemiBold, Color.Unspecified)) }, shape = RoundedCornerShape(999.dp), colors = chipColors)
            }
            item {
                FilterChip(selected = !ui.mine, onClick = { vm.setMine(false) }, label = { Text("Everyone", style = fig(13, FontWeight.SemiBold, Color.Unspecified)) }, shape = RoundedCornerShape(999.dp), colors = chipColors)
            }
        }
        // Tags (any of the picked ones), like the call tags
        LazyRow(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = ui.tagIds.isEmpty(), onClick = { vm.toggleTag(null) },
                    label = { Text("All tags", style = fig(13, FontWeight.SemiBold, Color.Unspecified)) },
                    shape = RoundedCornerShape(999.dp), colors = chipColors,
                )
            }
            items(ui.tags, key = { it.id }) { t ->
                val on = t.id in ui.tagIds
                val c = parseColor(t.color)
                FilterChip(
                    selected = on, onClick = { vm.toggleTag(t.id) },
                    label = { Text(t.name, style = fig(13, FontWeight.SemiBold, Color.Unspecified)) },
                    leadingIcon = { Spacer(Modifier.size(8.dp).background(c, RoundedCornerShape(999.dp))) },
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(containerColor = Tokens.Card, selectedContainerColor = c.copy(alpha = 0.16f), selectedLabelColor = c, labelColor = Tokens.Ink),
                )
            }
        }
        val shown = ui.shown
        val open = ui.whenFilter != RemindersWhen.DONE
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${shown.size} reminder${if (shown.size == 1) "" else "s"}", style = fig(13, color = Tokens.Muted), modifier = Modifier.weight(1f))
            if (open && shown.size > 1) {
                TextButton(onClick = { onQueueReminders(shown) }) { Text("Call all (${shown.size})", style = fig(14, FontWeight.Bold, Tokens.Green)) }
            }
        }
        ui.message?.let { Text(it, style = fig(13, FontWeight.SemiBold, Tokens.Red), modifier = Modifier.padding(horizontal = 16.dp)) }
        PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.load(refreshing = true) }, modifier = Modifier.weight(1f)) {
            when {
                ui.loading && ui.items.isEmpty() -> Loading()
                ui.error != null && ui.items.isEmpty() -> ErrorState(ui.error!!, onRetry = { vm.load() })
                shown.isEmpty() -> EmptyState(
                    when (ui.whenFilter) {
                        RemindersWhen.DONE -> "Nothing done in the last 30 days."
                        RemindersWhen.OVERDUE -> "Nothing overdue."
                        else -> "No reminders here. Use Remind me on a shop, or after a call."
                    },
                )
                else -> {
                    val today = LocalDate.now()
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        var last: String? = null
                        shown.forEach { r ->
                            val head = if (open) dayHeader(r, today) else "Done"
                            if (head != last) {
                                last = head
                                item(key = "h-$head") {
                                    Text(head, style = fig(15, FontWeight.Bold, if (head == "Overdue") Tokens.Red else Tokens.Ink), modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                            item(key = r.id) {
                                if (open) {
                                    ReminderRow(
                                        r, showShop = true, showAgent = !ui.mine,
                                        onCall = { onCallReminder(r) },
                                        onDone = { vm.done(r) },
                                        onEdit = { vm.edit(r) },
                                        onDelete = { vm.delete(r) },
                                        onClick = { onOpenShop(r.userId) },
                                    )
                                } else {
                                    DoneRow(r, onReopen = { vm.reopen(r) }, onOpenShop = { onOpenShop(r.userId) })
                                }
                            }
                        }
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

@Composable
private fun DoneRow(r: ReminderDto, onReopen: () -> Unit, onOpenShop: () -> Unit) {
    com.dailyworks.mylaundry.support.ui.components.SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("✓ ${r.title}", style = fig(15, FontWeight.Bold), modifier = Modifier.padding(bottom = 2.dp))
                val doneAt = parseInstant(r.doneAt)?.atZone(ZoneId.systemDefault())
                Text(
                    listOfNotNull(
                        r.tagLine,
                        doneAt?.let { "done ${it.format(DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH))}" },
                        "by ${r.agentName}",
                    ).joinToString(" · "),
                    style = fig(12, color = Tokens.Muted),
                )
                if (r.note.isNotBlank()) Text(r.note, style = fig(13, color = Tokens.InkSecondary))
            }
            TextButton(onClick = onOpenShop) { Text("Shop", style = fig(13, FontWeight.Bold, Tokens.Blue)) }
            OutlinedButton(onClick = onReopen, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Text("Reopen", style = fig(13, FontWeight.Bold, Tokens.Blue))
            }
        }
    }
}
