package com.dailyworks.apnalaundry.ui.screens.expenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.domain.Expense
import com.dailyworks.apnalaundry.domain.ExpenseCategory
import com.dailyworks.apnalaundry.domain.PayMethod
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppBottomSheet
import com.dailyworks.apnalaundry.ui.components.FieldBox
import com.dailyworks.apnalaundry.ui.components.OutlineButton
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.TopBar
import com.dailyworks.apnalaundry.ui.components.bric
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.showDatePicker
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.screens.earnings.DateBox
import com.dailyworks.apnalaundry.ui.theme.Tokens

/**
 * Add or change an expense (1.0.19), a page of its own: the amount, the day and an optional
 * note, then what it was for — optional ("+ Add category" opens [CategorySheet] over the page;
 * none = Other). [id] empty = a new expense.
 */
@Composable
fun ExpenseFormScreen(shopVm: ShopViewModel, navigator: AppNavigator, id: String) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val loaded by shopVm.loaded.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // An existing expense: wait for the shop's data (after Android restored the app it starts empty).
    if (id.isNotEmpty() && !loaded) {
        Box(Modifier.fillMaxSize().background(Tokens.Bg))
        return
    }
    val initial = remember(id) {
        if (id.isEmpty()) Expense("", AppDate.TODAY, 0, "", PayMethod.CASH, "", 0)
        else state.expenses.firstOrNull { it.id == id }
    }
    if (initial == null) { // deleted meanwhile (another phone)
        LaunchedEffect(Unit) { navigator.back() }
        return
    }
    // Deleted on another phone while this page is open: say so and leave — saving would bring it back.
    val gone = initial.id.isNotEmpty() && state.expenses.none { it.id == initial.id }
    LaunchedEffect(gone) {
        if (gone) { shopVm.showInfo(tr("This expense was deleted on another phone")); navigator.back() }
    }
    val isNew = initial.id.isEmpty()
    val mode = if (isNew) "new" else "edit"

    var amount by rememberSaveable { mutableStateOf(if (initial.amt > 0) initial.amt.toString() else "") }
    var date by rememberSaveable { mutableStateOf(initial.date) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var category by rememberSaveable { mutableStateOf(initial.category) }
    // Named in the category list: shown there until the page closes (it is saved with the expense).
    var added by rememberSaveable { mutableStateOf(listOf<String>()) }
    var choosing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) } // saved or deleted: the page is on its way out
    val amt = amount.toIntOrNull() ?: 0
    // Categories this shop added itself, most used first, then those named on this page.
    val custom = remember(state.expenses, added) {
        (added + state.expenses.map { it.category }
            .filter { it.isNotBlank() && ExpenseCategory.known(it) == null }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key })
            .distinctBy { it.lowercase() }
    }
    // How many of this shop's expenses use each category (lowercase name).
    val counts = remember(state.expenses) { state.expenses.groupingBy { it.category.lowercase() }.eachCount() }
    val amountFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        Analytics.expensePageShown(mode)
        if (isNew) runCatching { amountFocus.requestFocus() }
    }

    fun close() {
        Analytics.expensePageClosed(mode, amt > 0, category.isNotBlank())
        navigator.back()
    }
    BackHandler { close() }

    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
        TopBar(if (isNew) tr("Add expense") else tr("Edit expense"), onBack = { close() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Labelled(tr("Amount")) {
                FieldBox(
                    amount, { v -> amount = v.filter(Char::isDigit).take(7) },
                    Modifier.fillMaxWidth(), prefix = "₹", keyboardType = KeyboardType.Number,
                    height = 60.dp, textStyle = bric(24, FontWeight.Bold),
                    fieldModifier = Modifier.focusRequester(amountFocus),
                )
            }
            DateBox(tr("Date"), date, Modifier.fillMaxWidth()) {
                showDatePicker(context, date, null, maxIso = AppDate.TODAY) { d ->
                    if (d != date) Analytics.expenseDateChanged(mode, AppDate.daysBetween(d, AppDate.TODAY))
                    date = d
                }
            }
            Labelled(tr("Note (optional)")) {
                FieldBox(note, { note = it.take(200) }, Modifier.fillMaxWidth())
            }
            Labelled(tr("Category (optional)")) {
                if (category.isBlank()) {
                    OutlineButton("+ " + tr("Add category"), Modifier.fillMaxWidth(), height = 52.dp) {
                        Analytics.expenseCategoryTapped(mode, "add")
                        choosing = true
                    }
                } else {
                    CategoryBox(category, Modifier.fillMaxWidth()) {
                        Analytics.expenseCategoryTapped(mode, "change")
                        choosing = true
                    }
                }
            }
            if (!isNew) {
                OutlineButton(tr("Delete expense"), Modifier.fillMaxWidth().padding(top = 8.dp), border = Tokens.DeleteRed, fg = Tokens.DeleteRed) {
                    Analytics.expenseDeleteTapped(initial.category, initial.amt)
                    confirmDelete = true
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(Tokens.Card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                tr("Save"), Modifier.fillMaxWidth(), enabled = amt > 0, height = 56.dp,
                onDisabledClick = {
                    Analytics.expenseSaveBlocked(mode, "amount")
                },
            ) {
                if (done) return@PrimaryButton // a double tap on a slow phone: save once, go back once
                done = true
                // No category picked: Other.
                val x = initial.copy(amt = amt, category = category.ifBlank { ExpenseCategory.OTHER.key }, date = date, note = note.trim())
                shopVm.saveExpense(if (isNew) x.copy(id = shopVm.newExpenseId(), ts = System.currentTimeMillis()) else x, isNew, category.isNotBlank())
                navigator.back()
            }
        }
    }

    if (choosing) {
        CategorySheet(
            selected = category, custom = custom, counts = counts, mode = mode,
            onPick = { c ->
                if (ExpenseCategory.known(c) == null && custom.none { it.equals(c, ignoreCase = true) }) added = added + c
                category = c
                choosing = false
            },
            onRename = { old, new, merged ->
                shopVm.renameExpenseCategory(old, new, merged, mode)
                added = added.map { if (it.equals(old, ignoreCase = true)) new else it }
                    .filter { ExpenseCategory.known(it) == null }.distinctBy { it.lowercase() }
                if (category.equals(old, ignoreCase = true)) category = new
            },
            onDelete = { name ->
                shopVm.deleteExpenseCategory(name, mode)
                added = added.filterNot { it.equals(name, ignoreCase = true) }
                if (category.equals(name, ignoreCase = true)) category = ""
            },
            onBack = { choosing = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false; Analytics.expenseDeleteCancelled(initial.category, initial.amt) },
            title = { Text(tr("Delete this expense?"), style = fig(18, FontWeight.Bold)) },
            text = { Text(tr("{amount} for {what} will be removed.", "amount" to Money.rupees(initial.amt), "what" to ExpenseCategory.label(initial.category)), style = fig(15)) },
            confirmButton = {
                TextButton(onClick = { if (!done) { done = true; confirmDelete = false; shopVm.deleteExpense(initial); navigator.back() } }) {
                    Text(tr("Delete"), style = fig(15, FontWeight.Bold, Tokens.DeleteRed))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false; Analytics.expenseDeleteCancelled(initial.category, initial.amt) }) {
                    Text(tr("Cancel"), style = fig(15, FontWeight.Bold))
                }
            },
            containerColor = Tokens.Card,
        )
    }
}

