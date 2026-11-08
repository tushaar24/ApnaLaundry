package com.dailyworks.apnalaundry.ui.components

import android.app.DatePickerDialog
import android.content.Context
import com.dailyworks.apnalaundry.core.AppDate
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The system date picker, seeded at [seedIso]. [minIso] (optional) is the
 * earliest pickable date — e.g. delivery can't be before pickup. Past dates
 * are allowed when it's null (orders entered after the fact).
 */
fun showDatePicker(context: Context, seedIso: String, minIso: String?, onSet: (String) -> Unit) =
    showDatePicker(context, seedIso, minIso, null, onSet)

/** The same, with [maxIso] the latest pickable day (null = any). */
fun showDatePicker(context: Context, seedIso: String, minIso: String?, maxIso: String?, onSet: (String) -> Unit) {
    val d = runCatching { LocalDate.parse(seedIso) }.getOrElse { LocalDate.parse(AppDate.TODAY) }
    val dlg = DatePickerDialog(
        context,
        { _, y, m, day ->
            val picked = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, day)
            onSet(if (minIso != null && picked < minIso) minIso else if (maxIso != null && picked > maxIso) maxIso else picked)
        },
        d.year, d.monthValue - 1, d.dayOfMonth,
    )
    if (minIso != null) runCatching {
        val minMillis = LocalDate.parse(minIso).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // The start of the first allowed day (local time): the day before can't be picked.
        dlg.datePicker.minDate = minMillis
    }
    // [maxIso]: the latest pickable day (expenses: today — money already spent).
    if (maxIso != null) runCatching {
        dlg.datePicker.maxDate = LocalDate.parse(maxIso).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    }
    dlg.show()
}
