package com.dailyworks.mylaundry.support.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dailyworks.mylaundry.support.Graph
import com.dailyworks.mylaundry.support.data.ApiException
import com.dailyworks.mylaundry.support.data.CallDto
import com.dailyworks.mylaundry.support.data.Outcome
import com.dailyworks.mylaundry.support.data.PendingCall
import com.dailyworks.mylaundry.support.data.ReminderAlarms
import com.dailyworks.mylaundry.support.ui.components.ReminderDialog
import com.dailyworks.mylaundry.support.ui.components.reminderTime
import com.dailyworks.mylaundry.support.data.RecordingUploads
import com.dailyworks.mylaundry.support.data.TagDto
import com.dailyworks.mylaundry.support.data.TranscriptDto
import com.dailyworks.mylaundry.support.ui.components.ErrorState
import com.dailyworks.mylaundry.support.ui.components.Loading
import com.dailyworks.mylaundry.support.ui.components.RecordingLine
import com.dailyworks.mylaundry.support.ui.components.SectionCard
import com.dailyworks.mylaundry.support.ui.components.dayTime
import com.dailyworks.mylaundry.support.ui.components.duration
import com.dailyworks.mylaundry.support.ui.components.parseColor
import com.dailyworks.mylaundry.support.ui.components.parseInstant
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.fig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CallNotesUi(
    val call: CallDto? = null,
    val tags: List<TagDto> = emptyList(),
    val outcome: String? = null,
    val note: String = "",
    val tagIds: Set<String> = emptySet(),
    /** This call is still the phone's pending call (not finalized yet). */
    val pending: PendingCall? = null,
    /** null = unknown (no phone-state permission). */
    val inCall: Boolean? = null,
    val finalizing: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val dirty: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    /** The follow-up reminder dialog. */
    val reminding: Boolean = false,
    val savingReminder: Boolean = false,
    val reminderError: String? = null,
    /** "Reminder set for …" once saved. */
    val reminderSet: String? = null,
)

class CallNotesViewModel(private val graph: Graph, private val callId: String) : ViewModel() {
    private val _ui = MutableStateFlow(CallNotesUi())
    val ui: StateFlow<CallNotesUi> = _ui.asStateFlow()

    init {
        load()
        // A call through MyLaundry: follow it until its recording and transcript are in.
        viewModelScope.launch {
            while (true) {
                val c = _ui.value.call
                delay(if (c == null || c.isLive) 2_000 else 5_000)
                val now = _ui.value.call ?: continue
                if (!now.throughMyLaundry || !stillComing(now)) break
                refreshCall()
            }
        }
        viewModelScope.launch {
            val pending = graph.prefs.pendingCall.first()?.takeIf { it.callId == callId } ?: return@launch
            _ui.update { it.copy(pending = pending) }
            // Finish the call (talk time, end time, recording upload) once the phone
            // is idle again. This screen opens just BEFORE the dialer, when the phone
            // still reads idle — so idle only means "ended" after we've seen the call
            // in progress, or once it's clearly never going to start (dial failed).
            var seenBusy = false
            var giveUp: Job? = null
            graph.monitor.inCall().collect { busy ->
                _ui.update { it.copy(inCall = busy) }
                when (busy) {
                    true -> { seenBusy = true; giveUp?.cancel() }
                    false -> if (seenBusy) {
                        finalize()
                    } else if (giveUp == null) {
                        giveUp = launch {
                            delay((pending.startedAtMs + 20_000 - System.currentTimeMillis()).coerceAtLeast(0))
                            if (!seenBusy) finalize()
                        }
                    }
                    null -> Unit // no phone-state permission: the agent taps "Call has ended"
                }
            }
        }
    }

