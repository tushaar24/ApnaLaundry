package com.dailyworks.mylaundry.support.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dailyworks.mylaundry.support.Graph
import com.dailyworks.mylaundry.support.SupportApp
import com.dailyworks.mylaundry.support.data.ApiException
import com.dailyworks.mylaundry.support.data.WaContact
import com.dailyworks.mylaundry.support.data.WaConversation
import com.dailyworks.mylaundry.support.data.WaMessage
import com.dailyworks.mylaundry.support.data.WaNotifier
import com.dailyworks.mylaundry.support.data.WaPush
import com.dailyworks.mylaundry.support.data.WaSignedOut
import com.dailyworks.mylaundry.support.data.WaTemplate
import com.dailyworks.mylaundry.support.ui.components.EmptyState
import com.dailyworks.mylaundry.support.ui.components.ErrorState
import com.dailyworks.mylaundry.support.ui.components.Loading
import com.dailyworks.mylaundry.support.ui.components.SectionCard
import com.dailyworks.mylaundry.support.ui.components.parseInstant
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.bric
import com.dailyworks.mylaundry.support.ui.theme.fig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// The WhatsApp tab (8 Nov 2026): the MyLaundry number's chats from admin.mylaundry.work —
// read, reply (text, photo / file, template), and a notification for each new message.

private val WaGreen = Color(0xFF128C7E)
private val OutBubble = Color(0xFFDCF8C6)

private fun timeOf(iso: String?): String {
    val t = parseInstant(iso)?.atZone(ZoneId.systemDefault()) ?: return ""
    val pattern = if (t.toLocalDate() == LocalDate.now()) "h:mm a" else "d MMM, h:mm a"
    return t.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
}

// ───────── sign in ─────────

@Composable
fun WaSignInScreen(graph: Graph) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("WhatsApp", style = bric(26))
        Text(
            "Read and reply to messages on the MyLaundry WhatsApp number. Sign in once with the " +
                "admin.mylaundry.work login.",
            style = fig(15, color = Tokens.Muted),
        )
        SectionCard {
            OutlinedTextField(
                value = username, onValueChange = { username = it }, label = { Text("Username") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = fig(16),
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Password") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = fig(16),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(12.dp),
            )
            error?.let { Text(it, style = fig(14, FontWeight.SemiBold, Tokens.Red)) }
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            graph.wa.signIn(username, password)
                            WaPush.register(context)
                        } catch (e: ApiException) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WaGreen),
            ) { Text(if (busy) "Signing in…" else "Sign in", style = fig(16, FontWeight.Bold, Tokens.OnDark)) }
        }
    }
}

// ───────── chats ─────────

data class ChatsUi(
    val items: List<WaConversation> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val search: String = "",
    val unreadOnly: Boolean = false,
)

class ChatsViewModel(private val graph: Graph) : ViewModel() {
    private val _ui = MutableStateFlow(ChatsUi())
    val ui: StateFlow<ChatsUi> = _ui.asStateFlow()
    private var searchJob: Job? = null

    suspend fun refresh() {
        try {
            val list = graph.wa.conversations(_ui.value.search.trim())
            _ui.update { it.copy(items = list, loading = false, error = null) }
        } catch (_: WaSignedOut) {
            // The tab shows the sign-in.
        } catch (e: ApiException) {
            _ui.update { it.copy(loading = false, error = if (it.items.isEmpty()) e.message else null) }
        }
    }

    fun retry() { _ui.update { it.copy(loading = true, error = null) }; viewModelScope.launch { refresh() } }

