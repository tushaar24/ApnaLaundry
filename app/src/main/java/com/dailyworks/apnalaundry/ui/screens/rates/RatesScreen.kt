package com.dailyworks.apnalaundry.ui.screens.rates

import com.dailyworks.apnalaundry.ui.Selectors
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.data.LaundryRepository
import com.dailyworks.apnalaundry.domain.SeedData
import com.dailyworks.apnalaundry.domain.PricingMode
import com.dailyworks.apnalaundry.domain.Service
import com.dailyworks.apnalaundry.domain.ServiceItem
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.OutlineButton
import com.dailyworks.apnalaundry.ui.components.PillChip
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.Segmented
import com.dailyworks.apnalaundry.ui.components.TopBar
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.screens.onboarding.OnboardingFlow
import com.dailyworks.apnalaundry.ui.theme.Tokens
import com.dailyworks.apnalaundry.ui.components.dashedBorder
import org.koin.compose.koinInject

/** Which sub-screen of the rate list is open. */
private sealed interface RatePage {
    data object List : RatePage
    data class Edit(val serviceId: String) : RatePage
    data object Add : RatePage
}

private fun modeLabel(s: Service): String = if (s.mode == PricingMode.WEIGHT) tr("By weight") else tr("Per piece")

private fun summaryLine(s: Service): String = if (s.mode == PricingMode.WEIGHT) {
    val rate = if (s.ratePerKg != null) tr("{rate} per kg", "rate" to "₹${s.ratePerKg}") else tr("Rate not set")
    rate + (s.minKg?.takeIf { it > 0 }?.let { " · " + tr("minimum {kg} kg", "kg" to Selectors.trimKg(it)) } ?: "")
} else {
    val priced = s.items.filter { it.price != null }
    if (priced.isEmpty()) tr("No prices set yet — tap Edit")
    else priced.take(3).joinToString(" · ") { "${it.name} ₹${it.price}" } +
        (if (priced.size > 3) " · " + tr("+{n} more", "n" to priced.size - 3) else "")
}

@Composable
fun RatesScreen(shopVm: ShopViewModel, from: String, onDone: () -> Unit, onBack: () -> Unit) {
    val setup = from == "setup"
    LaunchedEffect(Unit) {
        // Same screen names as the web: onboarding is "setup", the editor "rates".
        Analytics.screen(if (setup) "setup" else "rates")
        if (!setup) Analytics.ratesOpened(from)
    }
    // First-run setup is the whole onboarding flow (intro → name → this rate
    // list → "Your bill"); it reuses [RatesEditor] for its services step.
    if (setup) {
        OnboardingFlow(shopVm, onFinished = onDone)
        return
    }
    RatesEditor(shopVm, setupHeader = null, onDone = onDone, onBack = onBack)
}

/**
 * The rate list with its edit / add sub-pages. With [setupHeader] it is
 * onboarding step 2: the step bar replaces the top bar and it ends in
 * "Next: Your bill".
 */
@Composable
fun RatesEditor(shopVm: ShopViewModel, setupHeader: (@Composable () -> Unit)?, onDone: () -> Unit, onBack: () -> Unit) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val scratch by shopVm.servicesScratch.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf<RatePage>(RatePage.List) }
    when (val p = page) {
        is RatePage.List -> RateListPage(
            shopVm = shopVm, setupHeader = setupHeader,
            onEdit = { page = RatePage.Edit(it) },
            onAdd = { page = RatePage.Add },
            onDone = onDone,
            onBack = onBack,
        )
        is RatePage.Edit -> {
            val svc = state.services.firstOrNull { it.id == p.serviceId }
            if (svc == null) { page = RatePage.List } else {
                EditServicePage(
                    original = svc, canDelete = state.services.size > 1 || scratch,
                    onSave = { shopVm.upsertService(it); Analytics.serviceEdited(it.id); page = RatePage.List },
                    onDelete = { shopVm.deleteService(svc.id); page = RatePage.List },
                    onBack = { page = RatePage.List },
                )
            }
        }
        // Services-from-scratch variant: the simple form (name, per piece / per kg, clothes + prices) everywhere.
        is RatePage.Add -> if (scratch) AddServiceSimplePage(
            existing = state.services,
            onAdd = { shopVm.upsertService(it); Analytics.serviceAdded(it.mode.name); page = RatePage.List },
            onBack = { page = RatePage.List },
        ) else AddServicePage(
            existing = state.services,
            onAdd = { shopVm.upsertService(it); Analytics.serviceAdded(it.mode.name); page = RatePage.List },
            onBack = { page = RatePage.List },
        )
    }
}

