package com.dailyworks.apnalaundry.ui.screens.expenses

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.domain.EarningsMath
import com.dailyworks.apnalaundry.domain.Expense
import com.dailyworks.apnalaundry.domain.ExpenseCategory
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppCard
import com.dailyworks.apnalaundry.ui.components.FitText
import com.dailyworks.apnalaundry.ui.components.PillChip
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
 * Expenses (1.0.19): what the shop spends — rent, salary, detergent… — for a period, by
 * category, and the profit after them (money received − expenses, as Earnings counts money
 * received). For the shop on screen. Opened from Settings and Earnings ([from] = analytics);
 * adding or changing one opens [ExpenseFormScreen].
 */
@Composable
fun ExpensesScreen(shopVm: ShopViewModel, navigator: AppNavigator, from: String) {
    val state by shopVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var period by rememberSaveable { mutableStateOf("month") }
    var customFrom by rememberSaveable { mutableStateOf(AppDate.add(AppDate.TODAY, -29)) }
    var customTo by rememberSaveable { mutableStateOf(AppDate.TODAY) }

    // Once per visit (not again on coming back from the add / edit page).
    var logged by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (logged) return@LaunchedEffect
        logged = true
        Analytics.screen("expenses")
        Analytics.expensesOpened(from, state.expenses.size)
    }

    val today = AppDate.TODAY
    val weekStart = java.time.LocalDate.parse(today).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }.toString()
    val monthStart = today.substring(0, 8) + "01"
    val inRange: (String) -> Boolean = when (period) {
        "today" -> { iso -> iso == today }
        "week" -> { iso -> iso in weekStart..today }
        "all" -> { _ -> true }
        "custom" -> { iso -> iso in customFrom..customTo }
        else -> { iso -> iso in monthStart..today } // month
    }
    val periodTitle = when (period) {
        "today" -> tr("Today"); "week" -> tr("This week"); "all" -> tr("All time")
        "custom" -> if (customFrom == customTo) AppDate.plain(customFrom) else tr("{from} – {to}", "from" to AppDate.plain(customFrom), "to" to AppDate.plain(customTo))
        else -> tr("This month")
    }

    val list = state.expenses.filter { inRange(it.date) }
    val spent = list.sumOf { it.amt }
    val received = EarningsMath.compute(state, inRange).received
    val profit = received - spent
    val byCategory = list.groupBy { it.category }
        .map { (c, xs) -> c to xs.sumOf { it.amt } }
        .sortedByDescending { it.second }


    Column(Modifier.fillMaxSize().background(Tokens.Bg).windowInsetsPadding(WindowInsets.systemBars)) {
        TopBar(tr("Expenses"), onBack = { navigator.back() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("today" to tr("Today"), "week" to tr("This week"), "month" to tr("This month"), "all" to tr("All time"), "custom" to tr("Custom")).forEach { (v, label) ->
                    PillChip(label, period == v) { period = v; Analytics.expensesPeriodChanged(v) }
                }
            }
            if (period == "custom") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateBox(tr("From"), customFrom, Modifier.weight(1f)) {
                        showDatePicker(context, customFrom, null) { d ->
                            customFrom = d; if (customTo < d) customTo = d
                            Analytics.expensesCustomDateSet("from", AppDate.daysBetween(customFrom, customTo) + 1)
                        }
                    }
                    DateBox(tr("To"), customTo, Modifier.weight(1f)) {
                        showDatePicker(context, customTo, customFrom) { d ->
                            customTo = d
                            Analytics.expensesCustomDateSet("to", AppDate.daysBetween(customFrom, customTo) + 1)
                        }
                    }
                }
            }

            // Spent
            AppCard(bg = Tokens.Ink, borderColor = Tokens.Ink) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Spent · {period}", "period" to periodTitle), style = fig(13, FontWeight.SemiBold, Tokens.OnDarkMuted))
                    FitText(Money.rupees(spent), style = bric(34, FontWeight.Bold, Tokens.OnDark))
                }
            }

            // Profit = money received − expenses (needs the shop's takings: owners, or staff with "See earnings")
            if (com.dailyworks.apnalaundry.ui.screens.staff.LocalStaffAccess.current.can(com.dailyworks.apnalaundry.domain.StaffPermission.SEE_EARNINGS)) AppCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(tr("Profit"), style = fig(15, FontWeight.Bold))
                    Spacer(Modifier.height(4.dp))
                    MoneyLine(tr("Money received"), Money.rupees(received))
                    MoneyLine("− " + tr("Expenses"), Money.rupees(spent), Tokens.OrangeText)
                    Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(Tokens.Divider))
                    MoneyLine(
                        tr("Profit"),
                        (if (profit < 0) "−" else "") + Money.rupees(profit),
                        if (profit < 0) Tokens.ErrorRed else Tokens.GreenText,
                        bold = true,
                    )
                    Text(tr("Money received is counted like on Earnings."), style = fig(12, color = Tokens.Faint))
                }
            }

            // By category
            if (byCategory.isNotEmpty()) {
                AppCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr("Where the money went"), style = fig(15, FontWeight.Bold))
                        val top = byCategory.first().second.coerceAtLeast(1)
                        byCategory.forEach { (c, amt) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${ExpenseCategory.emoji(c)}  ${ExpenseCategory.label(c)}", style = fig(14, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                                    Text(Money.rupees(amt), style = fig(14, FontWeight.Bold))
                                }
                                Box(Modifier.fillMaxWidth().height(6.dp).rounded(99.dp).background(Tokens.NeutralFill)) {
                                    Box(Modifier.fillMaxWidth(amt.toFloat() / top).height(6.dp).rounded(99.dp).background(Tokens.Orange))
                                }
                            }
                        }
                    }
                }
            }

            // The list, newest day first
            if (list.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("🧾", style = fig(40))
                    Text(tr("No expenses here yet"), style = bric(20, FontWeight.Bold))
                    Text(
                        tr("Write down rent, salary, detergent and other costs to see your real profit."),
                        style = fig(14, color = Tokens.Muted), textAlign = TextAlign.Center,
                    )
                }
            } else {
                list.groupBy { it.date }.forEach { (day, xs) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (day == today) tr("Today") else AppDate.plain(day), style = fig(13, FontWeight.Bold, Tokens.Muted), modifier = Modifier.weight(1f))
                            Text(Money.rupees(xs.sumOf { it.amt }), style = fig(13, FontWeight.Bold, Tokens.Muted))
                        }
                        xs.forEach { x ->
                            ExpenseRow(x) {
                                Analytics.expenseRowTapped(x.category, ExpenseCategory.known(x.category) == null, x.amt)
                                navigator.openExpense(x.id)
                            }
                        }
                    }
                }
            }
        }
        PrimaryButton(
            "+ " + tr("Add expense"),
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Analytics.expenseAddTapped(state.expenses.size)
            navigator.openExpense("")
        }
    }
}

@Composable
private fun MoneyLine(label: String, value: String, color: androidx.compose.ui.graphics.Color = Tokens.Ink, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = fig(if (bold) 16 else 14, if (bold) FontWeight.Bold else FontWeight.Normal, if (bold) Tokens.Ink else Tokens.InkSecondary))
        Text(value, style = fig(if (bold) 16 else 14, FontWeight.Bold, color), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ExpenseRow(x: Expense, onClick: () -> Unit) {
    AppCard(Modifier.tap(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).rounded(999.dp).background(Tokens.OrangeLight), contentAlignment = Alignment.Center) {
                Text(ExpenseCategory.emoji(x.category), style = fig(18))
            }
            Column(Modifier.weight(1f)) {
                Text(ExpenseCategory.label(x.category), style = fig(15, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (x.note.isNotBlank()) Text(x.note, style = fig(13, color = Tokens.Muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(Money.rupees(x.amt), style = fig(16, FontWeight.Bold))
        }
    }
}
