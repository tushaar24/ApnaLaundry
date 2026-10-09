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
fun showDatePicker(context: Context, seedIso: String, minIso: String?, onSet: (String) -> Unit) {
    val d = runCatching { LocalDate.parse(seedIso) }.getOrElse { LocalDate.parse(AppDate.TODAY) }
    val dlg = DatePickerDialog(
        context,
        { _, y, m, day ->
            val picked = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, day)
            onSet(if (minIso != null && picked < minIso) minIso else picked)
        },
        d.year, d.monthValue - 1, d.dayOfMonth,
    )
    if (minIso != null) runCatching {
        val minMillis = LocalDate.parse(minIso).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        dlg.datePicker.minDate = minMillis - 24L * 60 * 60 * 1000 // margin avoids a timezone off-by-one
    }
    dlg.show()
}
