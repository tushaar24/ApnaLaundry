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
    const val SUBSCRIPTION = "subscription"
    const val BILL_DESIGN = "bill-design"
}

/** Thin typed wrapper over the NavController used by every screen. */
class AppNavigator(val nav: NavHostController) {
    fun back() { if (!nav.popBackStack()) nav.navigate(Routes.HOME) }

    fun openHome() = nav.navigate(Routes.HOME) {
        popUpTo(nav.graph.startDestinationId) { inclusive = false }
        launchSingleTop = true
    }

    fun openRates(from: String) = nav.navigate("rates/$from")
    // query: the customer search pre-filled (Home "New order for …").
    fun openNewOrder(editId: Int? = null, custId: String? = null, from: String = "home", query: String = "") =
        nav.navigate("new?edit=${editId ?: -1}&cust=${custId ?: ""}&from=$from&q=${android.net.Uri.encode(query)}")
    fun openBill(orderId: Int, from: String = "home") = nav.navigate("bill/$orderId?from=$from")
    fun openOrder(orderId: Int, from: String = "home") = nav.navigate("order/$orderId?from=$from")
    fun openCustomer(custId: String, from: String = "customers") = nav.navigate("customer/$custId?from=$from")
    fun openCustomers(filter: String = "all") = navigateTab("customers?filter=$filter")
    fun openEarnings() = navigateTab(Routes.EARNINGS)
    fun openSettings() = navigateTab(Routes.SETTINGS)
    fun openSubscription() = nav.navigate(Routes.SUBSCRIPTION) { launchSingleTop = true }
    fun openBillDesign() = nav.navigate(Routes.BILL_DESIGN) { launchSingleTop = true }

    fun selectTab(tab: NavTab) = when (tab) {
        NavTab.ORDERS -> navigateTab(Routes.HOME)
        NavTab.CUSTOMERS -> navigateTab("customers?filter=all")
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
