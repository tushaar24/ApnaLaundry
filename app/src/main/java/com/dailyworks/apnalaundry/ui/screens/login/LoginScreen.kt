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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Sms
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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
    // The field and CTA must be on screen without scrolling on every phone, and
    // above the keyboard when it's up. So nothing scrolls: the sheet keeps its
    // size and the hero gets what's left — the art shrinks into that space
    // (hidden when it would be a sliver), and on short heights the ticks, then
    // the headline, then the whole hero drop out.
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val h = maxHeight
        val compact = h < 720.dp
        Column(Modifier.fillMaxSize()) {
            if (h >= 420.dp) {
                Hero(bottom = if (compact) 16 else 24, compact = compact, modifier = Modifier.weight(1f).clipToBounds()) {
                    BrandPill()
                    BoxWithConstraints(
                        Modifier.weight(1f, fill = false).heightIn(max = 200.dp).fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (maxHeight >= 72.dp) {
                            Image(
                                painterResource(R.drawable.login_hero),
                                contentDescription = "Ironed shirts on a rail, a stack of folded clothes and a phone showing a sent bill",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.widthIn(max = 342.dp).fillMaxSize(),
                            )
                        }
                    }
                    if (h >= 500.dp) {
                        Text("Laundry Business Made Easy!", style = bric(34, FontWeight.Bold, Tokens.OnDark).copy(lineHeight = 36.sp))
                    }
                    if (h >= 680.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Perk("Bills on WhatsApp")
                            Perk("Pickup & delivery tracking")
                            Perk("All your accounts in one place")
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f).windowInsetsPadding(WindowInsets.statusBars))
            }
            PhoneSheet(ui, vm)
        }
    }
}

@Composable
private fun PhoneSheet(ui: AuthUiState, vm: AuthViewModel) {
    Sheet(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mobile number", style = fig(14, FontWeight.SemiBold))
            // Focus (and so the keyboard) only when coming back via "Change number";
            // on first open the keyboard would collapse the hero straight away.
            PhoneField(ui.phone, vm::onPhone, onDone = vm::requestOtp, autoFocus = ui.phone.isNotEmpty())
            if (ui.error != null) {
                Text(ui.error!!, style = fig(14, FontWeight.SemiBold, Tokens.ErrorRed))
            } else {
                Text("The OTP will arrive on your WhatsApp / SMS", style = fig(14, color = Tokens.Muted))
            }
        }
        CtaButton(if (ui.loading) "Sending OTP…" else "Get started", enabled = ui.phoneValid && !ui.loading, onClick = vm::requestOtp)
        TermsLine()
    }
}

@Composable
private fun ColumnScope.OtpStep(ui: AuthUiState, vm: AuthViewModel) {
    // Short screens drop the art and tighten the hero so the boxes stay in view
    // (Continue is pinned above the keyboard either way).
    val compact = LocalConfiguration.current.screenHeightDp < 720
    FullHeightScroll {
        Hero(bottom = if (compact) 20 else 28, compact = compact) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(Tokens.OnDark.copy(alpha = 0.16f), CircleShape)
                        .tap(onClick = vm::backToPhone),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_login_back), "Back", tint = Tokens.OnDark, modifier = Modifier.size(22.dp))
                }
                BrandPill(small = true)
            }
            if (!compact) {
                Image(
                    painterResource(R.drawable.login_otp),
                    contentDescription = "An OTP arriving on a phone by WhatsApp or SMS",
                    modifier = Modifier.size(200.dp, 140.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter OTP", style = bric(34, FontWeight.Bold, Tokens.OnDark).copy(lineHeight = 36.sp))
                Text(
                    buildAnnotatedString {
                        append("Sent on WhatsApp / SMS to ")
                        withStyle(SpanStyle(color = Tokens.OnDark, fontWeight = FontWeight.Bold)) {
                            append("+91 ${ui.phone.take(5)} ${ui.phone.drop(5)}")
                        }
                        append(" · ")
                        val changeStyle = TextLinkStyles(
                            SpanStyle(color = Tokens.OnDark, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
                        )
                        withLink(LinkAnnotation.Clickable("change", changeStyle) { vm.backToPhone() }) {
                            append("Change number")
                        }
                    },
                    style = fig(16, color = Tokens.OnBlueMuted).copy(lineHeight = 23.sp),
                )
            }
        }
        // The CTA sits in the fixed bar below; a cream filler (not a weighted
        // sheet, which would collapse when the keyboard squeezes the scroll)
        // carries the sheet down to it.
        Sheet(gap = 18) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OtpBoxes(value = ui.otp, error = ui.error != null, onChange = vm::onOtp, onDone = vm::verify)
                OtpStatus(ui)
            }
            Column(
                Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                if (ui.resendInSecs > 0) {
                    Text("Didn't get the OTP? You can resend in ${ui.resendInSecs} sec", style = fig(14, color = Tokens.Muted))
                } else {
                    Text("Didn't get the OTP?", style = fig(14, FontWeight.SemiBold, Tokens.InkSecondary))
                    ResendButton(if (ui.loading) "Sending…" else "Resend OTP", onClick = vm::requestOtp)
                }
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
        CtaButton(if (ui.loading) "Verifying…" else "Continue", enabled = ui.otpValid && ui.error == null && !ui.loading, onClick = vm::verify)
    }
}

