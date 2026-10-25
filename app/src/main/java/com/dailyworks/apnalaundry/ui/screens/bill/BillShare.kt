package com.dailyworks.apnalaundry.ui.screens.bill

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.dailyworks.apnalaundry.core.I18n
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.core.tr
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

/** Bill-<id>.pdf in the shop's design (the bill document stays English, like its web page). */
private fun billPdfUri(context: Context, state: LaundryState, o: Order): Uri = I18n.english {
    val receipt = BillReceipt.of(state, o)
    val logo = cachedLogo(receipt.logoId)
    pdfUri(context, "Bill-${o.no().replace('/', '-')}.pdf", BillRender.height(context, receipt, logo)) {
        BillRender.drawPage(context, it, receipt, logo)
    }
}

/** Combined-bill-<FirstName>-<period>.pdf in the shop's design. */
private fun combinedBillPdfUri(context: Context, r: CombinedReceipt): Uri = I18n.english {
    val logo = cachedLogo(r.logoId)
    pdfUri(context, CombinedBill.fileName(r.customerName, r.period), BillRender.height(context, r, logo)) {
        BillRender.drawPage(context, it, r, logo)
    }
}

/**
 * Writes the bill as a PNG (3× for sharp text) at cacheDir/bills/[fileName] and
 * returns a content:// uri. WhatsApp keeps a caption on a picture, never on a
 * document — so bills go to customers as a picture, with the message.
 */
