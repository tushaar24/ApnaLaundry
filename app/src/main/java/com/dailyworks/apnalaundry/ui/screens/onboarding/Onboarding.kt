package com.dailyworks.apnalaundry.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import com.dailyworks.apnalaundry.core.AppDate
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
import java.time.LocalDate
import org.koin.compose.koinInject

/**
 * Onboarding after login + the paywall (handoff §1):
 *   Intro → 1 · Laundry name → 2 · Services & rates → 3 · Your bill → Home.
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
    // Read from the DB, not the VM's not-yet-loaded initial state.
    var step by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val saved = repo.onboardingStep()
        step = if (saved == "" || saved == "done") "intro" else saved
    }
    var finished by remember { mutableStateOf(false) }
    fun goTo(s: String) {
        step = s
        shopVm.setOnboardingStep(s)
    }
    fun finish() {
        if (finished) return
        finished = true
        shopVm.setOnboardingStep("done")
        Analytics.setupCompleted(state.services.size, state.shop.expressPct)
        shopVm.showInfo("All set! Take your first order.")
        onFinished()
    }

    // Finished on another device (or the website) while this one sat here.
    LaunchedEffect(state.shop.onboardingStep) {
        if (step != null && !finished && state.shop.onboardingStep == "done") {
            finished = true
            onFinished()
        }
    }

    when (step) {
        null -> Box(Modifier.fillMaxSize().background(Tokens.Bg))
        "intro" -> IntroStep(onStart = { goTo("name") })
        "name" -> {
            BackHandler { goTo("intro") }
            NameStep(
                initial = state.shop.name.takeUnless { SeedData.isDefaultShopName(it) } ?: "",
                onBack = { goTo("intro") },
                onNext = { name ->
                    shopVm.updateShopDetails(name = BillDetails.cleanName(name))
                    goTo("services")
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
            BackHandler { goTo("services") }
            BillDesignScreen(shopVm, editing = false, onBack = { goTo("services") }, onDone = ::finish)
        }
    }
}

private val STEP_LABELS = listOf("Laundry name", "Services", "Your bill")

/** "Step N of 3" with a back arrow, then three labelled segments (handoff §3). */
@Composable
fun StepBar(step: Int, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 0.dp)) {
            Box(Modifier.size(44.dp).rounded(999.dp).tap(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Tokens.Ink, modifier = Modifier.size(24.dp))
            }
            Text("Step $step of 3", style = fig(14, FontWeight.SemiBold, Tokens.Muted))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            STEP_LABELS.forEachIndexed { i, label ->
                val n = i + 1
                val done = n < step
                val current = n == step
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(6.dp).rounded(999.dp).background(if (done || current) Tokens.Blue else StepGrey))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (done) Icon(Icons.Filled.Check, null, tint = Green, modifier = Modifier.size(13.dp))
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

private data class IntroItem(val title: String, val body: String)

private val INTRO = listOf(
    IntroItem("Laundry name", "Printed at the top of every bill"),
    IntroItem("Services & rates", "Common prices are already filled in — just check them"),
    IntroItem("Your bill", "Pick a bill design. Add phone, email, address and GSTIN if you want"),
)

@Composable
private fun IntroStep(onStart: () -> Unit) {
    StepScaffold(bottom = { PrimaryButton("Start with step 1", height = 56.dp) { onStart() } }) {
        Row(
            Modifier.rounded(999.dp).background(GreenLight).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Check, null, tint = Green, modifier = Modifier.size(15.dp))
            Text("Number verified", style = fig(13, FontWeight.Bold, Green))
        }
        Text("Let's set up your shop", style = bric(30, FontWeight.Bold))
        Text("3 quick steps. Then you can send your first bill on WhatsApp.", style = fig(15, color = Tokens.Muted))
        Column {
            INTRO.forEachIndexed { i, item ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        val first = i == 0
                        Box(
                            Modifier.size(36.dp).rounded(999.dp)
                                .background(if (first) Tokens.Blue else Tokens.Card)
                                .then(if (first) Modifier else Modifier.border(2.dp, Tokens.Blue, RoundedCornerShape(999.dp))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", style = fig(15, FontWeight.Bold, if (first) Tokens.OnDark else Tokens.Blue))
                        }
                        if (i < INTRO.lastIndex) {
                            Box(Modifier.padding(vertical = 4.dp).width(2.dp).weight(1f).background(Tokens.BlueBorder))
                        }
                    }
                    Column(Modifier.padding(top = 6.dp, bottom = if (i < INTRO.lastIndex) 24.dp else 0.dp)) {
                        Text(item.title, style = fig(17, FontWeight.Bold))
                        Text(item.body, style = fig(14, color = Tokens.Muted))
                    }
                }
            }
        }
    }
}

@Composable
private fun NameStep(initial: String, onBack: () -> Unit, onNext: (String) -> Unit) {
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

    StepScaffold(bottom = { PrimaryButton("Next: Services", height = 56.dp, enabled = ok) { next() } }) {
        StepBar(1, onBack)
        Text("What is your laundry called?", style = bric(28, FontWeight.Bold))
        Text("This name goes on every bill you send to customers.", style = fig(14, color = Tokens.Muted))
        FieldBox(
            name, { name = it.take(40) },
            placeholder = "e.g. Sharma Laundry",
            height = 56.dp, borderColor = Tokens.Blue, borderWidth = 2.dp,
            fieldModifier = Modifier.focusRequester(focus).semantics { contentDescription = "Laundry name" },
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
            onImeAction = { next() },
        )
        MiniBill(name.trim())
    }
}

/** The small "On your bill" card under the name input; updates as the owner types. */
@Composable
private fun MiniBill(name: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ON YOUR BILL", style = fig(12, FontWeight.Bold, Tokens.Faint))
        Column(
            Modifier.fillMaxWidth().rounded(16.dp).background(Tokens.Card)
                .border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                name.ifEmpty { "Your laundry name" },
                style = bric(22, FontWeight.Bold, if (name.isEmpty()) Tokens.Faint else Tokens.Ink),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("Bill #1001 · ${AppDate.plain(LocalDate.now().toString())}", style = fig(13, color = Tokens.Muted))
            listOf(0.78f, 0.62f, 0.70f).forEach { w ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.fillMaxWidth(w).height(10.dp).rounded(999.dp).background(Tokens.NeutralFill))
                    Box(Modifier.width(40.dp).height(10.dp).rounded(999.dp).background(Tokens.NeutralFill))
                }
            }
        }
    }
}
