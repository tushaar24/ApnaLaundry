package com.dailyworks.mylaundry.support

import android.Manifest
import android.content.ActivityNotFoundException
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as SupportApp).graph
        setContent { SupportTheme { SupportRoot(graph) } }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("shops", "Shops", Icons.Outlined.Storefront),
    Tab("calls", "Calls", Icons.Outlined.Call),
    Tab("tags", "Tags", Icons.AutoMirrored.Outlined.Label),
    Tab("settings", "Settings", Icons.Outlined.Settings),
)

@Composable
private fun SupportRoot(graph: Graph) {
    // null until DataStore answers, so setup doesn't flash for a configured agent.
    val agent by remember { graph.prefs.settings }.collectAsStateWithLifecycle<com.dailyworks.mylaundry.support.data.Settings?>(null)
    val a = agent
    when {
        a == null -> Loading(Modifier.background(Tokens.Bg))
        a.agentName.isBlank() -> Box(Modifier.fillMaxSize().background(Tokens.Bg).statusBarsPadding()) {
            SettingsScreen(graph, isSetup = true, onDone = {})
        }
        else -> MainNav(graph)
    }
}

@Composable
private fun MainNav(graph: Graph) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // ── placing a call (shared by the list and the detail screen) ──
    var waitingForPermission by remember { mutableStateOf<OwnerSummary?>(null) }

    fun place(owner: OwnerSummary) {
        scope.launch {
            try {
                val label = owner.shopName ?: owner.name ?: "+91 ${owner.phone}"
                val pending = graph.calls.start(owner.id, owner.phone, label)
                // Open the notes screen first so returning from the dialer lands there.
                nav.navigate("call/${pending.callId}") { launchSingleTop = true }
                context.startActivity(graph.calls.dialIntent(owner.phone))
            } catch (e: ApiException) {
                snackbar.showSnackbar(e.message ?: "Couldn't start the call")
            } catch (e: ActivityNotFoundException) {
                snackbar.showSnackbar("This phone can't place calls")
            }
        }
    }

    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val owner = waitingForPermission
        waitingForPermission = null
        if (granted && owner != null) place(owner)
        else if (!granted) scope.launch { snackbar.showSnackbar("Allow phone calls to call from the app") }
    }

    val onCall: (OwnerSummary) -> Unit = { owner ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            place(owner)
        } else {
            waitingForPermission = owner
            callPermission.launch(Manifest.permission.CALL_PHONE)
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
                            icon = { Icon(t.icon, null) },
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
                    )
                }
            }
            composable("calls") {
                Box(Modifier.statusBarsPadding()) {
                    CallsScreen(viewModel { CallsViewModel(graph) }, onOpenCall = { nav.navigate("call/$it") })
                }
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
                )
            }
            composable("call/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                CallNotesScreen(
                    vm = viewModel { CallNotesViewModel(graph, id) },
                    onBack = { if (!nav.popBackStack()) nav.navigate("shops") },
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