    fun setSearch(q: String) {
        _ui.update { it.copy(search = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(400); refresh() }
    }

    fun setUnreadOnly(on: Boolean) = _ui.update { it.copy(unreadOnly = on) }
}

@Composable
fun ChatsScreen(vm: ChatsViewModel, onOpen: (String) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Fresh while on screen (the server also pushes each new message).
    LaunchedEffect(Unit) { while (true) { vm.refresh(); delay(10_000) } }
    val rows = if (ui.unreadOnly) ui.items.filter { it.unread > 0 } else ui.items
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("WhatsApp", style = bric(26))
            OutlinedTextField(
                value = ui.search, onValueChange = vm::setSearch,
                placeholder = { Text("Search shop, name or number", style = fig(15, color = Tokens.Faint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = fig(15),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Tokens.CardBorder, focusedBorderColor = WaGreen),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "All", true to "Unread (${ui.items.count { it.unread > 0 }})").forEach { (on, label) ->
                    FilterChip(
                        selected = ui.unreadOnly == on, onClick = { vm.setUnreadOnly(on) },
                        label = { Text(label, style = fig(14, FontWeight.SemiBold, Color.Unspecified)) },
                        shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Tokens.Ink, selectedLabelColor = Tokens.OnDark, labelColor = Tokens.Ink),
                    )
                }
            }
        }
        when {
            ui.loading && ui.items.isEmpty() -> Loading()
            ui.error != null -> ErrorState(ui.error!!, onRetry = vm::retry)
            rows.isEmpty() -> EmptyState(if (ui.unreadOnly) "No unread chats" else "No chats")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.waId }) { c -> ChatRow(c, onClick = { onOpen(c.waId) }) }
            }
        }
    }
}

