package com.dailyworks.apnalaundry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.dailyworks.apnalaundry.data.sync.SyncScheduler
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

// Implements Razorpay's result listener: Standard Checkout delivers the UPI
// AutoPay outcome here, which we forward to the paywall via CheckoutBridge.
class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    private val checkoutBridge: CheckoutBridge by inject()
    private val syncScheduler: SyncScheduler by inject()

    // Sync on open and every minute while visible (edits from other devices).
    override fun onStart() {
        super.onStart()
        syncScheduler.onForeground()
    }

    override fun onStop() {
        syncScheduler.onBackground()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Checkout.preload(applicationContext)
        enableEdgeToEdge()
        setContent {
            ApnaLaundryTheme {
                AppRoot()
            }
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

    Box(Modifier.fillMaxSize().background(Tokens.Bg)) {
        AppNavGraph(navController = navController, navigator = navigator, shopVm = shopVm)

        toast?.let { t ->
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    // Clears the New-order button and the bottom bar — a toast on
                    // the button turns a late "Undo" tap into a new order (web: 156px).
                    .padding(start = 16.dp, end = 16.dp, bottom = 156.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                ToastBar(text = t.text, hasUndo = t.hasUndo, onUndo = shopVm::undo)
            }
        }
    }
}
