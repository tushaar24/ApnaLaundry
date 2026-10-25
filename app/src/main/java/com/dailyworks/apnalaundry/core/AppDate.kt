package com.dailyworks.apnalaundry.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Date helpers ported from the prototype's `dates()`.
 * ISO dates are "yyyy-MM-dd" strings. "Today" and "now" read the device clock
 * (mirror of web/src/core/appdate.ts, which pins only when told to).
 */
object AppDate {
    /** Today's ISO date on the device clock. */
    val TODAY: String get() = LocalDate.now().toString()

    /** Minutes since midnight now (drives the Close-today banner and late-night rules). */
    val NOW_MINUTES: Int get() = LocalTime.now().let { it.hour * 60 + it.minute }

    fun nowText(): String {
        val hh = NOW_MINUTES / 60
        val disp = if (hh % 12 == 0) 12 else hh % 12
        val ap = if (hh >= 12) "PM" else "AM"
        return "$disp:${(NOW_MINUTES % 60).toString().padStart(2, '0')} $ap"
    }

    /** True after 9 PM — ready-messages are queued for 9 AM. */
    fun isLateNight(): Boolean = NOW_MINUTES >= 21 * 60

    // English words, shown through tr() (the bill builds its dates inside I18n.english).
    private val MON = listOf(
        i18n("Jan"), i18n("Feb"), i18n("Mar"), i18n("Apr"), i18n("May"), i18n("Jun"),
        i18n("Jul"), i18n("Aug"), i18n("Sep"), i18n("Oct"), i18n("Nov"), i18n("Dec"),
    )
    private val DAY_SHORT = listOf(i18n("Sun"), i18n("Mon"), i18n("Tue"), i18n("Wed"), i18n("Thu"), i18n("Fri"), i18n("Sat"))
    private val DAY_LONG = listOf(
        i18n("Sunday"), i18n("Monday"), i18n("Tuesday"), i18n("Wednesday"), i18n("Thursday"), i18n("Friday"), i18n("Saturday"),
    )

    private fun mon(d: LocalDate): String = tr(MON[d.monthValue - 1])
    private fun dayShort(d: LocalDate): String = tr(DAY_SHORT[dayIndex(d)])

    fun parse(iso: String): LocalDate = LocalDate.parse(iso)
    fun iso(d: LocalDate): String = d.toString()

    fun add(iso: String, days: Int): String = iso(parse(iso).plusDays(days.toLong()))

    fun daysBetween(from: String, to: String): Int =
        ChronoUnit.DAYS.between(parse(from), parse(to)).toInt()

    private fun dayIndex(d: LocalDate): Int = d.dayOfWeek.value % 7 // Mon=1..Sun=7 -> Sun=0

    fun rel(iso: String, today: String = TODAY): String? {
        val df = daysBetween(today, iso)
        return when (df) {
            0 -> tr("Today"); 1 -> tr("Tomorrow"); -1 -> tr("Yesterday"); else -> null
        }
    }

    /** "Sunday, 27 Sep" / "Today, 25 Sep" */
    fun long(iso: String, today: String = TODAY): String {
        val d = parse(iso)
        val prefix = rel(iso, today) ?: tr(DAY_LONG[dayIndex(d)])
        return "$prefix, ${d.dayOfMonth} ${mon(d)}"
    }

    /** "Sun, 27 Sep" / "Today, 25 Sep" */
    fun short(iso: String, today: String = TODAY): String {
        val d = parse(iso)
        val prefix = rel(iso, today) ?: dayShort(d)
        return "$prefix, ${d.dayOfMonth} ${mon(d)}"
    }

    /** "Fri, 25 Sep" — never relative. */
    fun plain(iso: String): String {
        val d = parse(iso)
        return "${dayShort(d)}, ${d.dayOfMonth} ${mon(d)}"
    }

    fun dayName(iso: String): String = dayShort(parse(iso))
    fun dayOfMonth(iso: String): Int = parse(iso).dayOfMonth

    /** "03/10/2026" — the Invoice bill's dates. Blank in -> blank out. */
    fun dmy(iso: String): String {
        if (iso.isBlank()) return ""
        val d = parse(iso)
        return "${d.dayOfMonth.toString().padStart(2, '0')}/${d.monthValue.toString().padStart(2, '0')}/${d.year}"
    }

    /** 24h "HH:mm" -> "5:00 PM". Blank in -> blank out. */
    fun to12h(hhmm: String): String {
        if (hhmm.isBlank()) return ""
        val parts = hhmm.split(":")
        val h = parts[0].toIntOrNull() ?: return ""
        val m = parts.getOrNull(1) ?: "00"
        val hr = h % 12
        val disp = if (hr == 0) 12 else hr
        val ap = if (h >= 12) "PM" else "AM"
        return "$disp:${m.padStart(2, '0')} $ap"
    }

    /** "5:00 PM" -> "17:00". Blank -> blank. */
    fun to24h(t12: String): String {
        if (t12.isBlank()) return ""
        val m = Regex("(\\d+):(\\d+)\\s*(AM|PM)", RegexOption.IGNORE_CASE).find(t12) ?: return ""
        var hh = m.groupValues[1].toInt() % 12
        if (m.groupValues[3].uppercase() == "PM") hh += 12
        return "${hh.toString().padStart(2, '0')}:${m.groupValues[2]}"
    }
}
