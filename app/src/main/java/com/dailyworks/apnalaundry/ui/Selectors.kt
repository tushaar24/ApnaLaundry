package com.dailyworks.apnalaundry.ui

import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.domain.Customer
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.domain.OrderLine

/** Small derived-data helpers shared by screens (ported from the prototype). */
object Selectors {
    private val unknownCustomer = Customer("", "—", "0000000000", "", 0, "", 9)

    fun customer(state: LaundryState, id: String): Customer =
        state.customers.firstOrNull { it.id == id } ?: unknownCustomer

    fun order(state: LaundryState, id: Int): Order? = state.orders.firstOrNull { it.id == id }

    fun balance(state: LaundryState, custId: String, exceptOrder: Int? = null): Int =
        LaundryMath.balance(custId, state.ledger, state.orders, exceptOrder)

    fun firstName(name: String): String =
        if (name.startsWith("+")) name else name.substringBefore(" ")

    fun fmtPhone(d: String): String = if (d.length == 10) d.substring(0, 5) + " " + d.substring(5) else d

    fun initials(name: String): String =
        name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

    /** "3 items", "2 kg", "3 items + 2 kg", "Not itemised". */
    fun itemsLabel(o: Order): String {
        if (o.lines.isEmpty()) return if (o.pieces > 0) tr("{n} pieces", "n" to o.pieces) else ""
        if (o.lines.all { it.isQuick && it.qty == 0 }) return tr("Not itemised")
        var pieces = 0; var kg = 0.0; var kgClothes = 0 // optional count of clothes in the by-weight bags
        o.lines.forEach { if (it.kg > 0) { kg += it.kg; kgClothes += it.qty } else pieces += it.qty }
        val parts = mutableListOf<String>()
        if (pieces > 0) parts += if (pieces == 1) tr("{n} item", "n" to pieces) else tr("{n} items", "n" to pieces)
        if (kg > 0) parts += if (kgClothes > 0) tr("{kg} kg ({n} clothes)", "kg" to trimKg(kg), "n" to kgClothes) else tr("{kg} kg", "kg" to trimKg(kg))
        return parts.joinToString(" + ")
    }

    fun svcLabel(o: Order): String =
        o.lines.map { it.serviceName }.distinct().joinToString(" + ")

    /** "Wash & Fold · 4 kg" or, with the clothes counted, "Wash & Fold · 4 kg · 12 clothes". */
    fun weightLabel(l: OrderLine): String =
        l.serviceName + " · " + tr("{kg} kg", "kg" to trimKg(l.kg)) +
            if (l.qty > 0) " · " + (if (l.qty == 1) tr("{n} cloth", "n" to l.qty) else tr("{n} clothes", "n" to l.qty)) else ""

    /** A by-weight bag's clothes list: "Shirt × 3 · Pant × 2"; "" when not listed. */
    fun clothesList(l: OrderLine): String =
        l.clothes.filter { it.qty > 0 }.joinToString(" · ") { "${it.name} × ${it.qty}" }

    /**
     * The serial a new order starts with: the latest order's number (its
     * serial, else its id) + 1, keeping any prefix and zero-padding ("A-102" →
     * "A-103", "0099" → "0100"). The first order — or a last number with no
     * digits to bump — starts at the order number it will get.
     * Port twin: web Sel.nextSerial.
     */
    fun nextSerial(state: LaundryState): String {
        val first = state.shop.nextOrder.toString()
        val last = state.orders.maxByOrNull { it.id } ?: return first
        val m = Regex("^(.*?)(\\d+)$").find(last.no()) ?: return first
        val (prefix, digits) = m.destructured
        val next = (digits.toBigInteger() + java.math.BigInteger.ONE).toString().padStart(digits.length, '0')
        return (prefix + next).take(12)
    }

    /**
     * "1 order" / "3 orders" — counted label with the right plural, in the
     * app's language. Each noun callers pass has its own pair of texts (a
     * translation can't just add an "s"); an unknown noun stays English.
     */
    fun countNoun(n: Int, noun: String): String = when (noun) {
        "order" -> if (n == 1) tr("{n} order", "n" to n) else tr("{n} orders", "n" to n)
        "customer" -> if (n == 1) tr("{n} customer", "n" to n) else tr("{n} customers", "n" to n)
        "item" -> if (n == 1) tr("{n} item", "n" to n) else tr("{n} items", "n" to n)
        "bill" -> if (n == 1) tr("{n} bill", "n" to n) else tr("{n} bills", "n" to n)
        else -> "$n $noun${if (n == 1) "" else "s"}"
    }

    /** "4", "4.5", "7.3" — sums of kg are doubles (3.1 + 4.2 = 7.300000000000001), so round to 2 places. */
    fun trimKg(kg: Double): String =
        java.math.BigDecimal.valueOf(kg).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** Orders for a customer that are not yet in khata (in progress). */
    fun orderCount(state: LaundryState, c: Customer): Int =
        c.pastOrders + state.orders.count { it.custId == c.id && it.status != com.dailyworks.apnalaundry.domain.OrderStatus.CANCELLED }
}
