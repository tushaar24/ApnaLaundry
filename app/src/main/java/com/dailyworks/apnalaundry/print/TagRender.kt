package com.dailyworks.apnalaundry.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.domain.LaundryTag
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

/**
 * Draws one laundry tag, black on white, [width] printer dots across — the
 * usual shop tag, every line centred:
 *
 *     Shop name
 *     CUSTOMER NAME
 *     #000059
 *     11 Oct 2026 · Due 15 Oct
 *     [ DC ] [EXPRESS]
 *     T-Shirt (Kids)
 *     2/5
 *     - - - - - - - -   (tear here)
 *
 * Drawn as a picture (not printer text) so Hindi names print too.
 */
object TagRender {
    /** Dots across 2-inch (58 mm) paper. */
    const val NARROW = 384
    /** Dots across 3-inch (80 mm) paper. */
    const val WIDE = 576

    fun render(t: LaundryTag, width: Int): Bitmap {
        val h = layout(null, t, width)
        val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        layout(c, t, width)
        return bmp
    }

    private val BOLD: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun paint(size: Float, face: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; textSize = size; typeface = face
    }

    private fun lh(p: Paint): Float = p.descent() - p.ascent()

    private fun fit(text: String, p: TextPaint, avail: Float): String =
        TextUtils.ellipsize(text, p, avail, TextUtils.TruncateAt.END).toString()

    /** "11 Oct 2026" */
    private fun day(iso: String, year: Boolean): String = runCatching {
        val d = AppDate.parse(iso)
        "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" + if (year) " ${d.year}" else ""
    }.getOrDefault("")

    /** Lays the tag out top to bottom; draws when [c] is given. Returns the height in dots. */
    private fun layout(c: Canvas?, t: LaundryTag, width: Int): Int {
        val s = width / NARROW.toFloat()
        val inner = width - 20 * s
        var y = 10 * s

        fun line(text: String, p: TextPaint, gapAfter: Float = 0f) {
            if (text.isBlank()) return
            val shown = fit(text, p, inner)
            c?.drawText(shown, (width - p.measureText(shown)) / 2, y - p.ascent(), p)
            y += lh(p) + gapAfter
        }

        line(t.shop, paint(24 * s, BOLD), 4 * s)
        line(t.customer.uppercase(), paint(26 * s, MEDIUM))
        line("#${t.orderNo}", paint(52 * s, BOLD))
        line(
            listOf(day(t.date, year = true), if (t.due.isNotBlank()) "Due ${day(t.due, year = false)}" else "")
                .filter { it.isNotEmpty() }.joinToString(" · "),
            paint(24 * s, MEDIUM),
        )

        // [ DC ] — and a filled [EXPRESS] beside it on rush orders.
        val boxText = paint(32 * s, BOLD)
        val boxes = listOfNotNull(t.code.ifBlank { null }, if (t.express) "EXPRESS" else null)
        if (boxes.isNotEmpty()) {
            y += 12 * s
            val padX = 26 * s; val boxH = lh(boxText) + 22 * s; val gap = 12 * s; val stroke = 3 * s
            // A short code still gets a good-sized box (about 40% of the tag).
            val widths = boxes.map { minOf(maxOf(boxText.measureText(it) + 2 * padX, 140 * s), inner) }
            var x = (width - (widths.sum() + gap * (boxes.size - 1))) / 2
            boxes.forEachIndexed { i, label ->
                val filled = t.express && i == boxes.lastIndex
                val r = RectF(x + stroke / 2, y + stroke / 2, x + widths[i] - stroke / 2, y + boxH - stroke / 2)
                c?.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK; style = if (filled) Paint.Style.FILL else Paint.Style.STROKE; strokeWidth = stroke
                })
                val p = TextPaint(boxText).apply { color = if (filled) Color.WHITE else Color.BLACK }
                val shown = fit(label, p, widths[i] - 2 * padX + 1)
                c?.drawText(shown, x + (widths[i] - p.measureText(shown)) / 2, y + 11 * s - p.ascent(), p)
                x += widths[i] + gap
            }
            y += boxH + 12 * s
        }

        // The cloth: a smaller size before it gets cut short.
        val item = paint(30 * s, MEDIUM)
        if (item.measureText(t.item) > inner) item.textSize = 24 * s
        line(t.item, item)
        line(t.note.replace(Regex("\\s+"), " ").let { if (it.isEmpty()) "" else "Note: $it" }, paint(22 * s, MEDIUM))
        line(t.count, paint(52 * s, BOLD))

        // - - - - tear here - - - -
        y += 10 * s
        c?.drawLine(0f, y, width.toFloat(), y, Paint().apply {
            color = Color.BLACK; strokeWidth = 3 * s; pathEffect = DashPathEffect(floatArrayOf(10 * s, 8 * s), 0f)
        })
        y += 3 * s + 12 * s
        return ceil(y).toInt()
    }
}
