package com.dailyworks.mylaundry.support

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dailyworks.mylaundry.support.data.ApiException
import com.dailyworks.mylaundry.support.data.IncomingNotifier
import com.dailyworks.mylaundry.support.data.QueueEntry
import com.dailyworks.mylaundry.support.data.QueueItemDto
import com.dailyworks.mylaundry.support.ui.screens.AddToQueueSheet
import com.dailyworks.mylaundry.support.ui.screens.QueueViewModel
import com.dailyworks.mylaundry.support.ui.screens.QueuesScreen
import com.dailyworks.mylaundry.support.ui.screens.QueuesViewModel
import com.dailyworks.mylaundry.support.data.ReminderAlarms
import com.dailyworks.mylaundry.support.data.ReminderDto
import com.dailyworks.mylaundry.support.ui.screens.QueueScreen
import com.dailyworks.mylaundry.support.ui.screens.RemindersScreen
import com.dailyworks.mylaundry.support.ui.screens.RemindersViewModel
import com.dailyworks.mylaundry.support.data.WaNotifier
import com.dailyworks.mylaundry.support.data.WaPush
import com.dailyworks.mylaundry.support.ui.screens.ChatScreen
import com.dailyworks.mylaundry.support.ui.screens.ChatViewModel
import com.dailyworks.mylaundry.support.ui.screens.ChatsViewModel
import com.dailyworks.mylaundry.support.ui.screens.WhatsAppTab
import com.dailyworks.mylaundry.support.data.OwnerSummary
import com.dailyworks.mylaundry.support.ui.components.Loading
import com.dailyworks.mylaundry.support.ui.screens.CallNotesScreen
import com.dailyworks.mylaundry.support.ui.screens.CallNotesViewModel
import com.dailyworks.mylaundry.support.ui.screens.CallsScreen
import com.dailyworks.mylaundry.support.ui.screens.CallsViewModel
import com.dailyworks.mylaundry.support.ui.screens.SettingsScreen
import com.dailyworks.mylaundry.support.ui.screens.ShopDetailScreen
import com.dailyworks.mylaundry.support.ui.screens.ShopDetailViewModel
import com.dailyworks.mylaundry.support.ui.screens.ShopsScreen
import com.dailyworks.mylaundry.support.ui.screens.ShopsViewModel
import com.dailyworks.mylaundry.support.ui.screens.TagsScreen
import com.dailyworks.mylaundry.support.ui.screens.TagsViewModel
import com.dailyworks.mylaundry.support.ui.theme.SupportTheme
import com.dailyworks.mylaundry.support.ui.theme.Tokens
import com.dailyworks.mylaundry.support.ui.theme.fig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // A notification was tapped: "… is calling" opens that call's notes, a WhatsApp
    // message opens its chat.
    private val opens = Opens()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        opens.take(intent)
        val graph = (application as SupportApp).graph
        setContent { SupportTheme { SupportRoot(graph, opens) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        opens.take(intent)
    }
}

/** What a tapped notification asks to open. */
class Opens {
    val call = MutableStateFlow<String?>(null)
    val chat = MutableStateFlow<String?>(null)
    val shop = MutableStateFlow<String?>(null)

    fun take(intent: Intent?) {
        intent?.getStringExtra(ReminderAlarms.EXTRA_OPEN_SHOP)?.takeIf { it.isNotBlank() }?.let { shop.value = it }
        intent?.getStringExtra(IncomingNotifier.EXTRA_CALL_ID)?.let { call.value = it }
        intent?.getStringExtra(WaNotifier.EXTRA_WA_ID)?.let { chat.value = it }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("shops", "Shops", Icons.Outlined.Storefront),
    Tab("calls", "Calls", Icons.Outlined.Call),
    Tab("wa", "WhatsApp", Icons.Outlined.Forum),
    Tab("tags", "Tags", Icons.AutoMirrored.Outlined.Label),
    Tab("settings", "Settings", Icons.Outlined.Settings),
)

@Composable
private fun SupportRoot(graph: Graph, opens: Opens) {
    // null until DataStore answers, so setup doesn't flash for a configured agent.
    val agent by remember { graph.prefs.settings }.collectAsStateWithLifecycle<com.dailyworks.mylaundry.support.data.Settings?>(null)
    val a = agent
    when {
        a == null -> Loading(Modifier.background(Tokens.Bg))
        // The phone number came with calls through MyLaundry (7 Nov 2026): agents set up
        // before that see the setup once more to add it.
        a.agentName.isBlank() || a.agentPhone.length != 10 -> Box(Modifier.fillMaxSize().background(Tokens.Bg).statusBarsPadding()) {
            SettingsScreen(graph, isSetup = true, onDone = {})
        }
        else -> MainNav(graph, opens)
    }
}

@Composable
private fun MainNav(graph: Graph, opens: Opens) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // ── placing a call (shops, a shop, a reminder, the queue) ──
    /**
     * Who to call; [onPlaced] gets the new call's id (to tick off a reminder / queue item);
     * [onFailed]: the call wasn't placed (a queue gives the person back).
     */
    class Dial(val userId: String, val phone: String, val label: String, val onFailed: () -> Unit = {}, val onPlaced: (String) -> Unit = {})
    var waitingForPermission by remember { mutableStateOf<Dial?>(null) }

