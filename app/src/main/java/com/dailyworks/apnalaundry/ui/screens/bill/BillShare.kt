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
import com.dailyworks.apnalaundry.data.LogoStore
import com.dailyworks.apnalaundry.domain.BillDetails
import com.dailyworks.apnalaundry.domain.BillReceipt
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.ui.Selectors
import java.io.File
import java.io.FileOutputStream
import org.koin.core.context.GlobalContext

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
        appendLine(BillDetails.fmtBillPhone(state.shop.billPhone.ifBlank { state.shop.phone }))
        appendLine()
        appendLine("Bill #${o.id} · ${AppDate.plain(o.createdOn)}")
        appendLine("To: ${c.name}")
        appendLine()
        appendLine(body)
        appendLine("————————")
        appendLine("*Total: $total*")
        if (BillDetails.isUpiValid(state.shop.upiId)) appendLine("Pay by UPI: ${state.shop.upiId}")
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

/** Renders the bill to a PNG and opens the share sheet (save or send). */
fun shareBillImage(context: Context, state: LaundryState, o: Order) {
    val logos = GlobalContext.get().get<LogoStore>()
    val receipt = BillReceipt.of(state, o)
    val bmp = BillRender.render(context, receipt, logos.cached(receipt.logoId))
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

/** Public bill pages: mylaundry.work/b/<token>. */
const val BILL_PAGE_URL = "https://mylaundry.work/b/"

/**
 * Onboarding "Test on WhatsApp": a message with the link to the shop's sample
 * bill page (in the chosen design), with no number, so WhatsApp lets the
 * owner pick who gets it. Real bills open the customer's chat directly.
 * Offline (no link) it falls back to the bill as text.
 */
fun sendTestBillOnWhatsApp(context: Context, r: BillReceipt, link: String?) {
    val text = if (link != null) {
        "Here is a sample bill from *${r.shopName}* — Total *${r.total}*.\n\nView / download the bill:\n$link\n\nThank you!"
    } else buildString {
        appendLine("*${r.shopName}* — sample bill")
        appendLine("${r.billNo} · ${r.date}")
        r.lines.forEach { appendLine("${it.item}  ${it.amount}") }
        r.extras.forEach { appendLine("${it.label}  ${it.value}") }
        appendLine("*Total ${r.total}*")
        append("Thank you!")
    }
    openWhatsApp(context, "https://wa.me/?text=${Uri.encode(text)}")
}