@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = fig(14, FontWeight.SemiBold))
        content()
    }
}

/** The chosen category on the form, like the date box above it; a tap changes it. */
@Composable
private fun CategoryBox(category: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 52.dp).rounded(12.dp).background(Tokens.Card)
            .border(1.5.dp, Tokens.FieldBorder, RoundedCornerShape(12.dp)).tap(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(ExpenseCategory.emoji(category), style = fig(18))
        Text(ExpenseCategory.label(category), style = fig(15, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(tr("Change"), style = fig(14, FontWeight.Bold, Tokens.Blue))
    }
}

/**
 * A half-screen sheet over the form: every category in one list — the shop's own first
 * ([custom]), then the common ones — with a search box. "+ New" beside it switches the sheet to
 * a name box (whatever was searched filled in) with Cancel / Add; the "Add … as a new category"
 * row, shown when the search matches none exactly, adds the search at once. [onPick] gets the
 * stored category (a common key or the name). The shop's own categories have an edit button:
 * rename ([onRename] old, new, merged into one that exists) or delete ([onDelete]; its expenses
 * move to Other). The common ones can't be changed. [counts]: expenses per lowercase category.
 */
@Composable
private fun CategorySheet(
    selected: String, custom: List<String>, counts: Map<String, Int>, mode: String,
    onPick: (String) -> Unit, onRename: (old: String, new: String, merged: Boolean) -> Unit, onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) } // "+ New": naming a category
    var newName by remember { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    var editing by remember { mutableStateOf<String?>(null) } // one of the shop's own, being renamed / deleted
    var editName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val q = query.trim().replace(Regex("\\s+"), " ")
    val own = custom.map { Triple(it, ExpenseCategory.emoji(it), it) }
    val common = ExpenseCategory.entries.map { Triple(it.key, it.emoji, tr(it.label)) }
    fun matches(key: String, label: String) = q.isEmpty() || label.contains(q, ignoreCase = true) ||
        ExpenseCategory.known(key)?.label?.contains(q, ignoreCase = true) == true
    val ownShown = own.filter { matches(it.first, it.third) }
    val commonShown = common.filter { matches(it.first, it.third) }
    val exact = q.isNotEmpty() && (own + common).any { it.third.equals(q, ignoreCase = true) || it.first == ExpenseCategory.keyFor(q) }
    // Title and handle take the rest: the whole sheet is about half the screen.
    val listHeight = (LocalConfiguration.current.screenHeightDp * 0.5f - 130f).coerceAtLeast(220f).dp

    fun pick(key: String, how: String) {
        if (how == "picked") Analytics.expenseCategoryPicked(mode, key, ExpenseCategory.known(key) == null, q.isNotEmpty())
        Analytics.expenseCategorySheetClosed(mode, how, q.take(30))
        onPick(key)
    }

    /** [name] as the expense's category: a new one, or the existing one it names. */
    fun add(name: String, from: String) {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(30)
        if (clean.isEmpty()) return
        val key = ExpenseCategory.keyFor(clean)
        val existing = custom.firstOrNull { it.equals(key, ignoreCase = true) }
        if (existing == null && ExpenseCategory.known(key) == null) {
            Analytics.expenseCategoryAdded(mode, key, from)
            pick(key, "added")
        } else {
            pick(existing ?: key, "picked")
        }
    }

    /** The edited category's new name: a rename, a merge into one that exists, or nothing to do. */
    fun rename(old: String) {
        val clean = editName.trim().replace(Regex("\\s+"), " ").take(30)
        if (clean.isEmpty()) return
        val key = ExpenseCategory.keyFor(clean)
        val existing = custom.firstOrNull { it.equals(key, ignoreCase = true) && !it.equals(old, ignoreCase = true) }
        val to = existing ?: key
        if (to != old) onRename(old, to, existing != null || ExpenseCategory.known(key) != null)
        editing = null
    }

    AppBottomSheet(
        title = when {
            editing != null -> tr("Edit category")
            creating -> tr("Add category")
            else -> tr("Choose category")
        },
        onDismiss = { Analytics.expenseCategorySheetClosed(mode, "dismissed", q.take(30)); onBack() },
    ) {
        editing?.let { old ->
            val n = counts[old.lowercase()] ?: 0
            LaunchedEffect(old) { runCatching { nameFocus.requestFocus() } }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FieldBox(
                    editName, { editName = it.take(30) }, Modifier.fillMaxWidth(),
                    placeholder = tr("Category name"), height = 56.dp, textStyle = fig(17, FontWeight.SemiBold),
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done, onImeAction = { rename(old) },
                    fieldModifier = Modifier.focusRequester(nameFocus),
                )
                Text(
                    when (n) {
                        0 -> tr("No expenses use this category yet.")
                        1 -> tr("1 expense uses this category. Renaming changes it there too.")
                        else -> tr("{n} expenses use this category. Renaming changes it on all of them.", "n" to n)
                    },
                    style = fig(13, color = Tokens.Muted),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(tr("Cancel"), Modifier.weight(1f), height = 52.dp) {
                        Analytics.expenseCategoryEditCancelled(mode, old)
                        editing = null
                    }
                    PrimaryButton(tr("Save"), Modifier.weight(1f), enabled = editName.isNotBlank() && editName.trim() != old, height = 52.dp) { rename(old) }
                }
                Text(
                    tr("Delete category"), style = fig(15, FontWeight.Bold, Tokens.DeleteRed), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().tap {
                        Analytics.expenseCategoryDeleteTapped(mode, old, n)
                        confirmDelete = true
                    }.padding(vertical = 12.dp),
                )
            }
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false; Analytics.expenseCategoryDeleteCancelled(mode, old) },
                    title = { Text(tr("Delete \u201c{name}\u201d?", "name" to old), style = fig(18, FontWeight.Bold)) },
                    text = {
                        Text(
                            when (n) {
                                0 -> tr("It will be removed from your categories.")
                                1 -> tr("Its 1 expense will move to Other.")
                                else -> tr("Its {n} expenses will move to Other.", "n" to n)
                            },
                            style = fig(15),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { confirmDelete = false; editing = null; onDelete(old) }) {
                            Text(tr("Delete"), style = fig(15, FontWeight.Bold, Tokens.DeleteRed))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = false; Analytics.expenseCategoryDeleteCancelled(mode, old) }) {
                            Text(tr("Cancel"), style = fig(15, FontWeight.Bold))
                        }
                    },
                    containerColor = Tokens.Card,
                )
            }
            return@AppBottomSheet
        }
        if (creating) {
            LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FieldBox(
                    newName, { newName = it.take(30) }, Modifier.fillMaxWidth(),
                    placeholder = tr("Type the new category name"), height = 56.dp, textStyle = fig(17, FontWeight.SemiBold),
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done, onImeAction = { add(newName, "button") },
                    fieldModifier = Modifier.focusRequester(nameFocus),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(tr("Cancel"), Modifier.weight(1f), height = 52.dp) {
                        Analytics.expenseNewCategoryCancelled(mode, newName.isNotBlank())
                        creating = false
                    }
                    PrimaryButton(tr("Add"), Modifier.weight(1f), enabled = newName.isNotBlank(), height = 52.dp) { add(newName, "button") }
                }
            }
            return@AppBottomSheet
        }
        Column(Modifier.fillMaxWidth().height(listHeight), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldBox(
                    query, { query = it.take(30) }, Modifier.weight(1f),
                    placeholder = tr("Search category"), height = 48.dp,
                    capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done,
                    leading = { Icon(Icons.Outlined.Search, null, tint = Tokens.Muted, modifier = Modifier.size(20.dp)) },
                )
                OutlineButton("+ " + tr("New"), Modifier.width(92.dp), height = 48.dp) {
                    Analytics.expenseNewCategoryTapped(mode, "button", q.isNotEmpty())
                    newName = if (exact) "" else q
                    creating = true
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                if (q.isNotEmpty() && !exact) {
                    item(key = "add") {
                        Row(
                            Modifier.padding(bottom = 6.dp).fillMaxWidth().heightIn(min = 52.dp).rounded(12.dp).background(Tokens.BlueLight)
                                .tap { Analytics.expenseNewCategoryTapped(mode, "search_row", true); add(q, "search_row") }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("+", style = fig(20, FontWeight.Bold, Tokens.Blue))
                            Text(
                                tr("Add \u201c{name}\u201d as a new category", "name" to q.take(30)),
                                style = fig(15, FontWeight.Bold, Tokens.Blue), modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (ownShown.isNotEmpty()) {
                    if (q.isEmpty()) item(key = "own") { SectionLabel(tr("Your categories")) }
                    items(ownShown, key = { "o:" + it.first }) { (key, emoji, label) ->
                        CategoryRow(
                            emoji, label, key == selected,
                            onEdit = {
                                Analytics.expenseCategoryEditTapped(mode, key, counts[key.lowercase()] ?: 0)
                                editName = key
                                editing = key
                            },
                        ) { pick(key, "picked") }
                    }
                }
                if (commonShown.isNotEmpty()) {
                    if (q.isEmpty() && ownShown.isNotEmpty()) item(key = "common") { SectionLabel(tr("Common categories")) }
                    items(commonShown, key = { "c:" + it.first }) { (key, emoji, label) -> CategoryRow(emoji, label, key == selected) { pick(key, "picked") } }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = fig(13, FontWeight.Bold, Tokens.Muted), modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
private fun CategoryRow(emoji: String, label: String, selected: Boolean, onEdit: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).tap(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).rounded(999.dp).background(Tokens.OrangeLight), contentAlignment = Alignment.Center) {
            Text(emoji, style = fig(18))
        }
        Text(label, style = fig(15, if (selected) FontWeight.Bold else FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (selected) Icon(Icons.Filled.Check, null, tint = Tokens.Blue, modifier = Modifier.size(22.dp))
        // Only the shop's own categories: the common ones can't be renamed or deleted.
        onEdit?.let {
            Box(Modifier.size(40.dp).rounded(999.dp).tap(onClick = it), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Edit, tr("Edit category"), tint = Tokens.Muted, modifier = Modifier.size(20.dp))
            }
        }
    }
}
