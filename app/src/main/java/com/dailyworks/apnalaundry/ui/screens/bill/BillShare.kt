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
import com.dailyworks.apnalaundry.domain.CombinedBill
import com.dailyworks.apnalaundry.domain.CombinedReceipt
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

/** Writes a one-page PDF of [heightPt] points at cacheDir/bills/[fileName] and returns a content:// uri. */
private fun pdfUri(context: Context, fileName: String, heightPt: Float, draw: (android.graphics.Canvas) -> Unit): Uri {
    val file = File(File(context.cacheDir, "bills").apply { mkdirs() }, fileName)
    val doc = PdfDocument()
    try {
        val h = kotlin.math.ceil(heightPt).toInt()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(BillRender.W.toInt(), h, 1).create())
        draw(page.canvas)
        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
    } finally {
        doc.close()
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun cachedLogo(logoId: String) = GlobalContext.get().get<LogoStore>().cached(logoId)

/** Bill-<id>.pdf in the shop's design. */
private fun billPdfUri(context: Context, state: LaundryState, o: Order): Uri {
    val receipt = BillReceipt.of(state, o)
    val logo = cachedLogo(receipt.logoId)
    return pdfUri(context, "Bill-${o.no().replace('/', '-')}.pdf", BillRender.height(context, receipt, logo)) {
        BillRender.drawPage(context, it, receipt, logo)
    }
}

/** Combined-bill-<FirstName>-<period>.pdf in the shop's design. */
private fun combinedBillPdfUri(context: Context, r: CombinedReceipt): Uri {
    val logo = cachedLogo(r.logoId)
    return pdfUri(context, CombinedBill.fileName(r.customerName, r.period), BillRender.height(context, r, logo)) {
        BillRender.drawPage(context, it, r, logo)
    }
}

// ---------------- sending ----------------

private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

/**
 * Message that travels with the PDF — the web's billMessage (asterisks render
 * bold) minus the bill-page link, since the PDF itself is attached.
 */
private fun billCaption(state: LaundryState, o: Order): String {
    val c = Selectors.customer(state, o.custId)
    return "Hi ${Selectors.firstName(c.name)}, here is your bill for order #${o.no()} from *${state.shop.name}* — " +
        "Total *${Money.rupees(LaundryMath.amtOf(o))}*.\n\nThank you!"
}

private fun combinedBillCaption(shopName: String, r: CombinedReceipt): String {
    val period = if (r.period.isNotEmpty()) " for ${r.period}" else ""
    val pay = if (r.fullyPaid) "All paid" else "To pay ${r.due}"
    return "Hi ${Selectors.firstName(r.customerName)}, here is your combined bill from $shopName$period — " +
        "${Selectors.countNoun(r.orders, "order")}, total ${r.total}. $pay. Thank you!"
}

/**
 * Hands a PDF to WhatsApp with the customer's chat preselected (the "jid"
 * extra), never through Android's share sheet:
 *  - one WhatsApp installed → straight into it;
 *  - both personal and Business → the owner picks once ("Send bills from"),
 *    and that choice is remembered for every later bill;
 *  - none → the share sheet, as the only way left.
 * The caption rides along as EXTRA_TEXT, and is also copied to the clipboard,
 * because WhatsApp doesn't always keep a caption on a document.
 */
private fun sendPdfOnWhatsApp(context: Context, uri: Uri, phoneDigits: String, caption: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, caption)
        if (phoneDigits.length == 10) putExtra("jid", "91$phoneDigits@s.whatsapp.net")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val installed = WHATSAPP_PACKAGES.filter { context.packageManager.getLaunchIntentForPackage(it) != null }
    val prefs = context.getSharedPreferences("bill_share", Context.MODE_PRIVATE)
    val saved = prefs.getString("whatsapp_pkg", null)?.takeIf { it in installed }

    fun launch(pkg: String) {
        copyCaption(context, caption)
        try {
            context.startActivity(Intent(send).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            android.widget.Toast.makeText(
                context, "Message copied — if WhatsApp doesn't show it, long-press the box and Paste", android.widget.Toast.LENGTH_LONG,
            ).show()
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    when {
        installed.isEmpty() -> {
            copyCaption(context, caption)
            context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        installed.size == 1 -> launch(installed[0])
        saved != null -> launch(saved)
        context !is android.app.Activity -> launch(installed[0]) // no screen to ask on
        else -> {
            // Both installed, not chosen yet: ask once, remember for next time.
            val labels = arrayOf("WhatsApp", "WhatsApp Business")
            android.app.AlertDialog.Builder(context)
                .setTitle("Send bills from")
                .setItems(labels) { _, which ->
                    val pkg = WHATSAPP_PACKAGES[which]
                    prefs.edit().putString("whatsapp_pkg", pkg).apply()
                    launch(pkg)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}

private fun copyCaption(context: Context, caption: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Bill message", caption))
    }
}

/** The system share sheet with a PDF (save to Files, or send anywhere). */
private fun sharePdf(context: Context, uri: Uri, title: String) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun phoneDigits(state: LaundryState, custId: String): String =
    Selectors.customer(state, custId).phone.filter { it.isDigit() }.takeLast(10)

/** Sends the bill PDF to the customer on WhatsApp (their chat opens directly). Caller marks it sent. */
fun sendBillOnWhatsApp(context: Context, state: LaundryState, o: Order) {
    sendPdfOnWhatsApp(context, billPdfUri(context, state, o), phoneDigits(state, o.custId), billCaption(state, o), "Send bill #${o.no()}")
}

/** Renders the bill to a PDF and opens the share sheet (save or send). */
fun shareBillPdf(context: Context, state: LaundryState, o: Order) {
    sharePdf(context, billPdfUri(context, state, o), "Bill #${o.no()}")
}

/**
 * Sends a combined bill (many orders, one PDF) to the customer on WhatsApp.
 * A document only — nothing is written to the khata.
 */
fun sendCombinedBillOnWhatsApp(context: Context, state: LaundryState, custId: String, r: CombinedReceipt) {
    sendPdfOnWhatsApp(
        context, combinedBillPdfUri(context, r), phoneDigits(state, custId),
        combinedBillCaption(state.shop.name, r), "Send combined bill",
    )
}

/** The combined bill as a PDF in the share sheet (save or send). */
fun shareCombinedBillPdf(context: Context, r: CombinedReceipt) {
    sharePdf(context, combinedBillPdfUri(context, r), "Combined bill")
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
