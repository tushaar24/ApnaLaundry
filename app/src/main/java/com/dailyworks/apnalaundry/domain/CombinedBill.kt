package com.dailyworks.apnalaundry.domain

import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.ui.Selectors
import java.net.URLEncoder
import java.time.LocalDate
import java.time.YearMonth

/**
 * Combined bill: many orders of one customer on one bill, for customers who
 * pay once a month. It is a document, not a charge — the orders were billed
 * one by one already, so sending it never touches the khata.
 * Port of the design's cbOrders / cbRange / cbPick / vCbill
 * (Claude Design canvas "Laundry App — Orders & Billing", boards 3b–3d).
 */
object CombinedBill {

    /** Which orders a chip ticks. [CUSTOM] uses the From / To dates. */
    enum class Preset(val label: String) {
        CUSTOM("Custom dates"),
        D7("Last 7 days"),
        D30("Last 30 days"),
        MONTH("This month"),
        LAST("Last month"),
    }

    /**
     * The orders that can go on a combined bill: counted (they have lines)
     * and not cancelled, newest pickup first. The pickup date decides the
     * month an order belongs to.
     */
    fun ordersOf(state: LaundryState, custId: String): List<Order> =
        state.orders
            .filter { it.custId == custId && it.status != OrderStatus.CANCELLED && it.lines.isNotEmpty() }
            .sortedWith(compareByDescending<Order> { it.pickupDate }.thenByDescending { it.id })

    /** Paid toward an order: what was collected once delivered, else the advance so far. Never above the total. */
    fun paidOf(o: Order): Int =
        minOf(LaundryMath.amtOf(o), if (o.status == OrderStatus.DELIVERED) o.paid else o.pre)

    /** Inclusive ISO [from, to] a chip covers, as of [today]. */
    fun range(preset: Preset, today: String, from: String = "", to: String = ""): Pair<String, String> {
        val ym = YearMonth.from(LocalDate.parse(today))
        return when (preset) {
            Preset.MONTH -> ym.atDay(1).toString() to today
            Preset.LAST -> ym.minusMonths(1).let { it.atDay(1).toString() to it.atEndOfMonth().toString() }
            Preset.D7 -> AppDate.add(today, -6) to today
            Preset.D30 -> AppDate.add(today, -29) to today
            Preset.CUSTOM -> from.ifBlank { ym.atDay(1).toString() } to to.ifBlank { today }
        }
    }

    /** The ids of [orders] whose pickup date falls in [range]. */
    fun pick(orders: List<Order>, range: Pair<String, String>): Set<Int> =
        orders.filter { it.pickupDate >= range.first && it.pickupDate <= range.second }.map { it.id }.toSet()

    data class Totals(val total: Int, val paid: Int) {
        val due: Int get() = total - paid
    }

    fun totals(picked: List<Order>): Totals =
        Totals(picked.sumOf { LaundryMath.amtOf(it) }, picked.sumOf { paidOf(it) })

    enum class TagKind { PAID, DUE, OPEN }

    /** The small status tag on an order row: Paid (green), ₹200 due (orange), not delivered (blue). */
    data class Tag(val label: String, val kind: TagKind)

    fun tag(o: Order): Tag {
        val amt = LaundryMath.amtOf(o)
        val paid = paidOf(o)
        return when {
            o.status != OrderStatus.DELIVERED -> Tag(
                when {
                    paid >= amt -> "Paid · not delivered"
                    o.status == OrderStatus.READY -> "Ready · not delivered"
                    else -> "In progress"
                },
                TagKind.OPEN,
            )
            paid >= amt -> Tag("Paid", TagKind.PAID)
            else -> Tag("${Money.rupees(amt - paid)} due", TagKind.DUE)
        }
    }

    private val MONTHS = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    /** "16 Sep" */
    fun dayMonth(iso: String): String = AppDate.plain(iso).substringAfter(", ")

    /** "September 2026" */
    fun monthName(iso: String): String = "${MONTHS[LocalDate.parse(iso).monthValue - 1]} ${iso.take(4)}"

    /** "16 Sep 2026" */
    fun dayMonthYear(iso: String): String = "${dayMonth(iso)} ${iso.take(4)}"

    /** "19 Sep – 25 Sep 2026" (one date: "19 Sep 2026"; across years: "28 Dec 2026 – 3 Jan 2027"). */
    fun span(a: String, b: String): String = when {
        a == b -> dayMonthYear(a)
        a.take(4) != b.take(4) -> "${dayMonthYear(a)} – ${dayMonthYear(b)}"
        else -> "${dayMonth(a)} – ${dayMonth(b)} ${b.take(4)}"
    }

    /**
     * The period printed on the bill: the month for This / Last month, the
     * date range for the other chips, and for hand-picked orders the first
     * to last pickup date of what was ticked.
     */
    fun period(preset: Preset?, picked: List<Order>, today: String, from: String = "", to: String = ""): String {
        if (preset == Preset.MONTH || preset == Preset.LAST) return monthName(range(preset, today).first)
        if (preset != null) return range(preset, today, from, to).let { span(it.first, it.second) }
        if (picked.isEmpty()) return ""
        val dates = picked.map { it.pickupDate }.sorted()
        return span(dates.first(), dates.last())
    }

