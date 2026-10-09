package com.dailyworks.apnalaundry.ui.screens.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.ui.theme.Tokens
import androidx.compose.ui.unit.dp
import com.dailyworks.apnalaundry.ui.components.fig
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import org.koin.androidx.compose.koinViewModel
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextAlign

/**
 * Subscription gate for the authed app (mirrors the web Gate). The subscription
 * is resolved right after login and BEFORE setup, so the order is
 * login -> subscription -> setup -> app.
 * Flow: logged in -> check subscription -> route; a loader shows until the
 * check resolves.
 *  - active subscription -> the content. Nothing else gets in: not "billing
 *    unconfigured", not "paywall not due".
 *  - no active subscription -> non-cancellable ₹2-trial paywall.
 *  - billing unreachable (after retries) -> the content only if the server
 *    confirmed an active subscription for this user in the last 7 days (cached
 *    in Prefs); otherwise a "Try again" screen.
 *
 * [enabled] is false only on the login screen, where billing must not be checked.
 */
@Composable
fun BillingGate(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    GatedContent(content)
}

@Composable
private fun GatedContent(content: @Composable () -> Unit) {
    val vm: PaywallViewModel = koinViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Latch the decision once billing first loads, so the success -> "Done"
    // screen isn't skipped the instant the subscription goes active.
    var decided by remember { mutableStateOf(false) }
    var gated by remember { mutableStateOf(false) }

    // Billing unreachable and no recent cached "active": can't let them in.
    var offline by remember { mutableStateOf(false) }

    LaunchedEffect(ui.loaded, ui.failed) {
        if (decided || !ui.loaded) return@LaunchedEffect
        offline = false
        if (ui.status != null) {
            // Only an active subscription gets in.
            gated = ui.shouldHardGate
            decided = true
        } else if (vm.cachedActive()) {
            // Offline, but the server confirmed an active subscription recently.
            gated = false
            decided = true
        } else {
            offline = true
        }
    }

    when {
        !decided && offline -> CheckFailed(onRetry = vm::refresh)
        !ui.loaded || !decided -> ShopLoader()
        gated -> PaywallScreen(
            hardGate = true,
            onClose = {},
            onDone = { gated = false },
            vm = vm,
        )
        else -> content()
    }
}

/** Billing couldn't be reached and there's no recent cached active subscription. */
@Composable
private fun CheckFailed(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Tokens.Bg).padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Can't check your subscription", style = fig(20, FontWeight.Bold), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Check your internet connection and try again.",
            style = fig(14, color = Tokens.Muted), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Try again", height = 52.dp, onClick = onRetry)
    }
}

/** The "Loading your shop…" splash (same as the web's); Home keeps it up until the database answers. */
@Composable
fun ShopLoader() {
    Column(
        Modifier.fillMaxSize().background(Tokens.Bg),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Tokens.Blue), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.LocalLaundryService, null, tint = Tokens.OnDark, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Loading your shop…", style = fig(14, FontWeight.SemiBold, Tokens.Muted))
    }
}