// ───────────────────────── list ─────────────────────────

@Composable
private fun RateListPage(
    shopVm: ShopViewModel, setupHeader: (@Composable () -> Unit)?,
    onEdit: (String) -> Unit, onAdd: () -> Unit, onDone: () -> Unit, onBack: () -> Unit,
) {
    val setup = setupHeader != null
    val state by shopVm.state.collectAsStateWithLifecycle()
    val scratch by shopVm.servicesScratch.collectAsStateWithLifecycle()
    val services = state.services

    fun commitAndDone() {
        shopVm.updateShop(state.shop.name, state.shop.expressPct)
        onDone()
    }

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars)) {
        if (!setup) TopBar(tr("Services"), onBack = onBack)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (setupHeader != null) {
                setupHeader()
                Text(tr("Setup Services"), style = bric(28, FontWeight.Bold))
                Text(
                    tr("These services and prices go on your bills. Check them once — change only what is different."),
                    style = fig(14, color = Tokens.Muted),
                )
            } else {
                InfoBox(
                    tr("Change prices anytime"),
                    tr("Tap Edit on a service to change its prices. New prices apply to new orders only."),
                )
            }

            Text(tr("YOUR SERVICES ({n})", "n" to services.size), style = fig(13, FontWeight.Bold, Tokens.InkSecondary))

            AddServiceCard(onAdd)

            services.forEach { s ->
                ServiceCard(s, canDelete = services.size > 1 || scratch, onEdit = { onEdit(s.id) }, onDelete = { shopVm.deleteService(s.id) })
            }

        }

        Column(Modifier.fillMaxWidth().background(Tokens.Card)) {
            Text(
                tr("Nothing else is needed. You can change prices anytime."),
                style = fig(13, color = Tokens.Muted),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            Box(Modifier.padding(16.dp)) {
                PrimaryButton(if (setup) tr("Next: Your bill") else tr("Done"), height = 56.dp) { commitAndDone() }
            }
        }
    }
}

@Composable
private fun InfoBox(title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().rounded(14.dp).background(Tokens.BlueLight).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Info, null, tint = Tokens.Blue, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = fig(15, FontWeight.Bold, Tokens.BlueText))
            Text(body, style = fig(14, color = Tokens.BlueText))
        }
    }
}

