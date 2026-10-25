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
    val readyBy: String, // "Ready by Sat, 10 Oct · 6 PM" / "Delivered …" / ""
    val lines: List<Line>,
    val subtotal: String,
    val extras: List<Row>, // express, pickup/delivery, discount, then exclusive GST (taxable, CGST, SGST, round off)
    val total: String,
    val gstNote: String, // inclusive GST: "Price includes GST 18%: …" under the total ("" = none)
    val payStatus: String, // real bills only; "" on the preview
    val upi: Upi?,
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

    companion object {
        /** A real order's bill. */
        fun of(state: LaundryState, o: Order): BillReceipt {
            val c = Selectors.customer(state, o.custId)
            return build(state.shop, c.name, c.phone, o, sample = false, expressPct = null, template = null)
        }

        /** The onboarding preview: a sample order built from the owner's rates. */
        fun sample(shop: Shop, order: Order, template: String): BillReceipt =
            build(shop, "Sample customer", "", order, sample = true, expressPct = shop.expressPct, template = template)

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

        private fun payStatus(paid: Int, total: Int): String = when {
            paid <= 0 -> "To pay ${Money.rupees(total)}"
            paid >= total -> "Paid in full"
            else -> "Paid ${Money.rupees(paid)} · to pay ${Money.rupees(total - paid)}"
        }

        private fun build(
            shop: Shop, custName: String, custPhone: String, o: Order,
            sample: Boolean, expressPct: Int?, template: String?,
        ): BillReceipt {
            val gst = LaundryMath.gstOf(o)
            val total = gst.total
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
                readyBy = readyBy(o),
                lines = o.lines.map(::line),
                subtotal = Money.rupees(LaundryMath.clothesOf(o)),
                extras = extras,
                total = Money.rupees(total),
                gstNote = if (gst.on && gst.mode == Gst.INCL) Gst.billNote(gst) else "",
                payStatus = if (sample) "" else payStatus(o.paid, total),
                upi = if (BillDetails.isUpiValid(shop.upiId)) {
                    Upi(shop.upiId, BillDetails.upiPayload(shop.upiId, shop.name, total, o.id), Money.rupees(total))
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
            )
        }
    }
}