@Composable
private fun ChatRow(c: WaConversation, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).background(if (c.userId != null) Tokens.GreenLight else Tokens.Neutral, CircleShape), contentAlignment = Alignment.Center) {
            Text(c.title.trimStart('+').take(1).uppercase(), style = fig(18, FontWeight.Bold, Tokens.GreenText))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.title, style = fig(16, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(timeOf(c.last.at), style = fig(12, if (c.unread > 0) FontWeight.Bold else FontWeight.Normal, if (c.unread > 0) WaGreen else Tokens.Faint))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val you = if (c.last.direction == "outbound") "You: " else ""
                Text(you + c.last.preview.orEmpty(), style = fig(14, color = Tokens.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (c.unread > 0) {
                    Box(Modifier.padding(start = 6.dp).background(WaGreen, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text("${c.unread}", style = fig(12, FontWeight.Bold, Tokens.OnDark))
                    }
                }
            }
        }
    }
}

// ───────── one chat ─────────

data class ChatUi(
    val contact: WaContact? = null,
    val messages: List<WaMessage> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val text: String = "",
    val sending: Boolean = false,
    val message: String? = null,
    val templates: List<WaTemplate>? = null,
)

class ChatViewModel(private val graph: Graph, val waId: String) : ViewModel() {
    private val _ui = MutableStateFlow(ChatUi())
    val ui: StateFlow<ChatUi> = _ui.asStateFlow()
    private var lastInboundId: String? = null

    suspend fun refresh() {
        try {
            val t = graph.wa.thread(waId)
            _ui.update { it.copy(contact = t.contact, messages = t.messages, loading = false, error = null) }
            // A new message from them while the chat is open: mark it read (blue ticks).
            val newest = t.messages.lastOrNull { !it.outbound }?.id
            if (newest != null && newest != lastInboundId) {
                lastInboundId = newest
                runCatching { graph.wa.markRead(waId) }
            }
        } catch (_: WaSignedOut) {
            // The tab shows the sign-in.
        } catch (e: ApiException) {
            _ui.update { it.copy(loading = false, error = if (it.contact == null) e.message else null) }
        }
    }

    fun retry() { _ui.update { it.copy(loading = true, error = null) }; viewModelScope.launch { refresh() } }

    fun setText(t: String) = _ui.update { it.copy(text = t, message = null) }

    private fun sending(block: suspend () -> WaMessage) {
        _ui.update { it.copy(sending = true, message = null) }
        viewModelScope.launch {
            try {
                val m = block()
                _ui.update { it.copy(messages = it.messages + m, sending = false) }
            } catch (_: WaSignedOut) {
                _ui.update { it.copy(sending = false) }
            } catch (e: ApiException) {
                _ui.update { it.copy(sending = false, message = e.message) }
            }
        }
    }

    fun sendText() {
        val text = _ui.value.text.trim()
        if (text.isEmpty()) return
        _ui.update { it.copy(text = "") }
        sending { graph.wa.send(waId, text) }
    }

    fun sendFile(context: Context, uri: Uri) {
        val caption = _ui.value.text.trim()
        _ui.update { it.copy(text = "") }
        sending {
            val (bytes, name) = withContext(Dispatchers.IO) {
                val r = context.contentResolver
                val name = r.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "file"
                val bytes = r.openInputStream(uri)?.use { it.readBytes() } ?: throw ApiException("Can't read that file")
                bytes to name
            }
            if (bytes.size > 20 * 1024 * 1024) throw ApiException("File is bigger than 20 MB")
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            graph.wa.sendMedia(waId, bytes, mime, name, caption)
        }
    }

    fun loadTemplates() {
        if (_ui.value.templates != null) return
        viewModelScope.launch {
            try {
                val list = graph.wa.templates()
                _ui.update { it.copy(templates = list) }
            } catch (e: ApiException) {
                _ui.update { it.copy(message = e.message) }
            } catch (_: WaSignedOut) {
            }
        }
    }

    fun sendTemplate(t: WaTemplate, params: List<String>) = sending { graph.wa.sendTemplate(waId, t, params) }
}

@Composable
fun ChatScreen(vm: ChatViewModel, onBack: () -> Unit, onOpenShop: (String) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var templatesOpen by remember { mutableStateOf(false) }
    // No notification for this chat while it's on screen; clear the one already shown.
    DisposableEffect(vm.waId) {
        WaNotifier.openChat = vm.waId
        WaNotifier.clear(context, vm.waId)
        onDispose { if (WaNotifier.openChat == vm.waId) WaNotifier.openChat = null }
    }
    LaunchedEffect(Unit) { while (true) { vm.refresh(); delay(4_000) } }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.sendFile(context, uri)
    }
    val c = ui.contact
    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = {
                Column {
                    Text(c?.title ?: "+${vm.waId}", style = fig(17, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(
                        c?.phone?.let { "+91 ${it.take(5)} ${it.drop(5)}" },
                        c?.ownerName?.takeIf { it != c.title },
                        if (c != null && c.userId == null) "not a MyLaundry shop" else null,
                    ).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = fig(12, color = Tokens.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                TextButton(onClick = { vm.loadTemplates(); templatesOpen = true }) {
                    Text("Template", style = fig(14, FontWeight.Bold, WaGreen))
                }
                c?.userId?.let { id -> IconButton(onClick = { onOpenShop(id) }) { Icon(Icons.Filled.Storefront, "Open shop", tint = Tokens.InkSecondary) } }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Tokens.Card),
        )
        when {
            ui.loading && c == null -> Loading(Modifier.weight(1f))
            ui.error != null -> ErrorState(ui.error!!, onRetry = vm::retry, modifier = Modifier.weight(1f))
            else -> {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().background(Color(0xFFEFE7DE)),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { Spacer(Modifier.height(6.dp)) }
                    items(ui.messages.asReversed(), key = { it.id }) { m -> Bubble(m) }
                    item { Spacer(Modifier.height(6.dp)) }
                }
                ui.message?.let {
                    Text(it, style = fig(13, FontWeight.SemiBold, Tokens.Red), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
                if (c != null && !c.windowOpen) {
                    // WhatsApp's rule: free replies only within 24 h of their last message.
                    Column(
                        Modifier.fillMaxWidth().background(Tokens.Card).padding(12.dp).navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Their last message was more than 24 hours ago (or they never wrote), so WhatsApp only allows a template message.",
                            style = fig(13, color = Tokens.Muted),
                        )
                        Button(
                            onClick = { vm.loadTemplates(); templatesOpen = true },
                            enabled = !ui.sending,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WaGreen),
                        ) { Text(if (ui.sending) "Sending…" else "Send a template", style = fig(15, FontWeight.Bold, Tokens.OnDark)) }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().background(Tokens.Card).padding(8.dp).navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { pickFile.launch(arrayOf("*/*")) }, enabled = !ui.sending) {
                            Icon(Icons.Filled.AttachFile, "Attach a photo or file", tint = Tokens.InkSecondary)
                        }
                        OutlinedTextField(
                            value = ui.text, onValueChange = vm::setText,
                            placeholder = { Text("Message", style = fig(15, color = Tokens.Faint)) },
                            modifier = Modifier.weight(1f).heightIn(max = 140.dp), textStyle = fig(15),
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Tokens.CardBorder, focusedBorderColor = WaGreen),
                        )
                        IconButton(onClick = vm::sendText, enabled = !ui.sending && ui.text.isNotBlank()) {
                            if (ui.sending) CircularProgressIndicator(Modifier.size(20.dp), color = WaGreen, strokeWidth = 2.dp)
                            else Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (ui.text.isNotBlank()) WaGreen else Tokens.Faint)
                        }
                    }
                }
            }
        }
    }
    if (templatesOpen) {
        TemplateDialog(
            templates = ui.templates,
            shopName = c?.shopName ?: c?.ownerName ?: c?.contactName ?: "",
            onDismiss = { templatesOpen = false },
            onSend = { t, params -> templatesOpen = false; vm.sendTemplate(t, params) },
        )
    }
}

