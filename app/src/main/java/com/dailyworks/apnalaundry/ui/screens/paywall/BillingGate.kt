package com.dailyworks.apnalaundry.ui.screens.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.theme.Tokens

// There is no paywall after login any more: a new shop sets up and takes its
// free orders, and the subscription is asked for only when those run out
// (OrderLimitGate). These are the shared billing loading / failure screens.

/** Billing couldn't be reached and there's no recent cached active subscription. */
@Composable
internal fun CheckFailed(onRetry: () -> Unit) {
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
