package com.dailyworks.apnalaundry.ui.screens.bill

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.data.LogoStore
import com.dailyworks.apnalaundry.domain.BillReceipt
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.ui.Selectors
import java.io.File
import java.io.FileOutputStream
import org.koin.core.context.GlobalContext

/**
 * "Download" and "Send on WhatsApp" for a bill (mirror of the web
 * ui/billActions.ts + ui/billPdf.ts). Both hand over the bill as a PDF in the
 * shop's chosen design ([BillRender], vector text) — no storage permission.
 *  - WhatsApp: ACTION_SEND straight into WhatsApp (or WhatsApp Business) with
 *    the customer's chat preselected via the "jid" extra and the PDF attached.
 *  - Download: the system share sheet with the PDF (save to Files, or send).
 */

/** Public bill pages: mylaundry.work/b/<token>. */
const val BILL_PAGE_URL = "https://mylaundry.work/b/"

/** Writes cacheDir/bills/Bill-<id>.pdf in the shop's design and returns a content:// uri. */
private fun billPdfUri(context: Context, state: LaundryState, o: Order): Uri {
    val receipt = BillReceipt.of(state, o)
    val logo = GlobalContext.get().get<LogoStore>().cached(receipt.logoId)
    val file = File(File(context.cacheDir, "bills").apply { mkdirs() }, "Bill-${o.id}.pdf")
    val doc = PdfDocument()
    try {
        val h = kotlin.math.ceil(BillRender.height(context, receipt, logo)).toInt()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(BillRender.W.toInt(), h, 1).create())
        BillRender.drawPage(context, page.canvas, receipt, logo)
        doc.finishPage(page)
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

/** Sends the bill PDF to the customer on WhatsApp (their chat opens directly). Caller marks it sent. */
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

// ---------------- khata reminder (text only) ----------------

private fun reminderText(state: LaundryState, custId: String, bal: Int): String {
    val c = Selectors.customer(state, custId)
    return buildString {
        appendLine("*${state.shop.name}*")
        appendLine("+91 ${Selectors.fmtPhone(state.shop.billPhone.ifBlank { state.shop.phone })}")
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
    openWhatsApp(context, if (digits.length == 10) "https://wa.me/91$digits?text=$text" else "https://wa.me/?text=$text")
}

/** Opens a wa.me link (WhatsApp, or the browser if it isn't installed). */
fun openWhatsApp(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No WhatsApp / browser to handle it.
    }
}