@Composable
private fun Bubble(m: WaMessage) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = if (m.outbound) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .background(if (m.outbound) OutBubble else Tokens.Card, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (m.type) {
                "template" -> {
                    val t = m.template
                    t?.header?.let { Text(it, style = fig(15, FontWeight.Bold)) }
                    Text(t?.body ?: m.body.orEmpty(), style = fig(15))
                    t?.footer?.let { Text(it, style = fig(12, color = Tokens.Faint)) }
                    t?.buttons?.filter { !it.text.isNullOrBlank() }?.forEach { Text("↗ ${it.text}", style = fig(14, FontWeight.SemiBold, Tokens.Blue)) }
                }
                "image", "sticker" -> {
                    m.mediaId?.let { WaImage(it, m.mediaMime ?: "image/jpeg") }
                    m.body?.let { Text(it, style = fig(15)) }
                }
                "video", "audio", "document" -> {
                    val label = when (m.type) { "video" -> "🎬 Video"; "audio" -> "🎤 Voice message"; else -> "📄 ${m.filename ?: "Document"}" }
                    FileLine(m, label)
                    m.body?.let { Text(it, style = fig(15)) }
                }
                "location" -> {
                    val l = m.location
                    Text(
                        "📍 " + listOfNotNull(l?.name, l?.address).joinToString(", ").ifBlank { "Location" },
                        style = fig(15, color = Tokens.Blue),
                        modifier = Modifier.clickable(enabled = l?.lat != null) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${l?.lat},${l?.lng}?q=${l?.lat},${l?.lng}"))) }
                        },
                    )
                }
                "reaction" -> Text("Reacted ${m.reaction?.emoji ?: m.body.orEmpty()}", style = fig(14, color = Tokens.Muted))
                "contacts" -> Text("👤 ${m.body ?: "Contact"}", style = fig(15))
                "unsupported" -> Text("⚠️ A message type WhatsApp can't show here", style = fig(14, color = Tokens.Muted))
                else -> Text(m.body ?: "[${m.type}]", style = fig(15))
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(timeOf(m.at), style = fig(11, color = Tokens.Faint))
                if (m.outbound) {
                    val (tick, color) = when (m.status) {
                        "read" -> "✓✓" to Tokens.Blue
                        "delivered" -> "✓✓" to Tokens.Faint
                        "failed" -> "!" to Tokens.Red
                        else -> "✓" to Tokens.Faint
                    }
                    Text(tick, style = fig(11, FontWeight.Bold, color))
                }
            }
            m.errorTitle?.let { Text("Not delivered: $it", style = fig(12, FontWeight.SemiBold, Tokens.Red)) }
        }
    }
}

// Photos: fetched once (through the admin site), kept in memory, shrunk to fit a bubble.
private object WaMediaCache : LruCache<String, ImageBitmap>(30)

@Composable
private fun WaImage(mediaId: String, mime: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = (context.applicationContext as SupportApp).graph.wa
    var bmp by remember(mediaId) { mutableStateOf(WaMediaCache.get(mediaId)) }
    var error by remember(mediaId) { mutableStateOf<String?>(null) }
    LaunchedEffect(mediaId) {
        if (bmp != null) return@LaunchedEffect
        try {
            val bytes = api.media(mediaId)
            bmp = withContext(Dispatchers.Default) { decodeScaled(bytes, 900) }?.also { WaMediaCache.put(mediaId, it) }
            if (bmp == null) error = "Can't show this photo"
        } catch (e: ApiException) {
            error = e.message
        } catch (_: WaSignedOut) {
        }
    }
    val b = bmp
    when {
        b != null -> Image(
            b, "Photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.widthIn(max = 260.dp).heightIn(max = 320.dp).clip(RoundedCornerShape(8.dp))
                .clickable { scope.launch { openMedia(context, mediaId, mime, "photo.${mime.substringAfter('/')}") } },
        )
        error != null -> Text("📷 $error", style = fig(14, color = Tokens.Muted))
        else -> Box(Modifier.size(200.dp, 140.dp).background(Tokens.Neutral, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), color = WaGreen, strokeWidth = 2.dp)
        }
    }
}

