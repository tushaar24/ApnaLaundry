package com.dailyworks.apnalaundry.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.dailyworks.apnalaundry.ui.screens.customer.CustomerScreen
import com.dailyworks.apnalaundry.ui.screens.customers.CustomersScreen
import com.dailyworks.apnalaundry.ui.screens.earnings.EarningsScreen
import com.dailyworks.apnalaundry.ui.screens.home.HomeScreen
import com.dailyworks.apnalaundry.ui.screens.login.LoginScreen
import com.dailyworks.apnalaundry.ui.screens.neworder.NewOrderScreen
import com.dailyworks.apnalaundry.ui.screens.order.OrderDetailScreen
import com.dailyworks.apnalaundry.ui.screens.paywall.BillingGate
import com.dailyworks.apnalaundry.ui.screens.paywall.PaywallScreen
import com.dailyworks.apnalaundry.ui.screens.rates.RatesScreen
import com.dailyworks.apnalaundry.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    LaunchedEffect(li) {
        if (!li && navController.currentDestination?.route?.let { it != Routes.LOGIN } == true) {
            navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
        }
    }

    // Trial variant is a hard gate inside the authed app; login/setup aren't gated.
    // "+ Continue with new order" on the gate's Done screen navigates once the
    // NavHost below has composed (never during the gate itself).
    var pendingNewOrder by remember { mutableStateOf(false) }
    BillingGate(enabled = li && sd, onContinueNewOrder = { pendingNewOrder = true }) {
    LaunchedEffect(pendingNewOrder) {
        if (pendingNewOrder) {
            pendingNewOrder = false
            navigator.openNewOrder(from = "paywall")
        }
    }
    NavHost(navController = navController, startDestination = start) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                // An existing account restored from the server skips setup
                // (AuthRepository set setupDone after the initial pull).
                scope.launch {
                    if (prefs.setupDone.first()) navigator.toHomeAfterSetup() else navigator.toRatesSetup()
                }
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
                        scope.launch { prefs.setSetupDone(true) }
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
            SettingsScreen(shopVm, navigator, onLogout = {
                scope.launch {
                    // Pushes unsynced work first; refuses to log out (data
                    // would be lost) if that fails.
                    authRepo.logout()
                        .onSuccess {
                            navigator.nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                        }
                        .onFailure { shopVm.showInfo(it.message ?: "Couldn't log out — try again") }
                }
            })
        }

        composable(
            Routes.NEW,
            arguments = listOf(
                navArgument("edit") { type = NavType.StringType; defaultValue = "-1" },
                navArgument("cust") { type = NavType.StringType; defaultValue = "" },
                navArgument("from") { type = NavType.StringType; defaultValue = "home" },
            ),
        ) { entry ->
            val edit = entry.arguments?.getString("edit")?.toIntOrNull()?.takeIf { it > 0 }
            val cust = entry.arguments?.getString("cust")?.takeIf { it.isNotBlank() }
            val from = entry.arguments?.getString("from") ?: "home"
            NewOrderScreen(shopVm, navigator, editId = edit, presetCustId = cust, from = from)
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

        // Soft paywall route (free-orders variant: new-order block / banner / settings).
        composable(
            Routes.PAYWALL,
            arguments = listOf(navArgument("reason") { type = NavType.StringType; defaultValue = "limit" }),
        ) { entry ->
            PaywallScreen(
                reason = entry.arguments?.getString("reason") ?: "limit",
                onClose = { navigator.back() },
                onDone = { continuing -> if (continuing) navigator.openNewOrder(from = "paywall") else navigator.openHome() },
            )
        }
    }
    }
}