private fun pngUri(context: Context, fileName: String, heightPt: Float, draw: (android.graphics.Canvas) -> Unit): Uri {
    // 3× for sharp text, less for a very tall bill (a long combined bill): a
    // bitmap past ~9000px tall runs cheap phones out of memory.
    val scale = minOf(3f, 9000f / heightPt.coerceAtLeast(1f))
    val file = File(File(context.cacheDir, "bills").apply { mkdirs() }, fileName)
    val bmp = android.graphics.Bitmap.createBitmap(
        (BillRender.W * scale).toInt(), kotlin.math.ceil(heightPt * scale).toInt(), android.graphics.Bitmap.Config.ARGB_8888,
    )
    try {
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.scale(scale, scale)
        draw(canvas)
        FileOutputStream(file).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    } finally {
        bmp.recycle()
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun billPngUri(context: Context, state: LaundryState, o: Order): Uri = I18n.english {
    val receipt = BillReceipt.of(state, o)
    val logo = cachedLogo(receipt.logoId)
    pngUri(context, "Bill-${o.no().replace('/', '-')}.png", BillRender.height(context, receipt, logo)) {
        BillRender.drawPage(context, it, receipt, logo)
    }
}

private fun combinedBillPngUri(context: Context, r: CombinedReceipt): Uri = I18n.english {
    val logo = cachedLogo(r.logoId)
    pngUri(context, CombinedBill.fileName(r.customerName, r.period).removeSuffix(".pdf") + ".png", BillRender.height(context, r, logo)) {
        BillRender.drawPage(context, it, r, logo)
    }
}

// ---------------- sending ----------------

private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

/**
 * Message that travels with the bill picture — the web's billMessage
 * (asterisks render bold), with the bill page's link so the customer can
 * download the PDF. Offline / not synced yet: the message without the link.
 */
private fun billCaption(state: LaundryState, o: Order, link: String?): String {
    val c = Selectors.customer(state, o.custId)
    val hello = tr("Hi {name}, here is your bill for order #{no} from *{shop}* — Total *{amount}*.",
        "name" to Selectors.firstName(c.name), "no" to o.no(), "shop" to state.shop.name, "amount" to Money.rupees(LaundryMath.amtOf(o)))
    return hello +
        (if (link != null) "\n\n" + tr("Download PDF: {link}", "link" to link) else "") +
        "\n\n" + tr("Thank you!")
}

private fun combinedBillCaption(shopName: String, r: CombinedReceipt): String {
    val orders = if (r.orders == 1) tr("{n} order", "n" to r.orders) else tr("{n} orders", "n" to r.orders)
    val name = Selectors.firstName(r.customerName)
    val head = if (r.period.isNotEmpty()) {
        tr("Hi {name}, here is your combined bill from {shop} for {period} — {orders}, total {total}.",
            "name" to name, "shop" to shopName, "period" to r.period, "orders" to orders, "total" to r.total)
    } else {
        tr("Hi {name}, here is your combined bill from {shop} — {orders}, total {total}.",
            "name" to name, "shop" to shopName, "orders" to orders, "total" to r.total)
    }
    val pay = if (r.fullyPaid) tr("All paid.") else tr("To pay {amount}.", "amount" to r.due)
    return "$head $pay " + tr("Thank you!")
}

/**
 * Hands the bill picture + message to WhatsApp with the customer's chat
 * preselected (the "jid" extra), never through Android's share sheet:
 *  - one WhatsApp installed → straight into it;
 *  - both personal and Business → the owner picks once ("Send bills from"),
 *    and that choice is remembered for every later bill;
 *  - none → the share sheet, as the only way left.
 */
private fun sendImageOnWhatsApp(context: Context, uri: Uri, phoneDigits: String, caption: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, caption)
        if (phoneDigits.length == 10) putExtra("jid", "91$phoneDigits@s.whatsapp.net")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val installed = WHATSAPP_PACKAGES.filter { context.packageManager.getLaunchIntentForPackage(it) != null }
    val prefs = context.getSharedPreferences("bill_share", Context.MODE_PRIVATE)
    val saved = prefs.getString("whatsapp_pkg", null)?.takeIf { it in installed }
    val chooser = { Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    fun launch(pkg: String) {
        try {
            context.startActivity(Intent(send).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(chooser())
        }
    }

    when {
        installed.isEmpty() -> context.startActivity(chooser())
        installed.size == 1 -> launch(installed[0])
        saved != null -> launch(saved)
        context !is android.app.Activity -> launch(installed[0]) // no screen to ask on
        else -> {
            // Both installed, not chosen yet: ask once, remember for next time.
            android.app.AlertDialog.Builder(context)
                .setTitle(tr("Send bills from"))
                .setItems(arrayOf("WhatsApp", "WhatsApp Business")) { _, which ->
                    val pkg = WHATSAPP_PACKAGES[which]
                    prefs.edit().putString("whatsapp_pkg", pkg).apply()
                    launch(pkg)
                }
                .setNegativeButton(tr("Cancel"), null)
                .show()
        }
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

/**
 * Sends the bill to the customer on WhatsApp — their chat opens directly with
 * the bill picture and the message ([link] = the bill page, for the PDF).
 * Caller marks it sent.
 */
suspend fun sendBillOnWhatsApp(context: Context, state: LaundryState, o: Order, link: String?) {
    // Drawing + saving the picture takes a moment on a cheap phone: off the main thread.
    val uri = withContext(Dispatchers.Default) { billPngUri(context, state, o) }
    sendImageOnWhatsApp(context, uri, phoneDigits(state, o.custId), billCaption(state, o, link), tr("Send bill #{no}", "no" to o.no()))
}

/** Renders the bill to a PDF and opens the share sheet (save or send). */
suspend fun shareBillPdf(context: Context, state: LaundryState, o: Order) {
    val uri = withContext(Dispatchers.Default) { billPdfUri(context, state, o) }
    sharePdf(context, uri, tr("Bill #{no}", "no" to o.no()))
}

/**
 * Sends a combined bill (many orders, one picture + message) to the customer on WhatsApp.
 * A document only — nothing is written to the khata.
 */
suspend fun sendCombinedBillOnWhatsApp(context: Context, state: LaundryState, custId: String, r: CombinedReceipt) {
    val uri = withContext(Dispatchers.Default) { combinedBillPngUri(context, r) }
    sendImageOnWhatsApp(context, uri, phoneDigits(state, custId), combinedBillCaption(state.shop.name, r), tr("Send combined bill"))
}

/** The combined bill as a PDF in the share sheet (save or send). */
suspend fun shareCombinedBillPdf(context: Context, r: CombinedReceipt) {
    val uri = withContext(Dispatchers.Default) { combinedBillPdfUri(context, r) }
    sharePdf(context, uri, tr("Combined bill"))
}

/**
 * Onboarding "Test on WhatsApp": a message with the link to the shop's sample
 * bill page (in the chosen design), with no number, so WhatsApp lets the
 * owner pick who gets it. Real bills open the customer's chat directly.
 * Offline (no link) it falls back to the bill as text.
 */
fun sendTestBillOnWhatsApp(context: Context, r: BillReceipt, link: String?) {
    val text = if (link != null) {
        tr("Here is a sample bill from *{shop}* — Total *{amount}*.", "shop" to r.shopName, "amount" to r.total) +
            "\n\n" + tr("View / download the bill:") + "\n$link\n\n" + tr("Thank you!")
    } else buildString {
        appendLine(tr("*{shop}* — sample bill", "shop" to r.shopName))
        appendLine("${r.billNo} · ${r.date}")
        r.lines.forEach { appendLine("${it.item}  ${it.amount}") }
        r.extras.forEach { appendLine("${it.label}  ${it.value}") }
        appendLine(tr("*Total {amount}*", "amount" to r.total))
        append(tr("Thank you!"))
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
        appendLine(tr("{name}, a gentle reminder — your laundry balance is *{amount}*.", "name" to c.name, "amount" to Money.rupees(bal)))
        append(tr("Please clear it on your next visit. Thank you!"))
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
