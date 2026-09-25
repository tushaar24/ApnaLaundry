package com.dailyworks.apnalaundry.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Date helpers ported from the prototype's `dates()`.
 * ISO dates are "yyyy-MM-dd" strings. "Today" is pinned to the seed date so the
 * bundled demo data (pending / late / delivered orders) stays coherent.
 */
object AppDate {
    /** Pinned "today" matching the prototype seed (Fri 25 Sep 2026). */
    const val TODAY = "2026-09-25"

    /** Pinned "now" = 9:10 PM, so the Close-today banner and late-night rules demo correctly. */
    const val NOW_MINUTES = 21 * 60 + 10

    fun nowText(): String {
        val hh = NOW_MINUTES / 60
        val disp = if (hh % 12 == 0) 12 else hh % 12
        val ap = if (hh >= 12) "PM" else "AM"
        return "$disp:${(NOW_MINUTES % 60).toString().padStart(2, '0')} $ap"
    }

    /** True after 9 PM — ready-messages are queued for 9 AM. */
    fun isLateNight(): Boolean = NOW_MINUTES >= 21 * 60

    private val MON = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val DAY_SHORT = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val DAY_LONG = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    fun parse(iso: String): LocalDate = LocalDate.parse(iso)
    fun iso(d: LocalDate): String = d.toString()

    fun add(iso: String, days: Int): String = iso(parse(iso).plusDays(days.toLong()))

    fun daysBetween(from: String, to: String): Int =
        ChronoUnit.DAYS.between(parse(from), parse(to)).toInt()

    private fun dayIndex(d: LocalDate): Int = d.dayOfWeek.value % 7 // Mon=1..Sun=7 -> Sun=0

    fun rel(iso: String, today: String = TODAY): String? {
        val df = daysBetween(today, iso)
        return when (df) {
            0 -> "Today"; 1 -> "Tomorrow"; -1 -> "Yesterday"; else -> null
        }
    }

    /** "Sunday, 27 Sep" / "Today, 25 Sep" */
    fun long(iso: String, today: String = TODAY): String {
        val d = parse(iso)
        val prefix = rel(iso, today) ?: DAY_LONG[dayIndex(d)]
        return "$prefix, ${d.dayOfMonth} ${MON[d.monthValue - 1]}"
    }

    /** "Sun, 27 Sep" / "Today, 25 Sep" */
    fun short(iso: String, today: String = TODAY): String {
        val d = parse(iso)
        val prefix = rel(iso, today) ?: DAY_SHORT[dayIndex(d)]
        return "$prefix, ${d.dayOfMonth} ${MON[d.monthValue - 1]}"
    }

    /** "Fri, 25 Sep" — never relative. */
    fun plain(iso: String): String {
        val d = parse(iso)
        return "${DAY_SHORT[dayIndex(d)]}, ${d.dayOfMonth} ${MON[d.monthValue - 1]}"
    }

    fun dayName(iso: String): String = DAY_SHORT[dayIndex(parse(iso))]
    fun dayOfMonth(iso: String): Int = parse(iso).dayOfMonth

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
