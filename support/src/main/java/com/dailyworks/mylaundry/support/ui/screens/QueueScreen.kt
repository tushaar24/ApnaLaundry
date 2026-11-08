package com.dailyworks.mylaundry.support.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.mylaundry.support.Graph
import com.dailyworks.mylaundry.support.data.QueueItem
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.bric
import com.dailyworks.mylaundry.support.ui.theme.fig
import kotlinx.coroutines.launch

/**
 * The call queue (8 Nov 2026): people picked in Shops (Select) or today's scheduled calls
 * (Call all). Call → the call → notes → back here with the next one ready: call, call, call.
 * Kept on this phone.
 */
@Composable
fun QueueScreen(graph: Graph, placing: Boolean, onBack: () -> Unit, onCall: (QueueItem) -> Unit, onOpenShop: (String) -> Unit) {
    val queue by graph.prefs.queue.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val current = queue.firstOrNull { it.status == "pending" }
    val called = queue.count { it.status == "called" }
    val skipped = queue.count { it.status == "skipped" }
    fun set(item: QueueItem, status: String) = scope.launch {
        graph.prefs.updateQueue { q -> q.map { if (it === item || it == item) it.copy(status = status) else it } }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Call queue", style = fig(18, FontWeight.Bold)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (queue.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { graph.prefs.setQueue(emptyList()); onBack() } }) {
                        Text("Clear", style = fig(14, FontWeight.Bold, Tokens.Red))
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Tokens.Bg),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "$called of ${queue.size} called" + if (skipped > 0) " · $skipped skipped" else "",
                        style = fig(14, FontWeight.SemiBold, Tokens.Muted),
                    )
                    LinearProgressIndicator(
                        progress = { if (queue.isEmpty()) 0f else (called + skipped) / queue.size.toFloat() },
                        modifier = Modifier.fillMaxWidth(), color = Tokens.Green, trackColor = Tokens.Neutral,
                    )
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
                        Text(
                            current.label, style = bric(24), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onOpenShop(current.userId) },
                        )
                        Text("+91 ${current.phone.take(5)} ${current.phone.drop(5)}", style = fig(15, FontWeight.SemiBold, Tokens.InkSecondary))
                        current.note?.takeIf { it.isNotBlank() }?.let { Text("⏰ $it", style = fig(14, color = Tokens.BlueText)) }
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { onCall(current) },
                            enabled = !placing,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Tokens.Green),
                        ) {
                            Icon(Icons.Filled.Call, null)
                            Spacer(Modifier.size(8.dp))
                            Text(if (placing) "Calling…" else "Call", style = fig(18, FontWeight.Bold, Tokens.OnDark))
                        }
                        OutlinedButton(
                            onClick = { set(current, "skipped") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("Skip", style = fig(15, FontWeight.Bold, Tokens.InkSecondary)) }
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().background(Tokens.GreenLight, RoundedCornerShape(16.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(if (queue.isEmpty()) "The queue is empty" else "All done 🎉", style = bric(22))
                        Text(
                            if (queue.isEmpty()) "Pick shops in Shops (Select) or use Call all on today's scheduled calls."
                            else "Everyone in the queue was called or skipped.",
                            style = fig(14, color = Tokens.InkSecondary),
                        )
                        if (queue.isNotEmpty()) {
                            Button(
                                onClick = { scope.launch { graph.prefs.setQueue(emptyList()); onBack() } },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Tokens.Ink),
                            ) { Text("Clear the queue", style = fig(15, FontWeight.Bold, Tokens.OnDark)) }
                        }
                    }
                }
            }
            // The next person's shop: plan, usage, sales, all details.
            current?.let { c -> item(key = "shop-" + c.userId) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { ShopCard(c.userId, onOpenShop) } } }
            if (queue.isNotEmpty()) item { Text("Everyone · ${queue.size}", style = fig(16, FontWeight.Bold), modifier = Modifier.padding(top = 6.dp)) }
            itemsIndexed(queue) { i, item ->
                val isNext = item == current
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
                        "skipped" -> "↷" to Tokens.Faint
                        else -> "${i + 1}" to Tokens.InkSecondary
                    }
                    Text(mark, style = fig(15, FontWeight.Bold, color), modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.label, style = fig(15, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when (item.status) { "called" -> "Called"; "skipped" -> "Skipped"; else -> "+91 ${item.phone}" },
                            style = fig(13, color = Tokens.Muted),
                        )
                    }
                    when (item.status) {
                        "pending" -> {
                            IconButton(onClick = { onCall(item) }, enabled = !placing) { Icon(Icons.Filled.Call, "Call now", tint = Tokens.Green) }
                            IconButton(onClick = { scope.launch { graph.prefs.updateQueue { q -> q.filterIndexed { j, _ -> j != i } } } }) {
                                Icon(Icons.Filled.Close, "Remove", tint = Tokens.Faint)
                            }
                        }
                        "skipped" -> TextButton(onClick = { set(item, "pending") }) { Text("Undo", style = fig(13, FontWeight.Bold, Tokens.Blue)) }
                    }
                }
            }
        }
    }
}
