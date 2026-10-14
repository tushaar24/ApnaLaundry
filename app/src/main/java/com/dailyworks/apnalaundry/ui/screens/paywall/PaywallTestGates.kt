package com.dailyworks.apnalaundry.ui.screens.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.billing.BillingRepository
import com.dailyworks.apnalaundry.ui.theme.Tokens
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

// Paywall test (2026-10-14). Only brand-new accounts whose first login was on
// app 1.0.12+ are in it — the server decides (/billing/status paywallGroup) and
// it never changes for an account. Everyone else: these draw nothing.
const val PAYWALL_HARD_TRIAL = "hard_trial"
const val PAYWALL_FREE1 = "free1"

/**
 * "hard_trial": right after login, before setup — a paywall with no close
 * button (1-day trial for ₹2, refunded, then ₹199/month) over the app until the
 * trial is active. While a brand-new account's first billing check is in
 * flight it shows the loader, so setup never flashes first.
 *
 * Drawn OVER the NavHost (which stays composed): [onUnblocked] fires once the
 * account may use the app, so a new login moves on to setup only then.
 * [active] = logged in and (first check pending, or saved group is hard_trial).
 */
@Composable
fun HardTrialGate(active: Boolean, pending: Boolean, onUnblocked: () -> Unit) {
    if (!active) {
        LaunchedEffect(Unit) { onUnblocked() }
        return
    }
    val vm: PaywallViewModel = koinViewModel(key = "hard_trial_gate")
    val billing: BillingRepository = koinInject()
    val ui by vm.ui.collectAsStateWithLifecycle()
    var decided by remember { mutableStateOf(false) }
    var gated by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }

    // Already in a trial or plan (saved, end date not passed): straight in, no loader.
    LaunchedEffect(Unit) { if (!pending && billing.savedActive()) decided = true }
    LaunchedEffect(ui.loaded, ui.status) {
        if (decided || !ui.loaded) return@LaunchedEffect
        val s = ui.status
        offline = false
        when {
            s != null -> { gated = s.paywallGroup == PAYWALL_HARD_TRIAL && !s.hasAccess; decided = true }
            // Offline: a non-new account that already has access (saved) gets in.
            !pending && billing.savedActive() -> { gated = false; decided = true }
            else -> offline = true
        }
    }
    LaunchedEffect(decided, gated) { if (decided && !gated) onUnblocked() }
    LaunchedEffect(offline) { if (offline) Analytics.paywallCheckFailed("hard_trial") }

    when {
        !decided && offline -> Box(Modifier.fillMaxSize().background(Tokens.Bg)) { CheckFailed(onRetry = vm::refresh) }
        !decided -> ShopLoader()
        gated -> PaywallScreen(
            onClose = {},
            onDone = { gated = false },
            from = "hard_trial",
            hardGate = true,
            vm = vm,
        )
    }
}

/**
 * "free1": one free order, then the paywall (closable; ₹199/month, video) —
 * shown once, the first time all of these hold: the first order is saved, the
 * guide is finished or skipped (if it's skipped before that order, the paywall
 * waits for the order), there's no plan, and the owner is on a main screen
 * (Home / Customers / Earnings / Settings), so it never cuts into sending the
 * first bill. After that, New order itself asks for the plan (1 free order).
 */
@Composable
fun FirstOrderPaywall(eligible: Boolean, onMainScreen: Boolean) {
    val prefs: Prefs = koinInject()
    val billing: BillingRepository = koinInject()
    val shown by prefs.firstOrderPaywallShown.collectAsStateWithLifecycle(initialValue = true)
    var open by remember { mutableStateOf(false) }

    LaunchedEffect(eligible, onMainScreen, shown) {
        if (open || shown || !eligible || !onMainScreen) return@LaunchedEffect
        if (billing.savedActive()) return@LaunchedEffect
        prefs.setFirstOrderPaywallShown() // once, whatever they do with it
        open = true
    }
    if (open) {
        val vm: PaywallViewModel = koinViewModel(key = "first_order_paywall")
        PaywallScreen(
            onClose = { open = false },
            onDone = { open = false },
            from = "first_order",
            vm = vm,
        )
    }
}