@Composable
private fun ServiceCard(s: Service, canDelete: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().rounded(16.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(s.name, style = bric(20, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(modeLabel(s), style = fig(13, color = Tokens.Muted))
            }
            Row(
                Modifier.height(44.dp).rounded(10.dp).background(Tokens.BlueLight).tap(onClick = onEdit).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.Edit, null, tint = Tokens.Blue, modifier = Modifier.size(18.dp))
                Text(tr("Edit"), style = fig(15, FontWeight.Bold, Tokens.Blue))
            }
            if (canDelete) Box(
                Modifier.size(44.dp).rounded(10.dp).border(1.dp, Tokens.OrangeBorder, RoundedCornerShape(10.dp)).tap(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.DeleteOutline, tr("Delete {service}", "service" to s.name), tint = Tokens.DeleteRed, modifier = Modifier.size(20.dp))
            }
        }
        Box(Modifier.fillMaxWidth().rounded(10.dp).background(Tokens.NeutralFill).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(summaryLine(s), style = fig(14, color = Tokens.InkSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AddServiceCard(onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().rounded(16.dp).background(Tokens.BlueLight)
            .border(1.5.dp, Tokens.Blue, RoundedCornerShape(16.dp)).tap(onClick = onAdd).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).rounded(999.dp).background(Tokens.Blue), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, null, tint = Tokens.OnDark, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(tr("Add a new service"), style = fig(16, FontWeight.Bold, Tokens.BlueText))
            Text(tr("Steam press, curtains, shoes… anything else you do"), style = fig(13, color = Tokens.BlueText))
        }
        Box(Modifier.rounded(10.dp).background(Tokens.Blue).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(tr("Add"), style = fig(14, FontWeight.Bold, Tokens.OnDark))
        }
    }
}

// ───────────────────────── edit ─────────────────────────

@Composable
private fun EditServicePage(
    original: Service, canDelete: Boolean,
    onSave: (Service) -> Unit, onDelete: () -> Unit, onBack: () -> Unit,
) {
    var draft by remember(original.id) { mutableStateOf(original) }
    BackHandler(onBack = onBack)
    // Open on the name, keyboard up (owner, 2026-10-28).
    val nameFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(original.id) { nameFocus.requestFocus(); keyboard?.show() }

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
        TopBar(tr("Edit service"), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(tr("Change only what you need. Everything else can stay as it is."), style = fig(15, color = Tokens.Muted))

            SectionCard(tr("Service name")) {
                FieldBox(draft.name, { draft = draft.copy(name = it) }, height = 56.dp, borderColor = Tokens.FieldBorder, textStyle = fig(18, FontWeight.Bold), fieldModifier = Modifier.focusRequester(nameFocus))
            }

            ChargeByCard(draft) { draft = it }

            if (draft.mode == PricingMode.PIECE) PerPieceCard(draft) { draft = it }
            else ByWeightCard(draft) { draft = it }

            ReadyInCard(draft.readyInDays) { draft = draft.copy(readyInDays = it) }
        }

        Row(Modifier.fillMaxWidth().background(Tokens.Card).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canDelete) {
                OutlineButton(tr("Delete"), height = 56.dp, border = Tokens.OrangeBorder, fg = Tokens.DeleteRed, onClick = onDelete)
            }
            PrimaryButton(tr("Save"), Modifier.weight(1f), height = 56.dp) { onSave(draft) }
        }
    }
}

// ───────────────────────── add ─────────────────────────

@Composable
private fun AddServicePage(existing: List<Service>, onAdd: (Service) -> Unit, onBack: () -> Unit) {
    val suggestions = listOf("Steam Press", "Shoe Cleaning", "Curtain Wash", "Carpet Cleaning", "Starch")
    // standard clothes (names from an existing per-piece service), all prices empty
    val baseItems = existing.firstOrNull { it.mode == PricingMode.PIECE }?.items?.map { ServiceItem(it.name, null) } ?: emptyList()

    var draft by remember {
        mutableStateOf(
            Service(
                id = "s${System.currentTimeMillis()}", name = "", mode = PricingMode.PIECE,
                ratePerKg = null, minKg = null, readyInDays = null, lockedToPiece = false,
                items = baseItems, sortOrder = existing.size,
            )
        )
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
        TopBar(tr("Add new service"), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InfoBox(tr("Add a new service"), tr("Add only if you do a service that is not in your list. You can skip this and add it later too."))

            SectionCard(tr("What is the service called?"), tr("Type a name or tap one below.")) {
                FieldBox(draft.name, { draft = draft.copy(name = it) }, placeholder = tr("e.g. Steam Press"), height = 56.dp)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { name -> PillChip(name, draft.name == name) { draft = draft.copy(name = name) } }
                }
            }

            ChargeByCard(draft) { draft = it }

            if (draft.mode == PricingMode.PIECE) PerPieceCard(draft, emptyHint = true) { draft = it }
            else ByWeightCard(draft) { draft = it }

            ReadyInCard(draft.readyInDays) { draft = draft.copy(readyInDays = it) }
        }

        Box(Modifier.fillMaxWidth().background(Tokens.Card).padding(16.dp)) {
            val named = draft.name.isNotBlank()
            PrimaryButton(if (named) tr("Add service") else tr("Type a name to add"), enabled = named, height = 56.dp) {
                onAdd(
                    draft.copy(
                        name = draft.name.trim(),
                        ratePerKg = if (draft.mode == PricingMode.WEIGHT) (draft.ratePerKg ?: 0) else null,
                        minKg = if (draft.mode == PricingMode.WEIGHT) (draft.minKg ?: 0.0) else null,
                    )
                )
            }
        }
    }
}