    /** Combined-bill-Anjali-September-2026.pdf */
    fun fileName(customerName: String, period: String): String {
        val stem = "Combined-bill-${Selectors.firstName(customerName)}-${period.ifBlank { "orders" }}"
        return stem.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-') + ".pdf"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** upi://pay link the combined bill's QR encodes — the amount is what is still to pay. */
    fun upiPayload(upiId: String, shopName: String, amount: Int, period: String): String =
        "upi://pay?pa=${enc(upiId)}&pn=${enc(shopName)}&am=$amount&cu=INR&tn=${enc("Combined bill $period")}"

    private fun itemLines(o: Order): List<BillReceipt.Row> = buildList {
        o.lines.forEach { l ->
            val name = when {
                l.kg > 0 -> Selectors.weightLabel(l)
                l.isQuick -> if (l.qty > 0) "${l.itemName} × ${l.qty}" else l.itemName
                else -> "${l.itemName} × ${l.qty}"
            }
            val rate = BillReceipt.rate(l)
            add(BillReceipt.Row(if (rate.isNotEmpty()) "$name · $rate" else name, Money.rupees(l.amt)))
        }
        if (o.express && o.exAmt > 0) add(BillReceipt.Row("Express", "+ ${Money.rupees(o.exAmt)}"))
        if (o.fee > 0) add(BillReceipt.Row("Pickup / delivery", "+ ${Money.rupees(o.fee)}"))
        if (o.discount > 0) add(BillReceipt.Row("Discount", "− ${Money.rupees(o.discount)}", discount = true))
    }

    /**
     * Everything printed on the combined bill, formatted. [picked] may be in
     * any order; the bill lists them oldest pickup first. [showItems] adds
     * each order's lines (Shirt × 2, Express, …) under its row.
     */
    fun receipt(
        state: LaundryState, custId: String, picked: List<Order>, period: String, showItems: Boolean,
        today: String = AppDate.TODAY,
    ): CombinedReceipt {
        val shop = state.shop
        val c = Selectors.customer(state, custId)
        val phone = c.phone.filter { it.isDigit() }.takeLast(10)
        val rows = picked.sortedWith(compareBy<Order> { it.pickupDate }.thenBy { it.id }).map { o ->
            CombinedReceipt.Row(
                title = "${dayMonth(o.pickupDate)} · #${o.no()}",
                sub = "${Selectors.itemsLabel(o)} · ${Selectors.svcLabel(o)}",
                amount = Money.rupees(LaundryMath.amtOf(o)),
                tag = tag(o),
                lines = if (showItems) itemLines(o) else emptyList(),
            )
        }
        val t = totals(picked)
        val due = maxOf(0, t.due)
        return CombinedReceipt(
            template = BillDetails.templateOrDefault(shop.billTemplate),
            shopName = shop.name,
            shopPhone = BillDetails.fmtBillPhone(shop.billPhone.ifBlank { shop.phone }),
            address = shop.address,
            gstin = if (BillDetails.isGstinValid(shop.gstin)) shop.gstin else "",
            email = if (BillDetails.isEmailValid(shop.email)) shop.email else "",
            logoId = shop.logoId,
            billDate = "Bill date ${dayMonthYear(today)}",
            period = period,
            customerName = c.name,
            customerPhone = if (phone.isNotEmpty()) "+91 ${Selectors.fmtPhone(phone)}" else "",
            rows = rows,
            orders = picked.size,
            totalLabel = "Total of ${Selectors.countNoun(picked.size, "order")}",
            total = Money.rupees(t.total),
            paid = if (t.paid > 0) Money.rupees(t.paid) else "",
            due = Money.rupees(due),
            totalAmount = t.total,
            dueAmount = due,
            upi = if (due > 0 && BillDetails.isUpiValid(shop.upiId)) {
                BillReceipt.Upi(shop.upiId, upiPayload(shop.upiId, shop.name, due, period), Money.rupees(due))
            } else null,
            terms = BillDetails.termLines(shop.terms, shop.termsCustom),
        )
    }
}

/**
 * A combined bill ready to draw — the same shop header, UPI QR and terms as
 * a single [BillReceipt], so the bill renderer prints it in the shop's
 * chosen design (Classic / Bold / Receipt).
 */
data class CombinedReceipt(
    val template: String,
    val shopName: String,
    val shopPhone: String,
    val address: String,
    val gstin: String,
    val email: String, // only when valid
    val logoId: String,
    val billDate: String, // "Bill date 25 Sep 2026"
    val period: String, // "September 2026" / "19 Sep – 25 Sep 2026" / ""
    val customerName: String,
    val customerPhone: String,
    val rows: List<Row>, // oldest pickup first
    val orders: Int,
    val totalLabel: String, // "Total of 3 orders"
    val total: String,
    val paid: String, // "" when nothing was paid
    val due: String,
    val totalAmount: Int,
    val dueAmount: Int, // 0 = fully paid
    val upi: BillReceipt.Upi?, // only while something is due and the UPI id is valid
    val terms: List<String>,
) {
    val fullyPaid: Boolean get() = dueAmount <= 0

    data class Row(
        val title: String, // "16 Sep · #1012"
        val sub: String, // "10 items · Wash & Iron"
        val amount: String,
        val tag: CombinedBill.Tag,
        val lines: List<BillReceipt.Row>, // only with "Show every item"
    )
}
