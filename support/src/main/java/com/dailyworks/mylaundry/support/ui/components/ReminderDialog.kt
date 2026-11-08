package com.dailyworks.mylaundry.support.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailyworks.mylaundry.support.data.ReminderDto
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.fig
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.dailyworks.mylaundry.support.SupportApp
import com.dailyworks.mylaundry.support.data.ApiException
import com.dailyworks.mylaundry.support.data.DEFAULT_REMINDER_TAG
import com.dailyworks.mylaundry.support.data.TagDto
import com.dailyworks.mylaundry.support.ui.screens.TagEditDialog
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Mon 10 Nov, 10:00 am". */
fun reminderTime(at: Instant): String =
    at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH))

/**
 * Schedule (or change) a reminder: its tags ("Call", "Follow up", the team's own — "+ New tag"
 * adds one), when (quick picks or any date + time) and a note. [onSave] gets the time, the
 * tag ids and the note.
 */
@Composable
fun ReminderDialog(
    title: String,
    saving: Boolean,
    error: String?,
    initialTagIds: List<String> = listOf(DEFAULT_REMINDER_TAG),
    initialNote: String = "",
    initialAt: Instant? = null,
    onDismiss: () -> Unit,
    onSave: (Instant, List<String>, String) -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val graph = (context.applicationContext as SupportApp).graph
    var allTags by remember { mutableStateOf<List<TagDto>?>(null) }
    var tagIds by remember { mutableStateOf(initialTagIds.toSet()) }
    var newTagOpen by remember { mutableStateOf(false) }
    var tagError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        allTags = runCatching { graph.api.reminderTags() }.getOrElse { tagError = (it as? ApiException)?.message; emptyList() }
    }
    var note by remember { mutableStateOf(initialNote) }
    var at by remember { mutableStateOf(initialAt) }
    val now = ZonedDateTime.now(zone)
    val at6pmToday = now.toLocalDate().atTime(18, 0).atZone(zone)
    val tomorrow = now.toLocalDate().plusDays(1)
    val picks = buildList {
        add("In 1 hour" to now.plusHours(1))
        add("In 3 hours" to now.plusHours(3))
        if (now.isBefore(at6pmToday.minusMinutes(30))) add("Today 6 pm" to at6pmToday)
        add("Tomorrow 10 am" to tomorrow.atTime(10, 0).atZone(zone))
        add("Tomorrow 5 pm" to tomorrow.atTime(17, 0).atZone(zone))
    }.map { (label, t) -> label to t.withSecond(0).withNano(0).toInstant() }

    fun pickDateTime() {
        val start = (at ?: now.plusHours(1).toInstant()).atZone(zone)
        DatePickerDialog(context, { _, y, m, d ->
            TimePickerDialog(context, { _, h, min ->
                at = LocalDate.of(y, m + 1, d).atTime(LocalTime.of(h, min)).atZone(zone).toInstant()
            }, start.hour, start.minute, false).show()
        }, start.year, start.monthValue - 1, start.dayOfMonth).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
        }.show()
    }

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Tokens.Ink, selectedLabelColor = Tokens.OnDark, labelColor = Tokens.Ink,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = fig(18, FontWeight.Bold)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tags", style = fig(13, FontWeight.Bold, Tokens.Muted))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tags = allTags
                    if (tags == null) Text("Loading tags…", style = fig(13, color = Tokens.Muted))
                    tags?.forEach { t ->
                        val on = t.id in tagIds
                        val c = parseColor(t.color)
                        FilterChip(
                            selected = on,
                            onClick = { tagIds = if (on) tagIds - t.id else tagIds + t.id },
                            label = { Text(t.name, style = fig(14, FontWeight.SemiBold, Color.Unspecified)) },
                            leadingIcon = { Spacer(Modifier.size(8.dp).background(c, RoundedCornerShape(999.dp))) },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = c.copy(alpha = 0.16f), selectedLabelColor = c, labelColor = Tokens.Ink,
                            ),
                        )
                    }
                    if (tags != null) {
                        FilterChip(
                            selected = false, onClick = { newTagOpen = true },
                            label = { Text("+ New tag", style = fig(14, FontWeight.SemiBold, Tokens.Blue)) },
                            shape = RoundedCornerShape(999.dp),
                        )
                    }
                }
                tagError?.let { Text(it, style = fig(12, color = Tokens.Red)) }
                Text("When", style = fig(13, FontWeight.Bold, Tokens.Muted))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    picks.forEach { (label, t) ->
                        FilterChip(
                            selected = at == t, onClick = { at = t },
                            label = { Text(label, style = fig(14, FontWeight.SemiBold, Color.Unspecified)) },
                            shape = RoundedCornerShape(999.dp), colors = chipColors,
                        )
                    }
                    FilterChip(
                        selected = at != null && picks.none { it.second == at }, onClick = ::pickDateTime,
                        label = { Text("Pick date & time", style = fig(14, FontWeight.SemiBold, Color.Unspecified)) },
                        shape = RoundedCornerShape(999.dp), colors = chipColors,
                    )
                }
                at?.let { Text("⏰ ${reminderTime(it)}", style = fig(15, FontWeight.Bold, Tokens.BlueText)) }
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(1000) },
                    placeholder = { Text("Note (e.g. asked to call after 5)", style = fig(14, color = Tokens.Faint)) },
                    modifier = Modifier.fillMaxWidth(), textStyle = fig(15), shape = RoundedCornerShape(12.dp),
                )
                error?.let { Text(it, style = fig(13, FontWeight.SemiBold, Tokens.Red)) }
            }
        },
        confirmButton = {
            val t = at
            TextButton(onClick = { if (t != null) onSave(t, tagIds.toList(), note.trim()) }, enabled = !saving && t != null && t.isAfter(Instant.now())) {
                Text(if (saving) "Saving…" else "Save", style = fig(15, FontWeight.Bold, Tokens.Blue))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = fig(15, FontWeight.SemiBold)) } },
    )
    if (newTagOpen) {
        TagEditDialog(
            title = "New reminder tag",
            initialName = "",
            initialColor = null,
            onDismiss = { newTagOpen = false },
            onSave = { name, color ->
                newTagOpen = false
                scope.launch {
                    try {
                        val t = graph.api.createReminderTag(name, color)
                        allTags = (allTags.orEmpty() + t).sortedBy { it.name.lowercase() }
                        tagIds = tagIds + t.id
                        tagError = null
                    } catch (e: ApiException) {
                        tagError = e.message
                    }
                }
            },
        )
    }
}

