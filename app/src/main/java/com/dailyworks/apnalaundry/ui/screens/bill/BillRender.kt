package com.dailyworks.apnalaundry.ui.screens.bill

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import com.dailyworks.apnalaundry.R
import com.dailyworks.apnalaundry.domain.BillDetails
import com.dailyworks.apnalaundry.domain.BillReceipt
import com.dailyworks.apnalaundry.domain.CombinedBill
import com.dailyworks.apnalaundry.domain.CombinedReceipt
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a [BillReceipt] — or a [CombinedReceipt] — in its design (Classic /
 * Bold / Receipt). The same drawing backs the shared bill image, the "View
 * bill" sheet, the combined-bill preview and the onboarding preview, so what
 * the owner picks is what customers get.
 * Geometry matches the web renderer (web/src/ui/billRender.ts): a 380-pt
 * card drawn at [SCALE]× for crisp text.
 */
object BillRender {
    const val W = 380f
    private const val PAD = 22f
    const val SCALE = 3f

    private val INK = Color.parseColor("#16191D")
    private val MUTED = Color.parseColor("#5B6168")
    private val HEADING = Color.parseColor("#3F444A")
    private val BORDER = Color.parseColor("#E2DED6")
    private val BLUE = Color.parseColor("#1D4ED8")
    private val TINT = Color.parseColor("#E6ECFB")
    private val BAND_TEXT = Color.parseColor("#DCE5FB")
    private val GREEN = Color.parseColor("#15803D")
    private val GREEN_LIGHT = Color.parseColor("#E3F4E8")
    private val ORANGE = Color.parseColor("#8A3A06")
    private val BLUE_TEXT = Color.parseColor("#1E3A8A")
    private val SLIP = Color.parseColor("#FFFEFA")

    private class Fonts(val body: Typeface, val display: Typeface)

