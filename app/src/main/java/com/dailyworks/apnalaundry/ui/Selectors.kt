package com.dailyworks.apnalaundry.ui

import com.dailyworks.apnalaundry.domain.Customer
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.Order

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
        var pieces = 0; var kg = 0.0
        o.lines.forEach { if (it.kg > 0) kg += it.kg else pieces += it.qty }
        val parts = mutableListOf<String>()
        if (pieces > 0) parts += "$pieces ${if (pieces == 1) "item" else "items"}"
        if (kg > 0) parts += "${trimKg(kg)} kg"
        return parts.joinToString(" + ")
    }

    fun svcLabel(o: Order): String =
        o.lines.map { it.serviceName }.distinct().joinToString(" + ")

    fun trimKg(kg: Double): String = if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()

    /** Orders for a customer that are not yet in khata (in progress). */
    fun orderCount(state: LaundryState, c: Customer): Int =
        c.pastOrders + state.orders.count { it.custId == c.id && it.status != com.dailyworks.apnalaundry.domain.OrderStatus.CANCELLED }
}
