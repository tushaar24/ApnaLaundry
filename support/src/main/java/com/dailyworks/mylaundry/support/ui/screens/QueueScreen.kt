package com.dailyworks.mylaundry.support.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.dailyworks.mylaundry.support.Graph
import com.dailyworks.mylaundry.support.data.ApiException
import com.dailyworks.mylaundry.support.data.QueueDto
import com.dailyworks.mylaundry.support.data.QueueItemDto
import com.dailyworks.mylaundry.support.data.showPhone
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.bric
import com.dailyworks.mylaundry.support.ui.theme.fig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Call queues (support app 1.10, 9 Nov 2026): named lists on the server, shared by every agent —
// made here (Shops → Select, Call all on reminders, + New queue) or on the server
// (backend/scripts/support-queue.js). Call → the call → notes → back here with the next one ready.
// The person's state follows the call: placed = called; its notes (or Vobiz) saying no answer /
// busy / switched off = "didn't pick up", with Call again. Two agents never call the same person.

// ───────────────────────────── the list of queues ─────────────────────────────

data class QueuesUi(val queues: List<QueueDto> = emptyList(), val loading: Boolean = true, val refreshing: Boolean = false, val error: String? = null)

class QueuesViewModel(private val graph: Graph) : ViewModel() {
    private val _ui = MutableStateFlow(QueuesUi())
    val ui: StateFlow<QueuesUi> = _ui.asStateFlow()

