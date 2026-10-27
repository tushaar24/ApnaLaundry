package com.dailyworks.apnalaundry.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.data.LaundryRepository
import com.dailyworks.apnalaundry.domain.BillDetails
import com.dailyworks.apnalaundry.domain.SeedData
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.screens.rates.RatesEditor
import com.dailyworks.apnalaundry.ui.theme.Tokens
import org.koin.compose.koinInject

/**
 * Onboarding after login + the paywall (handoff §1):
 *   1 · Laundry name → 2 · Services & rates → 3 · Your bill → Home.
 * (The old Intro screen is gone; a saved "intro" step — e.g. from the web — opens on the name.)
 * The step is saved on the shop after every move (and synced), so a killed
 * app — or another device — resumes exactly there. Port twin:
 * web/src/app/setup/page.tsx.
 */

internal val Green = Color(0xFF15803D)
internal val GreenLight = Color(0xFFE3F4E8)
private val StepGrey = Color(0xFFDAD5CB)

@Composable
fun OnboardingFlow(shopVm: ShopViewModel, onFinished: () -> Unit) {
    val repo: LaundryRepository = koinInject()
    val state by shopVm.state.collectAsStateWithLifecycle()
    // The name step reads the shop's saved name — wait for the real shop, not
    // the VM's stand-in, or a placeholder can show up in the field.
    val vmLoaded by shopVm.loaded.collectAsStateWithLifecycle()
    // Read from the DB, not the VM's not-yet-loaded initial state.
    var step by remember { mutableStateOf<String?>(null) }
    // A/B (2026-10-28), the services-from-scratch group: name -> bill, no services
    // step; the shop's sample services go (their prices were never the owner's) and
    // the first service is added from New order. The group was fetched at login
    // (AuthRepository), so it is known by the time the name step is done.
    val scratch by shopVm.servicesScratch.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        val saved = repo.onboardingStep()
        step = when {
            saved == "" || saved == "done" || saved == "intro" -> "name"
            saved == "services" && shopVm.servicesScratch.value -> "bill"
            else -> saved
        }
    }
    var finished by remember { mutableStateOf(false) }
    // One event per step shown, so the funnel shows where new shops drop off.
    LaunchedEffect(step) {
        val s = step ?: return@LaunchedEffect
        Analytics.onboardingStepViewed(s, listOf("intro", "name", "services", "bill").indexOf(s))
    }
    fun goTo(s: String) {
        step = s
        shopVm.setOnboardingStep(s)
    }
    fun finish() {
        if (finished) return
        finished = true
        shopVm.setOnboardingStep("done")
        Analytics.setupCompleted(state.services.size, state.shop.expressPct)
        shopVm.showInfo(tr("All set! Take your first order."))
        onFinished()
    }

    // Finished on another device (or the website) while this one sat here.
    LaunchedEffect(state.shop.onboardingStep) {
        if (step != null && !finished && state.shop.onboardingStep == "done") {
            finished = true
            onFinished()
        }
    }

    when (if (vmLoaded) step else null) {
        null -> Box(Modifier.fillMaxSize().background(Tokens.Bg))
        "name" -> {
            NameStep(
                initial = state.shop.name.takeUnless { SeedData.isDefaultShopName(it) } ?: "",
                onNext = { name ->
                    shopVm.updateShopDetails(name = BillDetails.cleanName(name))
                    if (scratch) { shopVm.removeSampleServices(); goTo("bill") } else goTo("services")
                },
            )
        }
        "services" -> {
            BackHandler { goTo("name") }
            RatesEditor(
                shopVm,
                setupHeader = { StepBar(2, onBack = { goTo("name") }) },
                onDone = { goTo("bill") },
                onBack = { goTo("name") },
            )
        }
        else -> {
            val prev = if (scratch) "name" else "services"
            BackHandler { goTo(prev) }
            BillDesignScreen(shopVm, editing = false, onBack = { goTo(prev) }, onDone = ::finish)
        }
    }
}

private val STEP_LABELS: List<String> get() = listOf(tr("Laundry name"), tr("Services"), tr("Your bill"))

/** "Step N of 3" with a back arrow (none on step 1), then three labelled segments (handoff §3). */
@Composable
fun StepBar(step: Int, onBack: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 44.dp)) {
            if (onBack != null) {
                Box(Modifier.size(44.dp).rounded(999.dp).tap(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back"), tint = Tokens.Ink, modifier = Modifier.size(24.dp))
                }
            }
            Text(tr("Step {n} of 3", "n" to step), style = fig(14, FontWeight.SemiBold, Tokens.Muted))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            STEP_LABELS.forEachIndexed { i, label ->
                val n = i + 1
                val done = n < step
                val current = n == step
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(6.dp).rounded(999.dp).background(if (done || current) Tokens.Blue else StepGrey))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (done) Icon(Icons.Filled.Check, null, tint = Green, modifier = Modifier.size(16.dp))
                        Text(
                            label,
                            style = fig(12, if (current) FontWeight.Bold else FontWeight.SemiBold, when {
                                done -> Green
                                current -> Tokens.Blue
                                else -> Tokens.Faint
                            }),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Screen scaffold: scrolling body + pinned bottom bar with the primary action. */
@Composable
internal fun StepScaffold(
    bottomNote: String? = null,
    top: (@Composable () -> Unit)? = null, // fixed above the scrolling body (e.g. a TopBar)
    bottom: @Composable () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
        top?.invoke()
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = body,
        )
        Column(Modifier.fillMaxWidth().background(Tokens.Card)) {
            if (bottomNote != null) {
                Text(bottomNote, style = fig(13, color = Tokens.Muted), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
            }
            Box(Modifier.padding(16.dp)) { bottom() }
        }
    }
}

@Composable
private fun NameStep(initial: String, onNext: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val ok = BillDetails.isNameOk(name)
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Open the keyboard straight away on the name field.
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    fun next() { if (ok) onNext(name.trim()) }

    StepScaffold(bottom = { PrimaryButton(tr("Next"), height = 56.dp, enabled = ok) { next() } }) {
        StepBar(1, onBack = null)
        Box(Modifier.size(64.dp).rounded(16.dp).background(Tokens.BlueLight), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Storefront, null, tint = Tokens.Blue, modifier = Modifier.size(32.dp))
        }
        Text(tr("What is your shop name?"), style = bric(28, FontWeight.Bold))
        Text(tr("Your customers will see this name on their bill."), style = fig(15, color = Tokens.Muted))
        FieldBox(
            name, { name = it.take(40) },
            height = 56.dp, borderColor = Tokens.Blue, borderWidth = 2.dp,
            fieldModifier = Modifier.focusRequester(focus).semantics { contentDescription = tr("Laundry name") },
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
            onImeAction = { next() },
        )
    }
}
