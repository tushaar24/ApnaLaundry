package com.dailyworks.apnalaundry.domain

import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.ui.Selectors

/**
 * Everything printed on a customer's bill, already formatted — one model
 * drives every design (Classic / Bold / Receipt), the shared bill image, the
 * "View bill" sheet and the onboarding preview, so they never drift apart.
 * Port twin: web/src/domain/billReceipt.ts.
 */
data class BillReceipt(
    val template: String,
    val shopName: String,
    val shopPhone: String, // "+91 98765 43210" or ""
    val address: String,
    val gstin: String, // only when valid
    val logoId: String,
    val billNo: String, // "Bill #1001"
    val date: String, // "Fri, 9 Oct"
    val customerName: String,
    val customerPhone: String,
    val readyBy: String, // "Ready by Sat, 10 Oct · 6 PM" / "Delivered …" / ""
    val lines: List<Line>,
    val subtotal: String,
    val extras: List<Row>,
    val total: String,
    val payStatus: String, // real bills only; "" on the preview
    val upi: Upi?,
    val terms: List<String>,
) {
    data class Line(val item: String, val sub: String, val amount: String)
    data class Row(val label: String, val value: String, val discount: Boolean = false)
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

        private fun line(l: OrderLine): Line = when {
            l.kg > 0 -> Line(Selectors.weightLabel(l), "", Money.rupees(l.amt))
            l.isQuick -> Line(if (l.qty > 0) "${l.itemName} × ${l.qty}" else l.itemName, "", Money.rupees(l.amt))
            else -> Line("${l.itemName} × ${l.qty}", l.serviceName, Money.rupees(l.amt))
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
            val total = LaundryMath.amtOf(o)
            val phone = custPhone.filter { it.isDigit() }.takeLast(10)
            val extras = buildList {
                if (o.express && o.exAmt > 0) {
                    add(Row(if (expressPct != null) "Express (urgent, +$expressPct%)" else "Express (urgent)", "+ ${Money.rupees(o.exAmt)}"))
                }
                if (o.fee > 0) add(Row("Pickup / delivery", "+ ${Money.rupees(o.fee)}"))
                if (o.discount > 0) add(Row("Discount", "− ${Money.rupees(o.discount)}", discount = true))
            }
            val billPhone = shop.billPhone.ifBlank { shop.phone }
            return BillReceipt(
                template = BillDetails.templateOrDefault(template ?: shop.billTemplate),
                shopName = shop.name,
                shopPhone = BillDetails.fmtBillPhone(billPhone),
                address = shop.address,
                gstin = if (BillDetails.isGstinValid(shop.gstin)) shop.gstin else "",
                logoId = shop.logoId,
                billNo = "Bill #${o.id}",
                date = AppDate.plain(o.createdOn),
                customerName = custName,
                customerPhone = if (phone.isNotEmpty()) "+91 ${Selectors.fmtPhone(phone)}" else "",
                readyBy = readyBy(o),
                lines = o.lines.map(::line),
                subtotal = Money.rupees(LaundryMath.clothesOf(o)),
                extras = extras,
                total = Money.rupees(total),
                payStatus = if (sample) "" else payStatus(o.paid, total),
                upi = if (BillDetails.isUpiValid(shop.upiId)) {
                    Upi(shop.upiId, BillDetails.upiPayload(shop.upiId, shop.name, total, o.id), Money.rupees(total))
                } else null,
                terms = BillDetails.termLines(shop.terms, shop.termsCustom),
            )
        }
    }
}
