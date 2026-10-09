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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a [BillReceipt] in its design (Classic / Bold / Receipt). The same
 * drawing backs the shared bill image, the "View bill" sheet and the
 * onboarding preview, so what the owner picks is what customers get.
 * Geometry matches the web renderer (web/src/ui/billRender.ts): a 380-pt
 * card drawn at [SCALE]× for crisp text.
 */
object BillRender {
    const val W = 380f
    private const val PAD = 22f
    const val SCALE = 3f

    private val INK = Color.parseColor("#16191D")
    private val MUTED = Color.parseColor("#5B6168")
    private val BORDER = Color.parseColor("#E2DED6")
    private val BLUE = Color.parseColor("#1D4ED8")
    private val TINT = Color.parseColor("#E6ECFB")
    private val BAND_TEXT = Color.parseColor("#DCE5FB")
    private val GREEN = Color.parseColor("#15803D")
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

    /** The bill's natural height in points (for equal-height slides). */
    fun height(context: Context, r: BillReceipt, logo: Bitmap?): Float {
        val c = Canvas()
        return layout(context, c, r, logo, draw = false, minH = 0f)
    }

    /** Renders the bill, optionally stretched to [minH] points ("Thank you" pinned to the bottom). */
    fun render(context: Context, r: BillReceipt, logo: Bitmap?, minH: Float = 0f): Bitmap {
        val h = ceil(layout(context, Canvas(), r, logo, draw = false, minH = minH))
        val bmp = Bitmap.createBitmap((W * SCALE).toInt(), (h * SCALE).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(SCALE, SCALE)
        canvas.drawColor(if (r.template == BillDetails.RECEIPT) SLIP else Color.WHITE)
        layout(context, canvas, r, logo, draw = true, minH = minH)
        return bmp
    }

    /** Draws the bill 1:1 onto a canvas of [W] × [height] points — a PDF page (vector text). */
    fun drawPage(context: Context, canvas: Canvas, r: BillReceipt, logo: Bitmap?) {
        canvas.drawColor(if (r.template == BillDetails.RECEIPT) SLIP else Color.WHITE)
        layout(context, canvas, r, logo, draw = true, minH = 0f)
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

    private fun layout(context: Context, canvas: Canvas, r: BillReceipt, logo: Bitmap?, draw: Boolean, minH: Float): Float {
        val t = r.template
        val receipt = t == BillDetails.RECEIPT
        val f = fonts(context, receipt)
        val left = PAD
        val right = W - PAD
        val mid = W / 2
        var y: Float
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

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

        val hasLogo = logo != null && r.logoId.isNotEmpty()
        val shopName = if (receipt) r.shopName.uppercase() else r.shopName
        val details = listOf(r.shopPhone, r.email, r.address, if (r.gstin.isNotEmpty()) "GSTIN ${r.gstin}" else "").filter { it.isNotEmpty() }

        // Mutable cursor helpers.
        var cy = 0f
        fun lines(s: String, size: Float, w: Int, display: Boolean, maxW: Float, x: Float, align: Paint.Align, color: Int, lh: Float) {
            font(size, w, display)
            for (l in wrap(p, s, maxW)) {
                cy += lh
                text(l, x, cy, align, color)
            }
        }

        // 1. shop block
        if (t == BillDetails.BOLD) {
            val textX = left + if (hasLogo) 56f else 0f
            val textW = right - textX
            font(22f, 700, display = true)
            var textH = wrap(p, shopName, textW).size * 26f
            font(12.5f, 400)
            details.forEach { textH += wrap(p, it, textW).size * 17f }
            val bandH = max(if (hasLogo) 44f else 0f, textH) + PAD * 2 - 4
            if (draw) {
                p.style = Paint.Style.FILL; p.color = BLUE
                canvas.drawRect(0f, 0f, W, bandH, p)
            }
            if (hasLogo && draw) {
                val ty = PAD - 2 + (max(44f, textH) - 44f) / 2
                p.color = Color.WHITE
                canvas.drawRoundRect(RectF(left, ty, left + 44, ty + 44), 10f, 10f, p)
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

        // 2. bill number + date
        cy += 22
        font(14f, 700); text(r.billNo, left, cy, Paint.Align.LEFT)
        font(13f, 400); text(r.date, right, cy, Paint.Align.RIGHT, MUTED)

        // 3. customer
        if (r.customerName.isNotEmpty()) lines(r.customerName, 15f, 700, false, right - left, left, Paint.Align.LEFT, INK, 22f)
        if (r.customerPhone.isNotEmpty()) lines(r.customerPhone, 13f, 400, false, right - left, left, Paint.Align.LEFT, MUTED, 17f)
        cy += 12
        rule(cy)

        // 4. items — each numbered (serial no.) in a narrow left column
        val snW = 28f
        val itemX = left + snW
        if (t == BillDetails.BOLD) {
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
        fun row(label: String, value: String, color: Int = INK) {
            cy += 20
            font(13.5f, 400); text(label, left, cy, Paint.Align.LEFT, if (color == INK) MUTED else color)
            font(13.5f, 600); text(value, right, cy, Paint.Align.RIGHT, color)
        }
        row("Subtotal", r.subtotal)
        r.extras.forEach { row(it.label, it.value, if (it.discount) GREEN else INK) }
        cy += 12
        if (t == BillDetails.BOLD) {
            val h = 44f
            if (draw) {
                p.style = Paint.Style.FILL; p.color = TINT
                canvas.drawRoundRect(RectF(left - 8, cy, right + 8, cy + h), h / 2, h / 2, p)
            }
            cy += 29
            font(16f, 700, display = true); text("Total", left + 6, cy, Paint.Align.LEFT, BLUE)
            font(20f, 700, display = true); text(r.total, right - 6, cy, Paint.Align.RIGHT, BLUE)
            cy += h - 29
        } else {
            rule(cy, width = if (receipt) 1f else 1.5f, color = INK, dashed = receipt)
            cy += 30
            font(if (receipt) 17f else 18f, 700, display = true); text(if (receipt) "TOTAL" else "Total", left, cy, Paint.Align.LEFT)
            font(if (receipt) 19f else 22f, 700, display = true); text(r.total, right, cy, Paint.Align.RIGHT)
            cy += 8
        }

        // 6. payment status (real bills)
        if (r.payStatus.isNotEmpty()) {
            cy += 22
            font(13.5f, 700)
            text(r.payStatus, mid, cy, Paint.Align.CENTER, if (r.payStatus == "Paid in full") GREEN else INK)
        }

        // 7. UPI pay QR (valid ids only) + ready-by
        val upi = r.upi
        if (upi != null) {
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
        } else {
            cy += 4
        }
        if (r.readyBy.isNotEmpty()) lines(r.readyBy, 12.5f, 400, false, right - left, mid, Paint.Align.CENTER, MUTED, 18f)

        // 8. terms
        if (r.terms.isNotEmpty()) {
            cy += 10
            rule(cy, dashed = true)
            cy += 2
            lines(if (receipt) "TERMS AND CONDITIONS" else "Terms and Conditions", 12.5f, 700, false, right - left, left, Paint.Align.LEFT, INK, 18f)
            cy += 2
            r.terms.forEachIndexed { i, term -> lines("${i + 1}. $term", 11.5f, 400, false, right - left, left, Paint.Align.LEFT, MUTED, 15f) }
        }

        // 9. thank you — pinned to the bottom when the card is stretched
        val natural = cy + 34 + PAD
        if (minH > natural) cy += minH - natural
        cy += 34
        font(14f, 700)
        text(if (receipt) "*** Thank you ***" else "Thank you!", mid, cy, Paint.Align.CENTER, if (receipt) INK else MUTED)
        y = max(natural, minH)
        return y
    }
}
