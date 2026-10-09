package com.dailyworks.apnalaundry.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Indian-grouped rupee formatting: ₹1,25,000. Mirrors the prototype's `money()`. */
object Money {
    fun grouping(value: Long): String {
        val neg = value < 0
        var s = abs(value).toString()
        if (s.length > 3) {
            val head = s.substring(0, s.length - 3)
            val tail = s.substring(s.length - 3)
            val chunks = head.reversed().chunked(2).map { it.reversed() }.reversed()
            s = chunks.joinToString(",") + "," + tail
        }
        return (if (neg) "-" else "") + s
    }

    /** ₹ with the absolute value, Indian-grouped (callers prepend +/− as needed). */
    fun rupees(value: Int): String = "₹" + grouping(abs(value).toLong())
    fun rupees(value: Long): String = "₹" + grouping(abs(value))
    fun rupees(value: Double): String = "₹" + grouping(abs(value).roundToLong())

    /** ₹ with paise, for GST lines: ₹1,234.56 (absolute value; callers prepend +/−). */
    fun paise(p: Long): String = "₹" + grouping(abs(p) / 100) + "." + String.format(Locale.US, "%02d", abs(p) % 100)

    /** Compact form for charts: 2.3k, 980. */
    fun short(value: Int): String =
        if (value >= 1000) (value / 1000.0).let { v ->
            val t = String.format("%.1f", v).removeSuffix(".0")
            "${t}k"
        } else value.toString()
}
