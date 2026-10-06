package com.dailyworks.apnalaundry.ui.screens.bill

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.ui.Selectors
import java.io.File
import java.io.FileOutputStream

/**
 * "Download" and "Send on WhatsApp" for a bill (mirror of the web
 * ui/billActions.ts + ui/billPdf.ts). Both hand over the bill as a PDF
 * receipt drawn with the platform PdfDocument — no storage permission.
 *  - WhatsApp: ACTION_SEND straight into WhatsApp (or WhatsApp Business) with
 *    the customer's chat preselected via the "jid" extra and the PDF attached.
 *  - Download: the system share sheet with the PDF (save to Files, or send).
 */

// ---------------- receipt drawing (shared by the PDF and the preview) ----------------

private const val W = 380f // receipt width in PDF points
private const val PAD = 22f
private const val COL_QTY = 190f // centred
private const val COL_RATE = 280f // right edge
private const val ITEM_W = 140f

private val INK = Color.parseColor("#16181F")
private val MUTED = Color.parseColor("#5B6168")
private val RULE = Color.parseColor("#9AA0A6")

private val REGULAR = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
private val BOLD = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

/** Greedy word-wrap; a single over-long word is left on its own line. */
private fun wrap(p: Paint, text: String, maxW: Float): List<String> {
    val out = mutableListOf<String>()
    var cur = ""
    text.split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { word ->
        val next = if (cur.isEmpty()) word else "$cur $word"
        if (cur.isNotEmpty() && p.measureText(next) > maxW) {
            out.add(cur); cur = word
        } else cur = next
    }
    if (cur.isNotEmpty()) out.add(cur)
    return out.ifEmpty { listOf("") }
}

/** Lays the receipt out; draws only when [canvas] is non-null. Returns the height. */
private fun drawReceipt(canvas: Canvas?, r: BillReceipt): Float {
    val left = PAD
    val right = W - PAD
    val mid = W / 2
    var y = PAD
    val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun font(size: Float, bold: Boolean = false) {
        p.textSize = size
        p.typeface = if (bold) BOLD else REGULAR
    }
    fun text(s: String, x: Float, yy: Float, align: Paint.Align, color: Int = INK) {
        if (canvas == null) return
        p.color = color
        p.textAlign = align
        p.style = Paint.Style.FILL
        canvas.drawText(s, x, yy, p)
    }
    fun rule(dashed: Boolean) {
        if (canvas == null) return
        val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dashed) RULE else INK
            strokeWidth = if (dashed) 1f else 1.2f
            style = Paint.Style.STROKE
            if (dashed) pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
        }
        canvas.drawLine(left, y, right, y, rp)
    }
    fun row(label: String, value: String, size: Float = 14f) {
        y += size + 7
        font(size); text(label, left, y, Paint.Align.LEFT)
        font(size, bold = true); text(value, right, y, Paint.Align.RIGHT)
    }

    // header
    font(22f, bold = true)
    wrap(p, r.shopName, right - left).forEach { y += 26; text(it, mid, y, Paint.Align.CENTER) }
    font(13f)
    if (r.shopPhone.isNotEmpty()) { y += 19; text(r.shopPhone, mid, y, Paint.Align.CENTER, MUTED) }
    y += 19; text("Bill Receipt", mid, y, Paint.Align.CENTER, MUTED)
    y += 14; rule(true); y += 6

    // order info
    r.info.forEach { row(it.label, it.value) }
    y += 14; rule(true); y += 6

    // table header
    y += 20
    font(13f, bold = true)
    text("Item", left, y, Paint.Align.LEFT)
    text("Qty", COL_QTY, y, Paint.Align.CENTER)
    text("Rate", COL_RATE, y, Paint.Align.RIGHT)
    text("Total", right, y, Paint.Align.RIGHT)
    y += 10; rule(true)

    // lines
    r.lines.forEach { l ->
        y += 20
        font(13f)
        text(l.qty, COL_QTY, y, Paint.Align.CENTER)
        text(l.rate, COL_RATE, y, Paint.Align.RIGHT)
        text(l.total, right, y, Paint.Align.RIGHT)
        wrap(p, l.item, ITEM_W).forEachIndexed { i, s ->
            if (i > 0) y += 16
            text(s, left, y, Paint.Align.LEFT)
        }
        if (l.sub.isNotEmpty()) {
            font(12f)
            wrap(p, l.sub, ITEM_W).forEach { y += 15; text(it, left, y, Paint.Align.LEFT, MUTED) }
        }
        y += 4
    }
    y += 8; rule(true); y += 4

    // totals
    row("Subtotal", r.subtotal)
    r.extras.forEach { row(it.label, it.value) }
    y += 12; rule(false); y += 32
    font(20f, bold = true); text("Total", left, y, Paint.Align.LEFT)
    font(22f, bold = true); text(r.total, right, y, Paint.Align.RIGHT)
    y += 16; rule(true); y += 26
    font(14f, bold = true); text("Thank you!", mid, y, Paint.Align.CENTER)
    return y + PAD
}

