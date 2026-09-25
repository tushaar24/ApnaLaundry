package com.dailyworks.apnalaundry

import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRODUCT_SPEC.md §14 acceptance scenarios, run against the pure domain maths.
 * These guarantee money always reconciles.
 */
class AcceptanceTest {

    private val services = SeedData.services

    // 1. Weight minimum: Wash & Fold 2 kg → ₹180 (charged as 3 kg).
    @Test fun weightMinimum() {
        assertEquals(180, LaundryMath.weightAmount(kg = 2.0, ratePerKg = 60, minKg = 3.0))
    }

    // 2. Mixed order with extras.
    @Test fun mixedOrderWithExtras() {
        val clothes = 1 * 25 + LaundryMath.weightAmount(2.0, 60, 3.0) // 25 + 180 = 205
        assertEquals(205, clothes)
        val express = LaundryMath.expressAuto(clothes, 50)             // round(102.5) = 103
        assertEquals(103, express)
        val order = order(
            lines = listOf(
                line("wi", "Shirt", 1, 25),
                weightLine("wf", 2.0, 60, 3.0),
            ),
            express = true, exAmt = express, fee = 30, discount = 0,
        )
        assertEquals(338, LaundryMath.amtOf(order))                    // 205 + 103 + 30
        assertEquals(323, LaundryMath.amtOf(order.copy(discount = 15)))
    }

    // 3. Part payment with old baaki.
    @Test fun partPaymentWithOldBaaki() {
        val a = LaundryMath.deliverAllocation(total = 520, prepaid = 0, oldBalance = 200, amountReceived = 500)
        assertEquals(500, a.cover)
        assertEquals(0, a.toOld)
        assertEquals(0, a.toAdv)
        assertEquals(220, a.newBalance)          // 220 baaki
        assertEquals(500, a.paidToward)
        assertEquals("Part paid", LaundryMath.paymentLabel(a.paidToward, 520))
    }

    // 4. Overpayment → advance.
    @Test fun overpayment() {
        val a = LaundryMath.deliverAllocation(total = 180, prepaid = 0, oldBalance = 0, amountReceived = 200)
        assertEquals(180, a.cover)
        assertEquals(20, a.toAdv)
        assertEquals(-20, a.newBalance)          // ₹20 advance
    }

    // 5. Advance used: ₹50 advance + ₹320 bill → balance ₹270.
    @Test fun advanceUsed() {
        val ledger = listOf(
            got("cx", 50, PayTag.RECEIVE, toAdv = 50),        // gives ₹50 advance
            bill("cx", 320, ref = 5000),
        )
        assertEquals(270, LaundryMath.balance("cx", ledger, emptyList()))
    }

    // 6. Prepaid order: khata unaffected until delivery.
    @Test fun prepaidOrderKhataUnaffectedUntilDelivery() {
        val inProgress = order(id = 42, status = OrderStatus.RECEIVED, lines = listOf(line("io", "Shirt", 6, 10)))
        val ledger = listOf(got("cy", 60, PayTag.PRE, cover = 60, ref = 42))
        assertEquals(0, LaundryMath.balance("cy", ledger, listOf(inProgress)))
        val delivered = inProgress.copy(status = OrderStatus.DELIVERED)
        // once delivered, the ₹60 pre payment counts (nothing else in ledger) → −60 advance
        assertEquals(-60, LaundryMath.balance("cy", ledger, listOf(delivered)))
    }

    // 7. Edit after delivery: ₹240 → ₹300 adds a +₹60 adjustment.
    @Test fun editAfterDelivery() {
        val before = 240; val after = 300
        val diff = after - before
        assertEquals(60, diff)
        val ledger = listOf(
            bill("cz", 240, ref = 30), got("cz", 240, PayTag.DELIVER, cover = 240, ref = 30),
            adj("cz", diff, ref = 30),
        )
        assertEquals(60, LaundryMath.balance("cz", ledger, emptyList()))
    }

    // 8. Earnings reconciliation on the seed day (must hold exactly).
    @Test fun earningsReconciliationSeedDay() {
        val state = SeedData.state()
        val today = AppDate.TODAY
        val e = EarningsMath.compute(state) { it == today }
        assertEquals(2345, e.received)
        assertEquals(1560, e.cash)
        assertEquals(785, e.upi)
        assertEquals(1315, e.work)
        assertEquals(180, e.baakiAdded)
        assertEquals(500, e.oldIn)
        assertEquals(100, e.advIn)
        assertEquals(610, e.prepaid)
        // received = work − baakiAdded + oldIn + advIn + prepaid
        assertEquals(e.received, e.work - e.baakiAdded + e.oldIn + e.advIn + e.prepaid)
        // received = cash + upi
        assertEquals(e.received, e.cash + e.upi)
        assertEquals(4, e.ordersDelivered)
    }

    // 9. Automatic delivery date follows the longest ready time and moves with the pickup.
    @Test fun automaticDeliveryDate() {
        val pickup = "2026-09-25"
        val readyIn = 2
        assertEquals("2026-09-27", AppDate.add(pickup, readyIn))       // Fri → Sun
        val newPickup = "2026-09-26"
        val shift = AppDate.daysBetween(pickup, newPickup)             // +1
        assertEquals("2026-09-28", AppDate.add("2026-09-27", shift))   // delivery moves to Mon
    }

    // ---- builders ----
    private fun line(svc: String, name: String, qty: Int, price: Int) =
        OrderLine(svc, svcName(svc), name, qty, price, price, 0.0, qty * price)

    private fun weightLine(svc: String, kg: Double, rate: Int, min: Double) =
        OrderLine(svc, svcName(svc), "By weight", 0, rate, rate, kg, LaundryMath.weightAmount(kg, rate, min))

    private fun svcName(id: String) = services.firstOrNull { it.id == id }?.name ?: id

    private fun order(
        id: Int = 1, status: OrderStatus = OrderStatus.RECEIVED, lines: List<OrderLine> = emptyList(),
        express: Boolean = false, exAmt: Int = 0, fee: Int = 0, discount: Int = 0,
    ) = Order(
        id = id, custId = "c", pickup = Route.SHOP, delivery = Route.SHOP,
        pickupDate = AppDate.TODAY, pickupTime = "", deliveryDate = "", deliveryTime = "",
        ddAuto = false, status = status, cancelReason = "", fee = fee, express = express,
        exAmt = exAmt, discount = discount, pre = 0, paid = 0, doneAt = "", doneDate = "",
        createdOn = AppDate.TODAY, billSent = false, pieces = 0, lines = lines,
    )

    private var t = 0L
    private fun entry(cust: String, kind: LedgerKind, amt: Int, method: PayMethod, tag: PayTag, cover: Int, toOld: Int, toAdv: Int, ref: Int?) =
        LedgerEntry("t${t++}", cust, AppDate.TODAY, "", t, kind, amt, method, tag, cover, toOld, toAdv, ref, "")

    private fun bill(c: String, amt: Int, ref: Int) = entry(c, LedgerKind.BILL, amt, PayMethod.NONE, PayTag.NONE, 0, 0, 0, ref)
    private fun adj(c: String, amt: Int, ref: Int) = entry(c, LedgerKind.ADJ, amt, PayMethod.NONE, PayTag.NONE, 0, 0, 0, ref)
    private fun got(c: String, amt: Int, tag: PayTag, cover: Int = 0, toOld: Int = 0, toAdv: Int = 0, ref: Int? = null) =
        entry(c, LedgerKind.GOT, amt, PayMethod.CASH, tag, cover, toOld, toAdv, ref)
}
