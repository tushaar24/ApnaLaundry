package com.dailyworks.apnalaundry.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.dailyworks.apnalaundry.domain.BillDetails
import com.dailyworks.apnalaundry.domain.BillReceipt
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.ceil

/**
 * Draws a customer's bill for the shop's 58 / 80 mm thermal printer in the
 * design the owner picked in Settings (BillDetails.CLASSIC / BOLD / RECEIPT /
 * INVOICE) — the same [BillReceipt] the WhatsApp bill image uses, redrawn in
 * black on white at sizes a 203-dpi printer can show (ui/screens/bill/BillRender
 * is the colour original; its type would be ~2 mm tall on 58 mm paper):
 *
 *   Classic  centred shop header, numbered items, heavy rule + large Total
 *   Bold     black header band and black Total band (white type), NO./ITEM/AMOUNT headings
 *   Receipt  monospace, SHOP NAME in capitals, dashed rules, "TOTAL"
 *   Invoice  "Mobile - …" header, Order/Customer/Mobile/dates as label → value, Item·Qty·Rate·Total table, paise
 *
 * The shop logo prints centred at the top in every design, like the bill image shows it
 * (when the shop has one and it has loaded), as black / white dots (print/ThermalLogo).
 * Drawn as a picture so any script prints.
 */
object ReceiptRender {
    /** [logo] = the shop's logo ([BillReceipt.logoId]'s picture), or null to print without it. */
    fun render(r: BillReceipt, width: Int, logo: Bitmap? = null): Bitmap {
        // The logo as printer dots, made once for both passes; a logo that can't be made is left off (never the print).
        val mark = if (r.logoId.isNotEmpty() && logo != null) runCatching { ThermalLogo.of(logo, width) }.getOrNull() else null
        val h = Pen(null, r, width, mark).layout()
        val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        Pen(c, r, width, mark).layout()
        return bmp
    }

    private val SANS_BOLD: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val SANS_MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val MONO: Typeface = Typeface.MONOSPACE
    private val MONO_BOLD: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    /** One pass down the page: measures when [c] is null, draws otherwise. */
    private class Pen(val c: Canvas?, val r: BillReceipt, val width: Int, val logo: Bitmap?) {
        val template = BillDetails.templateOrDefault(r.template)
        val receipt = template == BillDetails.RECEIPT
        val bold = template == BillDetails.BOLD
        val invoice = template == BillDetails.INVOICE
        val s = width / TagRender.NARROW.toFloat()
        val margin = 12 * s
        val left = margin
        val right = width - margin
        val inner = right - left
        val mid = width / 2f
        var y = 8 * s

        fun paint(size: Float, strong: Boolean = false, medium: Boolean = false, white: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (white) Color.WHITE else Color.BLACK
            textSize = size * s
            typeface = when {
                receipt -> if (strong) MONO_BOLD else MONO
                strong -> SANS_BOLD
                medium -> SANS_MEDIUM
                else -> Typeface.SANS_SERIF
            }
        }
        fun lh(p: Paint): Float = p.descent() - p.ascent()
        fun ell(text: String, p: TextPaint, w: Float) = TextUtils.ellipsize(text, p, w.coerceAtLeast(1f), TextUtils.TruncateAt.END).toString()

        /** One line at [x] with [align]; cut short with … if too long. */
        fun text(text: String, p: TextPaint, x: Float, align: Paint.Align, maxW: Float = inner, gapAfter: Float = 2 * s) {
            if (text.isBlank()) return
            val shown = ell(text, p, maxW)
            val w = p.measureText(shown)
            val sx = when (align) { Paint.Align.LEFT -> x; Paint.Align.CENTER -> x - w / 2; Paint.Align.RIGHT -> x - w }
            c?.drawText(shown, sx, y - p.ascent(), p)
            y += lh(p) + gapAfter
        }
        fun centred(text: String, p: TextPaint, gapAfter: Float = 2 * s) = text(text, p, mid, Paint.Align.CENTER, inner, gapAfter)

