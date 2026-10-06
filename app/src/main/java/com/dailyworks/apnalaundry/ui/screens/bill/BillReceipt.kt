package com.dailyworks.apnalaundry.ui.screens.bill

import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.domain.OrderLine
import com.dailyworks.apnalaundry.domain.OrderStatus
import com.dailyworks.apnalaundry.ui.Selectors

/**
 * Everything printed on the customer's bill receipt, already formatted —
 * one model drives the PDF and the "View bill" preview so they can never
 * drift apart. Port twin of web domain/billReceipt.ts.
 */
data class ReceiptLine(val item: String, val sub: String, val qty: String, val rate: String, val total: String)

data class ReceiptRow(val label: String, val value: String)

data class BillReceipt(
    val shopName: String,
    val shopPhone: String,        // "+91 98765 43210", or "" if the shop has no phone
    val info: List<ReceiptRow>,   // Order / Customer / Mobile / Created At / Delivery Date
    val lines: List<ReceiptLine>,
    val subtotal: String,
    val extras: List<ReceiptRow>, // express, pickup/delivery, discount
    val total: String,
)

/** ₹1,250.00 — receipts show paise like a printed bill. */
fun receiptMoney(v: Int): String = "${Money.rupees(v)}.00"

/** "2026-10-04" → "04/10/2026". */
fun receiptDate(iso: String): String {
    val p = iso.split("-")
    return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else iso
}

private fun line(l: OrderLine): ReceiptLine = when {
    l.kg > 0 -> ReceiptLine(
        item = l.serviceName,
        sub = "By weight",
        qty = "${Selectors.trimKg(l.kg)} kg",
        rate = if (l.price > 0) "${receiptMoney(l.price)}/kg" else "—",
        total = receiptMoney(l.amt),
    )
    l.isQuick -> ReceiptLine(l.itemName, "", if (l.qty > 0) l.qty.toString() else "—", "—", receiptMoney(l.amt))
    else -> ReceiptLine(l.itemName, l.serviceName, l.qty.toString(), receiptMoney(l.price), receiptMoney(l.amt))
}

fun billReceipt(state: LaundryState, o: Order): BillReceipt {
    val c = Selectors.customer(state, o.custId)
    val phone = c.phone.filter { it.isDigit() }.takeLast(10)

    val info = buildList {
        add(ReceiptRow("Order", o.id.toString().padStart(6, '0')))
        add(ReceiptRow("Customer", c.name.uppercase()))
        if (phone.isNotEmpty()) add(ReceiptRow("Mobile", phone))
        add(ReceiptRow("Created At", receiptDate(o.createdOn)))
        if (o.status == OrderStatus.DELIVERED && o.doneDate.isNotBlank()) add(ReceiptRow("Delivered Date", receiptDate(o.doneDate)))
        else if (o.deliveryDate.isNotBlank()) add(ReceiptRow("Delivery Date", receiptDate(o.deliveryDate)))
    }
    val extras = buildList {
        if (o.express && o.exAmt > 0) add(ReceiptRow("Express", "+ ${receiptMoney(o.exAmt)}"))
        if (o.fee > 0) add(ReceiptRow("Pickup / delivery", "+ ${receiptMoney(o.fee)}"))
        if (o.discount > 0) add(ReceiptRow("Discount", "− ${receiptMoney(o.discount)}"))
    }
    return BillReceipt(
        shopName = state.shop.name,
        shopPhone = if (state.shop.phone.isNotBlank()) "+91 ${Selectors.fmtPhone(state.shop.phone)}" else "",
        info = info,
        lines = o.lines.map(::line),
        subtotal = receiptMoney(LaundryMath.clothesOf(o)),
        extras = extras,
        total = receiptMoney(LaundryMath.amtOf(o)),
    )
}
