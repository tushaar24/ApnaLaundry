package com.dailyworks.apnalaundry.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure money maths. Direct port of PRODUCT_SPEC.md §4, §8 and the prototype's
 * bill / khata / allocation functions. No Android or storage dependencies, so
 * every acceptance scenario in §14 can be tested here.
 */
object LaundryMath {

    fun clothesOf(o: Order): Int = o.lines.sumOf { it.amt }

    /** Order total = clothes + express + fee − discount. Never stored separately. */
    fun amtOf(o: Order): Int =
        clothesOf(o) + (if (o.express) o.exAmt else 0) + o.fee - o.discount

    /** Express amount = round(clothes × pct / 100). */
    fun expressAuto(clothesTotal: Int, pct: Int): Int =
        (clothesTotal * pct / 100.0).roundToInt()

    fun serviceName(id: String, services: List<Service>, snapshot: String? = null): String {
        services.firstOrNull { it.id == id }?.let { return it.name }
        if (!snapshot.isNullOrBlank()) return snapshot
        return when (id) {
            "wf" -> "Wash & Fold"; "wi" -> "Wash & Iron"; "io" -> "Iron Only"
            "dc" -> "Dry Cleaning"; "quick" -> "Not itemised"; else -> "Other service"
        }
    }

    /**
     * Khata balance for a customer.
     * balance = Σ bills + Σ old baaki + Σ bill changes − Σ payments.
     * A `pre` payment counts only once its order is delivered.
     */
    fun balance(custId: String, ledger: List<LedgerEntry>, orders: List<Order>): Int {
        var b = 0
        for (e in ledger) {
            if (e.custId != custId) continue
            when (e.kind) {
                LedgerKind.BILL, LedgerKind.OLD, LedgerKind.ADJ -> b += e.amt
                LedgerKind.GOT -> {
                    if (e.tag == PayTag.PRE) {
                        val o = orders.firstOrNull { it.id == e.ref }
                        if (o == null || o.status != OrderStatus.DELIVERED) continue
                    }
                    b -= e.amt
                }
            }
        }
        return b
    }

    data class Allocation(
        val cover: Int,      // paid this bill
        val toOld: Int,      // cleared old baaki
        val toAdv: Int,      // kept as advance
        val newBalance: Int, // customer khata after this
        val billDue: Int,    // bill remaining before this payment
        val paidToward: Int, // order.paid after this
    )

    /**
     * Allocation of `amountReceived` at delivery (PRODUCT_SPEC.md §8).
     *   billDue = total − prepaid
     *   cover   = min(received, billDue)
     *   rest    = received − cover
     *   toOld   = min(rest, oldBaaki if positive)
     *   toAdv   = rest − toOld
     */
    fun deliverAllocation(total: Int, prepaid: Int, oldBalance: Int, amountReceived: Int): Allocation {
        val billDue = max(0, total - prepaid)
        val cover = min(amountReceived, billDue)
        val rest = amountReceived - cover
        val toOld = min(rest, max(0, oldBalance))
        val toAdv = rest - toOld
        val newBalance = oldBalance + total - prepaid - amountReceived
        return Allocation(cover, toOld, toAdv, newBalance, billDue, prepaid + cover)
    }

    /** Allocation when receiving a standalone khata payment (not tied to a delivery). */
    fun receiveAllocation(oldBalance: Int, amountReceived: Int): Pair<Int, Int> {
        val toOld = min(amountReceived, max(0, oldBalance))
        return toOld to (amountReceived - toOld) // toOld, toAdv
    }

    /** Weight line amount = round(max(kg, minKg) × ratePerKg). */
    fun weightAmount(kg: Double, ratePerKg: Int, minKg: Double): Int =
        (max(kg, minKg) * ratePerKg).roundToInt()

    fun paymentLabel(paid: Int, total: Int): String = when {
        paid >= total && total > 0 -> "Paid"
        paid > 0 -> "Part paid"
        else -> "In khata"
    }
}