    fun load(refreshing: Boolean = false) {
        _ui.update { it.copy(loading = it.queues.isEmpty() && !refreshing, refreshing = refreshing && it.queues.isEmpty(), error = null) }
        viewModelScope.launch {
            try {
                val list = graph.api.queues()
                _ui.update { it.copy(queues = list, loading = false, refreshing = false) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, refreshing = false, error = e.message) }
            }
        }
    }

    fun create(title: String, description: String, onMade: (QueueDto) -> Unit) {
        viewModelScope.launch {
            try {
                onMade(graph.api.createQueue(title, description, graph.prefs.current().agentName))
            } catch (e: ApiException) {
                _ui.update { it.copy(error = e.message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueuesScreen(vm: QueuesViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var making by remember { mutableStateOf(false) }
    RefreshWhileShown(10_000) { vm.load() }
    if (making) {
        QueueTextDialog(title = "New queue", confirm = "Make queue", onDismiss = { making = false }) { t, d ->
            making = false
            vm.create(t, d) { onOpen(it.id) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Call queues", style = fig(18, FontWeight.Bold)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                TextButton(onClick = { making = true }) {
                    Icon(Icons.Filled.Add, null, tint = Tokens.Blue)
                    Text("New queue", style = fig(14, FontWeight.Bold, Tokens.Blue))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Tokens.Bg),
        )
        PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.load(refreshing = true) }, modifier = Modifier.weight(1f)) {
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Tokens.Blue) }
                ui.error != null && ui.queues.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(ui.error!!, style = fig(15, color = Tokens.Red))
                    Button(onClick = { vm.load() }) { Text("Try again") }
                }
                ui.queues.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No call queues yet", style = bric(22))
                    Text(
                        "Make one with New queue, or pick shops in Shops (Select → Call queue), or use Call all on reminders.",
                        style = fig(14, color = Tokens.InkSecondary),
                    )
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ui.queues, key = { it.id }) { q -> QueueRow(q) { onOpen(q.id) } }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(q: QueueDto, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Tokens.Card, RoundedCornerShape(14.dp))
            .border(1.dp, Tokens.CardBorder, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(q.title, style = fig(17, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (q.description.isNotBlank()) Text(q.description, style = fig(14, color = Tokens.InkSecondary), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            (if (q.left > 0) "${q.left} to call" else if (q.total > 0) "Everyone called" else "Empty") +
                " · ${q.total} in all" + (if (q.calling > 0) " · ${q.calling} on a call now" else "") + " · by ${q.createdBy}",
            style = fig(13, FontWeight.SemiBold, if (q.left > 0) Tokens.GreenText else Tokens.Muted),
        )
        if (q.total > 0) {
            LinearProgressIndicator(
                progress = { (q.total - q.left) / q.total.toFloat() },
                modifier = Modifier.fillMaxWidth(), color = Tokens.Green, trackColor = Tokens.Neutral,
            )
        }
    }
}

// ───────────────────────────── one queue ─────────────────────────────

data class QueueUi(val queue: QueueDto? = null, val loading: Boolean = true, val gone: Boolean = false, val error: String? = null)

class QueueViewModel(private val graph: Graph, val queueId: String) : ViewModel() {
    private val _ui = MutableStateFlow(QueueUi())
    val ui: StateFlow<QueueUi> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            try {
                val q = graph.api.queue(queueId)
                _ui.update { it.copy(queue = q, loading = false, error = null) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, gone = e.status == 404, error = e.message) }
            }
        }
    }

    private fun act(block: suspend () -> QueueDto) {
        viewModelScope.launch {
            try {
                val q = block()
                _ui.update { it.copy(queue = q, error = null) }
            } catch (e: ApiException) {
                _ui.update { it.copy(error = e.message) }
                load()
            }
        }
    }

    fun skip(item: QueueItemDto) = act { graph.api.setQueueItem(queueId, item.id, "skipped") }
    fun backInLine(item: QueueItemDto) = act { graph.api.setQueueItem(queueId, item.id, "pending") }
    fun remove(item: QueueItemDto) = act { graph.api.removeQueueItem(queueId, item.id) }
    fun retry() = act { graph.api.retryQueue(queueId) }
    fun edit(title: String, description: String) = act { graph.api.editQueue(queueId, title, description) }
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                graph.api.deleteQueue(queueId)
                onDone()
            } catch (e: ApiException) {
                _ui.update { it.copy(error = e.message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    vm: QueueViewModel,
    placing: Boolean,
    agentName: String,
    onBack: () -> Unit,
    onCall: (QueueItemDto, refresh: () -> Unit) -> Unit,
    onOpenShop: (String) -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Other agents work the same queue: keep it fresh while it's on screen.
    RefreshWhileShown(8_000) { vm.load() }
    LaunchedEffect(ui.gone) { if (ui.gone) onBack() }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val q = ui.queue
    val items = q?.items.orEmpty()
    val current = items.firstOrNull { it.status == "pending" }
    val call: (QueueItemDto) -> Unit = { onCall(it) { vm.load() } }

    if (editing && q != null) {
        QueueTextDialog(title = "Edit queue", confirm = "Save", initialTitle = q.title, initialDescription = q.description, onDismiss = { editing = false }) { t, d ->
            editing = false
            vm.edit(t, d)
        }
    }
    if (confirmDelete && q != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${q.title}\"?", style = fig(18, FontWeight.Bold)) },
            text = { Text((if (q.left > 0) "${q.left} still to call. " else "") + "It goes for every agent. Calls already made stay in Calls.", style = fig(15)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Delete", style = fig(15, FontWeight.Bold, Tokens.Red)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", style = fig(15, FontWeight.Bold, Tokens.Blue)) } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(q?.title ?: "Call queue", style = fig(18, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (q != null) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Edit title / description", style = fig(15)) }, onClick = { menu = false; editing = true })
                            DropdownMenuItem(text = { Text("Delete queue", style = fig(15, color = Tokens.Red)) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Tokens.Bg),
        )
        if (q == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (ui.loading) CircularProgressIndicator(color = Tokens.Blue)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(ui.error ?: "Couldn't load the queue", style = fig(15, color = Tokens.Red))
                    Button(onClick = { vm.load() }) { Text("Try again") }
                }
            }
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (q.description.isNotBlank()) Text(q.description, style = fig(15, color = Tokens.InkSecondary))
                    Text(
                        "${q.called} of ${q.total} reached" +
                            (if (q.notReached > 0) " · ${q.notReached} didn't pick up" else "") +
                            (if (q.skipped > 0) " · ${q.skipped} skipped" else "") +
                            (if (q.calling > 0) " · ${q.calling} on a call now" else ""),
                        style = fig(14, FontWeight.SemiBold, Tokens.Muted),
                    )
                    LinearProgressIndicator(
                        progress = { if (q.total == 0) 0f else (q.total - q.left) / q.total.toFloat() },
                        modifier = Modifier.fillMaxWidth(), color = Tokens.Green, trackColor = Tokens.Neutral,
                    )
                    ui.error?.let { Text(it, style = fig(13, FontWeight.SemiBold, Tokens.Red)) }
                }
            }
            item {
                if (current != null) {
                    Column(
                        Modifier.fillMaxWidth().background(Tokens.Card, RoundedCornerShape(16.dp))
                            .border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("NEXT", style = fig(12, FontWeight.Bold, Tokens.Muted))
                        Text(current.label, style = bric(24), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { onOpenShop(current.userId) })
                        if (current.phone.isNotBlank()) {
                            Text(showPhone(current.phone), style = fig(15, FontWeight.SemiBold, Tokens.InkSecondary))
                        }
                        current.note.takeIf { it.isNotBlank() }?.let { Text("⏰ $it", style = fig(14, color = Tokens.BlueText)) }
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { call(current) },
                            enabled = !placing,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Tokens.Green),
                        ) {
                            Icon(Icons.Filled.Call, null)
                            Spacer(Modifier.size(8.dp))
                            Text(if (placing) "Calling…" else "Call", style = fig(18, FontWeight.Bold, Tokens.OnDark))
                        }
                        OutlinedButton(onClick = { vm.skip(current) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Text("Skip", style = fig(15, FontWeight.Bold, Tokens.InkSecondary))
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().background(Tokens.GreenLight, RoundedCornerShape(16.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            when {
                                q.total == 0 -> "No one in this queue yet"
                                q.notReached > 0 -> "Round done"
                                q.calling > 0 -> "Last calls going on"
                                else -> "All done 🎉"
                            },
                            style = bric(22),
                        )
                        Text(
                            when {
                                q.total == 0 -> "Add shops from Shops (Select → Call queue) or use Call all on reminders, then pick this queue."
                                q.notReached > 0 -> "${q.notReached} didn't pick up. Call them again, or come back later."
                                q.calling > 0 -> "An agent is on a call with the last ones."
                                else -> "Everyone in this queue was called or skipped."
                            },
                            style = fig(14, color = Tokens.InkSecondary),
                        )
                        if (q.notReached > 0) {
                            Button(
                                onClick = { vm.retry() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Tokens.Green),
                            ) { Text("Call the ${q.notReached} again", style = fig(15, FontWeight.Bold, Tokens.OnDark)) }
                        }
                    }
                }
            }
            // The next person's shop: plan, usage, sales, all details.
            current?.let { c -> item(key = "shop-" + c.userId) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { ShopCard(c.userId, onOpenShop) } } }
            if (items.isNotEmpty()) item { Text("Everyone · ${items.size}", style = fig(16, FontWeight.Bold), modifier = Modifier.padding(top = 6.dp)) }
            itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                QueuePersonRow(
                    i, item, isNext = item.id == current?.id, placing = placing, me = agentName,
                    onOpenShop = onOpenShop, onCall = call, onRemove = vm::remove, onBackInLine = vm::backInLine,
                )
            }
        }
    }
}

