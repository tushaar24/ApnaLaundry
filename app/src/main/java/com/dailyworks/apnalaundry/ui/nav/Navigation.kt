package com.dailyworks.apnalaundry.ui.nav

import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.dailyworks.apnalaundry.ui.components.NavTab

object Routes {
    const val LOGIN = "login"
    const val RATES = "rates/{from}"
    const val HOME = "home"
    const val CUSTOMERS = "customers?filter={filter}"
    const val EARNINGS = "earnings"
    const val SETTINGS = "settings"
    const val NEW = "new?edit={edit}&cust={cust}&from={from}&q={q}"
    const val BILL = "bill/{orderId}?from={from}"
    const val ORDER = "order/{orderId}?from={from}"
    const val CUSTOMER = "customer/{custId}?from={from}"
    const val COMBINED_BILL = "combined-bill/{custId}"
    const val SUBSCRIPTION = "subscription"
    const val BILL_DESIGN = "bill-design"
    const val ADD_SHOP = "add-shop/{from}"
    const val ASSISTANT = "assistant?from={from}"
}

/** Thin typed wrapper over the NavController used by every screen. */
class AppNavigator(val nav: NavHostController) {
    fun back() { if (!nav.popBackStack()) nav.navigate(Routes.HOME) }

    fun openHome() = nav.navigate(Routes.HOME) {
        popUpTo(nav.graph.startDestinationId) { inclusive = false }
        launchSingleTop = true
    }

    fun openRates(from: String) = push("rates/$from")
    // query: the customer search pre-filled (Home "New order for …").
    fun openNewOrder(editId: Int? = null, custId: String? = null, from: String = "home", query: String = "") =
        push("new?edit=${editId ?: -1}&cust=${custId ?: ""}&from=$from&q=${android.net.Uri.encode(query)}")
    fun openBill(orderId: Int, from: String = "home") = push("bill/$orderId?from=$from")
    fun openOrder(orderId: Int, from: String = "home") = push("order/$orderId?from=$from")
    fun openCustomer(custId: String, from: String = "customers") = push("customer/$custId?from=$from")
    fun openCombinedBill(custId: String) = push("combined-bill/$custId")
    /** "Ask" — the assistant (only when the server has it on for this shop). */
    fun openAssistant(from: String = "home") = push("assistant?from=$from")

    /**
     * Opens a screen — once. A double tap on a slow phone (or a tap on the
     * screen that is fading out) used to open the same screen twice, so Back
     * landed on a copy of it. The same route again within 700ms is ignored.
     */
    private fun push(route: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (route == lastRoute && now - lastAt < 700) return
        lastRoute = route; lastAt = now
        nav.navigate(route)
    }

    private companion object {
        // Kept outside the instance: AppRoot makes a new AppNavigator on recomposition.
        var lastRoute = ""
        var lastAt = 0L
    }
    fun openCustomers(filter: String = "all") = navigateTab("customers?filter=$filter")
    fun openEarnings() = navigateTab(Routes.EARNINGS)
    fun openSettings() = navigateTab(Routes.SETTINGS)
    fun openSubscription() = nav.navigate(Routes.SUBSCRIPTION) { launchSingleTop = true }
    fun openBillDesign() = nav.navigate(Routes.BILL_DESIGN) { launchSingleTop = true }
    /** Add a shop (paid — the screen shows the paywall first without a plan). */
    fun openAddShop(from: String) = nav.navigate("add-shop/$from") { launchSingleTop = true }

    fun selectTab(tab: NavTab) = when (tab) {
        NavTab.ORDERS -> navigateTab(Routes.HOME)
        NavTab.CUSTOMERS -> navigateTab("customers?filter=all")
        NavTab.ASK -> openAssistant("nav") // its own screen (back returns here), not a tab page
        NavTab.EARNINGS -> navigateTab(Routes.EARNINGS)
        NavTab.SETTINGS -> navigateTab(Routes.SETTINGS)
    }

    private fun navigateTab(route: String) = nav.navigate(route) {
        popUpTo(Routes.HOME) { inclusive = false; saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun toHomeAfterSetup() = nav.navigate(Routes.HOME) {
        popUpTo(0) { inclusive = true }
    }

    fun toRatesSetup() = nav.navigate("rates/setup") {
        popUpTo(0) { inclusive = true }
    }
}

fun NavController.argInt(key: String, default: Int = -1): Int =
    currentBackStackEntry?.arguments?.getString(key)?.toIntOrNull() ?: default