// ───────────────────────── shared cards ─────────────────────────

@Composable
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().rounded(16.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(if (subtitle != null) 2.dp else 10.dp),
    ) {
        Text(title, style = fig(16, FontWeight.Bold))
        if (subtitle != null) {
            Text(subtitle, style = fig(13, color = Tokens.Muted))
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

@Composable
private fun ChargeByCard(draft: Service, onChange: (Service) -> Unit) {
    SectionCard(tr("How do you charge for this?")) {
        if (draft.lockedToPiece) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).rounded(11.dp).background(Tokens.NeutralFill).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Lock, null, tint = Tokens.InkSecondary, modifier = Modifier.size(16.dp))
                Text(tr("Dry cleaning is always per piece"), style = fig(14, FontWeight.SemiBold, Tokens.InkSecondary))
            }
        } else {
            Segmented(listOf(tr("Per piece") to (draft.mode == PricingMode.PIECE), tr("By weight") to (draft.mode == PricingMode.WEIGHT))) { i ->
                onChange(draft.copy(mode = if (i == 0) PricingMode.PIECE else PricingMode.WEIGHT))
            }
        }
    }
}

@Composable
private fun PerPieceCard(draft: Service, emptyHint: Boolean = false, onChange: (Service) -> Unit) {
    SectionCard(
        tr("Price of each piece"),
        if (emptyHint) tr("Fill only the clothes you take. Leave the rest empty.")
        else tr("Leave it empty if you don't take that cloth. Empty ones won't show in orders."),
    ) {
        draft.items.forEachIndexed { idx, item ->
            if (idx > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    item.name, style = fig(16, FontWeight.SemiBold, if (item.price != null) Tokens.Ink else Tokens.Muted),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                FieldBox(
                    item.price?.toString() ?: "",
                    { v ->
                        val p = v.filter { c -> c.isDigit() }.toIntOrNull()
                        onChange(draft.copy(items = draft.items.mapIndexed { j, it -> if (j == idx) it.copy(price = p) else it }))
                    },
                    prefix = "₹", placeholder = "—", modifier = Modifier.width(100.dp), height = 48.dp,
                    keyboardType = KeyboardType.Number, textStyle = fig(16, FontWeight.Bold),
                )
                Box(
                    Modifier.size(44.dp).rounded(10.dp)
                        .tap { onChange(draft.copy(items = draft.items.filterIndexed { j, _ -> j != idx })) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.DeleteOutline, tr("Remove {item}", "item" to item.name), tint = Tokens.DeleteRed, modifier = Modifier.size(20.dp))
                }
            }
        }
        var newName by remember { mutableStateOf("") }
        val name = newName.trim()
        val exists = draft.items.any { it.name.equals(name, ignoreCase = true) }
        val canAdd = name.isNotEmpty() && !exists
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldBox(
                newName, { newName = it }, placeholder = tr("Add a cloth, e.g. Blazer"), height = 48.dp, modifier = Modifier.weight(1f),
                imeAction = ImeAction.Done,
                onImeAction = { if (canAdd) { onChange(draft.copy(items = draft.items + ServiceItem(name, null))); newName = "" } },
            )
            Row(
                Modifier.height(48.dp).rounded(10.dp).background(if (canAdd) Tokens.BlueLight else Tokens.NeutralFill)
                    .tap(enabled = canAdd) {
                        onChange(draft.copy(items = draft.items + ServiceItem(name, null)))
                        newName = ""
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val c = if (canAdd) Tokens.Blue else Tokens.Muted
                Icon(Icons.Filled.Add, null, tint = c, modifier = Modifier.size(18.dp))
                Text(tr("Add"), style = fig(15, FontWeight.Bold, c))
            }
        }
        if (exists && name.isNotEmpty()) {
            Text(tr("{item} is already in the list", "item" to name), style = fig(13, color = Tokens.Muted), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ByWeightCard(draft: Service, onChange: (Service) -> Unit) {
    SectionCard(tr("Price by weight")) {
        Text(tr("Rate for 1 kg"), style = fig(14, FontWeight.Bold))
        Spacer(Modifier.height(4.dp))
        FieldBox(
            draft.ratePerKg?.toString() ?: "",
            { onChange(draft.copy(ratePerKg = it.filter { c -> c.isDigit() }.toIntOrNull())) },
            prefix = "₹", suffix = tr("per kg"), height = 64.dp, borderColor = Tokens.Blue, borderWidth = 2.dp,
            keyboardType = KeyboardType.Number, textStyle = bric(28, FontWeight.Bold),
        )
        Spacer(Modifier.height(10.dp))
        var minText by remember(draft.id) { mutableStateOf(draft.minKg?.takeIf { it > 0 }?.let(Selectors::trimKg) ?: "") }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(tr("Minimum weight (optional)"), style = fig(15, FontWeight.Bold))
                Text(tr("Small bags are charged for this much"), style = fig(13, color = Tokens.Muted))
            }
            FieldBox(
                minText,
                { v ->
                    val digits = v.filter { c -> c.isDigit() || c == '.' }
                    // one dot at most: "1.5", not "1.5.2"
                    val clean = digits.substringBefore('.') + (if ('.' in digits) "." + digits.substringAfter('.').replace(".", "").take(2) else "")
                    minText = clean.take(5)
                    onChange(draft.copy(minKg = minText.toDoubleOrNull()))
                },
                placeholder = "0", suffix = tr("kg"), modifier = Modifier.width(100.dp), height = 52.dp,
                keyboardType = KeyboardType.Decimal, textStyle = fig(16, FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun ReadyInCard(current: Int?, onPick: (Int?) -> Unit) {
    var moreOpen by remember { mutableStateOf(current != null && current >= 4) }
    var moreText by remember { mutableStateOf(if (current != null && current >= 4) current.toString() else "") }
    SectionCard(tr("Ready in (optional)"), tr("Fills the delivery date for you. Skip it if it changes every time.")) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to tr("Same day"), 1 to tr("1 day"), 2 to tr("2 days"), 3 to tr("3 days")).forEach { (v, label) ->
                PillChip(label, current == v && !moreOpen) { onPick(if (current == v && !moreOpen) null else v); moreOpen = false }
            }
            PillChip(tr("More"), moreOpen) {
                moreOpen = !moreOpen
                onPick(if (moreOpen) moreText.toIntOrNull() else null)
            }
        }
        if (moreOpen) {
            Spacer(Modifier.height(10.dp))
            FieldBox(
                moreText,
                { moreText = it.filter { c -> c.isDigit() }.take(2); onPick(moreText.toIntOrNull()) },
                placeholder = "4", suffix = tr("days"), modifier = Modifier.width(120.dp), height = 48.dp, keyboardType = KeyboardType.Number,
            )
        }
    }
}
