package com.dailyworks.mylaundry.support.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dailyworks.mylaundry.support.Graph
import com.dailyworks.mylaundry.support.data.ApiException
import com.dailyworks.mylaundry.support.data.Outcome
import com.dailyworks.mylaundry.support.data.OwnerSummary
import com.dailyworks.mylaundry.support.data.ShopFilters
import com.dailyworks.mylaundry.support.data.TagDto
import com.dailyworks.mylaundry.support.ui.components.EmptyState
import com.dailyworks.mylaundry.support.ui.components.ErrorState
import com.dailyworks.mylaundry.support.ui.components.Loading
import com.dailyworks.mylaundry.support.ui.components.STATE_ORDER
import com.dailyworks.mylaundry.support.ui.components.StateBadge
import com.dailyworks.mylaundry.support.ui.components.TagChip
import com.dailyworks.mylaundry.support.ui.components.ago
import com.dailyworks.mylaundry.support.ui.components.stateLook
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.bric
import com.dailyworks.mylaundry.support.ui.theme.fig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ───────────────────────── state ─────────────────────────

data class ShopsUi(
    val filters: ShopFilters = ShopFilters(),
    val items: List<OwnerSummary> = emptyList(),
    val total: Int = 0,
    val counts: Map<String, Int> = emptyMap(),
    val page: Int = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val tags: List<TagDto> = emptyList(),
    val agents: List<String> = emptyList(),
) {
    val endReached get() = items.size >= total
}

class ShopsViewModel(private val graph: Graph) : ViewModel() {
    private val _ui = MutableStateFlow(ShopsUi())
    val ui: StateFlow<ShopsUi> = _ui.asStateFlow()
    private var loadJob: Job? = null
    private val pageSize = 30

    init {
        reload()
        loadOptions()
    }

    fun loadOptions() {
        viewModelScope.launch {
            runCatching { graph.api.tags() }.onSuccess { t -> _ui.update { it.copy(tags = t) } }
            runCatching { graph.api.agents() }.onSuccess { a -> _ui.update { it.copy(agents = a) } }
        }
    }

    fun setFilters(f: ShopFilters, debounce: Boolean = false) {
        _ui.update { it.copy(filters = f) }
        reload(debounceMs = if (debounce) 350 else 0)
    }

    fun refresh() {
        _ui.update { it.copy(refreshing = true) }
        reload()
        loadOptions()
    }

    fun reload(debounceMs: Long = 0) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            _ui.update { it.copy(loading = it.items.isEmpty() || !it.refreshing, error = null) }
            try {
                val p = graph.api.shops(_ui.value.filters, page = 1, pageSize = pageSize)
                _ui.update {
                    it.copy(items = p.items, total = p.total, counts = p.counts, page = 1, loading = false, refreshing = false)
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, refreshing = false, error = e.message) }
            }
        }
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loading || s.loadingMore || s.endReached || s.error != null) return
        _ui.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val p = graph.api.shops(s.filters, page = s.page + 1, pageSize = pageSize)
                _ui.update {
                    it.copy(items = (it.items + p.items).distinctBy { o -> o.id }, total = p.total, page = p.page, loadingMore = false)
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(loadingMore = false) }
            }
        }
    }
}

// ───────────────────────── UI ─────────────────────────

private val SORTS = listOf(
    "joined_desc" to "Newest signups",
    "joined_asc" to "Oldest signups",
    "active_desc" to "Recently active",
    "active_asc" to "Least recently active",
    "last_call_desc" to "Recently called",
    "last_call_asc" to "Not called longest",
    "orders_desc" to "Most orders",
)

