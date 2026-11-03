package com.dailyworks.apnalaundry.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.dailyworks.apnalaundry.domain.BillReceipt
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.ceil

/**
 * Draws a customer's bill for the shop's 58 / 80 mm thermal printer — the same
 * [BillReceipt] the WhatsApp bill image uses, laid out as a till receipt: shop
 * header, bill no. and date, customer, every item with its service and amount,
 * totals, what is paid, a UPI QR for what is due, terms, a tear line. Black on
 * white, [width] printer dots across; drawn as a picture so any script prints.
 */
object ReceiptRender {
    fun render(r: BillReceipt, width: Int): Bitmap {
        val h = layout(null, r, width)
        val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        layout(c, r, width)
        return bmp
    }

    private val BOLD: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val NORMAL: Typeface = Typeface.SANS_SERIF

    private fun paint(size: Float, face: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; textSize = size; typeface = face
    }

    private fun lh(p: Paint): Float = p.descent() - p.ascent()

    private fun layout(c: Canvas?, r: BillReceipt, width: Int): Int {
        val s = width / TagRender.NARROW.toFloat()
        val margin = 12 * s
        val inner = width - 2 * margin
        var y = 8 * s

        fun centred(text: String, p: TextPaint, gapAfter: Float = 2 * s) {
            if (text.isBlank()) return
            val shown = TextUtils.ellipsize(text, p, inner, TextUtils.TruncateAt.END).toString()
            c?.drawText(shown, (width - p.measureText(shown)) / 2, y - p.ascent(), p)
            y += lh(p) + gapAfter
        }
        // A left-aligned paragraph that wraps (terms, long item names).
        fun wrapped(text: String, p: TextPaint, maxWidth: Float = inner, x: Float = margin, gapAfter: Float = 2 * s, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
            if (text.isBlank()) return
            val sl = StaticLayout.Builder.obtain(text, 0, text.length, p, maxWidth.toInt().coerceAtLeast(1)).setAlignment(align).setIncludePad(false).build()
            if (c != null) { c.save(); c.translate(x, y); sl.draw(c); c.restore() }
            y += sl.height + gapAfter
        }
        // "label ........ value" on one line; a long label wraps above the value.
        fun row(label: String, value: String, p: TextPaint, gapAfter: Float = 3 * s) {
            val vw = p.measureText(value)
            val labelMax = inner - vw - 8 * s
            val shown = TextUtils.ellipsize(label, p, labelMax.coerceAtLeast(40 * s), TextUtils.TruncateAt.END).toString()
            c?.drawText(shown, margin, y - p.ascent(), p)
            c?.drawText(value, width - margin - vw, y - p.ascent(), p)
            y += lh(p) + gapAfter
        }
        fun rule(dashed: Boolean = false, gap: Float = 6 * s) {
            y += gap
            c?.drawLine(margin, y, width - margin, y, Paint().apply {
                color = Color.BLACK; strokeWidth = 2 * s
                if (dashed) pathEffect = DashPathEffect(floatArrayOf(6 * s, 5 * s), 0f)
            })
            y += gap
        }

        val small = paint(20 * s, NORMAL)
        val body = paint(23 * s, NORMAL)
        val bodyBold = paint(23 * s, BOLD)

        // 1. shop
        centred(r.shopName.trim(), paint(32 * s, BOLD), 4 * s)
        centred(r.address, small)
        centred(listOf(r.shopPhone, r.email).filter { it.isNotBlank() }.joinToString(" · "), small)
        if (r.gstin.isNotBlank()) centred("GSTIN $r.gstin", small)
        rule()

        // 2. bill no., date, customer
        if (r.taxInvoice) centred("TAX INVOICE", paint(22 * s, BOLD), 4 * s)
        row(r.billNo, r.date, bodyBold)
        row(r.customerName.trim().ifBlank { "Customer" }, r.customerPhone, body)
        if (r.readyBy.isNotBlank()) wrapped(r.readyBy, small, gapAfter = 2 * s)
        rule(dashed = true)

        // 3. items: "Shirt × 3" with the amount; the service (or the bag's clothes) and the rate under it
        row("Item", "Amount", paint(20 * s, MEDIUM), 4 * s)
        for (l in r.lines) {
            row(l.item, l.amount, body, 0f)
            val under = listOf(l.sub, l.rate).filter { it.isNotBlank() }.joinToString(" · ")
            if (under.isNotBlank()) wrapped(under, small, maxWidth = inner - 10 * s, x = margin + 10 * s, gapAfter = 3 * s) else y += 3 * s
        }
        rule(dashed = true)

        // 4. totals
        if (r.extras.isNotEmpty() || r.lines.size > 1) row("Subtotal", r.subtotal, body)
        for (e in r.extras) row(e.label, e.value, body)
        row("TOTAL", r.total, paint(30 * s, BOLD), 2 * s)
        if (r.gstNote.isNotBlank()) wrapped(r.gstNote, small)
        if (r.payStatus.isNotBlank()) {
            y += 4 * s
            centred(r.payStatus, paint(24 * s, BOLD), 4 * s)
        }

        // 5. UPI QR for what is still to pay
        val upi = r.upi
        if (upi != null && r.payStatus != "Paid in full") {
            rule(dashed = true)
            centred("Scan to pay ${upi.amount}", paint(22 * s, BOLD), 6 * s)
            val size = (inner * 0.55f).toInt().coerceAtLeast(120)
            drawQr(c, upi.payload, (width - size) / 2f, y, size)
            y += size + 6 * s
            centred(upi.id, small, 4 * s)
        }

        // 6. terms + thanks
        if (r.terms.isNotEmpty()) {
            rule(dashed = true)
            r.terms.forEach { wrapped("• $it", paint(18 * s, NORMAL), gapAfter = 2 * s) }
        }
        y += 8 * s
        centred("Thank you!", paint(24 * s, MEDIUM), 4 * s)
        centred("MyLaundry app", paint(18 * s, NORMAL), 0f)

        // tear line
        y += 14 * s
        c?.drawLine(0f, y, width.toFloat(), y, Paint().apply {
            color = Color.BLACK; strokeWidth = 3 * s; pathEffect = DashPathEffect(floatArrayOf(10 * s, 8 * s), 0f)
        })
        y += 3 * s + 12 * s
        return ceil(y).toInt()
    }

    private fun drawQr(c: Canvas?, payload: String, left: Float, top: Float, size: Int) {
        if (c == null) return
        val m = runCatching { QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, mapOf(com.google.zxing.EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, com.google.zxing.EncodeHintType.MARGIN to 0)) }.getOrNull() ?: return
        val cell = size.toFloat() / m.width
        val p = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        for (yy in 0 until m.height) for (xx in 0 until m.width) {
            if (m.get(xx, yy)) c.drawRect(left + xx * cell, top + yy * cell, left + (xx + 1) * cell + 0.5f, top + (yy + 1) * cell + 0.5f, p)
        }
    }
}