/** "Overdue · Mon 10 Nov, 10:00 am" / "Today, 4:00 pm" / "Tue 11 Nov, 10:00 am". */
fun reminderWhen(iso: String): Pair<String, Boolean> {
    val at = parseInstant(iso) ?: return iso to false
    val local = at.atZone(ZoneId.systemDefault())
    val overdue = at.isBefore(Instant.now())
    val text = if (local.toLocalDate() == LocalDate.now()) "Today, " + local.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
    else reminderTime(at)
    return (if (overdue) "Overdue · $text" else text) to overdue
}

/** One reminder, with Call / Done and a menu to reschedule or delete. */
@Composable
fun ReminderRow(
    r: ReminderDto,
    showShop: Boolean,
    showAgent: Boolean,
    onCall: () -> Unit,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val (whenText, overdue) = reminderWhen(r.dueAt)
    Column(
        Modifier.fillMaxWidth()
            .background(Tokens.Card, RoundedCornerShape(14.dp))
            .border(1.dp, if (overdue) Tokens.Red.copy(alpha = 0.35f) else Tokens.CardBorder, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⏰ $whenText", style = fig(13, FontWeight.Bold, if (overdue) Tokens.Red else Tokens.BlueText), modifier = Modifier.weight(1f))
        }
        if (r.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                r.tags.forEach { TagChip(it.name, it.color) }
            }
        }
        if (showShop) Text(r.title, style = fig(16, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (r.note.isNotBlank()) Text(r.note, style = fig(14, color = Tokens.InkSecondary))
        if (showAgent) Text("Set by ${r.agentName}", style = fig(12, color = Tokens.Faint))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCall, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Tokens.Green),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Filled.Call, null, Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Call", style = fig(14, FontWeight.Bold, Tokens.OnDark))
            }
            OutlinedButton(onClick = onDone, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Text("Done", style = fig(14, FontWeight.Bold, Tokens.Blue))
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Tokens.Muted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Reschedule", style = fig(15)) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete", style = fig(15, color = Tokens.Red)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
