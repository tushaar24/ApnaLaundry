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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dailyworks.mylaundry.support.Graph
import com.dailyworks.mylaundry.support.data.ApiException
import com.dailyworks.mylaundry.support.data.TagDto
import com.dailyworks.mylaundry.support.ui.components.EmptyState
import com.dailyworks.mylaundry.support.ui.components.ErrorState
import com.dailyworks.mylaundry.support.ui.components.Loading
import com.dailyworks.mylaundry.support.ui.components.TAG_COLORS
import com.dailyworks.mylaundry.support.ui.components.TagChip
import com.dailyworks.mylaundry.support.ui.components.parseColor
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.bric
import com.dailyworks.mylaundry.support.ui.theme.fig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TagsUi(
    val tags: List<TagDto> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
)

class TagsViewModel(private val graph: Graph) : ViewModel() {
    private val _ui = MutableStateFlow(TagsUi())
    val ui: StateFlow<TagsUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = it.tags.isEmpty(), error = null) }
        viewModelScope.launch {
            try {
                val tags = graph.api.tags()
                _ui.update { it.copy(tags = tags, loading = false) }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                load()
                _ui.update { it.copy(message = null) }
            } catch (e: ApiException) {
                _ui.update { it.copy(message = e.message) }
            }
        }
    }

    fun create(name: String, color: String) = act { graph.api.createTag(name, color) }
    fun update(id: String, name: String, color: String) = act { graph.api.updateTag(id, name, color) }
    fun delete(id: String) = act { graph.api.deleteTag(id) }
}

@Composable
fun TagsScreen(vm: TagsViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TagDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TagDto?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text("Tags", style = bric(26), modifier = Modifier.padding(start = 16.dp, top = 12.dp))
            Text(
                "Label calls so the team can filter shop owners by them.",
                style = fig(14, color = Tokens.Muted),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
            )
            ui.message?.let { Text(it, style = fig(14, FontWeight.SemiBold, Tokens.Red), modifier = Modifier.padding(horizontal = 16.dp)) }
            when {
                ui.loading -> Loading()
                ui.error != null && ui.tags.isEmpty() -> ErrorState(ui.error!!, onRetry = vm::load)
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (ui.tags.isEmpty()) item { EmptyState("No tags yet.") }
                    items(ui.tags, key = { it.id }) { t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Tokens.Card, RoundedCornerShape(14.dp))
                                .border(1.dp, Tokens.CardBorder, RoundedCornerShape(14.dp))
                                .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TagChip(t.name, t.color)
                            Text(
                                "${t.calls ?: 0} call${if (t.calls == 1) "" else "s"}",
                                style = fig(13, color = Tokens.Muted),
                                modifier = Modifier.weight(1f).padding(start = 10.dp),
                            )
                            IconButton(onClick = { editing = t }) { Icon(Icons.Outlined.Edit, "Edit", tint = Tokens.InkSecondary) }
                            IconButton(onClick = { deleting = t }) { Icon(Icons.Outlined.Delete, "Delete", tint = Tokens.Red) }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("New tag", style = fig(15, FontWeight.Bold)) },
            containerColor = Tokens.Blue,
            contentColor = Tokens.OnDark,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating) {
        TagEditDialog("New tag", "", null, onDismiss = { creating = false }) { n, c -> creating = false; vm.create(n, c) }
    }
    editing?.let { t ->
        TagEditDialog("Edit tag", t.name, t.color, onDismiss = { editing = null }) { n, c -> editing = null; vm.update(t.id, n, c) }
    }
    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${t.name}”?", style = fig(18, FontWeight.Bold)) },
            text = { Text("It will be removed from ${t.calls ?: 0} call(s). This can't be undone.", style = fig(15)) },
            confirmButton = {
                TextButton(onClick = { deleting = null; vm.delete(t.id) }) { Text("Delete", style = fig(15, FontWeight.Bold, Tokens.Red)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", style = fig(15, FontWeight.Bold)) } },
            containerColor = Tokens.Card,
        )
    }
}

/** Name + colour editor shared by the Tags screen and "New tag" on call notes. */
@Composable
fun TagEditDialog(
    title: String,
    initialName: String,
    initialColor: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor ?: TAG_COLORS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = fig(18, FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Name") },
                    singleLine = true,
                    textStyle = fig(16),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TAG_COLORS.forEach { hex ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .background(parseColor(hex), CircleShape)
                                .clickable { color = hex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (hex.equals(color, ignoreCase = true)) Icon(Icons.Filled.Check, "Selected", tint = Tokens.OnDark, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (name.isNotBlank()) TagChip(name.trim(), color)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), color) }, enabled = name.isNotBlank()) {
                Text("Save", style = fig(15, FontWeight.Bold, if (name.isNotBlank()) Tokens.Blue else Tokens.Faint))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = fig(15, FontWeight.Bold)) } },
        containerColor = Tokens.Card,
    )
}