        /** A wrapped paragraph from [x], [maxW] wide. */
        fun wrapped(text: String, p: TextPaint, maxW: Float = inner, x: Float = left, gapAfter: Float = 2 * s, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
            if (text.isBlank()) return
            val sl = StaticLayout.Builder.obtain(text, 0, text.length, p, maxW.toInt().coerceAtLeast(1)).setAlignment(align).setIncludePad(false).build()
            if (c != null) { c.save(); c.translate(x, y); sl.draw(c); c.restore() }
            y += sl.height + gapAfter
        }

        /** "label …… value" on one line; the label gives way to the value. */
        fun row(label: String, value: String, pl: TextPaint, pv: TextPaint = pl, gapAfter: Float = 3 * s) {
            val vw = pv.measureText(value)
            val shown = ell(label, pl, inner - vw - 8 * s)
            val h = maxOf(lh(pl), lh(pv))
            c?.drawText(shown, left, y - pl.ascent(), pl)
            c?.drawText(value, right - vw, y - pv.ascent(), pv)
            y += h + gapAfter
        }

        fun rule(gap: Float = 6 * s, heavy: Boolean = false, dashed: Boolean = receipt) {
            y += gap
            c?.drawLine(left, y, right, y, Paint().apply {
                color = Color.BLACK; strokeWidth = (if (heavy) 3 else 2) * s
                if (dashed) pathEffect = DashPathEffect(floatArrayOf(6 * s, 5 * s), 0f)
            })
            y += gap
        }

        /** A black band [h] tall across the paper; returns its top so white text can go on it. */
        fun band(h: Float): Float {
            val top = y
            c?.drawRect(RectF(0f, top, width.toFloat(), top + h), Paint().apply { color = Color.BLACK })
            return top
        }

        fun layout(): Int {
            if (invoice) layoutInvoice() else layoutStandard()
            // tear line
            y += 14 * s
            c?.drawLine(0f, y, width.toFloat(), y, Paint().apply {
                color = Color.BLACK; strokeWidth = 3 * s; pathEffect = DashPathEffect(floatArrayOf(10 * s, 8 * s), 0f)
            })
            y += 3 * s + 12 * s
            return ceil(y).toInt()
        }

        /** The shop logo, centred on whole dots, drawn 1:1 (no filtering) so its black / white dots print as made. */
        fun logoBlock() {
            val m = logo ?: return
            val top = ceil(y).toInt()
            c?.drawBitmap(m, ((width - m.width) / 2).toFloat(), top.toFloat(), null)
            y = top + m.height + 10 * s
        }

        // ---- blocks shared by Classic / Bold / Receipt ----

        fun shopBlock() {
            val name = if (receipt) r.shopName.trim().uppercase() else r.shopName.trim()
            val details = listOf(r.shopPhone, r.email, r.address, if (r.gstin.isNotEmpty()) "GSTIN ${r.gstin}" else "").filter { it.isNotEmpty() }
            if (bold) {
                // Black header band, shop name and details in white, left-aligned.
                val namePaint = paint(30f, strong = true, white = true)
                val detPaint = paint(18f, white = true)
                val nameLines = StaticLayout.Builder.obtain(name, 0, name.length, namePaint, inner.toInt()).setIncludePad(false).build()
                var h = 14 * s + nameLines.height + 5 * s
                details.forEach { d -> h += StaticLayout.Builder.obtain(d, 0, d.length, detPaint, inner.toInt()).setIncludePad(false).build().height + 2 * s }
                h += 12 * s
                val top = band(h)
                y = top + 14 * s
                wrapped(name, namePaint, gapAfter = 5 * s)
                details.forEach { wrapped(it, detPaint, gapAfter = 2 * s) }
                y = top + h + 12 * s
            } else {
                wrapped(name, paint(if (receipt) 27f else 32f, strong = true), gapAfter = 5 * s, align = Layout.Alignment.ALIGN_CENTER)
                details.forEach { wrapped(it, paint(19f), gapAfter = 2 * s, align = Layout.Alignment.ALIGN_CENTER) }
                rule(gap = 8 * s)
            }
        }