@Composable
fun ShopsScreen(
    vm: ShopsViewModel,
    onOpen: (String) -> Unit,
    onCall: (OwnerSummary) -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    val f = ui.filters

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Shop owners", style = bric(26))
            OutlinedTextField(
                value = f.q,
                onValueChange = { vm.setFilters(f.copy(q = it), debounce = true) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search name, shop or phone", style = fig(15, color = Tokens.Faint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Tokens.Muted) },
                trailingIcon = {
                    if (f.q.isNotEmpty()) {
                        IconButton(onClick = { vm.setFilters(f.copy(q = "")) }) { Icon(Icons.Filled.Close, "Clear", tint = Tokens.Muted) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                textStyle = fig(16),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Tokens.Card, unfocusedContainerColor = Tokens.Card,
                    unfocusedBorderColor = Tokens.CardBorder, focusedBorderColor = Tokens.Blue,
                ),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val all = ui.counts.values.sum()
                Chip("All · $all", f.sub.isEmpty()) { vm.setFilters(f.copy(sub = emptySet())) }
            }
            items(STATE_ORDER) { state ->
                val on = state in f.sub
                Chip("${stateLook(state).label} · ${ui.counts[state] ?: 0}", on) {
                    vm.setFilters(f.copy(sub = if (on) f.sub - state else f.sub + state))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { showFilters = true }, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Filled.FilterList, null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(if (f.sheetCount > 0) "Filters · ${f.sheetCount}" else "Filters", style = fig(14, FontWeight.Bold, Tokens.Blue))
            }
            Spacer(Modifier.weight(1f))
            SortMenu(f.sort) { vm.setFilters(f.copy(sort = it)) }
        }
        Text(
            "${ui.total} shop owner${if (ui.total == 1) "" else "s"}",
            style = fig(13, color = Tokens.Muted),
            modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp),
        )

        PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = vm::refresh, modifier = Modifier.weight(1f)) {
            when {
                ui.loading && ui.items.isEmpty() -> Loading()
                ui.error != null && ui.items.isEmpty() -> ErrorState(ui.error!!, onRetry = { vm.reload() })
                else -> {
                    val list = rememberLazyListState()
                    val nearEnd by remember {
                        derivedStateOf {
                            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= list.layoutInfo.totalItemsCount - 5
                        }
                    }
                    LaunchedEffect(nearEnd, ui.items.size) { if (nearEnd) vm.loadMore() }
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (ui.items.isEmpty()) item { EmptyState("No shop owners match these filters.") }
                        items(ui.items, key = { it.id }) { o ->
                            OwnerRow(o, ui.tags, onClick = { onOpen(o.id) }, onCall = { onCall(o) })
                        }
                        if (ui.loadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp), color = Tokens.Blue, strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            initial = f,
            tags = ui.tags,
            agents = ui.agents,
            onDismiss = { showFilters = false },
            onApply = { vm.setFilters(it); showFilters = false },
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = fig(13, FontWeight.SemiBold)) },
        shape = RoundedCornerShape(999.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Tokens.Card,
            selectedContainerColor = Tokens.Ink,
            selectedLabelColor = Tokens.OnDark,
            labelColor = Tokens.Ink,
        ),
    )
}

@Composable
private fun SortMenu(current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp), tint = Tokens.InkSecondary)
            Spacer(Modifier.size(6.dp))
            Text(SORTS.firstOrNull { it.first == current }?.second ?: "Sort", style = fig(14, FontWeight.SemiBold, Tokens.InkSecondary))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SORTS.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = fig(15, if (key == current) FontWeight.Bold else FontWeight.Normal)) },
                    onClick = { open = false; onPick(key) },
                )
            }
        }
    }
}

