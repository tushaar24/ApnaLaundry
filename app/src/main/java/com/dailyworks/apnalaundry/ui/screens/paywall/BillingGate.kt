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
import org.koin.androidx.compose.koinViewModel

/**
 * Subscription gate for the authed app (mirrors the web Gate). The subscription
 * is resolved right after login and BEFORE setup, so the order is
 * login -> subscription -> setup -> app.
 * Flow: logged in -> check subscription -> route; a loader shows until the
 * check resolves.
 *  - no active subscription -> non-cancellable ₹2-trial paywall (app unreachable).
 *  - otherwise (active sub, or billing unconfigured/unreachable) -> the content.
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

    LaunchedEffect(ui.loaded) {
        if (!decided && ui.loaded) {
            gated = ui.shouldHardGate
            decided = true
        }
    }

    when {
        !ui.loaded || !decided -> Loader()
        gated -> PaywallScreen(
            hardGate = true,
            onClose = {},
            onDone = { gated = false },
            vm = vm,
        )
        else -> content()
    }
}

@Composable
private fun Loader() {
    Box(Modifier.fillMaxSize().background(Tokens.Bg), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Tokens.Blue)
    }
}
