package com.dailyworks.apnalaundry.ui.screens.bill

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.ui.Selectors
import java.io.File
import java.io.FileOutputStream

/**
 * Real "Download" and "Send on WhatsApp" for a bill (mirror of the web
 * ui/billActions.ts). The old handlers only showed a toast.
 *  - WhatsApp: opens wa.me/91<customer> prefilled with the bill as text.
 *  - Download: renders the bill to a PNG and opens the system share sheet
 *    (save to Files/Downloads, or send on WhatsApp) — works on all supported
 *    API levels with no storage permission.
 */

private data class BillLine(val label: String, val value: String)

private fun billRows(o: Order): Pair<List<BillLine>, String> {
    val rows = mutableListOf<BillLine>()
    o.lines.forEach { l ->
        rows.add(
            BillLine(
                if (l.kg > 0) "${l.serviceName} · ${Selectors.trimKg(l.kg)} kg" else "${l.itemName} × ${l.qty}",
                Money.rupees(l.amt),
            ),
        )
    }
    if (o.express && o.exAmt > 0) rows.add(BillLine("Express", "+ ${Money.rupees(o.exAmt)}"))
    if (o.fee > 0) rows.add(BillLine("Pickup / delivery", "+ ${Money.rupees(o.fee)}"))
    if (o.discount > 0) rows.add(BillLine("Discount", "− ${Money.rupees(o.discount)}"))
    return rows to Money.rupees(LaundryMath.amtOf(o))
}

private fun billText(state: LaundryState, o: Order): String {
    val c = Selectors.customer(state, o.custId)
    val (rows, total) = billRows(o)
    val body = rows.joinToString("\n") { "${it.label}  —  ${it.value}" }
    return buildString {
        appendLine("*${state.shop.name}*")
        appendLine("+91 ${Selectors.fmtPhone(state.shop.phone)}")
        appendLine()
        appendLine("Bill #${o.id} · ${AppDate.plain(o.createdOn)}")
        appendLine("To: ${c.name}")
        appendLine()
        appendLine(body)
        appendLine("————————")
        appendLine("*Total: $total*")
        appendLine()
        append("Thank you!")
    }
}

/** Opens WhatsApp to the customer with the bill text. Caller marks it sent. */
fun sendBillOnWhatsApp(context: Context, state: LaundryState, o: Order) {
    val c = Selectors.customer(state, o.custId)
    val digits = c.phone.filter { it.isDigit() }.takeLast(10)
    val text = Uri.encode(billText(state, o))
    val url = if (digits.length == 10) "https://wa.me/91$digits?text=$text" else "https://wa.me/?text=$text"
    openWhatsApp(context, url)
}

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
    openWhatsApp(context, url)
}

private fun openWhatsApp(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No WhatsApp / browser to handle it.
    }
}

private fun renderBillBitmap(state: LaundryState, o: Order): Bitmap {
    val c = Selectors.customer(state, o.custId)
    val (rows, total) = billRows(o)
    val scale = 2
    val w = 620
    val pad = 36
    val lineH = 34
    val h = 150 + rows.size * lineH + 120
    val bmp = Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.scale(scale.toFloat(), scale.toFloat())
    canvas.drawColor(Color.WHITE)

    val left = pad.toFloat()
    val right = (w - pad).toFloat()
    var y = (pad + 24).toFloat()

    val ink = Color.parseColor("#16181F")
    val muted = Color.parseColor("#5B6168")
    val blue = Color.parseColor("#1D4ED8")
    val ruleColor = Color.parseColor("#E7E3DA")
    val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun text(s: String, x: Float, yy: Float, size: Float, bold: Boolean, color: Int, alignRight: Boolean = false) {
        p.color = color
        p.textSize = size
        p.typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.textAlign = if (alignRight) Paint.Align.RIGHT else Paint.Align.LEFT
        canvas.drawText(s, x, yy, p)
    }
    fun rule(yy: Float) {
        p.color = ruleColor
        p.strokeWidth = 1f
        canvas.drawLine(left, yy, right, yy, p)
    }

    text(state.shop.name, left, y, 30f, true, ink); y += 26
    text("+91 ${Selectors.fmtPhone(state.shop.phone)}", left, y, 15f, false, muted); y += 20
    rule(y); y += 26
    text("Bill #${o.id} · ${AppDate.plain(o.createdOn)}", left, y, 14f, true, muted); y += 24
    text("To: ${c.name}", left, y, 16f, true, ink); y += 30
    rows.forEach { r ->
        text(r.label, left, y, 16f, false, ink)
        text(r.value, right, y, 16f, true, ink, alignRight = true)
        y += lineH
    }
    y += 2; rule(y); y += 30
    text("Total", left, y, 20f, true, ink)
    text(total, right, y, 20f, true, ink, alignRight = true); y += 38
    text("Thank you!", left, y, 16f, true, blue)
    return bmp
}

/** Renders the bill to a PNG and opens the share sheet (save or send). */
fun shareBillImage(context: Context, state: LaundryState, o: Order) {
    val bmp = renderBillBitmap(state, o)
    val dir = File(context.cacheDir, "bills").apply { mkdirs() }
    val file = File(dir, "Bill-${o.id}.png")
    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, "Bill #${o.id}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