@Composable
private fun Hero(
    bottom: Int,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 24.dp, end = 24.dp, top = if (compact) 12.dp else 24.dp, bottom = bottom.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
        content = content,
    )
}

/** The cream bottom sheet holding the form. */
@Composable
private fun Sheet(modifier: Modifier = Modifier, gap: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Tokens.Bg, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(gap.dp),
        content = content,
    )
}

/** Full-width 58dp CTA; disabled is the design's grey, not the app's pale blue. */
@Composable
private fun CtaButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(if (enabled) Tokens.Blue else Tokens.CardBorder, RoundedCornerShape(14.dp))
            .tap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = fig(17, FontWeight.Bold, if (enabled) Tokens.OnDark else Tokens.DisabledFg))
    }
}

@Composable
private fun ResendButton(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Tokens.Card, RoundedCornerShape(12.dp))
            .border(1.5.dp, Tokens.Blue, RoundedCornerShape(12.dp))
            .tap(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Sms, null, tint = Tokens.Blue, modifier = Modifier.size(18.dp))
        Text(text, style = fig(15, FontWeight.Bold, Tokens.Blue))
    }
}

/** +91 field that shows the number as XXXXX XXXXX; focused on entry (incl. "Change number"). */
@Composable
private fun PhoneField(value: String, onChange: (String) -> Unit, onDone: () -> Unit, autoFocus: Boolean) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (autoFocus) focus.requestFocus() }
    val textStyle = fig(19, FontWeight.SemiBold).copy(letterSpacing = 0.02.em)
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Tokens.Card, RoundedCornerShape(14.dp))
            .border(2.dp, if (focused) Tokens.Blue else Tokens.CardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("+91", style = fig(19, FontWeight.SemiBold, Tokens.Muted))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("Mobile Number", style = textStyle.copy(color = Tokens.Placeholder), maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(Tokens.Blue),
                visualTransformation = PhoneSpacing,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onDone() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

/** Inserts a space after the 5th digit: 9876543210 -> 98765 43210. */
private val PhoneSpacing = VisualTransformation { text ->
    val raw = text.text
    val out = if (raw.length > 5) raw.take(5) + " " + raw.drop(5) else raw
    TransformedText(
        AnnotatedString(out),
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset > 5) offset + 1 else offset
            override fun transformedToOriginal(offset: Int) = if (offset > 5) offset - 1 else offset
        },
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
            Icon(painterResource(R.drawable.ic_login_drop), null, tint = Tokens.OnDark, modifier = Modifier.size(if (small) 17.dp else 19.dp))
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
            Text("OTP filled in", style = fig(14, FontWeight.SemiBold, Tokens.GreenText))
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), color = Tokens.Blue, strokeWidth = 2.5.dp)
            Text(if (ui.resent) "OTP sent again — check your WhatsApp / SMS" else "Waiting for the OTP on WhatsApp / SMS", style = fig(14, color = Tokens.InkSecondary))
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