        fun taxInvoice() {
            if (!r.taxInvoice) return
            centred("TAX INVOICE", paint(20f, strong = true), 6 * s)
        }

        fun payStatus() {
            if (r.payStatus.isBlank()) return
            y += 8 * s
            centred(r.payStatus, paint(23f, strong = true), 6 * s)
        }

        fun gstNote() { if (r.gstNote.isNotBlank()) wrapped(r.gstNote, paint(18f)) }

        /** The payment status — or, for a customer with old baaki / advance, Paid ± previous = Total to pay. */
        fun payBlock() {
            val k = r.khata ?: return payStatus()
            y += 8 * s
            val tl = paint(if (invoice) 20f else 21f)
            val tv = paint(if (invoice) 20f else 21f, strong = invoice, medium = !invoice)
            k.rows.forEach { row(it.label, if (invoice) it.exact else it.value, tl, tv, gapAfter = 5 * s) }
            rule(gap = 4 * s, heavy = !receipt, dashed = receipt)
            row(
                if (receipt) "TOTAL TO PAY" else k.toPay.label, if (invoice) k.toPay.exact else k.toPay.value,
                paint(24f, strong = true), paint(28f, strong = true), gapAfter = 4 * s,
            )
            if (k.advanceLeft.isNotEmpty()) centred(k.advanceLeft, paint(20f, strong = true), 4 * s)
        }

        /** The pay QR's modules (null when there's no UPI QR on this bill, or it can't be made). */
        val qr: BitMatrix? by lazy { r.upi?.let { qrMatrix(it.payload) } }

        fun qrBlock() {
            val upi = r.upi ?: return // none when nothing is left to pay
            val m = qr ?: return
            rule(dashed = true)
            centred("Scan to pay ${upi.amount}", paint(22f, strong = true), 0f)
            // Whole printer dots per module, a 4-module white border, centred on whole dots.
            val dot = qrDots(m.width, width)
            val side = (m.width + 2 * QR_QUIET) * dot
            val x0 = (width - side) / 2
            val y0 = ceil(y).toInt()
            if (c != null) drawQr(c, m, x0 + QR_QUIET * dot, y0 + QR_QUIET * dot, dot)
            y = (y0 + side).toFloat()
            centred(upi.id, paint(18f), 4 * s)
        }

        fun termsAndThanks() {
            if (r.terms.isNotEmpty()) {
                rule(dashed = true)
                r.terms.forEach { wrapped("• $it", paint(17f), gapAfter = 4 * s) }
            }
            y += 10 * s
            centred("Thank you!", paint(23f, medium = true), 3 * s)
            centred("MyLaundry app", paint(17f), 0f)
        }

        // ---- Classic / Bold / Receipt ----