private fun decodeScaled(bytes: ByteArray, maxSide: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

@Composable
private fun FileLine(m: WaMessage, label: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Row(
        Modifier.background(Tokens.Neutral.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .border(1.dp, Tokens.CardBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = m.mediaId != null && !busy) {
                busy = true
                error = null
                scope.launch {
                    error = openMedia(context, m.mediaId!!, m.mediaMime ?: "application/octet-stream", m.filename ?: label.substringAfter(' '))
                    busy = false
                }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = fig(14, FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = WaGreen, strokeWidth = 2.dp)
        else Text("Open", style = fig(14, FontWeight.Bold, WaGreen))
    }
    error?.let { Text(it, style = fig(12, color = Tokens.Red)) }
}

/** Downloads a chat's photo / file and opens it in another app. Returns an error to show, or null. */
private suspend fun openMedia(context: Context, mediaId: String, mime: String, filename: String): String? {
    val api = (context.applicationContext as SupportApp).graph.wa
    return try {
        val bytes = api.media(mediaId)
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "wa").apply { mkdirs() }
            File(dir, "${mediaId.takeLast(12)}-${filename.replace(Regex("[^\\w.-]"), "_")}").apply { writeBytes(bytes) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                filename,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        null
    } catch (e: ApiException) {
        e.message
    } catch (_: WaSignedOut) {
        "Sign in again"
    } catch (_: Exception) {
        "No app on this phone can open it"
    }
}

@Composable
private fun TemplateDialog(
    templates: List<WaTemplate>?,
    shopName: String,
    onDismiss: () -> Unit,
    onSend: (WaTemplate, List<String>) -> Unit,
) {
    var picked by remember { mutableStateOf<WaTemplate?>(null) }
    val values = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(picked?.name ?: "Send a template", style = fig(18, FontWeight.Bold)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val t = picked
                when {
                    templates == null -> Loading(Modifier.height(80.dp))
                    templates.isEmpty() -> Text("No approved templates on the WhatsApp account.", style = fig(14, color = Tokens.Muted))
                    t == null -> templates.forEach { x ->
                        Column(
                            Modifier.fillMaxWidth().border(1.dp, Tokens.CardBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    picked = x
                                    values.clear()
                                    repeat(x.params) { i -> values.add(if (i == 0) shopName else "") }
                                }
                                .padding(10.dp),
                        ) {
                            Text(x.name, style = fig(14, FontWeight.Bold))
                            Text(x.body, style = fig(13, color = Tokens.Muted), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    else -> {
                        values.forEachIndexed { i, v ->
                            OutlinedTextField(
                                value = v, onValueChange = { values[i] = it }, label = { Text("{{${i + 1}}}") },
                                singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = fig(15),
                            )
                        }
                        val filled = Regex("""\{\{(\d+)\}\}""").replace(t.body) { m -> values.getOrNull(m.groupValues[1].toInt() - 1)?.ifBlank { null } ?: m.value }
                        Text("Preview", style = fig(12, FontWeight.Bold, Tokens.Muted))
                        Text(filled, style = fig(14), modifier = Modifier.background(OutBubble, RoundedCornerShape(10.dp)).padding(10.dp))
                    }
                }
            }
        },
        confirmButton = {
            val t = picked
            if (t != null) {
                TextButton(onClick = { onSend(t, values.toList()) }, enabled = values.all { it.isNotBlank() }) {
                    Text("Send", style = fig(15, FontWeight.Bold, WaGreen))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (picked != null) picked = null else onDismiss() }) {
                Text(if (picked != null) "Back" else "Cancel", style = fig(15, FontWeight.SemiBold))
            }
        },
    )
}

/** The WhatsApp tab: sign in once, then the chats. */
@Composable
fun WhatsAppTab(graph: Graph, chats: ChatsViewModel, onOpen: (String) -> Unit) {
    val settings by graph.prefs.settings.collectAsStateWithLifecycle<com.dailyworks.mylaundry.support.data.Settings?>(null)
    val s = settings ?: return Loading()
    Box(Modifier.statusBarsPadding()) {
        if (s.waCookie == null) WaSignInScreen(graph) else ChatsScreen(chats, onOpen)
    }
}