    fun load() {
        _ui.update { it.copy(loading = it.call == null, error = null) }
        viewModelScope.launch {
            try {
                val tags = graph.api.tags()
                val call = graph.api.getCall(callId)
                _ui.update { s ->
                    // Don't clobber what the agent is typing.
                    if (s.dirty) s.copy(call = call, tags = tags, loading = false)
                    else s.copy(
                        call = call, tags = tags, loading = false,
                        outcome = call.outcome, note = call.note, tagIds = call.tags.map { it.id }.toSet(),
                    )
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    /** Re-reads the call (progress, outcome, recording, transcript) without a spinner. */
    private suspend fun refreshCall() {
        try {
            val call = graph.api.getCall(callId)
            _ui.update { s ->
                // The server may have filled in the outcome; keep the agent's own pick.
                if (s.dirty) s.copy(call = call) else s.copy(call = call, outcome = call.outcome)
            }
        } catch (_: ApiException) {
            // Next round.
        }
    }

    /** Something about this call through MyLaundry is still on its way from the server. */
    private fun stillComing(c: CallDto): Boolean {
        if (c.isLive || c.recording.processing) return true
        if (c.live?.status == "agent_missed") return false
        if (c.transcript?.status in setOf("pending", "processing")) return true
        // Just ended: the recording webhook comes a little after the hang-up.
        val ended = parseInstant(c.endedAt)?.toEpochMilli() ?: return false
        return !c.recording.uploaded && System.currentTimeMillis() - ended < 3 * 60_000
    }

    /** Also the manual "Call ended" button when phone state can't be read. */
    fun finalize() {
        val p = _ui.value.pending ?: return
        if (_ui.value.finalizing) return
        _ui.update { it.copy(finalizing = true) }
        viewModelScope.launch {
            try {
                val call = graph.calls.finalize(p)
                _ui.update { s ->
                    s.copy(
                        call = call, pending = null, finalizing = false,
                        // finalize() may have suggested "No answer"; keep the agent's own pick.
                        outcome = if (s.dirty && s.outcome != null) s.outcome else call.outcome ?: s.outcome,
                    )
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(finalizing = false, message = e.message) }
            }
        }
    }

    // "Call back later" usually means: when? Offer the reminder right away.
    fun setOutcome(o: String?) = _ui.update {
        it.copy(outcome = o, dirty = true, reminding = it.reminding || (o == Outcome.CALL_BACK.name && it.reminderSet == null))
    }

    fun remind(open: Boolean) = _ui.update { it.copy(reminding = open, reminderError = null) }

    fun saveReminder(context: android.content.Context, at: java.time.Instant, kind: String, note: String) {
        val userId = _ui.value.call?.userId ?: return
        _ui.update { it.copy(savingReminder = true, reminderError = null) }
        viewModelScope.launch {
            try {
                graph.api.createReminder(userId, graph.prefs.current().agentName, at.toString(), kind, note)
                _ui.update { it.copy(savingReminder = false, reminding = false, reminderSet = reminderTime(at)) }
                ReminderAlarms.syncSoon(context.applicationContext)
            } catch (e: ApiException) {
                _ui.update { it.copy(savingReminder = false, reminderError = e.message) }
            }
        }
    }


    fun setNote(n: String) = _ui.update { it.copy(note = n, dirty = true) }
    fun toggleTag(id: String) = _ui.update {
        it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id, dirty = true)
    }

    fun createTag(name: String, color: String) {
        viewModelScope.launch {
            try {
                val t = graph.api.createTag(name, color)
                _ui.update { it.copy(tags = (it.tags + t).sortedBy { x -> x.name.lowercase() }, tagIds = it.tagIds + t.id, dirty = true) }
            } catch (e: ApiException) {
                _ui.update { it.copy(message = e.message) }
            }
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _ui.value
        _ui.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            try {
                val call = graph.api.updateCall(callId, outcome = s.outcome, note = s.note, tagIds = s.tagIds.toList())
                _ui.update { it.copy(call = call, saving = false, dirty = false) }
                onDone()
            } catch (e: ApiException) {
                _ui.update { it.copy(saving = false, message = e.message) }
            }
        }
    }

    fun attachRecording(context: android.content.Context, uri: Uri) {
        val c = _ui.value.call ?: return
        val start = parseInstant(c.startedAt)?.toEpochMilli() ?: 0
        val end = parseInstant(c.endedAt)?.toEpochMilli() ?: System.currentTimeMillis()
        RecordingUploads.enqueue(context, c.id, c.phone, start, end, fileUri = uri)
        _ui.update { it.copy(message = "Uploading the attached recording…") }
    }
}

@Composable
fun CallNotesScreen(vm: CallNotesViewModel, onBack: () -> Unit, onOpenShop: (String) -> Unit = {}) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var newTagOpen by remember { mutableStateOf(false) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.attachRecording(context, uri)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = {
                Column {
                    Text("Call notes", style = fig(18, FontWeight.Bold))
                    val c = ui.call
                    Text(
                        c?.shopName ?: ui.pending?.label ?: c?.let { "+91 ${it.phone}" } ?: "",
                        style = fig(13, color = Tokens.Muted),
                    )
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Tokens.Bg),
        )
        val call = ui.call
        when {
            ui.loading && call == null -> Loading()
            call == null -> ErrorState(ui.error ?: "Couldn't load the call", onRetry = vm::load)
            else -> {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatusCard(ui, call, onEnded = vm::finalize, onOpenShop = onOpenShop)

                    SectionCard("How did it go?") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Outcome.entries.forEach { o ->
                                val on = ui.outcome == o.name
                                FilterChip(
                                    selected = on,
                                    onClick = { vm.setOutcome(if (on) null else o.name) },
                                    label = { Text(o.label, style = fig(14, FontWeight.SemiBold, Color.Unspecified)) },
                                    shape = RoundedCornerShape(999.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Tokens.Ink, selectedLabelColor = Tokens.OnDark, labelColor = Tokens.Ink,
                                    ),
                                )
                            }
                        }
                    }

                    SectionCard("Notes") {
                        OutlinedTextField(
                            value = ui.note,
                            onValueChange = vm::setNote,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                            placeholder = { Text("What did they say? What's the follow-up?", style = fig(15, color = Tokens.Faint)) },
                            textStyle = fig(15),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Tokens.CardBorder, focusedBorderColor = Tokens.Blue,
                            ),
                        )
                    }

                    SectionCard("Tags") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ui.tags.forEach { t ->
                                val on = t.id in ui.tagIds
                                val c = parseColor(t.color)
                                FilterChip(
                                    selected = on,
                                    onClick = { vm.toggleTag(t.id) },
                                    label = { Text(t.name, style = fig(14, FontWeight.SemiBold, Color.Unspecified)) },
                                    leadingIcon = { Spacer(Modifier.size(8.dp).background(c, RoundedCornerShape(999.dp))) },
                                    shape = RoundedCornerShape(999.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = c.copy(alpha = 0.16f), selectedLabelColor = c, labelColor = Tokens.Ink,
                                    ),
                                )
                            }
                            FilterChip(
                                selected = false,
                                onClick = { newTagOpen = true },
                                label = { Text("New tag", style = fig(14, FontWeight.SemiBold, Tokens.Blue)) },
                                leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp), tint = Tokens.Blue) },
                                shape = RoundedCornerShape(999.dp),
                            )
                        }
                    }

                    if (call.userId != null) {
                        SectionCard("Follow-up") {
                            ui.reminderSet?.let { Text("⏰ Reminder set for $it", style = fig(14, FontWeight.Bold, Tokens.GreenText)) }
                            OutlinedButton(onClick = { vm.remind(true) }, shape = RoundedCornerShape(12.dp)) {
                                Text(if (ui.reminderSet == null) "⏰ Remind me to call again" else "Add another reminder", style = fig(14, FontWeight.Bold, Tokens.Blue))
                            }
                        }
                    }

                    call.transcript?.let { TranscriptCard(it, if (call.live?.status == "voicemail") "Their message" else "What was said") }

                    SectionCard("Recording") {
                        RecordingLine(call)
                        if (!call.recording.uploaded && !call.throughMyLaundry) {
                            OutlinedButton(onClick = { pickFile.launch(arrayOf("audio/*")) }, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Filled.AttachFile, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Attach recording file", style = fig(14, FontWeight.Bold, Tokens.Blue))
                            }
                        }
                    }
                    ui.message?.let { Text(it, style = fig(14, FontWeight.SemiBold, Tokens.OrangeText)) }
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = { vm.save(onBack) },
                    enabled = !ui.saving,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Tokens.Blue),
                ) {
                    Text(if (ui.saving) "Saving…" else "Save notes", style = fig(17, FontWeight.Bold, Tokens.OnDark))
                }
            }
        }
    }

    if (ui.reminding) {
        ReminderDialog(
            title = "Remind me · ${ui.call?.shopName ?: ui.call?.ownerName ?: "this shop"}",
            saving = ui.savingReminder,
            error = ui.reminderError,
            initialNote = if (ui.outcome == Outcome.CALL_BACK.name) "Call back" else "",
            onDismiss = { vm.remind(false) },
            onSave = { at, kind, note -> vm.saveReminder(context, at, kind, note) },
        )
    }

    if (newTagOpen) {
        TagEditDialog(
            title = "New tag",
            initialName = "",
            initialColor = null,
            onDismiss = { newTagOpen = false },
            onSave = { name, color -> newTagOpen = false; vm.createTag(name, color) },
        )
    }
}

