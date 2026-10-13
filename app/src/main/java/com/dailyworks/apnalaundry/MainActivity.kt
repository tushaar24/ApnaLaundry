package com.dailyworks.apnalaundry

import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.clevertap.android.sdk.CleverTapAPI
import com.dailyworks.apnalaundry.push.DeepLinks
import com.dailyworks.apnalaundry.push.Push
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.dailyworks.apnalaundry.data.billing.CheckoutBridge
import com.dailyworks.apnalaundry.data.billing.CheckoutResult
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.ToastBar
import com.dailyworks.apnalaundry.ui.nav.AppNavGraph
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.theme.ApnaLaundryTheme
import com.dailyworks.apnalaundry.ui.theme.Tokens
import com.dailyworks.apnalaundry.ui.tour.TourPart
import com.dailyworks.apnalaundry.ui.tour.TourStage
import com.dailyworks.apnalaundry.ui.tour.TourSteps
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.dailyworks.apnalaundry.data.sync.SyncScheduler
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

// Implements Razorpay's result listener: Standard Checkout delivers the UPI
// AutoPay outcome here, which we forward to the paywall via CheckoutBridge.
/** The largest text scale the app uses (see setContent). */
private const val MAX_FONT_SCALE = 1.15f

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    private val checkoutBridge: CheckoutBridge by inject()
    private val syncScheduler: SyncScheduler by inject()

    // Sync on open and every minute while visible (edits from other devices).
    override fun onStart() {
        Push.reportEnabled(this) // they may have switched notifications on/off in Settings
        super.onStart()
        syncScheduler.onForeground()
    }

    override fun onStop() {
        syncScheduler.onBackground()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) DeepLinks.offer(intent)
        Checkout.preload(applicationContext)
        // The app is light-only: dark status-bar and navigation icons always, even
        // when the phone is in dark mode (the default would draw white icons on
        // the app's light background — invisible clock, battery, back/home).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            // Text follows the phone's font size up to 115%. Beyond that ("Extra
            // large", common on budget phones) words get cut off in buttons,
            // chips and tiles; Large and smaller look exactly as set.
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(MAX_FONT_SCALE))) {
                ApnaLaundryTheme {
                    AppRoot()
                }
            }
        }
    }

    // A CleverTap push tapped while the app is already open: Android 12+ opens
    // this activity directly (no trampoline), so CleverTap's lifecycle hook —
    // which records the click only when the activity is created — misses it.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinks.offer(intent)
        val extras = intent.extras ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && CleverTapAPI.getNotificationInfo(extras).fromCleverTap) {
            CleverTapAPI.getDefaultInstance(applicationContext)?.pushNotificationClickedEvent(extras)
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        checkoutBridge.post(CheckoutResult.Success(razorpayPaymentId))
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        checkoutBridge.post(CheckoutResult.Failure(code, response))
    }
}

@Composable
private fun AppRoot() {
    val shopVm: ShopViewModel = koinViewModel()
    val navController = rememberNavController()
    val navigator = AppNavigator(navController)
    val toast by shopVm.toast.collectAsStateWithLifecycle()
    val waPrompt by shopVm.waPrompt.collectAsStateWithLifecycle()
    val tour by shopVm.tour.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(Modifier.fillMaxSize().background(Tokens.Bg)) {
        AppNavGraph(navController = navController, navigator = navigator, shopVm = shopVm)

        toast?.let { t ->
            Box(
                Modifier
                    .fillMaxSize()
                    // Above the keyboard too (a toast while typing was hidden under it).
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    // Clears the New-order button and the bottom bar — a toast on
                    // the button turns a late "Undo" tap into a new order (web: 156px).
                    .padding(start = 16.dp, end = 16.dp, bottom = 156.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                ToastBar(text = t.text, hasUndo = t.hasUndo, onUndo = shopVm::undo)
            }
        }
        waPrompt?.let { url ->
            com.dailyworks.apnalaundry.ui.components.WhatsAppPromptDialog(
                url = url,
                onSend = { com.dailyworks.apnalaundry.ui.screens.bill.openWhatsApp(context, url); shopVm.dismissWaPrompt() },
                onDismiss = shopVm::dismissWaPrompt,
                // The guide: after marking ready (part 2) / delivered (part 3, already on khata).
                tip = when (tour) {
                    TourStage.STATUS -> TourSteps.whatsAppPrompt(TourPart.READY)
                    TourStage.KHATA -> TourSteps.whatsAppPrompt(TourPart.DELIVER)
                    else -> null
                },
            )
        }
    }
}
