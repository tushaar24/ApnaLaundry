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
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.ToastBar
import com.dailyworks.apnalaundry.ui.nav.AppNavGraph
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.theme.ApnaLaundryTheme
import com.dailyworks.apnalaundry.ui.theme.Tokens
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ApnaLaundryTheme {
                AppRoot()
            }
        }
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
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                ToastBar(text = t.text, hasUndo = t.hasUndo, onUndo = shopVm::undo)
            }
        }
    }
}
