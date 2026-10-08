package com.dailyworks.mylaundry.support.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dailyworks.mylaundry.support.Graph
import com.dailyworks.mylaundry.support.data.ApiException
import com.dailyworks.mylaundry.support.data.CallDto
import com.dailyworks.mylaundry.support.ui.components.CallCard
import com.dailyworks.mylaundry.support.ui.components.EmptyState
import com.dailyworks.mylaundry.support.ui.components.ErrorState
import com.dailyworks.mylaundry.support.ui.components.Loading
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.bric
import com.dailyworks.mylaundry.support.ui.theme.fig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
)

class CallsViewModel(private val graph: Graph) : ViewModel() {
    private val _ui = MutableStateFlow(CallsUi())
    val ui: StateFlow<CallsUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _ui.update { it.copy(agent = graph.prefs.settings.first().agentName) }
            load()
        }
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
}

@Composable
fun CallsScreen(vm: CallsViewModel, onOpenCall: (String) -> Unit) {
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
                    label = { Text(label, style = fig(14, FontWeight.SemiBold)) },
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Tokens.Card, selectedContainerColor = Tokens.Ink, selectedLabelColor = Tokens.OnDark,
                    ),
                )
            }
        }
        PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.load(refreshing = true) }, modifier = Modifier.weight(1f)) {
            when {
                ui.loading && ui.items.isEmpty() -> Loading()
                ui.error != null && ui.items.isEmpty() -> ErrorState(ui.error!!, onRetry = { vm.load() })
                else -> {
                    val list = rememberLazyListState()
                    val nearEnd by remember {
                        derivedStateOf {
                            (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 5
                        }
                    }
                    LaunchedEffect(nearEnd, ui.items.size) { if (nearEnd) vm.loadMore() }
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (ui.items.isEmpty()) item { EmptyState("No calls yet.") }
                        items(ui.items, key = { it.id }) { c -> CallCard(c, showShop = true, onClick = { onOpenCall(c.id) }) }
                    }
                }
            }
        }
    }
}