@Composable
private fun StatusCard(ui: CallNotesUi, call: CallDto, onEnded: () -> Unit, onOpenShop: (String) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Call, null, tint = if (ui.pending != null || call.isLive) Tokens.Green else Tokens.Muted)
            Column(Modifier.weight(1f)) {
                val title = when {
                    call.incoming -> when (call.live?.status) {
                        "agent_ringing" -> "Incoming call — ringing your team…"
                        "connected" -> "On the call — you can write notes now"
                        "voicemail" -> if (call.endedAt == null) "Nobody picked up — they're leaving a message…" else "Missed call — they left a message"
                        "missed" -> "Missed call — call them back"
                        else -> if (call.endedAt == null) "Incoming call…" else "Incoming call · ${duration(call.durationSec)}"
                    }
                    call.throughMyLaundry -> when (call.live?.status) {
                        "agent_ringing" -> "Your phone is ringing — pick up"
                        "owner_ringing" -> "Ringing the shop owner…"
                        "connected" -> "On the call — you can write notes now"
                        "agent_missed" -> "You didn't pick up, so the shop owner wasn't called"
                        else -> if (call.endedAt == null) "Call in progress…" else "Call ended · ${duration(call.durationSec)}"
                    }
                    ui.finalizing -> "Finishing the call…"
                    ui.pending != null && ui.inCall == true -> "On the call — you can write notes now"
                    ui.pending != null -> "Waiting for the call to end…"
                    else -> "Call ended · ${duration(call.durationSec)}"
                }
                Text(title, style = fig(15, FontWeight.Bold))
                Text("${dayTime(call.startedAt)} · ${call.agentName}", style = fig(13, color = Tokens.Muted))
            }
            if (ui.finalizing || (call.isLive && call.live?.status != "connected")) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Tokens.Blue, strokeWidth = 2.dp)
            }
        }
        // They called: the shop's page has their details and the Call button to call back.
        if (call.incoming) {
            val userId = call.userId
            if (userId != null) {
                OutlinedButton(onClick = { onOpenShop(userId) }, shape = RoundedCornerShape(12.dp)) {
                    Text(if (call.missed) "Open shop to call back" else "Open shop", style = fig(14, FontWeight.Bold, Tokens.Blue))
                }
            } else {
                Text("+91 ${call.phone} isn't a MyLaundry shop yet.", style = fig(13, color = Tokens.Muted))
            }
        }
        // Without phone-state permission we can't see the hang-up: let the agent say so.
        if (ui.pending != null && ui.inCall == null && !ui.finalizing) {
            OutlinedButton(onClick = onEnded, shape = RoundedCornerShape(12.dp)) {
                Text("Call has ended", style = fig(14, FontWeight.Bold, Tokens.Blue))
            }
        }
    }
}