        fun layoutStandard() {
            logoBlock()
            shopBlock()
            taxInvoice()
            // bill no. + date, customer
            row(r.billNo, r.date, paint(23f, strong = true), paint(21f), gapAfter = 6 * s)
            if (r.customerName.isNotBlank()) wrapped(r.customerName.trim(), paint(23f, medium = !receipt), gapAfter = 2 * s)
            if (r.customerPhone.isNotBlank()) text(r.customerPhone, paint(19f), left, Paint.Align.LEFT)
            if (r.customerAddress.isNotBlank()) wrapped(r.customerAddress, paint(19f), gapAfter = 2 * s)
            rule(gap = 8 * s)

            // items, numbered; Bold has column headings
            val numW = 26 * s
            val itemX = left + numW
            if (bold) {
                val hp = paint(16f, strong = true)
                c?.drawText("NO.", left, y - hp.ascent(), hp)
                c?.drawText("ITEM", itemX, y - hp.ascent(), hp)
                c?.drawText("AMOUNT", right - hp.measureText("AMOUNT"), y - hp.ascent(), hp)
                y += lh(hp) + 2 * s
                rule(gap = 3 * s)
            }
            val ip = paint(if (receipt) 21f else 23f, strong = !receipt && bold, medium = !receipt && !bold)
            val np = paint(if (receipt) 19f else 21f)
            val sp = paint(18f)
            r.lines.forEachIndexed { n, l ->
                val amtW = ip.measureText(l.amount)
                val itemW = right - itemX - amtW - 10 * s
                c?.drawText("${n + 1}.", left, y - np.ascent(), np)
                c?.drawText(l.amount, right - amtW, y - ip.ascent(), ip)
                wrapped(l.item, ip, maxW = itemW, x = itemX, gapAfter = 2 * s)
                val sub = listOf(l.rate, l.sub).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) wrapped(sub, sp, maxW = right - itemX, x = itemX, gapAfter = 7 * s) else y += 6 * s
            }
            rule(gap = 7 * s)

            // totals
            val tl = paint(21f); val tv = paint(21f, medium = true)
            row("Subtotal", r.subtotal, tl, tv, gapAfter = 5 * s)
            r.extras.forEach { row(it.label, it.value, tl, tv, gapAfter = 5 * s) }
            y += 4 * s
            if (bold) {
                val lp = paint(24f, strong = true, white = true); val vp = paint(30f, strong = true, white = true)
                val h = lh(vp) + 16 * s
                val top = band(h)
                c?.drawText("Total", left + 4 * s, top + (h - lh(lp)) / 2 - lp.ascent(), lp)
                c?.drawText(r.total, right - 4 * s - vp.measureText(r.total), top + (h - lh(vp)) / 2 - vp.ascent(), vp)
                y = top + h + 6 * s
            } else {
                rule(gap = 4 * s, heavy = !receipt, dashed = receipt)
                row(if (receipt) "TOTAL" else "Total", r.total, paint(26f, strong = true), paint(30f, strong = true), gapAfter = 4 * s)
            }
            gstNote()
            payBlock()
            qrBlock()
            if (r.readyBy.isNotBlank()) { y += 4 * s; centred(r.readyBy, paint(18f)) }
            termsAndThanks()
        }

        // ---- Invoice ----

