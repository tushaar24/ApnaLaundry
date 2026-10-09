package com.dailyworks.apnalaundry.ui

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
        if (o.lines.isEmpty()) return if (o.pieces > 0) "${o.pieces} pieces" else ""
        if (o.lines.all { it.isQuick && it.qty == 0 }) return "Not itemised"
        var pieces = 0; var kg = 0.0; var kgClothes = 0 // optional count of clothes in the by-weight bags
        o.lines.forEach { if (it.kg > 0) { kg += it.kg; kgClothes += it.qty } else pieces += it.qty }
        val parts = mutableListOf<String>()
        if (pieces > 0) parts += "$pieces ${if (pieces == 1) "item" else "items"}"
        if (kg > 0) parts += "${trimKg(kg)} kg" + if (kgClothes > 0) " ($kgClothes clothes)" else ""
        return parts.joinToString(" + ")
    }

    fun svcLabel(o: Order): String =
        o.lines.map { it.serviceName }.distinct().joinToString(" + ")

    /** "Wash & Fold · 4 kg" or, with the clothes counted, "Wash & Fold · 4 kg · 12 clothes". */
    fun weightLabel(l: OrderLine): String =
        "${l.serviceName} · ${trimKg(l.kg)} kg" + if (l.qty > 0) " · ${l.qty} ${if (l.qty == 1) "cloth" else "clothes"}" else ""

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

    /** "1 order" / "3 orders" — counted label with the right plural. */
    fun countNoun(n: Int, noun: String): String = "$n $noun${if (n == 1) "" else "s"}"

    fun trimKg(kg: Double): String = if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()

    /** Orders for a customer that are not yet in khata (in progress). */
    fun orderCount(state: LaundryState, c: Customer): Int =
        c.pastOrders + state.orders.count { it.custId == c.id && it.status != com.dailyworks.apnalaundry.domain.OrderStatus.CANCELLED }
}