    // Through the phone's dialer: the app finds and uploads the dialer's recording.
    fun place(d: Dial) {
        scope.launch {
            try {
                val pending = graph.calls.start(d.userId, d.phone, d.label)
                d.onPlaced(pending.callId)
                // Open the notes screen first so returning from the dialer lands there.
                nav.navigate("call/${pending.callId}") { launchSingleTop = true }
                context.startActivity(graph.calls.dialIntent(d.phone))
            } catch (e: ApiException) {
                d.onFailed()
                snackbar.showSnackbar(e.message ?: "Couldn't start the call")
            } catch (e: ActivityNotFoundException) {
                d.onFailed()
                snackbar.showSnackbar("This phone can't place calls")
            }
        }
    }

    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val d = waitingForPermission
        waitingForPermission = null
        if (granted && d != null) place(d)
        else if (!granted) {
            d?.onFailed?.invoke()
            scope.launch { snackbar.showSnackbar("Allow phone calls to call from the app") }
        }
    }

    fun placeWithDialer(d: Dial) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            place(d)
        } else {
            waitingForPermission = d
            callPermission.launch(Manifest.permission.CALL_PHONE)
        }
    }

    // Through MyLaundry: the server rings this phone, then the owner, and records +
    // transcribes the call. Only while that isn't set up on the server (503) does the
    // dialer take over.
    var placing by remember { mutableStateOf(false) }
    fun startCall(d: Dial) {
        if (placing) return
        placing = true
        scope.launch {
            try {
                val call = graph.calls.bridge(d.userId)
                d.onPlaced(call.id)
                nav.navigate("call/${call.id}") { launchSingleTop = true }
            } catch (e: ApiException) {
                if (e.status == 503) placeWithDialer(d)
                else {
                    d.onFailed()
                    snackbar.showSnackbar(e.message ?: "Couldn't place the call")
                }
            } finally {
                placing = false
            }
        }
    }
    val onCall: (OwnerSummary) -> Unit = { o -> startCall(Dial(o.id, o.phone, o.shopName ?: o.name ?: "+91 ${o.phone}")) }

    // A reminder's Call: the reminder is done once the call is placed.
    fun reminderDone(reminderId: String, callId: String) = scope.launch {
        runCatching { graph.api.updateReminder(reminderId, done = true, callId = callId) }
        ReminderAlarms.syncSoon(context)
    }
    val onCallReminder: (ReminderDto) -> Unit = { r ->
        startCall(Dial(r.userId, r.phone.orEmpty(), r.title) { callId -> reminderDone(r.id, callId) })
    }

    // Call queues (1.10): on the server, shared by every agent. Adding people asks which queue —
    // one there is, or a new one (title, description optional).
    var adding by remember { mutableStateOf<List<QueueEntry>?>(null) }
    var addingBusy by remember { mutableStateOf(false) }
    fun startQueue(entries: List<QueueEntry>) {
        if (entries.isNotEmpty()) adding = entries.distinctBy { it.reminderId ?: it.userId }
    }
    adding?.let { entries ->
        AddToQueueSheet(
            graph, count = entries.size, busy = addingBusy,
            onDismiss = { adding = null },
            onPick = { q ->
                addingBusy = true
                scope.launch {
                    try {
                        graph.api.addToQueue(q.id, entries)
                        adding = null
                        nav.navigate("queue/${q.id}") { launchSingleTop = true }
                    } catch (e: ApiException) {
                        snackbar.showSnackbar(e.message ?: "Couldn't add to the queue")
                    } finally {
                        addingBusy = false
                    }
                }
            },
            onNew = { title, description ->
                addingBusy = true
                scope.launch {
                    try {
                        val q = graph.api.createQueue(title, description, graph.prefs.current().agentName, entries)
                        adding = null
                        nav.navigate("queue/${q.id}") { launchSingleTop = true }
                    } catch (e: ApiException) {
                        snackbar.showSnackbar(e.message ?: "Couldn't make the queue")
                    } finally {
                        addingBusy = false
                    }
                }
            },
        )
    }
    // A person in a queue: take them (another agent calling them already → say so), place the
    // call, tell the server it's placed (a reminder they came from is then done), or give them
    // back if the call couldn't be placed.
    fun callFromQueue(queueId: String, item: QueueItemDto, refresh: () -> Unit) {
        if (placing) return
        scope.launch {
            val agent = graph.prefs.current().agentName
            try {
                graph.api.startQueueItem(queueId, item.id, agent)
            } catch (e: ApiException) {
                refresh()
                snackbar.showSnackbar(e.message ?: "Couldn't start the call")
                return@launch
            }
            startCall(
                Dial(
                    item.userId, item.phone, item.label,
                    onFailed = { scope.launch { runCatching { graph.api.releaseQueueItem(queueId, item.id) }; refresh() } },
                ) { callId -> scope.launch { runCatching { graph.api.placedQueueItem(queueId, item.id, callId, agent) } } },
            )
        }
    }

    // The queue this phone kept itself (1.4–1.9) moves to the server once, as it was — who was
    // called, skipped or didn't pick up — as "<agent>'s queue". The phone's copy goes only after
    // the server has it (offline: next start).
    LaunchedEffect(Unit) {
        val local = graph.prefs.queue.first()
        if (local.isEmpty()) return@LaunchedEffect
        val agent = graph.prefs.current().agentName
        runCatching {
            graph.api.createQueue(
                "$agent's queue", "Moved from this phone", agent,
                local.map { QueueEntry(it.userId, it.reminderId, it.note, it.status, it.callId) }, keepState = true,
            )
        }.onSuccess { graph.prefs.setQueue(emptyList()) }
    }

    val callToOpen by opens.call.collectAsStateWithLifecycle()
    LaunchedEffect(callToOpen) {
        val id = callToOpen ?: return@LaunchedEffect
        opens.call.value = null
        nav.navigate("call/$id") { launchSingleTop = true }
    }
    val shopToOpen by opens.shop.collectAsStateWithLifecycle()
    LaunchedEffect(shopToOpen) {
        val id = shopToOpen ?: return@LaunchedEffect
        opens.shop.value = null
        nav.navigate("shop/$id") { launchSingleTop = true }
    }
    // Reminders: alarms for this agent's, kept in step with the server.
    LaunchedEffect(Unit) {
        ReminderAlarms.keepSynced(context)
        ReminderAlarms.syncSoon(context)
    }
    val chatToOpen by opens.chat.collectAsStateWithLifecycle()
    LaunchedEffect(chatToOpen) {
        val id = chatToOpen ?: return@LaunchedEffect
        opens.chat.value = null
        nav.navigate("wa/$id") { launchSingleTop = true }
    }

    // WhatsApp: the notification channel (also used by pushes Android shows itself), this
    // phone registered for pushes, and the unread-chats badge on the tab.
    val waCookie by remember { graph.prefs.settings.map { it.waCookie } }.collectAsStateWithLifecycle(null)
    var waUnread by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        WaNotifier.ensureChannel(context)
        com.dailyworks.mylaundry.support.data.PaymentsNotifier.ensureChannel(context)
    }
    LaunchedEffect(waCookie) {
        if (waCookie == null) { waUnread = 0; return@LaunchedEffect }
        WaPush.register(context)
        while (true) {
            runCatching { graph.wa.unread() }.onSuccess { waUnread = it }
            delay(30_000)
        }
    }

    // A call still pending when the app comes back (even after process death):
    // take the agent to its notes so it gets finalized and the recording uploaded.
    LifecycleResumeEffect(Unit) {
        scope.launch {
            val p = graph.prefs.currentPending() ?: return@launch
            if (nav.currentBackStackEntry?.arguments?.getString("id") != p.callId) {
                nav.navigate("call/${p.callId}") { launchSingleTop = true }
            }
        }
        onPauseOrDispose { }
    }

    val onTab = TABS.any { it.route == route }
    Scaffold(
        containerColor = Tokens.Bg,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (onTab) {
                NavigationBar(containerColor = Tokens.Card) {
                    TABS.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.switchTab(t.route) },
                            icon = {
                                if (t.route == "wa" && waUnread > 0) {
                                    BadgedBox(badge = { Badge { Text("$waUnread") } }) { Icon(t.icon, null) }
                                } else {
                                    Icon(t.icon, null)
                                }
                            },
                            label = { Text(t.label, style = fig(12, FontWeight.SemiBold)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Tokens.Blue, selectedTextColor = Tokens.Blue,
                                indicatorColor = Tokens.BlueLight,
                            ),
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = "shops", modifier = Modifier.padding(inner)) {
            composable("shops") {
                Box(Modifier.statusBarsPadding()) {
                    ShopsScreen(
                        vm = viewModel { ShopsViewModel(graph) },
                        onOpen = { id -> nav.navigate("shop/$id") },
                        onCall = onCall,
                        onQueue = { owners -> startQueue(owners.map { o -> QueueEntry(o.id) }) },
                        onReminders = { nav.navigate("reminders") { launchSingleTop = true } },
                    )
                }
            }
            composable("calls") {
                Box(Modifier.statusBarsPadding()) {
                    CallsScreen(
                        viewModel { CallsViewModel(graph, context.applicationContext) },
                        onOpenCall = { nav.navigate("call/$it") },
                        onOpenShop = { nav.navigate("shop/$it") },
                        onCallReminder = onCallReminder,
                        onQueueReminders = { rs ->
                            startQueue(rs.map { r -> QueueEntry(r.userId, reminderId = r.id, note = r.note.ifBlank { null }) })
                        },
                        onOpenQueue = { nav.navigate("queues") { launchSingleTop = true } },
                        onAllReminders = { nav.navigate("reminders") { launchSingleTop = true } },
                    )
                }
            }
            composable("reminders") {
                Box(Modifier.statusBarsPadding()) {
                    RemindersScreen(
                        viewModel { RemindersViewModel(graph, context.applicationContext) },
                        onBack = { if (!nav.popBackStack()) nav.navigate("shops") },
                        onOpenShop = { nav.navigate("shop/$it") },
                        onCallReminder = onCallReminder,
                        onQueueReminders = { rs ->
                            startQueue(rs.map { r -> QueueEntry(r.userId, reminderId = r.id, note = r.note.ifBlank { null }) })
                        },
                    )
                }
            }
            composable("queues") {
                Box(Modifier.statusBarsPadding()) {
                    QueuesScreen(
                        viewModel { QueuesViewModel(graph) },
                        onBack = { if (!nav.popBackStack()) nav.navigate("calls") },
                        onOpen = { nav.navigate("queue/$it") },
                    )
                }
            }
            composable("queue/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val agent by remember { graph.prefs.settings.map { it.agentName } }.collectAsStateWithLifecycle("")
                Box(Modifier.statusBarsPadding()) {
                    QueueScreen(
                        viewModel(key = "queue-$id") { QueueViewModel(graph, id) },
                        placing = placing,
                        agentName = agent,
                        onBack = { if (!nav.popBackStack()) nav.navigate("queues") },
                        onCall = { item, refresh -> callFromQueue(id, item, refresh) },
                        onOpenShop = { nav.navigate("shop/$it") },
                    )
                }
            }
            composable("wa") {
                WhatsAppTab(graph, viewModel { ChatsViewModel(graph) }, onOpen = { nav.navigate("wa/$it") })
            }
            composable("wa/{waId}") { entry ->
                val waId = entry.arguments?.getString("waId").orEmpty()
                ChatScreen(
                    vm = viewModel(key = "chat-$waId") { ChatViewModel(graph, waId) },
                    onBack = { if (!nav.popBackStack()) nav.navigate("wa") },
                    onOpenShop = { nav.navigate("shop/$it") },
                )
            }
            composable("tags") {
                Box(Modifier.statusBarsPadding()) { TagsScreen(viewModel { TagsViewModel(graph) }) }
            }
            composable("settings") {
                Box(Modifier.statusBarsPadding()) { SettingsScreen(graph, isSetup = false, onDone = {}) }
            }
            composable("shop/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                ShopDetailScreen(
                    vm = viewModel { ShopDetailViewModel(graph, id) },
                    onBack = { nav.popBackStack() },
                    onCall = onCall,
                    onOpenCall = { nav.navigate("call/$it") },
                    onWhatsApp = { nav.navigate("wa/$it") },
                    onCallReminder = onCallReminder,
                )
            }
            composable("call/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                CallNotesScreen(
                    vm = viewModel { CallNotesViewModel(graph, id) },
                    onBack = { if (!nav.popBackStack()) nav.navigate("shops") },
                    onOpenShop = { userId -> nav.navigate("shop/$userId") },
                )
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo("shops") { saveState = true }
    launchSingleTop = true
    restoreState = true
}
