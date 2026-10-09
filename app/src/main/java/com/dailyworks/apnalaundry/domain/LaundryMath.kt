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

    /** The amount GST applies to: clothes + express + fee − discount. */
    fun baseOf(o: Order): Int = clothesOf(o) + (if (o.express) o.exAmt else 0) + o.fee - o.discount

    /** The order's GST breakdown (none for orders from before GST, or with GST off). */
    fun gstOf(o: Order): Gst.Breakdown = Gst.calc(baseOf(o), o.gstOn, o.gstPct, o.gstMode)

    /**
     * Order total = what the customer pays: clothes + express + fee − discount,
     * plus exclusive GST rounded to the rupee (inclusive GST is already inside).
     * Never stored separately — bills, khata, payments and earnings all call this.
     */
    fun amtOf(o: Order): Int = gstOf(o).total

    /** Express amount = round(clothes × pct / 100). */
    fun expressAuto(clothesTotal: Int, pct: Int): Int =
        (clothesTotal * pct / 100.0).roundToInt()

    /**
     * Re-derives a %-based express / discount from the clothes (a fixed ₹
     * amount stays as is). Called whenever an order's clothes change — on a
     * pickup order the % is set before the clothes are counted.
     * [legacyExpressPct] covers older express orders that never stored a %
     * and have no amount yet. Port twin: web withPctExtras.
     */
    fun withPctExtras(o: Order, legacyExpressPct: Int): Order {
        val clothes = clothesOf(o)
        val exAmt = when {
            !o.express -> 0
            o.exPct > 0 -> expressAuto(clothes, o.exPct)
            o.exAmt == 0 -> expressAuto(clothes, legacyExpressPct)
            else -> o.exAmt
        }
        val discount = if (o.discPct > 0) (clothes * o.discPct / 100.0).roundToInt() else o.discount
        return o.copy(exAmt = exAmt, discount = discount)
    }

    fun serviceName(id: String, services: List<Service>, snapshot: String? = null): String {
        services.firstOrNull { it.id == id }?.let { return it.name }
        if (!snapshot.isNullOrBlank()) return snapshot
        return when (id) {
            "wf" -> "Wash & Fold"; "wi" -> "Wash & Iron"; "io" -> "Iron Only"
            "dc" -> "Dry Cleaning"; "quick" -> "Not itemised"; else -> "Other service"
        }
    }

    /** Not yet delivered or cancelled — its total is in the baaki but has no `BILL` entry yet. */
    fun isOpen(o: Order): Boolean = o.status != OrderStatus.DELIVERED && o.status != OrderStatus.CANCELLED

    /**
     * Khata balance for a customer.
     * balance = Σ bills + Σ old baaki + Σ bill changes + Σ open orders − Σ payments.
     * An open order counts at its current total from the moment it is created; on
     * delivery it is replaced by its `BILL` entry. `exceptOrder` leaves one open
     * order and its prepayments out (the "old" side of a delivery).
     */
    fun balance(custId: String, ledger: List<LedgerEntry>, orders: List<Order>, exceptOrder: Int? = null): Int {
        var b = 0
        for (e in ledger) {
            if (e.custId != custId) continue
            when (e.kind) {
                LedgerKind.BILL, LedgerKind.OLD, LedgerKind.ADJ -> b += e.amt
                LedgerKind.GOT -> {
                    if (e.tag == PayTag.PRE && exceptOrder != null && e.ref == exceptOrder) continue
                    b -= e.amt
                }
            }
        }
        for (o in orders) {
            if (o.custId == custId && o.id != exceptOrder && isOpen(o)) b += amtOf(o)
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