    private fun weight(base: Typeface, w: Int): Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, w, false)
        else Typeface.create(base, if (w >= 600) Typeface.BOLD else Typeface.NORMAL)

    private fun fonts(context: Context, receipt: Boolean): Fonts {
        if (receipt) return Fonts(Typeface.MONOSPACE, Typeface.MONOSPACE)
        val fig = runCatching { ResourcesCompat.getFont(context, R.font.figtree) }.getOrNull() ?: Typeface.SANS_SERIF
        val bric = runCatching { ResourcesCompat.getFont(context, R.font.bricolage_grotesque) }.getOrNull() ?: fig
        return Fonts(fig, bric)
    }

    // ---------------- single bill ----------------

    /** The bill's natural height in points (for equal-height slides). */
    fun height(context: Context, r: BillReceipt, logo: Bitmap?): Float =
        layout(context, Canvas(), r, logo, draw = false, minH = 0f)

    /** Renders the bill, optionally stretched to [minH] points ("Thank you" pinned to the bottom). */
    fun render(context: Context, r: BillReceipt, logo: Bitmap?, minH: Float = 0f): Bitmap =
        toBitmap(r.template, layout(context, Canvas(), r, logo, draw = false, minH = minH)) { c ->
            layout(context, c, r, logo, draw = true, minH = minH)
        }

    /** Draws the bill 1:1 onto a canvas of [W] × [height] points — a PDF page (vector text). */
    fun drawPage(context: Context, canvas: Canvas, r: BillReceipt, logo: Bitmap?) {
        canvas.drawColor(paper(r.template))
        layout(context, canvas, r, logo, draw = true, minH = 0f)
    }

    // ---------------- combined bill ----------------

    fun height(context: Context, r: CombinedReceipt, logo: Bitmap?): Float =
        layoutCombined(context, Canvas(), r, logo, draw = false, minH = 0f)

    fun render(context: Context, r: CombinedReceipt, logo: Bitmap?, minH: Float = 0f): Bitmap =
        toBitmap(r.template, layoutCombined(context, Canvas(), r, logo, draw = false, minH = minH)) { c ->
            layoutCombined(context, c, r, logo, draw = true, minH = minH)
        }

    fun drawPage(context: Context, canvas: Canvas, r: CombinedReceipt, logo: Bitmap?) {
        canvas.drawColor(paper(r.template))
        layoutCombined(context, canvas, r, logo, draw = true, minH = 0f)
    }

    // ---------------- shared plumbing ----------------

    private fun paper(template: String) = if (template == BillDetails.RECEIPT) SLIP else Color.WHITE

    private fun toBitmap(template: String, heightPt: Float, drawOn: (Canvas) -> Unit): Bitmap {
        val h = ceil(heightPt)
        val bmp = Bitmap.createBitmap((W * SCALE).toInt(), (h * SCALE).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(SCALE, SCALE)
        canvas.drawColor(paper(template))
        drawOn(canvas)
        return bmp
    }

    private fun wrap(p: Paint, text: String, maxW: Float): List<String> {
        val out = mutableListOf<String>()
        var cur = ""
        for (word in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val next = if (cur.isEmpty()) word else "$cur $word"
            if (cur.isNotEmpty() && p.measureText(next) > maxW) {
                out.add(cur); cur = word
            } else cur = next
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out.ifEmpty { listOf("") }
    }

    /**
     * One pass over a bill: a cursor [cy] moving down the card plus the text,
     * rule and logo primitives, and the blocks both bill kinds share (shop
     * header, customer, total band, UPI QR, terms, thank-you). With [draw]
     * off nothing is painted — the pass only measures.
     */
    private class Pen(
        context: Context, val canvas: Canvas, val template: String, val logo: Bitmap?, val logoId: String, val draw: Boolean,
    ) {
        val receipt = template == BillDetails.RECEIPT
        val bold = template == BillDetails.BOLD
        val f = fonts(context, receipt)
        val left = PAD
        val right = W - PAD
        val mid = W / 2
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        var cy = 0f

        fun font(size: Float, w: Int = 400, display: Boolean = false) {
            p.textSize = size
            p.typeface = weight(if (display) f.display else f.body, w)
        }

        fun text(s: String, x: Float, yy: Float, align: Paint.Align, color: Int = INK) {
            if (!draw) return
            p.style = Paint.Style.FILL
            p.color = color
            p.textAlign = align
            canvas.drawText(s, x, yy, p)
        }

        fun rule(atY: Float, width: Float = 1f, color: Int = if (receipt) INK else BORDER, dashed: Boolean = false) {
            if (!draw) return
            val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; strokeWidth = width; style = Paint.Style.STROKE
                if (dashed || receipt) pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
            }
            canvas.drawLine(left, atY, right, atY, rp)
        }

        fun fill(rect: RectF, color: Int, radius: Float) {
            if (!draw) return
            p.style = Paint.Style.FILL
            p.color = color
            if (radius > 0) canvas.drawRoundRect(rect, radius, radius, p) else canvas.drawRect(rect, p)
        }

        fun drawLogo(size: Float, x: Float, yy: Float, gray: Boolean) {
            val bmp = logo ?: return
            if (!draw) return
            val k = min(size / bmp.width, size / bmp.height)
            val dw = bmp.width * k
            val dh = bmp.height * k
            val lp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (gray) {
                // Thermal printers print only black: grayscale + a little contrast.
                val cm = ColorMatrix().apply { setSaturation(0f) }
                val c = 1.25f
                val o = 128f * (1 - c)
                cm.postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, o, 0f, c, 0f, 0f, o, 0f, 0f, c, 0f, o, 0f, 0f, 0f, 1f, 0f)))
                lp.colorFilter = ColorMatrixColorFilter(cm)
            }
            val dx = x + (size - dw) / 2
            val dy = yy + (size - dh) / 2
            canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), lp)
        }

        /** Wrapped text: each line advances the cursor by [lh] before it is drawn. */
        fun lines(s: String, size: Float, w: Int, display: Boolean, maxW: Float, x: Float, align: Paint.Align, color: Int, lh: Float) {
            font(size, w, display)
            for (l in wrap(p, s, maxW)) {
                cy += lh
                text(l, x, cy, align, color)
            }
        }

        /** 1. Shop block: logo, name, phone / email / address / GSTIN — a blue band on Bold, centred otherwise. */
        fun shopBlock(name: String, phone: String, email: String, address: String, gstin: String) {
            val hasLogo = logo != null && logoId.isNotEmpty()
            val shopName = if (receipt) name.uppercase() else name
            val details = listOf(phone, email, address, if (gstin.isNotEmpty()) "GSTIN $gstin" else "").filter { it.isNotEmpty() }
            if (bold) {
                val textX = left + if (hasLogo) 56f else 0f
                val textW = right - textX
                font(22f, 700, display = true)
                var textH = wrap(p, shopName, textW).size * 26f
                font(12.5f, 400)
                details.forEach { textH += wrap(p, it, textW).size * 17f }
                val bandH = max(if (hasLogo) 44f else 0f, textH) + PAD * 2 - 4
                fill(RectF(0f, 0f, W, bandH), BLUE, 0f)
                if (hasLogo) {
                    val ty = PAD - 2 + (max(44f, textH) - 44f) / 2
                    fill(RectF(left, ty, left + 44, ty + 44), Color.WHITE, 10f)
                    drawLogo(36f, left + 4, ty + 4, false)
                }
                cy = PAD - 2 + max(0f, ((if (hasLogo) 44f else 0f) - textH) / 2) - 6
                lines(shopName, 22f, 700, true, textW, textX, Paint.Align.LEFT, Color.WHITE, 26f)
                details.forEach { lines(it, 12.5f, 400, false, textW, textX, Paint.Align.LEFT, BAND_TEXT, 17f) }
                cy = bandH + 6
            } else {
                cy = PAD
                val size = if (receipt) 44f else 52f
                if (hasLogo) {
                    drawLogo(size, mid - size / 2, cy, receipt)
                    cy += size + 4
                }
                if (receipt) lines(shopName, 18f, 700, true, right - left, mid, Paint.Align.CENTER, INK, 22f)
                else lines(shopName, 24f, 700, true, right - left, mid, Paint.Align.CENTER, INK, 28f)
                details.forEach { lines(it, if (receipt) 12f else 13f, 400, false, right - left, mid, Paint.Align.CENTER, MUTED, 17f) }
                cy += 14
                rule(cy)
            }
        }

        /** 3. Customer name + phone, then a rule. */
        fun customerBlock(name: String, phone: String) {
            if (name.isNotEmpty()) lines(name, 15f, 700, false, right - left, left, Paint.Align.LEFT, INK, 22f)
            if (phone.isNotEmpty()) lines(phone, 13f, 400, false, right - left, left, Paint.Align.LEFT, MUTED, 17f)
            cy += 12
            rule(cy)
        }

        /** 5. A totals row (Subtotal, Express, …). */
        fun totalRow(label: String, value: String, color: Int = INK) {
            cy += 20
            font(13.5f, 400); text(label, left, cy, Paint.Align.LEFT, if (color == INK) MUTED else color)
            font(13.5f, 600); text(value, right, cy, Paint.Align.RIGHT, color)
        }

        /** 5. The big total: a tinted pill on Bold, a heavy rule and large type otherwise. */
        fun totalBand(label: String, value: String) {
            if (bold) {
                val h = 44f
                fill(RectF(left - 8, cy, right + 8, cy + h), TINT, h / 2)
                cy += 29
                font(16f, 700, display = true); text(label, left + 6, cy, Paint.Align.LEFT, BLUE)
                font(20f, 700, display = true); text(value, right - 6, cy, Paint.Align.RIGHT, BLUE)
                cy += h - 29
            } else {
                rule(cy, width = if (receipt) 1f else 1.5f, color = INK, dashed = receipt)
                cy += 30
                font(if (receipt) 17f else 18f, 700, display = true); text(label, left, cy, Paint.Align.LEFT)
                font(if (receipt) 19f else 22f, 700, display = true); text(value, right, cy, Paint.Align.RIGHT)
                cy += 8
            }
        }

        /** 7. UPI pay QR (valid ids only) with "Scan to pay"; a small gap when there is none. */
        fun qrBlock(upi: BillReceipt.Upi?) {
            if (upi == null) {
                cy += 4
                return
            }
            cy += 18
            val box = 124f
            val x0 = mid - box / 2
            val matrix = runCatching {
                QRCodeWriter().encode(
                    upi.payload, BarcodeFormat.QR_CODE, 0, 0,
                    mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2),
                )
            }.getOrNull()
            if (draw && matrix != null) {
                p.style = Paint.Style.FILL
                p.color = Color.WHITE
                canvas.drawRect(x0, cy, x0 + box, cy + box, p)
                p.color = Color.BLACK
                val n = matrix.width
                val cell = box / n
                for (ry in 0 until n) for (cx in 0 until n) {
                    if (matrix.get(cx, ry)) canvas.drawRect(x0 + cx * cell, cy + ry * cell, x0 + (cx + 1) * cell + 0.05f, cy + (ry + 1) * cell + 0.05f, p)
                }
            }
            cy += box
            lines("Scan to pay ${upi.amount}", 14f, 700, false, right - left, mid, Paint.Align.CENTER, INK, 20f)
            lines("UPI · ${upi.id}", 12f, 400, false, right - left, mid, Paint.Align.CENTER, MUTED, 16f)
        }

        /** 8. Terms and conditions, numbered. */
        fun termsBlock(terms: List<String>) {
            if (terms.isEmpty()) return
            cy += 10
            rule(cy, dashed = true)
            cy += 2
            lines(if (receipt) "TERMS AND CONDITIONS" else "Terms and Conditions", 12.5f, 700, false, right - left, left, Paint.Align.LEFT, INK, 18f)
            cy += 2
            terms.forEachIndexed { i, term -> lines("${i + 1}. $term", 11.5f, 400, false, right - left, left, Paint.Align.LEFT, MUTED, 15f) }
        }

        /** 9. Thank you — pinned to the bottom when the card is stretched to [minH]. Returns the card height. */
        fun finish(minH: Float): Float {
            val natural = cy + 34 + PAD
            if (minH > natural) cy += minH - natural
            cy += 34
            font(14f, 700)
            text(if (receipt) "*** Thank you ***" else "Thank you!", mid, cy, Paint.Align.CENTER, if (receipt) INK else MUTED)
            return max(natural, minH)
        }
    }

    // ---------------- layouts ----------------

    private fun layout(context: Context, canvas: Canvas, r: BillReceipt, logo: Bitmap?, draw: Boolean, minH: Float): Float =
        with(Pen(context, canvas, r.template, logo, r.logoId, draw)) {
            // 1. shop block
            shopBlock(r.shopName, r.shopPhone, r.email, r.address, r.gstin)

            // 1b. "TAX INVOICE" — GST on the order and a GSTIN on the bill
            if (r.taxInvoice) {
                cy += 20
                font(12f, 800)
                p.letterSpacing = 0.14f
                text("TAX INVOICE", mid, cy, Paint.Align.CENTER, HEADING)
                p.letterSpacing = 0f
                cy -= 4
            }

            // 2. bill number + date
            cy += 22
            font(14f, 700); text(r.billNo, left, cy, Paint.Align.LEFT)
            font(13f, 400); text(r.date, right, cy, Paint.Align.RIGHT, MUTED)

            // 3. customer
            customerBlock(r.customerName, r.customerPhone)

            // 4. items — each numbered (serial no.) in a narrow left column
            val snW = 28f
            val itemX = left + snW
            if (bold) {
                cy += 18
                font(11f, 700)
                text("NO.", left, cy, Paint.Align.LEFT, MUTED)
                text("ITEM", itemX, cy, Paint.Align.LEFT, MUTED)
                text("AMOUNT", right, cy, Paint.Align.RIGHT, MUTED)
                cy += 8
                rule(cy)
            }
            val itemW = right - itemX - 90
            r.lines.forEachIndexed { n, l ->
                cy += 21
                font(14f, if (receipt) 400 else 600)
                text(l.amount, right, cy, Paint.Align.RIGHT)
                font(14f, 400)
                text("${n + 1}.", left, cy, Paint.Align.LEFT, MUTED)
                font(14f, if (receipt) 400 else 600)
                wrap(p, l.item, itemW).forEachIndexed { i, s ->
                    if (i > 0) cy += 18
                    text(s, itemX, cy, Paint.Align.LEFT)
                }
                if (l.sub.isNotEmpty()) lines(l.sub, 12f, 400, false, itemW, itemX, Paint.Align.LEFT, MUTED, 15f)
            }
            cy += 12
            rule(cy)

            // 5. totals
            totalRow("Subtotal", r.subtotal)
            r.extras.forEach { totalRow(it.label, it.value, if (it.discount) GREEN else INK) }
            cy += 12
            totalBand(if (receipt) "TOTAL" else "Total", r.total)

            // 5b. inclusive GST: what the total already includes
            if (r.gstNote.isNotEmpty()) {
                cy += 4
                lines(r.gstNote, 11.5f, 400, false, right - left, left, Paint.Align.LEFT, MUTED, 15f)
            }

            // 6. payment status (real bills)
            if (r.payStatus.isNotEmpty()) {
                cy += 22
                font(13.5f, 700)
                text(r.payStatus, mid, cy, Paint.Align.CENTER, if (r.payStatus == "Paid in full") GREEN else INK)
            }

            // 7. UPI pay QR + ready-by
            qrBlock(r.upi)
            if (r.readyBy.isNotEmpty()) lines(r.readyBy, 12.5f, 400, false, right - left, mid, Paint.Align.CENTER, MUTED, 18f)

            // 8. terms, 9. thank you
            termsBlock(r.terms)
            finish(minH)
        }

    private fun tagColor(kind: CombinedBill.TagKind): Int = when (kind) {
        CombinedBill.TagKind.PAID -> GREEN
        CombinedBill.TagKind.DUE -> ORANGE
        CombinedBill.TagKind.OPEN -> BLUE_TEXT
    }

    /**
     * The combined bill: same header and footer as a single bill; in between
     * "COMBINED BILL" + bill date, the period, "Bill to", one row per order
     * (oldest first, optionally with its items), then Total of N orders →
     * Already paid → To pay, and a pay QR or a "Fully paid" strip.
     */
    private fun layoutCombined(context: Context, canvas: Canvas, r: CombinedReceipt, logo: Bitmap?, draw: Boolean, minH: Float): Float =
        with(Pen(context, canvas, r.template, logo, r.logoId, draw)) {
            // 1. shop block
            shopBlock(r.shopName, r.shopPhone, r.email, r.address, r.gstin)

            // 2. title + bill date, period
            cy += 22
            font(14f, 700); text("COMBINED BILL", left, cy, Paint.Align.LEFT)
            font(13f, 400); text(r.billDate, right, cy, Paint.Align.RIGHT, MUTED)
            if (r.period.isNotEmpty()) {
                cy += 18
                font(12.5f, 400); text("Period", left, cy, Paint.Align.LEFT, MUTED)
                val labelW = p.measureText("Period") + 8
                font(13f, 600); text(r.period, left + labelW, cy, Paint.Align.LEFT, INK)
            }

            // 3. bill to
            cy += 8
            lines(if (receipt) "BILL TO" else "Bill to", 11f, 700, false, right - left, left, Paint.Align.LEFT, MUTED, 16f)
            customerBlock(r.customerName, r.customerPhone)

            // 4. one row per order
            val amtW = 90f
            if (bold) {
                cy += 18
                font(11f, 700)
                text("ORDER", left, cy, Paint.Align.LEFT, MUTED)
                text("AMOUNT", right, cy, Paint.Align.RIGHT, MUTED)
                cy += 8
                rule(cy)
            }
            r.rows.forEachIndexed { n, row ->
                if (n > 0) {
                    cy += 10
                    rule(cy)
                }
                cy += 21
                font(14f, if (receipt) 400 else 700)
                text(row.amount, right, cy, Paint.Align.RIGHT)
                wrap(p, row.title, right - left - amtW).forEachIndexed { i, s ->
                    if (i > 0) cy += 18
                    text(s, left, cy, Paint.Align.LEFT)
                }
                // sub line with the status tag on its right
                font(11f, 700)
                val tagW = p.measureText(row.tag.label)
                font(12f, 400)
                val subLines = wrap(p, row.sub, right - left - tagW - 10)
                subLines.forEachIndexed { i, s ->
                    cy += 16
                    text(s, left, cy, Paint.Align.LEFT, MUTED)
                    if (i == 0) {
                        font(11f, 700)
                        text(row.tag.label, right, cy, Paint.Align.RIGHT, tagColor(row.tag.kind))
                        font(12f, 400)
                    }
                }
                // every item (optional)
                row.lines.forEach { l ->
                    cy += 16
                    font(12f, 400)
                    text(l.value, right, cy, Paint.Align.RIGHT, if (l.discount) GREEN else MUTED)
                    wrap(p, l.label, right - left - amtW - 12).forEachIndexed { i, s ->
                        if (i > 0) cy += 15
                        text(s, left + 12, cy, Paint.Align.LEFT, MUTED)
                    }
                }
            }
            cy += 12
            rule(cy)

            // 5. totals
            totalRow(r.totalLabel, r.total)
            if (r.paid.isNotEmpty()) totalRow("Already paid", "− ${r.paid}", GREEN)
            cy += 12
            totalBand(if (receipt) "TO PAY" else "To pay", r.due)

            // 6. fully paid strip, or 7. the pay QR for what is due
            if (r.fullyPaid) {
                cy += 18
                val h = 36f
                if (!receipt) fill(RectF(left, cy, right, cy + h), GREEN_LIGHT, 10f)
                cy += 23
                font(13.5f, 700)
                text(if (receipt) "*** FULLY PAID — THANK YOU ***" else "Fully paid — thank you!", mid, cy, Paint.Align.CENTER, if (receipt) INK else GREEN)
                cy += h - 23
            } else {
                qrBlock(r.upi)
            }

            // 8. terms, 9. thank you
            termsBlock(r.terms)
            finish(minH)
        }
}