        fun layoutInvoice() {
            logoBlock()
            val digits = r.shopPhone.filter { it.isDigit() }.takeLast(10)
            wrapped(r.shopName.trim(), paint(30f, strong = true), gapAfter = 3 * s, align = Layout.Alignment.ALIGN_CENTER)
            listOf(r.address, if (digits.isNotEmpty()) "Mobile - $digits" else "", r.email, if (r.gstin.isNotEmpty()) "GSTIN ${r.gstin}" else "")
                .filter { it.isNotEmpty() }.forEach { wrapped(it, paint(19f), gapAfter = 1 * s, align = Layout.Alignment.ALIGN_CENTER) }
            rule(dashed = false)
            taxInvoice()
            // order details as label → value
            val details = listOf(
                "Order" to r.orderNo, "Customer" to r.customerName.trim().uppercase(), "Mobile" to r.customerMobile,
                "Created At" to r.createdAt, r.dateLabel to r.dateValue,
            ).filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
            val dl = paint(20f); val dv = paint(20f, strong = true)
            details.forEach { (label, value) ->
                // a long value (customer name) shrinks rather than overlapping the label
                val room = inner - dl.measureText(label) - 10 * s
                var vp = dv
                var size = 20f
                while (vp.measureText(value) > room && size > 13f) { size -= 1f; vp = paint(size, strong = true) }
                row(label, ell(value, vp, room), dl, vp, gapAfter = 5 * s)
            }
            if (r.customerAddress.isNotBlank()) {
                text("Address", dl, left, Paint.Align.LEFT, gapAfter = 3 * s)
                wrapped(r.customerAddress, dv, gapAfter = 5 * s)
            }
            rule(gap = 7 * s, dashed = false)

            // Item · Qty · Rate · Total — on thermal paper the item and its total share a line and
            // "qty × rate" sits under the item (four columns collide on 58 mm).
            val hp = paint(19f, strong = true)
            c?.drawText("Item", left, y - hp.ascent(), hp)
            c?.drawText("Qty × Rate", left + 0.48f * inner, y - hp.ascent(), hp)
            c?.drawText("Total", right - hp.measureText("Total"), y - hp.ascent(), hp)
            y += lh(hp) + 3 * s
            rule(gap = 3 * s, heavy = true, dashed = false)
            val ip = paint(22f); val sp = paint(18f)
            r.lines.forEachIndexed { i, l ->
                if (i > 0) rule(gap = 4 * s, dashed = true)
                val tot = l.totalExact.ifEmpty { l.amount }
                val totW = ip.measureText(tot)
                c?.drawText(tot, right - totW, y - ip.ascent(), ip)
                wrapped(l.name.ifEmpty { l.item }, ip, maxW = inner - totW - 10 * s, gapAfter = 2 * s)
                val qtyRate = listOf(l.qty, l.unit).filter { it.isNotBlank() }.joinToString(" × ")
                val under = listOf(qtyRate, l.sub).filter { it.isNotBlank() }.joinToString(" · ")
                if (under.isNotBlank()) wrapped(under, sp, gapAfter = 3 * s)
            }
            rule(dashed = false)
            val tl = paint(20f); val tv = paint(20f, strong = true)
            row("Subtotal", r.subtotalExact.ifEmpty { r.subtotal }, tl, tv, gapAfter = 5 * s)
            r.extras.forEach { row(it.label, it.exact, tl, tv, gapAfter = 5 * s) }
            rule(gap = 5 * s, dashed = false)
            row("Total", r.totalExact.ifEmpty { r.total }, paint(26f, strong = true), paint(28f, strong = true), gapAfter = 4 * s)
            gstNote()
            payBlock()
            qrBlock()
            termsAndThanks()
        }
    }

    /** White modules around the QR — the 4 the QR standard asks for; text closer than that stops phones finding it. */
    private const val QR_QUIET = 4

    /** The QR's modules alone (no border), error correction M — survives a smudge or a faint patch. */
    private fun qrMatrix(payload: String): BitMatrix? = runCatching {
        QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))
    }.getOrNull()

    /**
     * Printer dots per QR module, a whole number: as big as fits ~68% of the paper
     * with its white border, at most 8. A bill's short pay link (BillDetails.upiLink)
     * is a 37–41-module QR: 5 dots (0.6 mm) a module on 58 mm paper, 7–8 on 80 mm.
     * Uneven or 3-dot modules (the old bill: a long Hindi link drawn at 3.05 dots)
     * blur together on thermal paper and phones can't read them.
     */
    fun qrDots(modules: Int, width: Int): Int {
        val total = modules + 2 * QR_QUIET
        val fit = (width * 0.68f).toInt() / total
        // A long link: never under 4 dots while the full width still fits it.
        return if (fit >= 4) fit.coerceAtMost(8) else (width / total).coerceIn(1, 4)
    }

    /** Solid black squares on whole dots — no anti-aliasing, so every printed module is crisp. */
    private fun drawQr(c: Canvas, m: BitMatrix, left: Int, top: Int, dot: Int) {
        val p = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL; isAntiAlias = false }
        for (yy in 0 until m.height) for (xx in 0 until m.width) {
            if (m.get(xx, yy)) {
                val x = (left + xx * dot).toFloat()
                val y = (top + yy * dot).toFloat()
                c.drawRect(x, y, x + dot, y + dot, p)
            }
        }
    }
}
