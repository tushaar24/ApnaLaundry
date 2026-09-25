package com.dailyworks.apnalaundry.domain

import kotlin.math.max

/**
 * Earnings, computed live from the ledger and orders (PRODUCT_SPEC.md §9).
 * Because everything is derived, money always reconciles:
 *   received = work − baakiAdded + oldIn + advIn + prepaid
 *   received = cash + upi
 */
object EarningsMath {

    data class ServiceShare(val label: String, val amount: Int, val isExtra: Boolean)

    data class Earnings(
        val received: Int,
        val cash: Int,
        val upi: Int,
        val work: Int,
        val baakiAdded: Int,
        val oldIn: Int,
        val advIn: Int,
        val prepaid: Int,
        val ordersDelivered: Int,
        val pieces: Int,
        val kg: Double,
        val byService: List<ServiceShare>,
        val discounts: Int,
        val baakiMarket: Int,
        val baakiCustomers: Int,
    )

    fun compute(
        state: LaundryState,
        inRange: (String) -> Boolean,
    ): Earnings {
        val gots = state.ledger.filter { it.kind == LedgerKind.GOT && inRange(it.date) }
        var received = 0; var cash = 0; var oldIn = 0; var advIn = 0
        for (e in gots) {
            received += e.amt
            if (e.method == PayMethod.CASH) cash += e.amt
            oldIn += e.toOld
            advIn += e.toAdv
        }
        val delivered = state.orders.filter {
            it.status == OrderStatus.DELIVERED && inRange(it.doneDate)
        }
        var work = 0; var baakiAdded = 0; var pieces = 0; var kg = 0.0; var discounts = 0
        val svc = linkedMapOf<String, Int>()
        var express = 0; var fee = 0
        for (o in delivered) {
            val a = LaundryMath.amtOf(o)
            work += a
            baakiAdded += max(0, a - o.paid)
            for (l in o.lines) {
                svc[l.serviceId] = (svc[l.serviceId] ?: 0) + l.amt
                if (l.kg > 0) kg += l.kg else pieces += l.qty
            }
            fee += o.fee
            express += if (o.express) o.exAmt else 0
            discounts += o.discount
        }
        val prepaid = received - (work - baakiAdded + oldIn + advIn)

        val shares = mutableListOf<ServiceShare>()
        svc.filterValues { it > 0 }.forEach { (id, amt) ->
            shares += ServiceShare(LaundryMath.serviceName(id, state.services), amt, isExtra = false)
        }
        if (express > 0) shares += ServiceShare("Express charges", express, isExtra = true)
        if (fee > 0) shares += ServiceShare("Pickup / delivery charges", fee, isExtra = true)
        shares.sortWith(compareBy({ it.isExtra }, { -it.amount }))

        val baakiCust = state.customers.filter {
            LaundryMath.balance(it.id, state.ledger, state.orders) > 0
        }
        val baakiMarket = baakiCust.sumOf { LaundryMath.balance(it.id, state.ledger, state.orders) }

        return Earnings(
            received = received, cash = cash, upi = received - cash,
            work = work, baakiAdded = baakiAdded, oldIn = oldIn, advIn = advIn, prepaid = prepaid,
            ordersDelivered = delivered.size, pieces = pieces, kg = kg,
            byService = shares, discounts = discounts,
            baakiMarket = baakiMarket, baakiCustomers = baakiCust.size,
        )
    }

    /** Money received on a single day (any source) — used by the Home dashboard strip. */
    fun collectedOn(iso: String, ledger: List<LedgerEntry>): Int =
        ledger.filter { it.kind == LedgerKind.GOT && it.date == iso }.sumOf { it.amt }
}