@Composable
private fun OwnerRow(o: OwnerSummary, tags: List<TagDto>, onClick: () -> Unit, onCall: () -> Unit) {
    val title = o.shopName ?: o.name ?: "Shop not set up yet"
    Row(
        Modifier
            .fillMaxWidth()
            .background(Tokens.Card, RoundedCornerShape(16.dp))
            .border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title, style = fig(16, FontWeight.Bold, if (o.shopName == null) Tokens.Muted else Tokens.Ink),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                StateBadge(o.subscription.state)
            }
            Text("+91 ${o.phone.take(5)} ${o.phone.drop(5)} · joined ${ago(o.joinedAt)}", style = fig(13, color = Tokens.InkSecondary))
            Text(
                "${o.orders} orders · ${o.customers} customers · active ${ago(o.lastActiveAt)}",
                style = fig(13, color = Tokens.Muted),
            )
            val c = o.calls
            Text(
                if (c.count == 0) "Never called"
                else "Called ${c.count}× · last ${ago(c.lastAt)}" +
                    (c.lastAgent?.let { " by $it" } ?: "") +
                    (Outcome.of(c.lastOutcome)?.let { " · ${it.label}" } ?: ""),
                style = fig(13, FontWeight.SemiBold, if (c.count == 0) Tokens.Orange else Tokens.BlueText),
            )
            val ownerTags = tags.filter { it.id in o.tagIds }
            if (ownerTags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ownerTags.forEach { TagChip(it.name, it.color) }
                }
            }
        }
        FilledIconButton(
            onClick = onCall,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Tokens.Green, contentColor = Tokens.OnDark),
        ) { Icon(Icons.Filled.Call, "Call") }
    }
}

// ───────────────────────── filter sheet ─────────────────────────

@Composable
private fun FilterSheet(
    initial: ShopFilters,
    tags: List<TagDto>,
    agents: List<String>,
    onDismiss: () -> Unit,
    onApply: (ShopFilters) -> Unit,
) {
    var f by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Tokens.Bg) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Filters", style = bric(22))

            Section("Shop setup") {
                Opt("Any", f.setup == null) { f = f.copy(setup = null) }
                Opt("Set up", f.setup == "done") { f = f.copy(setup = "done") }
                Opt("Not set up", f.setup == "pending") { f = f.copy(setup = "pending") }
            }
            Section("Orders") {
                Opt("Any", f.orders == null) { f = f.copy(orders = null) }
                Opt("Has orders", f.orders == "has") { f = f.copy(orders = "has") }
                Opt("No orders yet", f.orders == "none") { f = f.copy(orders = "none") }
            }
            Section("Last active") {
                Opt("Any", f.activeWithinDays == null && f.inactiveForDays == null) {
                    f = f.copy(activeWithinDays = null, inactiveForDays = null)
                }
                listOf(1, 7, 30).forEach { d ->
                    Opt("Within ${if (d == 1) "a day" else "$d days"}", f.activeWithinDays == d) {
                        f = f.copy(activeWithinDays = d, inactiveForDays = null)
                    }
                }
                listOf(7, 30).forEach { d ->
                    Opt("Inactive $d+ days", f.inactiveForDays == d) { f = f.copy(inactiveForDays = d, activeWithinDays = null) }
                }
            }
            Section("Calls") {
                Opt("Any", f.called == null) { f = f.copy(called = null) }
                Opt("Never called", f.called == "never") { f = f.copy(called = "never") }
                Opt("Called before", f.called == "any") { f = f.copy(called = "any") }
            }
            if (agents.isNotEmpty()) {
                Section("Called by") {
                    Opt("Anyone", f.agent == null) { f = f.copy(agent = null) }
                    agents.forEach { a -> Opt(a, f.agent == a) { f = f.copy(agent = a) } }
                }
            }
            if (tags.isNotEmpty()) {
                Section("Has a call tagged (any of)") {
                    tags.forEach { t ->
                        val on = t.id in f.tagIds
                        Opt(t.name, on) { f = f.copy(tagIds = if (on) f.tagIds - t.id else f.tagIds + t.id) }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { f = ShopFilters(q = f.q, sub = f.sub, sort = f.sort) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Reset", style = fig(16, FontWeight.Bold, Tokens.Blue)) }
                Button(
                    onClick = { onApply(f) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Tokens.Blue),
                ) { Text("Show results", style = fig(16, FontWeight.Bold, Tokens.OnDark)) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = fig(14, FontWeight.Bold, Tokens.InkSecondary))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Opt(label: String, selected: Boolean, onClick: () -> Unit) = Chip(label, selected, onClick)
