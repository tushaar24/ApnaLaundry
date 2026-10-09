package com.dailyworks.apnalaundry.domain

import java.net.URLEncoder
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Optional bill details (phone on bill, address, GSTIN, UPI, logo, terms) and
 * the bill designs: defaults, validation, and the sample order the onboarding
 * "Your bill" step previews. Port twin: web/src/domain/billDetails.ts.
 */
object BillDetails {
    const val CLASSIC = "classic"
    const val BOLD = "bold"
    const val RECEIPT = "receipt"

    val TEMPLATES = listOf(CLASSIC to "Classic", BOLD to "Bold", RECEIPT to "Receipt")

    val STEPS = listOf("intro", "name", "services", "bill", "done")

    data class Term(val id: String, val text: String)

    val TERM_PRESETS = listOf(
        Term("check", "Please check your clothes at delivery. No claims after."),
        Term("fade", "Not responsible for colour fading or shrinkage."),
        Term("pocket", "Please empty pockets. Not responsible for things left inside."),
        Term("days", "Clothes not collected within 30 days are at the owner's risk."),
    )

    /** The editable bill details of a shop. */
    data class Fields(
        val billPhone: String = "",
        val address: String = "",
        val gstin: String = "",
        val upiId: String = "",
        val logoId: String = "",
        val terms: List<String> = emptyList(),
        val termsCustom: String = "",
        val billTemplate: String = CLASSIC,
    )

    fun fieldsOf(s: Shop, loginPhone: String = s.phone) = Fields(
        billPhone = s.billPhone.ifBlank { loginPhone }, address = s.address, gstin = s.gstin,
        upiId = s.upiId, logoId = s.logoId, terms = s.terms, termsCustom = s.termsCustom, billTemplate = s.billTemplate,
    )

    fun Shop.withFields(f: Fields) = copy(
        billPhone = f.billPhone, address = f.address, gstin = f.gstin, upiId = f.upiId, logoId = f.logoId,
        terms = f.terms, termsCustom = f.termsCustom, billTemplate = f.billTemplate,
    )

    fun templateOrDefault(t: String) = if (TEMPLATES.any { it.first == t }) t else CLASSIC
    fun stepOrBlank(s: String) = if (s in STEPS) s else ""
    fun templateLabel(t: String) = TEMPLATES.firstOrNull { it.first == t }?.second ?: "Classic"

    // ── input clean-up ──
    fun cleanName(s: String) = s.trim().replace(Regex("\\s+"), " ").take(40)
    fun cleanPhone(s: String) = s.filter { it.isDigit() }.takeLast(10)
    fun cleanAddress(s: String) = s.replace(Regex("\\s+"), " ").trim().take(120)
    fun cleanGstin(s: String) = s.uppercase().filter { it in '0'..'9' || it in 'A'..'Z' }.take(15)
    fun cleanUpi(s: String) = s.trim().replace(Regex("\\s+"), "").take(60)
    fun cleanTermsCustom(s: String) = s.replace(Regex("\\s+"), " ").trim().take(80)

    // ── validation (handoff §8) ──
    const val NAME_MIN = 2
    fun isNameOk(s: String) = s.trim().length >= NAME_MIN