/** The exact receipt that goes into the PDF, as a bitmap for the "View bill" preview. */
fun billPreviewBitmap(state: LaundryState, o: Order, scale: Float = 2.5f): Bitmap {
    val r = billReceipt(state, o)
    val h = drawReceipt(null, r)
    val bmp = Bitmap.createBitmap((W * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)
    canvas.scale(scale, scale)
    drawReceipt(canvas, r)
    return bmp
}

/** Writes cacheDir/bills/Bill-<id>.pdf and returns a shareable content:// uri. */
private fun billPdfUri(context: Context, state: LaundryState, o: Order): Uri {
    val r = billReceipt(state, o)
    val h = drawReceipt(null, r)
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), kotlin.math.ceil(h).toInt(), 1).create())
    page.canvas.drawColor(Color.WHITE)
    drawReceipt(page.canvas, r)
    doc.finishPage(page)
    val dir = File(context.cacheDir, "bills").apply { mkdirs() }
    val file = File(dir, "Bill-${o.id}.pdf")
    try {
        FileOutputStream(file).use { doc.writeTo(it) }
    } finally {
        doc.close()
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

// ---------------- sending ----------------

private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

/** Short message that travels with the PDF. */
private fun billCaption(state: LaundryState, o: Order): String {
    val c = Selectors.customer(state, o.custId)
    return "Hi ${Selectors.firstName(c.name)}, here is your bill for order #${o.id} from ${state.shop.name} — " +
        "Total ${Money.rupees(LaundryMath.amtOf(o))}. Thank you!"
}

/** Sends the bill PDF to the customer on WhatsApp. Caller marks it sent. */
fun sendBillOnWhatsApp(context: Context, state: LaundryState, o: Order) {
    val uri = billPdfUri(context, state, o)
    val digits = Selectors.customer(state, o.custId).phone.filter { it.isDigit() }.takeLast(10)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, billCaption(state, o))
        // Opens the customer's chat directly instead of WhatsApp's contact picker.
        if (digits.length == 10) putExtra("jid", "91$digits@s.whatsapp.net")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // Exactly one WhatsApp → go straight in; both (personal + Business) or
    // none → let the owner pick from the share sheet.
    val installed = WHATSAPP_PACKAGES.filter { context.packageManager.getLaunchIntentForPackage(it) != null }
    val chooser = Intent.createChooser(send, "Send bill #${o.id}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        if (installed.size == 1) {
            context.startActivity(Intent(send).setPackage(installed[0]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            context.startActivity(chooser)
        }
    } catch (e: ActivityNotFoundException) {
        context.startActivity(chooser)
    }
}

/** Renders the bill to a PDF and opens the share sheet (save or send). */
fun shareBillPdf(context: Context, state: LaundryState, o: Order) {
    val uri = billPdfUri(context, state, o)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, "Bill #${o.id}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ---------------- khata reminder (text only) ----------------

private fun reminderText(state: LaundryState, custId: String, bal: Int): String {
    val c = Selectors.customer(state, custId)
    return buildString {
        appendLine("*${state.shop.name}*")
        appendLine("+91 ${Selectors.fmtPhone(state.shop.phone)}")
        appendLine()
        appendLine("${c.name}, a gentle reminder — your laundry balance is *${Money.rupees(bal)}*.")
        append("Please clear it on your next visit. Thank you!")
    }
}

/** Opens WhatsApp to the customer with a khata balance reminder. */
fun sendReminderOnWhatsApp(context: Context, state: LaundryState, custId: String, bal: Int) {
    val c = Selectors.customer(state, custId)
    val digits = c.phone.filter { it.isDigit() }.takeLast(10)
    val text = Uri.encode(reminderText(state, custId, bal))
    val url = if (digits.length == 10) "https://wa.me/91$digits?text=$text" else "https://wa.me/?text=$text"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No WhatsApp / browser to handle it.
    }
}
