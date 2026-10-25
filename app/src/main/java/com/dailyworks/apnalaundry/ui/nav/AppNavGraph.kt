package com.dailyworks.apnalaundry.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dailyworks.apnalaundry.ui.screens.paywall.FirstOrderPaywall
import com.dailyworks.apnalaundry.ui.screens.paywall.HardTrialGate
import com.dailyworks.apnalaundry.ui.screens.paywall.PAYWALL_FREE1
import com.dailyworks.apnalaundry.ui.screens.paywall.PAYWALL_HARD_TRIAL
import com.dailyworks.apnalaundry.ui.tour.TourStage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dailyworks.apnalaundry.data.AuthRepository
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.screens.bill.BillScreen
import com.dailyworks.apnalaundry.ui.screens.customer.CombinedBillScreen
import com.dailyworks.apnalaundry.ui.screens.customer.CustomerScreen
import com.dailyworks.apnalaundry.ui.screens.customers.CustomersScreen
import com.dailyworks.apnalaundry.ui.screens.earnings.EarningsScreen
import com.dailyworks.apnalaundry.ui.screens.home.HomeScreen
import com.dailyworks.apnalaundry.ui.screens.login.LoginScreen
import com.dailyworks.apnalaundry.ui.screens.neworder.NewOrderScreen
import com.dailyworks.apnalaundry.ui.screens.order.OrderDetailScreen
import com.dailyworks.apnalaundry.ui.screens.onboarding.BillDesignScreen
import com.dailyworks.apnalaundry.ui.screens.paywall.OrderLimitGate
import com.dailyworks.apnalaundry.ui.screens.paywall.PaywallScreen
import com.dailyworks.apnalaundry.ui.screens.rates.RatesScreen
import com.dailyworks.apnalaundry.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.dailyworks.apnalaundry.data.billing.BillingRepository
import com.dailyworks.apnalaundry.data.experiments.ExperimentsRepository
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.push.DeepLinks
import com.dailyworks.apnalaundry.core.tr
import org.koin.compose.koinInject

