package com.dailyworks.apnalaundry.ui.screens.login

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.R
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.OutlineButton
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens
import org.koin.androidx.compose.koinViewModel

private const val OTP_LEN = 6
private const val SITE = "https://mylaundry.work"

/** Ad end-card look: blue hero on top, cream sheet with the form at the bottom. */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, vm: AuthViewModel = koinViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { Analytics.screen("login") }
    LaunchedEffect(ui.done) { if (ui.done) onLoggedIn() }
    LightStatusBarIcons()
    BackHandler(enabled = ui.step == AuthUiState.Step.OTP, onBack = vm::backToPhone)

    Column(Modifier.fillMaxSize().background(Tokens.Blue).imePadding()) {
        if (ui.step == AuthUiState.Step.PHONE) PhoneStep(ui, vm) else OtpStep(ui, vm)
    }
}

/**
 * Scrolls when the keyboard squeezes it, but otherwise stretches to the full
 * height so the weighted spacer inside can push the sheet to the bottom.
 */
@Composable
private fun ColumnScope.FullHeightScroll(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
            content = content,
        )
    }
}

@Composable
private fun ColumnScope.PhoneStep(ui: AuthUiState, vm: AuthViewModel) {
    FullHeightScroll {
        Hero(bottom = 24) {
            BrandPill()
            Image(
                painterResource(R.drawable.login_hero),
                contentDescription = "Ironed shirts on a rail, a stack of folded clothes and a phone showing a sent bill",
                contentScale = ContentScale.Fit,
                modifier = Modifier.widthIn(max = 342.dp).fillMaxWidth().height(200.dp),
            )
            Text("Laundry Business Made Easy!", style = bric(34, FontWeight.Bold, Tokens.OnDark).copy(lineHeight = 36.sp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Perk("Bills on WhatsApp")
                Perk("Pickup & delivery tracking")
                Perk("All your accounts in one place")
            }
        }
        Spacer(Modifier.weight(1f))
        Sheet(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Mobile number", style = fig(14, FontWeight.SemiBold))
                FieldBox(
                    value = ui.phone,
                    onValueChange = vm::onPhone,
                    prefix = "+91",
                    height = 60.dp,
                    borderColor = Tokens.CardBorder,
                    borderWidth = 2.dp,
                    keyboardType = KeyboardType.Phone,
                    textStyle = fig(19, FontWeight.SemiBold).copy(letterSpacing = 0.02.em),
                    placeholder = "Mobile Number",
                )
                if (ui.error != null) {
                    Text(ui.error!!, style = fig(14, FontWeight.SemiBold, Tokens.ErrorRed))
                } else {
                    Text("We'll send an OTP to your WhatsApp / SMS", style = fig(14, color = Tokens.Muted))
                }
            }
            PrimaryButton(if (ui.loading) "Sending OTP…" else "Get started", enabled = ui.phoneValid && !ui.loading, height = 58.dp) { vm.requestOtp() }
            TermsLine()
        }
    }
}

@Composable
private fun ColumnScope.OtpStep(ui: AuthUiState, vm: AuthViewModel) {
    FullHeightScroll {
        Hero(bottom = 28) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(Tokens.OnDark.copy(alpha = 0.16f), CircleShape)
                        .tap(onClick = vm::backToPhone),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", tint = Tokens.OnDark, modifier = Modifier.size(28.dp))
                }
                BrandPill(small = true)
            }
            Image(
                painterResource(R.drawable.login_otp),
                contentDescription = "An OTP arriving on a phone by WhatsApp or SMS",
                modifier = Modifier.size(200.dp, 140.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter OTP", style = bric(34, FontWeight.Bold, Tokens.OnDark).copy(lineHeight = 36.sp))
                Text(
                    buildAnnotatedString {
                        append("Sent via WhatsApp / SMS to ")
                        withStyle(SpanStyle(color = Tokens.OnDark, fontWeight = FontWeight.Bold)) {
                            append("+91 ${ui.phone.take(5)} ${ui.phone.drop(5)}")
                        }
                    },
                    style = fig(16, color = Tokens.OnBlueMuted).copy(lineHeight = 23.sp),
                )
            }
        }
        // The CTA sits in the fixed bar below; a cream filler (not a weighted
        // sheet, which would collapse when the keyboard squeezes the scroll)
        // carries the sheet down to it.
        Sheet {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OtpBoxes(value = ui.otp, error = ui.error != null, onChange = vm::onOtp, onDone = vm::verify)
                OtpStatus(ui)
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (ui.resendInSecs > 0) {
                    Text("Didn't get it? Resend in ${ui.resendInSecs}s", style = fig(14, color = Tokens.Muted))
                } else {
                    OutlineButton("Resend OTP", height = 44.dp, bg = Tokens.Card, onClick = vm::requestOtp)
                }
                Text(
                    "Change number",
                    style = fig(14, FontWeight.SemiBold, Tokens.Blue).copy(textDecoration = TextDecoration.Underline),
                    modifier = Modifier.padding(start = 12.dp).tap(onClick = vm::backToPhone),
                )
            }
        }
        Spacer(Modifier.weight(1f).fillMaxWidth().background(Tokens.Bg))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Tokens.Bg)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
    ) {
        PrimaryButton(if (ui.loading) "Verifying…" else "Continue", enabled = ui.otpValid && !ui.loading, height = 58.dp) { vm.verify() }
    }
}