@Composable
private fun QueuePersonRow(
    i: Int, item: QueueItemDto, isNext: Boolean, placing: Boolean, me: String,
    onOpenShop: (String) -> Unit, onCall: (QueueItemDto) -> Unit, onRemove: (QueueItemDto) -> Unit, onBackInLine: (QueueItemDto) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (isNext) Tokens.BlueLight else Tokens.Card, RoundedCornerShape(12.dp))
            .border(1.dp, Tokens.CardBorder, RoundedCornerShape(12.dp))
            .clickable { onOpenShop(item.userId) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val (mark, color) = when (item.status) {
            "called" -> "✓" to Tokens.GreenText
            "no_answer" -> "✕" to Tokens.Red
            "skipped" -> "↷" to Tokens.Faint
            "calling" -> "☎" to Tokens.Blue
            else -> "${i + 1}" to Tokens.InkSecondary
        }
        Text(mark, style = fig(15, FontWeight.Bold, color), modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(item.label, style = fig(15, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val by = item.agentName?.takeIf { it.isNotBlank() }?.let { if (it == me) "you" else it }
            Text(
                when (item.status) {
                    "called" -> "Called" + (by?.let { " by $it" } ?: "")
                    "no_answer" -> "Didn't pick up" + (by?.let { " · $it called" } ?: "")
                    "skipped" -> "Skipped"
                    "calling" -> if (by == "you") "You're calling" else "${by ?: "An agent"} is calling"
                    else -> showPhone(item.phone).ifBlank { "Waiting" }
                },
                style = fig(13, color = Tokens.Muted),
            )
        }
        when (item.status) {
            "pending" -> {
                IconButton(onClick = { onCall(item) }, enabled = !placing) { Icon(Icons.Filled.Call, "Call now", tint = Tokens.Green) }
                IconButton(onClick = { onRemove(item) }) { Icon(Icons.Filled.Close, "Remove", tint = Tokens.Faint) }
            }
            "no_answer" -> {
                TextButton(onClick = { onCall(item) }, enabled = !placing) { Text("Call again", style = fig(13, FontWeight.Bold, Tokens.Green)) }
                IconButton(onClick = { onRemove(item) }) { Icon(Icons.Filled.Close, "Remove", tint = Tokens.Faint) }
            }
            "skipped" -> TextButton(onClick = { onBackInLine(item) }) { Text("Undo", style = fig(13, FontWeight.Bold, Tokens.Blue)) }
        }
    }
}

// ───────────────────────────── adding people: which queue? ─────────────────────────────

/**
 * Shops → Select → Call queue, or Call all on reminders: make a new queue (title, description
 * optional) or pick one to add [count] people to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToQueueSheet(graph: Graph, count: Int, busy: Boolean, onDismiss: () -> Unit, onPick: (QueueDto) -> Unit, onNew: (title: String, description: String) -> Unit) {
    var list by remember { mutableStateOf<List<QueueDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            list = graph.api.queues()
        } catch (e: ApiException) {
            error = e.message
            list = emptyList()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tokens.Bg) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 640.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("Add $count to a call queue", style = bric(22)) }
            val queues = list
            when {
                queues == null -> item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Tokens.Blue) } }
                queues.isNotEmpty() -> {
                    item { Text("Add to a queue", style = fig(15, FontWeight.Bold, Tokens.Muted)) }
                    items(queues, key = { it.id }) { q -> QueueRow(q) { if (!busy) onPick(q) } }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 6.dp).background(Tokens.Card, RoundedCornerShape(14.dp))
                        .border(1.dp, Tokens.CardBorder, RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(if (list.isNullOrEmpty()) "New queue" else "Or make a new queue", style = fig(16, FontWeight.Bold))
                    QueueField(title, { title = it.take(80) }, "Title, e.g. Bangalore — never ordered")
                    QueueField(description, { description = it.take(500) }, "Description (optional)", singleLine = false)
                    Button(
                        onClick = { onNew(title.trim(), description.trim()) },
                        enabled = title.isNotBlank() && !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Tokens.Green),
                    ) { Text("Make queue and add $count", style = fig(15, FontWeight.Bold, Tokens.OnDark)) }
                }
            }
            error?.let { e -> item { Text(e, style = fig(13, FontWeight.SemiBold, Tokens.Red)) } }
        }
    }
}

@Composable
private fun QueueTextDialog(
    title: String, confirm: String, initialTitle: String = "", initialDescription: String = "",
    onDismiss: () -> Unit, onSave: (String, String) -> Unit,
) {
    var t by remember { mutableStateOf(initialTitle) }
    var d by remember { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = fig(18, FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QueueField(t, { t = it.take(80) }, "Title")
                QueueField(d, { d = it.take(500) }, "Description (optional)", singleLine = false)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(t.trim(), d.trim()) }, enabled = t.isNotBlank()) { Text(confirm, style = fig(15, FontWeight.Bold, Tokens.Blue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = fig(15, FontWeight.Bold, Tokens.Muted)) } },
    )
}

@Composable
private fun QueueField(value: String, onChange: (String) -> Unit, placeholder: String, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = fig(15, color = Tokens.Faint)) },
        singleLine = singleLine, minLines = if (singleLine) 1 else 2,
        shape = RoundedCornerShape(12.dp), textStyle = fig(16),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Tokens.Card, unfocusedContainerColor = Tokens.Card,
            unfocusedBorderColor = Tokens.CardBorder, focusedBorderColor = Tokens.Blue,
        ),
    )
}

/** Runs [refresh] now and every [everyMs] while the screen is in front. */
@Composable
private fun RefreshWhileShown(everyMs: Long, refresh: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                refresh()
                delay(everyMs)
            }
        }
    }
}
