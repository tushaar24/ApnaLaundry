package com.dailyworks.apnalaundry.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, vm: AuthViewModel = koinViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.done) { if (ui.done) onLoggedIn() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.Bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        Box(
            Modifier.size(48.dp).background(Tokens.Blue, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.LocalLaundryService, null, tint = Tokens.OnDark, modifier = Modifier.size(26.dp))
        }
        if (ui.step == AuthUiState.Step.PHONE) PhoneStep(ui, vm) else OtpStep(ui, vm)
    }
}

@Composable
private fun ColumnScope.PhoneStep(ui: AuthUiState, vm: AuthViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Orders and bills,\nin two taps.", style = bric(38, FontWeight.Bold).copy(lineHeight = 40.sp))
        Text("Enter your mobile number to start. No password, no long forms.", style = fig(17, color = Tokens.Muted))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Mobile number", style = fig(14, FontWeight.SemiBold))
        FieldBox(
            value = ui.phone,
            onValueChange = vm::onPhone,
            prefix = "+91",
            height = 60.dp,
            borderColor = Tokens.Blue,
            borderWidth = 2.dp,
            keyboardType = KeyboardType.Phone,
            textStyle = fig(19, FontWeight.SemiBold).copy(letterSpacing = 0.02.em),
            placeholder = "98765 43210",
        )
        Text("We'll send a one-time password to this number by SMS.", style = fig(14, color = Tokens.Muted))
        if (ui.error != null) Text(ui.error!!, style = fig(14, FontWeight.SemiBold, Tokens.OrangeText))
    }
    Spacer(Modifier.weight(1f))
    PrimaryButton(if (ui.loading) "Sending…" else "Continue", enabled = ui.phoneValid && !ui.loading, height = 58.dp) { vm.requestOtp() }
}

@Composable
private fun ColumnScope.OtpStep(ui: AuthUiState, vm: AuthViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Enter the code", style = bric(38, FontWeight.Bold).copy(lineHeight = 40.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sent to +91 ${ui.phone}", style = fig(17, color = Tokens.Muted))
            Text("Change", style = fig(17, FontWeight.Bold, Tokens.Blue), modifier = Modifier.tap(onClick = vm::backToPhone))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("6-digit OTP", style = fig(14, FontWeight.SemiBold))
        FieldBox(
            value = ui.otp,
            onValueChange = vm::onOtp,
            height = 60.dp,
            borderColor = Tokens.Blue,
            borderWidth = 2.dp,
            keyboardType = KeyboardType.NumberPassword,
            textStyle = fig(24, FontWeight.Bold).copy(letterSpacing = 0.4.em),
            textAlign = TextAlign.Center,
            placeholder = "••••••",
        )
        if (ui.error != null) {
            Text(ui.error!!, style = fig(14, FontWeight.SemiBold, Tokens.OrangeText))
        } else if (ui.attemptsRemaining != null && ui.attemptsRemaining < 5) {
            Text("${ui.attemptsRemaining} attempts left", style = fig(14, color = Tokens.Muted))
        }
        Text(
            if (ui.resendInSecs > 0) "Resend code in ${ui.resendInSecs}s" else "Resend code",
            style = if (ui.resendInSecs > 0) fig(14, color = Tokens.Muted) else fig(14, FontWeight.Bold, Tokens.Blue),
            modifier = if (ui.resendInSecs > 0) Modifier else Modifier.tap(onClick = vm::requestOtp),
        )
    }
    Spacer(Modifier.weight(1f))
    PrimaryButton(if (ui.loading) "Verifying…" else "Verify & continue", enabled = ui.otpValid && !ui.loading, height = 58.dp) { vm.verify() }
}