@Composable
private fun Hero(bottom: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = bottom.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content,
    )
}

/** The cream bottom sheet holding the form. */
@Composable
private fun Sheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Tokens.Bg, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun BrandPill(small: Boolean = false) {
    Row(
        Modifier
            .background(Tokens.Card, RoundedCornerShape(if (small) 14.dp else 16.dp))
            .padding(start = if (small) 6.dp else 8.dp, top = if (small) 6.dp else 8.dp, bottom = if (small) 6.dp else 8.dp, end = if (small) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(if (small) 30.dp else 34.dp).background(Tokens.Blue, RoundedCornerShape(if (small) 9.dp else 10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.WaterDrop, null, tint = Tokens.OnDark, modifier = Modifier.size(if (small) 18.dp else 20.dp))
        }
        Text(
            buildAnnotatedString {
                append("My")
                withStyle(SpanStyle(color = Tokens.Blue)) { append("Laundry") }
            },
            style = bric(if (small) 20 else 22),
        )
    }
}

@Composable
private fun Perk(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(24.dp).background(Tokens.Green, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = Tokens.OnDark, modifier = Modifier.size(16.dp))
        }
        Text(text, style = fig(16, FontWeight.SemiBold, Tokens.OnDark))
    }
}

@Composable
private fun TermsLine() {
    val link = TextLinkStyles(SpanStyle(color = Tokens.Blue, textDecoration = TextDecoration.Underline))
    Text(
        buildAnnotatedString {
            append("By continuing, you agree to our ")
            withLink(LinkAnnotation.Url("$SITE/terms", link)) { append("Terms") }
            append(" and ")
            withLink(LinkAnnotation.Url("$SITE/privacy", link)) { append("Privacy Policy") }
        },
        style = fig(13, color = Tokens.Muted).copy(textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Six digit boxes drawn over one real text field, so paste and the keyboard's
 * OTP suggestion fill all boxes at once.
 */
@Composable
private fun OtpBoxes(value: String, error: Boolean, onChange: (String) -> Unit, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(OTP_LEN) { i ->
                        val ch = value.getOrNull(i)?.toString() ?: ""
                        val active = focused && !error && i == minOf(value.length, OTP_LEN - 1)
                        val border = when {
                            error -> Tokens.ErrorRed
                            ch.isNotEmpty() || active -> Tokens.Blue
                            else -> Tokens.CardBorder
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .background(Tokens.Card, RoundedCornerShape(12.dp))
                                .border(2.dp, border, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(ch, style = bric(24))
                        }
                    }
                }
                Box(Modifier.matchParentSize()) { inner() }
            }
        },
    )
}

@Composable
private fun OtpStatus(ui: AuthUiState) {
    when {
        ui.error != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = Tokens.ErrorRed, modifier = Modifier.size(18.dp))
            val attempts = ui.attemptsRemaining?.takeIf { it < 5 }?.let { " · $it attempts left" } ?: ""
            Text(ui.error + attempts, style = fig(14, FontWeight.SemiBold, Tokens.ErrorRed))
        }
        ui.otpValid -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(20.dp).background(Tokens.Green, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Tokens.OnDark, modifier = Modifier.size(14.dp))
            }
            Text("OTP entered", style = fig(14, FontWeight.SemiBold, Tokens.GreenText))
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), color = Tokens.Blue, strokeWidth = 2.5.dp)
            Text("Waiting for your OTP on WhatsApp / SMS", style = fig(14, color = Tokens.InkSecondary))
        }
    }
}

/** White status-bar icons while the blue hero sits under them; restored on leave. */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val prev = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = prev }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