@Composable
fun AppNavGraph(navController: NavHostController, navigator: AppNavigator, shopVm: ShopViewModel) {
    val prefs: Prefs = koinInject()
    val authRepo: AuthRepository = koinInject()
    val scope = rememberCoroutineScope()
    val loggedIn by prefs.loggedIn.collectAsStateWithLifecycle(initialValue = null)
    val setupDone by prefs.setupDone.collectAsStateWithLifecycle(initialValue = null)

    val li = loggedIn ?: return
    val sd = setupDone ?: return

    val start = remember {
        when {
            !li -> Routes.LOGIN
            !sd -> "rates/setup"
            else -> Routes.HOME
        }
    }

    // If the session dies mid-use (refresh token revoked/expired — TokenManager
    // flips loggedIn off), return to login. Local data is kept; logging back
    // into the same account merges cleanly.
    // A push deep link (mylaundry://…): open its screen once the owner is in.
    val link by DeepLinks.pending.collectAsStateWithLifecycle()
    LaunchedEffect(link, li, sd) {
        if (link == null || !li || !sd) return@LaunchedEffect
        val screen = DeepLinks.consume() ?: return@LaunchedEffect
        Analytics.deepLinkOpened(screen)
        when (screen) {
            "new-order" -> navigator.openNewOrder(from = "push")
            "customers" -> navigator.openCustomers()
            "earnings" -> navigator.openEarnings()
            "subscription" -> navigator.openSubscription()
            "assistant" -> if (prefs.assistantOn.first()) navigator.openAssistant("push") else navigator.openHome()
            else -> navigator.openHome()
        }
    }

    // Keep the saved subscription fresh in the background (app start / login):
    // the free-orders gate on New order reads it instead of waiting on the network.
    val billing: BillingRepository = koinInject()
    LaunchedEffect(li) { if (li) billing.refreshSaved() }
    // A/B groups (the guide, the first-customer flow): fetched at app start / right after login, while setup runs.
    val experiments: ExperimentsRepository = koinInject()
    LaunchedEffect(li) { if (li) { experiments.restore(); experiments.refresh() } }
    // The assistant ("Ask" on Home): on only where the server says so (an older server: off).
    val assistantApi: com.dailyworks.apnalaundry.data.assistant.AssistantApi = koinInject()
    LaunchedEffect(li) { if (li) assistantApi.refreshEnabled() }
    // The owner's shops (multi-shop): a shop added on another phone shows up here.
    LaunchedEffect(li) { if (li) shopVm.refreshShops() }

    LaunchedEffect(li) {
        if (!li && navController.currentDestination?.route?.let { it != Routes.LOGIN } == true) {
            navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
        }
    }

    // Paywall test (2026-10-14): only brand-new accounts whose first login was on
    // 1.0.12+ (server-decided, /billing/status paywallGroup). Everyone else: no
    // paywall after login (login -> setup -> app), and a shop pays only when its
    // free orders run out — OrderLimitGate on New order. Both gates draw over the
    // NavHost and draw nothing outside the test.
    val paywallGroup by prefs.paywallGroup.collectAsStateWithLifecycle(initialValue = null)
    val paywallPending by prefs.paywallCheckPending.collectAsStateWithLifecycle(initialValue = false)
    val tourStage by shopVm.tour.collectAsStateWithLifecycle()
    val shopState by shopVm.state.collectAsStateWithLifecycle()
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    // A brand-new login waits for the hard-trial check before moving on to setup.
    var navAfterGate by remember { mutableStateOf(false) }
    val afterLogin = {
        // An existing account restored from the server skips setup
        // (AuthRepository set setupDone after the initial pull).
        scope.launch { if (prefs.setupDone.first()) navigator.toHomeAfterSetup() else navigator.toRatesSetup() }
    }

    Box(Modifier.fillMaxSize()) {
    NavHost(navController = navController, startDestination = start) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                scope.launch { if (prefs.paywallCheckPending.first()) navAfterGate = true else afterLogin() }
            })
        }

        composable(
            Routes.RATES,
            arguments = listOf(navArgument("from") { type = NavType.StringType; defaultValue = "setup" }),
        ) { entry ->
            val from = entry.arguments?.getString("from") ?: "setup"
            RatesScreen(
                shopVm = shopVm, from = from,
                onDone = {
                    if (from == "setup") {
                        // First-time setup of a new shop: the one time the guide starts by itself.
                        scope.launch { prefs.setGuideNewShop(true); prefs.setSetupDone(true) }
                        navigator.toHomeAfterSetup()
                    } else navigator.back()
                },
                onBack = { if (from == "setup") Unit else navigator.back() },
            )
        }

        composable(Routes.HOME) { HomeScreen(shopVm, navigator) }

        composable(
            Routes.CUSTOMERS,
            arguments = listOf(navArgument("filter") { type = NavType.StringType; defaultValue = "all" }),
        ) { entry ->
            CustomersScreen(shopVm, navigator, entry.arguments?.getString("filter") ?: "all")
        }

        composable(Routes.EARNINGS) { EarningsScreen(shopVm, navigator) }

        composable(Routes.SETTINGS) {
            SettingsScreen(shopVm, navigator, onLogout = { onFailed ->
                scope.launch {
                    // Pushes unsynced work first; refuses to log out (data
                    // would be lost) if that fails.
                    authRepo.logout()
                        .onSuccess {
                            navigator.nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                        }
                        .onFailure { shopVm.showInfo(it.message ?: tr("Couldn't log out — try again")); onFailed() }
                }
            })
        }

        composable(
            Routes.NEW,
            arguments = listOf(
                navArgument("edit") { type = NavType.StringType; defaultValue = "-1" },
                navArgument("cust") { type = NavType.StringType; defaultValue = "" },
                navArgument("from") { type = NavType.StringType; defaultValue = "home" },
                navArgument("q") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val edit = entry.arguments?.getString("edit")?.toIntOrNull()?.takeIf { it > 0 }
            val cust = entry.arguments?.getString("cust")?.takeIf { it.isNotBlank() }
            val from = entry.arguments?.getString("from") ?: "home"
            val q = entry.arguments?.getString("q") ?: ""
            // Editing an existing order is never gated; a new one may need a plan.
            if (edit != null) NewOrderScreen(shopVm, navigator, editId = edit, presetCustId = cust, from = from, presetQuery = q)
            else OrderLimitGate(onBack = { navigator.back() }) {
                NewOrderScreen(shopVm, navigator, editId = null, presetCustId = cust, from = from, presetQuery = q)
            }
        }

        composable(
            Routes.BILL,
            arguments = listOf(
                navArgument("orderId") { type = NavType.IntType },
                navArgument("from") { type = NavType.StringType; defaultValue = "home" },
            ),
        ) { entry ->
            BillScreen(shopVm, navigator, orderId = entry.arguments?.getInt("orderId") ?: 0, from = entry.arguments?.getString("from") ?: "home")
        }

        composable(
            Routes.ORDER,
            arguments = listOf(
                navArgument("orderId") { type = NavType.IntType },
                navArgument("from") { type = NavType.StringType; defaultValue = "home" },
            ),
        ) { entry ->
            OrderDetailScreen(shopVm, navigator, orderId = entry.arguments?.getInt("orderId") ?: 0, from = entry.arguments?.getString("from") ?: "home")
        }

        composable(
            Routes.CUSTOMER,
            arguments = listOf(
                navArgument("custId") { type = NavType.StringType },
                navArgument("from") { type = NavType.StringType; defaultValue = "customers" },
            ),
        ) { entry ->
            CustomerScreen(shopVm, navigator, custId = entry.arguments?.getString("custId") ?: "", from = entry.arguments?.getString("from") ?: "customers")
        }

        // Customer → Combined bill: many orders of one customer on one bill.
        composable(
            Routes.COMBINED_BILL,
            arguments = listOf(navArgument("custId") { type = NavType.StringType }),
        ) { entry ->
            CombinedBillScreen(shopVm, navigator, custId = entry.arguments?.getString("custId") ?: "")
        }

        // Settings → Subscription: manage/cancel the active plan.
        // Settings → Bill design & details: onboarding "Your bill" in edit mode.
        composable(Routes.BILL_DESIGN) {
            BillDesignScreen(shopVm, editing = true, onBack = { navigator.back() }, onDone = { navigator.back() })
        }

        composable(
            Routes.ADD_SHOP,
            arguments = listOf(navArgument("from") { type = NavType.StringType; defaultValue = "home" }),
        ) { entry ->
            com.dailyworks.apnalaundry.ui.screens.shops.AddShopScreen(shopVm, navigator, entry.arguments?.getString("from") ?: "home")
        }

        composable(
            Routes.ASSISTANT,
            arguments = listOf(navArgument("from") { type = NavType.StringType; defaultValue = "home" }),
        ) { entry ->
            com.dailyworks.apnalaundry.ui.screens.assistant.AssistantScreen(shopVm, navigator, entry.arguments?.getString("from") ?: "home")
        }

        composable(Routes.SUBSCRIPTION) {
            PaywallScreen(
                onClose = { navigator.back() },
                onDone = { navigator.openHome() },
                from = "settings",
            )
        }
    }

    // hard_trial: the no-close paywall right after login (before setup) until the trial is active.
    HardTrialGate(
        active = li && (paywallPending || paywallGroup == PAYWALL_HARD_TRIAL),
        pending = paywallPending,
        onUnblocked = { if (navAfterGate) { navAfterGate = false; afterLogin() } },
    )
    // free1: the paywall once, after the first order + the guide finished or skipped.
    FirstOrderPaywall(
        eligible = li && paywallGroup == PAYWALL_FREE1 && tourStage == TourStage.DONE && shopState.orders.isNotEmpty(),
        onMainScreen = route == Routes.HOME || route == Routes.CUSTOMERS || route == Routes.EARNINGS || route == Routes.SETTINGS,
    )
    }
}