    private val GSTIN_RE = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]$")
    private val UPI_RE = Regex("^[A-Za-z0-9._-]{2,}@[A-Za-z]{2,}$")

    fun isGstinValid(s: String) = GSTIN_RE.matches(s)
    fun isUpiValid(s: String) = UPI_RE.matches(s)
    fun isPhoneValid(s: String) = s.length == 10 && s.all { it.isDigit() }

    data class Check(val ok: Boolean, val message: String)

    /** Live message under the GSTIN input; null while empty. Format-only. */
    fun gstinCheck(s: String): Check? = when {
        s.isEmpty() -> null
        s.length != 15 -> Check(false, "A GSTIN has 15 characters — you typed ${s.length}")
        !isGstinValid(s) -> Check(false, "This does not look like a GSTIN — please check it")
        else -> Check(true, "Looks right — prints under your laundry name")
    }

    fun upiCheck(s: String): Check? = when {
        s.isEmpty() -> null
        !isUpiValid(s) -> Check(false, "A UPI ID looks like name@bank — please check it")
        else -> Check(true, "A pay QR will print on every bill")
    }

    /** "9876543210" -> "+91 98765 43210". */
    fun fmtBillPhone(p: String): String = when {
        p.length == 10 -> "+91 ${p.substring(0, 5)} ${p.substring(5)}"
        p.isNotEmpty() -> "+91 $p"
        else -> ""
    }

    /** Terms printed at the bottom of a bill: presets in order, then the custom line. */
    fun termLines(terms: List<String>, custom: String): List<String> =
        TERM_PRESETS.filter { it.id in terms }.map { it.text } + listOfNotNull(custom.trim().ifEmpty { null })

    /** How many optional fields the owner filled (the "+ Add fields" badge). */
    fun addedCount(f: Fields, loginPhone: String): Int {
        var n = 0
        if (isUpiValid(f.upiId)) n++
        if (f.logoId.isNotEmpty()) n++
        if (f.address.isNotEmpty()) n++
        if (termLines(f.terms, f.termsCustom).isNotEmpty()) n++
        if (isGstinValid(f.gstin)) n++
        if (f.billPhone.isNotEmpty() && f.billPhone != loginPhone) n++
        return n
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** upi://pay link the bill's QR encodes. */
    fun upiPayload(upiId: String, shopName: String, amount: Int, billNo: Int): String =
        "upi://pay?pa=${enc(upiId)}&pn=${enc(shopName)}&am=$amount&cu=INR&tn=${enc("Bill $billNo")}"

    // ── the sample order for the "Your bill" preview (handoff §9) ──

    private fun priced(s: Service) = s.items.filter { (it.price ?: 0) > 0 }

    private fun pieceLine(s: Service, name: String, qty: Int): OrderLine? {
        val it = priced(s).firstOrNull { i -> i.name.equals(name, ignoreCase = true) } ?: return null
        val p = it.price ?: return null
        return OrderLine(s.id, s.name, it.name, qty, p, p, 0.0, p * qty)
    }

    /** A believable order built from the owner's own rates. */
    fun sampleOrder(services: List<Service>, expressPct: Int, billNo: Int, today: LocalDate = LocalDate.now()): Order {
        val svcs = services.filter { it.mode == PricingMode.PIECE && priced(it).isNotEmpty() }
        val wi = svcs.firstOrNull { Regex("wash\\s*&?\\s*iron", RegexOption.IGNORE_CASE).containsMatchIn(it.name) } ?: svcs.firstOrNull()
        val dc = svcs.firstOrNull { Regex("dry\\s*clean", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
            ?: svcs.firstOrNull { it != wi }
        val lines = mutableListOf<OrderLine>()
        if (wi != null) {
            pieceLine(wi, "Shirt", 3)?.let(lines::add)
            pieceLine(wi, "Pant", 2)?.let(lines::add)
            if (lines.isEmpty()) {
                priced(wi).take(2).forEachIndexed { i, it -> pieceLine(wi, it.name, if (i == 0) 3 else 2)?.let(lines::add) }
            }
        }
        if (dc != null) {
            (pieceLine(dc, "Saree", 1) ?: priced(dc).firstOrNull()?.let { pieceLine(dc, it.name, 1) })?.let(lines::add)
        }
        if (lines.size < 2) {
            services.firstOrNull { it.mode == PricingMode.WEIGHT && (it.ratePerKg ?: 0) > 0 }?.let { s ->
                val rate = s.ratePerKg ?: 0
                lines.add(OrderLine(s.id, s.name, "By weight", 0, rate, rate, 4.0, rate * 4))
            }
        }
        val subtotal = lines.sumOf { it.amt }
        val exAmt = (subtotal * expressPct / 100.0).roundToInt()
        val t = today.toString()
        return Order(
            id = billNo, custId = "", pickup = Route.SHOP, delivery = Route.SHOP,
            pickupDate = t, pickupTime = "", deliveryDate = today.plusDays(1).toString(), deliveryTime = "6 PM", ddAuto = false,
            status = OrderStatus.RECEIVED, cancelReason = "", fee = 0, express = exAmt > 0, exAmt = exAmt,
            discount = if (subtotal >= 100) 20 else 0, pre = 0, paid = 0, doneAt = "", doneDate = "",
            createdOn = t, billSent = false, pieces = 0, lines = lines,
        )
    }
}
