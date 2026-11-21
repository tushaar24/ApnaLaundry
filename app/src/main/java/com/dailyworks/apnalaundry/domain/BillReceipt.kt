package com.dailyworks.apnalaundry.domain

import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.ui.Selectors

/**
 * Everything printed on a customer's bill, already formatted — one model
 * drives every design (Classic / Bold / Receipt / Invoice), the shared bill image, the
 * "View bill" sheet and the onboarding preview, so they never drift apart.
 * Port twin: web/src/domain/billReceipt.ts.
 */
data class BillReceipt(
    val template: String,
    val shopName: String,
    val shopPhone: String, // "+91 98765 43210" or ""
    val address: String,
    val gstin: String, // only when valid
    val email: String, // only when valid
    val logoId: String,
    val taxInvoice: Boolean, // "TAX INVOICE" over the bill number: GST on the order + a valid GSTIN
    val billNo: String, // "Bill #1001"
    val date: String, // "Fri, 9 Oct"
    val customerName: String,
    val customerPhone: String,
    val customerAddress: String = "", // only when the shop prints it and the customer has one
    val readyBy: String, // "Ready by Sat, 10 Oct · 6 PM" / "Delivered …" / ""
    val lines: List<Line>,
    val subtotal: String,
    val extras: List<Row>, // express, pickup/delivery, discount, then exclusive GST (taxable, CGST, SGST, round off)
    val total: String,
    val gstNote: String, // inclusive GST: "Price includes GST 18%: …" under the total ("" = none)
    val payStatus: String, // real bills only; "" on the preview. Paid = what's paid toward this bill (before delivery: paid in advance)
    val upi: Upi?, // what's left to pay (with old baaki / advance: [Khata.toPayAmount]); null when nothing is (or no valid UPI id)
    val terms: List<String>,
    // The Invoice design (BillDetails.INVOICE): order details as a table of
    // label → value, money with paise.
    val orderNo: String = "", // "1005" / the owner's serial
    val customerMobile: String = "", // "9455838720" (no +91)
    val createdAt: String = "", // "03/10/2026"
    val dateLabel: String = "", // "Delivered Date" / "Delivery Date" / ""
    val dateValue: String = "", // "04/10/2026"
    val subtotalExact: String = "", // "₹840.00"
    val totalExact: String = "",
    // Real bills of a customer with old baaki or advance in the khata (else null): drawn instead of [payStatus].
    val khata: Khata? = null,
) {
    /**
     * [name] / [qty] / [unit] / [totalExact]: the Invoice design's columns —
     * "Shirt", "3", "₹80.00", "₹240.00" ("" where a lump bill has none).
     */
    data class Line(
        val item: String, val sub: String, val amount: String, val rate: String = "", // rate: "₹20 each" / "₹60/kg" / ""
        val name: String = "", val qty: String = "", val unit: String = "", val totalExact: String = "",
    )
    data class Row(val label: String, val value: String, val discount: Boolean = false, val exact: String = value) // exact: with paise
    data class Upi(val id: String, val payload: String, val amount: String)

    /**
     * Under a real bill, when the customer owes from earlier (old baaki, other
     * orders) or has advance with the shop (owner, 21 Nov 2026: "old baaki &
     * advance payment is not added in the bill"): Total − Paid ± previous =
     * Total to pay. [previous] is the khata without this bill — everything the
     * customer owes now minus what's still due on this bill — so a payment made
     * at this delivery that cleared old baaki is already in it. Port twin: web ReceiptKhata.
     */
    data class Khata(
        val rows: List<Row>, // "Paid" "− ₹100" (when something is paid on this bill), then "Previous due (old baaki)" "+ ₹200" / "Advance with shop" "− ₹500"
        val toPay: Row, // "Total to pay" "₹400" — never below ₹0
        val advanceLeft: String, // "Advance left ₹200" when the advance is more than everything due; ""
        val previous: Int, // + old baaki / − advance
        val toPayAmount: Int,
    )

    companion object {
        /** A real order's bill, with the customer's old baaki / advance from the khata (a cancelled order's bill: without). */
        fun of(state: LaundryState, o: Order): BillReceipt {
            val c = Selectors.customer(state, o.custId)
            val balance = if (o.status == OrderStatus.CANCELLED) null else LaundryMath.balance(o.custId, state.ledger, state.orders)
            return build(state.shop, c.name, c.phone, o, sample = false, expressPct = null, template = null, custAddress = c.address, balance = balance)
        }

        /** The onboarding preview: a sample order built from the owner's rates. */
        fun sample(shop: Shop, order: Order, template: String): BillReceipt =
            build(shop, "Sample customer", "", order, sample = true, expressPct = shop.expressPct, template = template,
                custAddress = BillDetails.SAMPLE_CUSTOMER_ADDRESS)

        /** The address line under the customer: their saved address, only when the shop prints it. */
        fun customerAddress(shop: Shop, address: String): String =
            if (shop.custAddressOnBill) address.replace(Regex("\\s+"), " ").trim() else ""

        /** The price per item printed under it: "₹20 each", "₹60/kg"; "" for a lump (not itemised) bill. */
        fun rate(l: OrderLine): String = when {
            l.isQuick || l.price <= 0 -> ""
            l.kg > 0 -> "${Money.rupees(l.price)}/kg"
            else -> "${Money.rupees(l.price)} each"
        }

        /** ₹ with paise: "₹840.00". */
        private fun exact(rupees: Int): String = Money.paise(rupees * 100L)

        private fun line(l: OrderLine): Line = when {
            l.kg > 0 -> Line(
                Selectors.weightLabel(l), Selectors.clothesList(l), Money.rupees(l.amt), rate(l),
                name = l.serviceName, qty = "${Selectors.trimKg(l.kg)} kg", unit = if (l.price > 0) exact(l.price) + "/kg" else "", totalExact = exact(l.amt),
            )
            l.isQuick -> Line(
                if (l.qty > 0) "${l.itemName} × ${l.qty}" else l.itemName, "", Money.rupees(l.amt),
                name = l.itemName, qty = if (l.qty > 0) l.qty.toString() else "", totalExact = exact(l.amt),
            )
            else -> Line(
                "${l.itemName} × ${l.qty}", l.serviceName, Money.rupees(l.amt), rate(l),
                name = l.itemName, qty = l.qty.toString(), unit = exact(l.price), totalExact = exact(l.amt),
            )
        }

        private fun readyBy(o: Order): String = when {
            o.status == OrderStatus.DELIVERED && o.doneDate.isNotEmpty() -> "Delivered ${AppDate.plain(o.doneDate)}"
            o.deliveryDate.isEmpty() -> ""
            else -> "Ready by ${AppDate.plain(o.deliveryDate)}" + if (o.deliveryTime.isNotEmpty()) " · ${o.deliveryTime}" else ""
        }

        /** Paid toward [o]'s bill: once delivered what was collected for it, before that what was paid in advance. */
        fun paidOn(o: Order, total: Int): Int =
            (if (o.status == OrderStatus.DELIVERED) o.paid else o.pre).coerceIn(0, maxOf(0, total))

        /** The old baaki / advance lines, for [previous] ≠ 0 (+ owes from before, − advance). */
        private fun khata(total: Int, paid: Int, previous: Int): Khata {
            val sum = total - paid + previous
            val toPay = maxOf(0, sum)
            val rows = buildList {
                if (paid > 0) add(Row("Paid", "− ${Money.rupees(paid)}", discount = true, exact = "− ${exact(paid)}"))
                if (previous > 0) add(Row("Previous due (old baaki)", "+ ${Money.rupees(previous)}", exact = "+ ${exact(previous)}"))
                else add(Row("Advance with shop", "− ${Money.rupees(previous)}", discount = true, exact = "− ${exact(-previous)}"))
            }
            return Khata(
                rows, Row("Total to pay", Money.rupees(toPay), exact = exact(toPay)),
                if (sum < 0) "Advance left ${Money.rupees(sum)}" else "", previous, toPay,
            )
        }

        private fun payStatus(paid: Int, total: Int): String = when {
            paid <= 0 -> "To pay ${Money.rupees(total)}"
            paid >= total -> "Paid in full"
            else -> "Paid ${Money.rupees(paid)} · to pay ${Money.rupees(total - paid)}"
        }

        private fun build(
            shop: Shop, custName: String, custPhone: String, o: Order,
            sample: Boolean, expressPct: Int?, template: String?, custAddress: String = "",
            balance: Int? = null, // the customer's whole khata now (+ owes / − advance); null = no old baaki / advance lines
        ): BillReceipt {
            val gst = LaundryMath.gstOf(o)
            val total = gst.total
            val paid = if (sample) 0 else paidOn(o, total)
            val previous = if (balance != null) balance - (total - paid) else 0
            val khata = if (!sample && previous != 0) khata(total, paid, previous) else null
            // The pay QR asks for what's left: the whole total on the preview.
            val toPay = when {
                sample -> total
                khata != null -> khata.toPayAmount
                else -> total - paid
            }
            val phone = custPhone.filter { it.isDigit() }.takeLast(10)
            val extras = buildList {
                if (o.express && o.exAmt > 0) {
                    add(Row(if (expressPct != null) "Express (urgent, +$expressPct%)" else "Express (urgent)", "+ ${Money.rupees(o.exAmt)}", exact = "+ ${exact(o.exAmt)}"))
                }
                if (o.fee > 0) add(Row("Pickup / delivery", "+ ${Money.rupees(o.fee)}", exact = "+ ${exact(o.fee)}"))
                if (o.discount > 0) add(Row("Discount", "− ${Money.rupees(o.discount)}", discount = true, exact = "− ${exact(o.discount)}"))
                if (gst.on && gst.mode == Gst.EXCL) {
                    add(Row("Taxable value", Money.paise(gst.taxablePaise)))
                    add(Row("CGST @${Gst.halfRate(gst.pct)}%", "+ ${Money.paise(gst.cgstPaise)}"))
                    add(Row("SGST @${Gst.halfRate(gst.pct)}%", "+ ${Money.paise(gst.sgstPaise)}"))
                    if (gst.roundOffPaise != 0L) add(Row("Round off", Gst.roundOffValue(gst)))
                }
            }
            val gstin = if (BillDetails.isGstinValid(shop.gstin)) shop.gstin else ""
            val billPhone = shop.billPhone.ifBlank { shop.phone }
            return BillReceipt(
                template = BillDetails.templateOrDefault(template ?: shop.billTemplate),
                shopName = shop.name,
                shopPhone = BillDetails.fmtBillPhone(billPhone),
                address = shop.address,
                gstin = gstin,
                email = if (BillDetails.isEmailValid(shop.email)) shop.email else "",
                logoId = shop.logoId,
                taxInvoice = gst.on && gstin.isNotEmpty(),
                billNo = "Bill #${o.no()}",
                date = AppDate.plain(o.createdOn),
                customerName = custName,
                customerPhone = if (phone.isNotEmpty()) "+91 ${Selectors.fmtPhone(phone)}" else "",
                customerAddress = customerAddress(shop, custAddress),
                readyBy = readyBy(o),
                lines = o.lines.map(::line),
                subtotal = Money.rupees(LaundryMath.clothesOf(o)),
                extras = extras,
                total = Money.rupees(total),
                gstNote = if (gst.on && gst.mode == Gst.INCL) Gst.billNote(gst) else "",
                payStatus = if (sample) "" else payStatus(paid, total),
                upi = if (toPay > 0 && BillDetails.isUpiValid(shop.upiId)) {
                    Upi(shop.upiId, BillDetails.upiPayload(shop.upiId, shop.name, toPay, o.id), Money.rupees(toPay))
                } else null,
                terms = BillDetails.termLines(shop.terms, shop.termsCustom),
                orderNo = o.no(),
                customerMobile = phone,
                createdAt = AppDate.dmy(o.createdOn),
                dateLabel = when {
                    o.status == OrderStatus.DELIVERED && o.doneDate.isNotEmpty() -> "Delivered Date"
                    o.deliveryDate.isNotEmpty() -> "Delivery Date"
                    else -> ""
                },
                dateValue = when {
                    o.status == OrderStatus.DELIVERED && o.doneDate.isNotEmpty() -> AppDate.dmy(o.doneDate)
                    else -> AppDate.dmy(o.deliveryDate)
                },
                subtotalExact = exact(LaundryMath.clothesOf(o)),
                totalExact = exact(total),
                khata = khata,
            )
        }
    }
}