/** What was said on a call through MyLaundry (Sarvam's transcript, made by the server). */
@Composable
private fun TranscriptCard(t: TranscriptDto, title: String) {
    SectionCard(title) {
        when (t.status) {
            "done" -> {
                val lines = t.text.orEmpty().lines().filter { it.isNotBlank() }
                if (lines.isEmpty()) Text("Nobody spoke on this call.", style = fig(14, color = Tokens.Muted))
                lines.forEach { line ->
                    // "Speaker 1: …" — the label bold, the words plain.
                    val who = line.substringBefore(": ", "")
                    Text(
                        buildAnnotatedString {
                            if (who.isNotEmpty()) {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(who) }
                                append(": ")
                                append(line.substringAfter(": "))
                            } else {
                                append(line)
                            }
                        },
                        style = fig(14),
                    )
                }
                Text(
                    "Written by a computer, so it may have mistakes. Speaker 1 is whoever spoke first.",
                    style = fig(12, color = Tokens.Faint),
                )
            }
            "pending", "processing" -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Tokens.Blue, strokeWidth = 2.dp)
                Text("Writing down the call… takes a minute or two", style = fig(14, color = Tokens.Muted))
            }
            "skipped" -> Text("Nothing to write down — the shop owner didn't pick up.", style = fig(14, color = Tokens.Muted))
            else -> Text("Couldn't write down this call.", style = fig(14, FontWeight.SemiBold, Tokens.OrangeText))
        }
    }
}
