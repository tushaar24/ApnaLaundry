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
 * Subscription gate inside the authed app (mirrors the web BillingGate).
 * Flow: logged in -> check subscription -> route; a loader shows until the
 * check resolves.
 *  - trial_2 + no active subscription -> non-cancellable paywall (app unreachable).
 *  - otherwise (free_<N>, active sub, or billing unreachable) -> the app.
 *
 * [enabled] is false during login/setup, where billing must not be checked.
 * [onContinueNewOrder] fires when the Done screen's "+ Continue with new order"
 * is tapped — the host navigates once the gate (and NavHost) are composed.
 */
@Composable
fun BillingGate(enabled: Boolean, onContinueNewOrder: () -> Unit = {}, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    GatedContent(onContinueNewOrder, content)
}

@Composable
private fun GatedContent(onContinueNewOrder: () -> Unit, content: @Composable () -> Unit) {
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
            reason = "trial",
            hardGate = true,
            onClose = {},
            onDone = { continuing ->
                gated = false
                if (continuing) onContinueNewOrder()
            },
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
