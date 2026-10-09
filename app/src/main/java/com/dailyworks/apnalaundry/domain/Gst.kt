package com.dailyworks.apnalaundry.domain

import com.dailyworks.apnalaundry.core.Money
import kotlin.math.abs

/**
 * GST on an order — exclusive (added on top of the price) or inclusive
 * (already in the price). [calc] is the ONE place the tax maths lives:
 * [LaundryMath.amtOf] is its total, and every screen, bill design, khata
 * entry and payment goes through that. Port twin: web/src/domain/gst.ts.
 *
 * Tax figures are kept in paise (Long) so web and Android agree to the paisa;
 * the total a customer pays stays in whole rupees.
 */
object Gst {
    const val EXCL = "excl" // added on top of the price
    const val INCL = "incl" // already in the price
    const val DEFAULT_PCT = 18.0
    val RATE_CHIPS = listOf(5.0, 12.0, 18.0)

    fun modeOrDefault(m: String?): String = if (m == INCL) INCL else EXCL

    /** A rate from the wire or a text box: 0–100 (two decimals), else [fallback]. */
    fun pctFrom(v: Double?, fallback: Double = 0.0): Double =
        if (v != null && v.isFinite() && v >= 0) minOf(100.0, Math.round(v * 100) / 100.0) else fallback

    fun pctFrom(text: String, fallback: Double = 0.0): Double = pctFrom(text.toDoubleOrNull(), fallback)

    data class Breakdown(
        val on: Boolean,         // false = no GST on this amount (switched off, 0%, or nothing to tax)
        val pct: Double,
        val mode: String,
        val base: Int,           // ₹ clothes + express + fee − discount
        val taxablePaise: Long,
        val taxPaise: Long,
        val cgstPaise: Long,
        val sgstPaise: Long,
        val roundOffPaise: Long, // exclusive only: whole-rupee total − exact amount (−20 = −₹0.20)
        val total: Int,          // ₹ the customer pays, whole rupees
    )

    /**
     * The maths (HANDOFF-gst §3), on [base] = clothes + express + fee − discount:
     *   exclusive: tax = round2(base × pct/100); total = round(base + tax); roundOff = total − (base + tax)
     *   inclusive: total = base; taxable = round2(base × 100/(100+pct)); tax = base − taxable
     *   CGST = half the tax (rounded down to the paisa), SGST = the rest.
     */
    fun calc(base: Int, on: Boolean, pct: Double, mode: String): Breakdown {
        val rate = if (on) pct else 0.0
        if (rate <= 0 || base <= 0) return Breakdown(false, 0.0, mode, base, base * 100L, 0, 0, 0, 0, base)
        val basePaise = base * 100L
        val taxablePaise: Long
        val taxPaise: Long
        val total: Int
        val roundOffPaise: Long
        if (mode == INCL) {
            taxablePaise = Math.round(basePaise * 100.0 / (100 + rate))
            taxPaise = basePaise - taxablePaise
            total = base
            roundOffPaise = 0
        } else {
            taxablePaise = basePaise
            taxPaise = Math.round(basePaise * rate / 100)
            total = Math.round((basePaise + taxPaise) / 100.0).toInt()
            roundOffPaise = total * 100L - (basePaise + taxPaise)
        }
        val cgst = taxPaise / 2
        return Breakdown(true, rate, mode, base, taxablePaise, taxPaise, cgst, taxPaise - cgst, roundOffPaise, total)
    }

    // ── labels shared by the screens and the bill ──

    /** "18", "2.5" — a rate without trailing zeros. */
    fun fmtPct(pct: Double): String {
        val r = Math.round(pct * 100) / 100.0
        return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
    }

    /** "9" for 18% — the CGST / SGST half. */
    fun halfRate(pct: Double): String = fmtPct(pct / 2)

    /** App screens: the GST row above the total (exclusive). [markRounded] folds the round-off into the label. */
    fun rowLabel(g: Breakdown, markRounded: Boolean = false): String =
        "GST ${fmtPct(g.pct)}% (CGST + SGST)" + (if (markRounded && g.roundOffPaise != 0L) " · rounded" else "")

    fun rowValue(g: Breakdown): String = "+ ${Money.paise(g.taxPaise)}"

    fun roundOffValue(g: Breakdown): String = (if (g.roundOffPaise < 0) "− " else "+ ") + Money.paise(abs(g.roundOffPaise))

    /** App screens: the small line under the total (inclusive). */
    fun inclusiveLine(g: Breakdown): String =
        "Includes GST ${fmtPct(g.pct)}%: ${Money.paise(g.taxPaise)} · taxable ${Money.paise(g.taxablePaise)}"

    /** Printed bill: the note under the total (inclusive). */
    fun billNote(g: Breakdown): String =
        "Price includes GST ${fmtPct(g.pct)}%: taxable ${Money.paise(g.taxablePaise)} + CGST ${Money.paise(g.cgstPaise)} + SGST ${Money.paise(g.sgstPaise)}"

    /** New-order card: what the switch does right now. */
    fun hint(on: Boolean, pct: Double, mode: String): String =
        if (!on) "Add GST to this bill"
        else "${fmtPct(pct)}% · " + (if (mode == INCL) "already in your price" else "added on top of the price")
}
