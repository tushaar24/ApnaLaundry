package com.dailyworks.apnalaundry.ui.screens.rates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.domain.PricingMode
import com.dailyworks.apnalaundry.domain.Service
import com.dailyworks.apnalaundry.domain.ServiceItem
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.Segmented
import com.dailyworks.apnalaundry.ui.components.TopBar
import com.dailyworks.apnalaundry.ui.components.dashedBorder
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.theme.Tokens

/**
 * "Add new service", the from-scratch version (owner's mock, 2026-10-28): a
 * name, per piece / per kg, then clothes typed one by one with their price (or
 * one per-kg rate). No suggestions, no sample prices, no ready-in. Used by the
 * services-from-scratch A/B group (ExperimentsRepository.SERVICES_SCRATCH), where a new
 * shop has no services until it adds one from New order.
 */
@Composable
fun AddServiceSimplePage(existing: List<Service>, onAdd: (Service) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(PricingMode.PIECE) }
    val rows = remember { mutableStateListOf(ClothRow()) }
    var rateText by remember { mutableStateOf("") }
    var minText by remember { mutableStateOf("") }
    BackHandler(onBack = onBack)
    val nameFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { nameFocus.requestFocus(); keyboard?.show() }

    val named = name.isNotBlank()
    val priced = if (mode == PricingMode.PIECE) rows.any { it.name.isNotBlank() && (it.price.toIntOrNull() ?: 0) > 0 }
    else (rateText.toIntOrNull() ?: 0) > 0
    val label = when {
        !named -> tr("Type service name")
        !priced && mode == PricingMode.PIECE -> tr("Add a cloth and its price")
        !priced -> tr("Enter the price per kg")
        else -> tr("Add service")
    }

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
        TopBar(tr("Add new service"), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(tr("Service name")) {
                FieldBox(
                    name, { name = it.take(40) }, placeholder = tr("e.g. Steam Press"), height = 56.dp,
                    capitalization = KeyboardCapitalization.Words, textStyle = fig(17, FontWeight.SemiBold),
                    fieldModifier = Modifier.focusRequester(nameFocus),
                )
            }

            Card(tr("Charge by")) {
                Segmented(listOf(tr("Per piece") to (mode == PricingMode.PIECE), tr("Per kg") to (mode == PricingMode.WEIGHT))) { i ->
                    mode = if (i == 0) PricingMode.PIECE else PricingMode.WEIGHT
                }
            }

            if (mode == PricingMode.PIECE) {
                Card(tr("Clothes & price")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("Cloth"), style = fig(13, FontWeight.SemiBold, Tokens.Muted), modifier = Modifier.weight(1f))
                        Text(tr("Price"), style = fig(13, FontWeight.SemiBold, Tokens.Muted), modifier = Modifier.width(110.dp))
                        Spacer(Modifier.width(36.dp))
                    }
                    rows.forEachIndexed { i, row ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FieldBox(
                                row.name, { v -> rows[i] = row.copy(name = v.take(30)) },
                                placeholder = tr("e.g. Shirt"), height = 56.dp, modifier = Modifier.weight(1f),
                                capitalization = KeyboardCapitalization.Sentences,
                            )
                            FieldBox(
                                row.price, { v -> rows[i] = row.copy(price = v.filter { c -> c.isDigit() }.take(5)) },
                                prefix = "₹", placeholder = "0", height = 56.dp, modifier = Modifier.width(110.dp),
                                keyboardType = KeyboardType.Number, textStyle = fig(16, FontWeight.Bold),
                            )
                            Box(
                                Modifier.size(36.dp).rounded(8.dp).tap {
                                    if (rows.size > 1) rows.removeAt(i) else rows[i] = ClothRow()
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Close, tr("Remove {item}", "item" to row.name.ifBlank { tr("cloth") }), tint = Tokens.Muted, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).rounded(12.dp).background(Tokens.BlueLight)
                            .dashedBorder(Tokens.Blue, 1.5.dp, 12.dp)
                            .tap { rows.add(ClothRow()) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Filled.Add, null, tint = Tokens.Blue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Add cloth"), style = fig(16, FontWeight.Bold, Tokens.Blue))
                    }
                }
            } else {
                Card(tr("Price per kg")) {
                    FieldBox(
                        rateText, { rateText = it.filter { c -> c.isDigit() }.take(5) },
                        prefix = "₹", suffix = tr("per kg"), placeholder = "0", height = 64.dp,
                        borderColor = Tokens.Blue, borderWidth = 2.dp,
                        keyboardType = KeyboardType.Number, textStyle = fig(24, FontWeight.Bold),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Minimum weight (optional)"), style = fig(15, FontWeight.Bold))
                            Text(tr("Small bags are charged for this much"), style = fig(13, color = Tokens.Muted))
                        }
                        FieldBox(
                            minText,
                            { v ->
                                val digits = v.filter { c -> c.isDigit() || c == '.' }
                                val clean = digits.substringBefore('.') + (if ('.' in digits) "." + digits.substringAfter('.').replace(".", "").take(2) else "")
                                minText = clean.take(5)
                            },
                            placeholder = "0", suffix = tr("kg"), modifier = Modifier.width(100.dp), height = 52.dp,
                            keyboardType = KeyboardType.Decimal, textStyle = fig(16, FontWeight.Bold),
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().background(Tokens.Card).padding(16.dp)) {
            PrimaryButton(label, enabled = named && priced, height = 56.dp) {
                val weight = mode == PricingMode.WEIGHT
                onAdd(
                    Service(
                        id = "s${System.currentTimeMillis()}", name = name.trim(), mode = mode,
                        ratePerKg = if (weight) (rateText.toIntOrNull() ?: 0) else null,
                        minKg = if (weight) (minText.toDoubleOrNull() ?: 0.0) else null,
                        readyInDays = null, lockedToPiece = false,
                        items = if (weight) emptyList()
                        else rows.filter { it.name.isNotBlank() }.map { ServiceItem(it.name.trim(), it.price.toIntOrNull()?.takeIf { p -> p > 0 }) },
                        sortOrder = existing.size,
                    )
                )
            }
        }
    }
}

private data class ClothRow(val name: String = "", val price: String = "")

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().rounded(16.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = fig(17, FontWeight.Bold))
        content()
    }
}
