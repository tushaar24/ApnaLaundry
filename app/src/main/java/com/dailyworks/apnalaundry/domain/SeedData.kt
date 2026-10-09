package com.dailyworks.apnalaundry.domain

import com.dailyworks.apnalaundry.core.AppDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The bundled demo dataset, transcribed verbatim from prototype-logic.js `fresh()`.
 * Used to seed Room on first launch and by the domain unit tests. Do not change the
 * numbers without re-checking the acceptance scenarios (PRODUCT_SPEC.md §14).
 */
object SeedData {
    /** Name a freshly seeded shop starts with. */
    const val DEFAULT_SHOP_NAME = "MyLaundry"

    // Placeholder names this and older builds have seeded — a shop still
    // carrying one of these hasn't been named by its owner yet.
    private val UNNAMED_SHOP_NAMES = setOf("", "mylaundry", "apna laundry", "apnalaundry", "my shop")

    fun isDefaultShopName(name: String?): Boolean = (name ?: "").trim().lowercase() in UNNAMED_SHOP_NAMES

    private const val T = AppDate.TODAY // 2026-09-25

    private fun items(vararg pairs: Pair<String, Int>) =
        pairs.map { ServiceItem(it.first, it.second) }

    val shop = Shop(name = "Shine Laundry", phone = "9876543210", expressPct = 50)

    val services = listOf(
        Service(
            id = "wf", name = "Wash & Fold", mode = PricingMode.WEIGHT,
            ratePerKg = 60, minKg = 3.0, readyInDays = null, lockedToPiece = false, sortOrder = 0,
            items = items("Shirt" to 15, "T-shirt" to 12, "Pant" to 20, "Jeans" to 25, "Kurta" to 20, "Saree" to 50, "Bedsheet" to 40, "Blanket" to 100),
        ),
        Service(
            id = "wi", name = "Wash & Iron", mode = PricingMode.PIECE,
            ratePerKg = null, minKg = null, readyInDays = null, lockedToPiece = false, sortOrder = 1,
            items = items("Shirt" to 25, "T-shirt" to 20, "Pant" to 30, "Jeans" to 35, "Kurta" to 30, "Saree" to 80, "Bedsheet" to 60, "Blanket" to 150),
        ),
        Service(
            id = "io", name = "Iron Only", mode = PricingMode.PIECE,
            ratePerKg = null, minKg = null, readyInDays = null, lockedToPiece = false, sortOrder = 2,
            items = items("Shirt" to 10, "T-shirt" to 8, "Pant" to 12, "Jeans" to 15, "Kurta" to 12, "Saree" to 40, "Bedsheet" to 25, "Blanket" to 60),
        ),
        Service(
            id = "dc", name = "Dry Cleaning", mode = PricingMode.PIECE,
            ratePerKg = null, minKg = null, readyInDays = null, lockedToPiece = true, sortOrder = 3,
            items = items("Suit" to 350, "Blazer" to 250, "Saree" to 180, "Lehenga" to 450, "Sherwani" to 400, "Jacket" to 250, "Shirt" to 60, "Pant" to 80),
        ),
    )

    private fun c(id: String, name: String, phone: String, addr: String, past: Int, last: String, ago: Int) =
        Customer(id, name, phone, addr, past, last, ago)

    val customers = listOf(
        c("c1", "Priya Sharma", "9876543210", "House 14, Shastri Nagar, near Hanuman Mandir", 13, "Today", 0),
        c("c2", "Anjali Gupta", "9001122334", "22, Gandhi Chowk", 20, "23 Sep", 2),
        c("c3", "Rakesh Verma", "9898012345", "12, Civil Lines", 7, "Today", 0),
        c("c4", "Sunita Rao", "9123456780", "Near Bus Stand, Station Road", 10, "Today", 0),
        c("c5", "Mohd. Irfan", "9765400123", "", 4, "Today", 0),
        c("c6", "Vikas Jain", "9988776655", "45, Nehru Colony", 8, "Yesterday", 1),
        c("c7", "Pooja Verma", "9810098100", "", 6, "Yesterday", 1),
        c("c8", "Deepak Yadav", "9870011223", "Plot 9, Sector 4", 3, "22 Sep", 3),
        c("c9", "Kavita Singh", "9899900112", "", 11, "22 Sep", 3),
        c("c10", "Suresh Patel", "9876012345", "", 5, "21 Sep", 4),
        c("c11", "Neha Kapoor", "9811122233", "B-7, Model Town", 1, "Today", 0),
        c("c12", "Arjun Mehta", "9812345670", "", 9, "23 Sep", 2),
        c("c13", "Ashok Kumar", "9828098280", "Ashok Vihar", 6, "23 Sep", 2),
        c("c14", "Meena Joshi", "9829098290", "", 15, "Yesterday", 1),
        c("c15", "Sanjay Mishra", "9831098310", "Lal Kothi", 2, "22 Sep", 3),
        c("c16", "Manish Gupta", "9825098250", "Station Road, Gali 3", 3, "Today", 0),
    )

    // ---- line helpers ----
    private fun L(svc: String, name: String, qty: Int, price: Int) =
        OrderLine(serviceId = svc, serviceName = svcName(svc), itemName = name, qty = qty, price = price, base = price, kg = 0.0, amt = qty * price)

    private fun K(svc: String, kg: Double, rate: Int, min: Double) =
        OrderLine(serviceId = svc, serviceName = svcName(svc), itemName = "By weight", qty = 0, price = rate, base = rate, kg = kg, amt = (max(kg, min) * rate).roundToInt())

    private fun svcName(id: String) = services.first { it.id == id }.name

    private fun order(
        id: Int, cust: String, pt: Route, dt: Route, pd: String, dd: String,
        ptime: String = "", dtime: String = "", fee: Int = 0, lines: List<OrderLine> = emptyList(),
        status: OrderStatus = OrderStatus.RECEIVED, pre: Int = 0, paid: Int = 0,
        doneAt: String = "", doneDate: String = "", reason: String = "",
        express: Boolean = false, exAmt: Int = 0, discount: Int = 0,
        createdOn: String = pd, ddAuto: Boolean = true, billSent: Boolean = true, pieces: Int = 0,
    ) = Order(id, cust, pt, dt, pd, ptime, dd, dtime, ddAuto, status, reason, fee, express, exAmt, discount, pre, paid, doneAt, doneDate, createdOn, billSent, pieces, lines)

    val orders = listOf(
        order(1043, "c3", Route.HOME, Route.HOME, T, "2026-09-27", ptime = "5:00 PM", fee = 30, status = OrderStatus.CREATED),
        order(1044, "c4", Route.HOME, Route.SHOP, T, "2026-09-26", ptime = "11:30 AM", fee = 20, status = OrderStatus.CREATED),
        order(1041, "c1", Route.SHOP, Route.HOME, T, "2026-09-27", dtime = "6:00 PM", express = true, exAmt = 170,
            lines = listOf(L("wi", "Shirt", 4, 25), L("wi", "T-shirt", 2, 20), L("wi", "Pant", 2, 30), L("wi", "Saree", 1, 80), L("wi", "Bedsheet", 1, 60))),
        order(1042, "c5", Route.SHOP, Route.SHOP, T, T, dtime = "7:00 PM", pre = 60, lines = listOf(L("io", "Shirt", 6, 10))),
        order(1045, "c11", Route.HOME, Route.HOME, T, "2026-09-27", ptime = "10:00 AM", fee = 30, status = OrderStatus.CANCELLED, reason = "Customer not home"),
        order(1036, "c6", Route.HOME, Route.HOME, "2026-09-24", "2026-09-26", ptime = "6:00 PM", fee = 30, status = OrderStatus.CREATED),
        order(1037, "c2", Route.SHOP, Route.HOME, "2026-09-23", T, dtime = "6:00 PM", status = OrderStatus.READY,
            lines = listOf(L("wi", "Shirt", 8, 25), L("wi", "Pant", 4, 30), L("wi", "Saree", 1, 80), L("wi", "Bedsheet", 2, 60))),
        order(1033, "c8", Route.HOME, Route.HOME, "2026-09-22", T, dtime = "7:30 PM", fee = 40,
            lines = listOf(L("dc", "Suit", 1, 350), L("dc", "Pant", 1, 80))),
        order(1035, "c12", Route.SHOP, Route.SHOP, "2026-09-23", T, status = OrderStatus.READY, pre = 290,
            lines = listOf(L("wi", "Shirt", 6, 25), L("wi", "Pant", 2, 30), L("wi", "Kurta", 2, 30), L("wi", "T-shirt", 1, 20))),
        order(1030, "c9", Route.SHOP, Route.SHOP, "2026-09-22", T, status = OrderStatus.DELIVERED, doneAt = "11:40 AM", doneDate = T, paid = 240,
            lines = listOf(K("wf", 4.0, 60, 3.0))),
        order(1027, "c13", Route.SHOP, Route.HOME, "2026-09-23", T, status = OrderStatus.DELIVERED, doneAt = "4:45 PM", doneDate = T, fee = 30, paid = 410,
            lines = listOf(L("wi", "Shirt", 8, 25), L("wi", "Pant", 4, 30), L("wi", "Bedsheet", 1, 60))),
        order(1026, "c14", Route.SHOP, Route.SHOP, "2026-09-24", T, status = OrderStatus.DELIVERED, doneAt = "5:10 PM", doneDate = T, paid = 185,
            lines = listOf(L("io", "Shirt", 10, 10), L("io", "Pant", 5, 12), L("io", "Bedsheet", 1, 25))),
        order(1024, "c15", Route.HOME, Route.HOME, "2026-09-22", T, status = OrderStatus.DELIVERED, doneAt = "6:00 PM", doneDate = T, fee = 50, paid = 300,
            lines = listOf(L("dc", "Blazer", 1, 250), L("dc", "Saree", 1, 180))),
        order(1029, "c10", Route.SHOP, Route.SHOP, "2026-09-21", "2026-09-24", status = OrderStatus.READY,
            lines = listOf(L("dc", "Saree", 1, 180))),
        order(1040, "c7", Route.SHOP, Route.SHOP, "2026-09-24", "", ddAuto = false, pre = 260,
            lines = listOf(L("wi", "Shirt", 6, 25), L("wi", "Pant", 2, 30), L("wi", "T-shirt", 1, 20), L("wi", "Kurta", 1, 30))),
        order(1039, "c16", Route.HOME, Route.HOME, "2026-09-26", "2026-09-28", ptime = "10:00 AM", fee = 30, status = OrderStatus.CREATED, createdOn = T),
    )

    private var ts = 0L
    private fun e(
        cust: String, date: String, time: String, kind: LedgerKind, amt: Int,
        method: PayMethod = PayMethod.NONE, tag: PayTag = PayTag.NONE,
        cover: Int = 0, toOld: Int = 0, toAdv: Int = 0, ref: Int? = null, note: String = "",
    ): LedgerEntry {
        ts += 1
        return LedgerEntry("s$ts", cust, date, time, ts, kind, amt, method, tag, cover, toOld, toAdv, ref, note)
    }

    val ledger: List<LedgerEntry> = run {
        ts = 0
        listOf(
            e("c2", "2026-09-01", "", LedgerKind.BILL, 450, ref = 971, note = "8 items · Wash & Iron"),
            e("c2", "2026-09-01", "", LedgerKind.GOT, 500, PayMethod.CASH, PayTag.DELIVER, cover = 450, toAdv = 50, ref = 971),
            e("c6", "2026-09-05", "", LedgerKind.OLD, 950),
            e("c2", "2026-09-10", "", LedgerKind.BILL, 320, ref = 988, note = "5 kg · Wash & Fold"),
            e("c2", "2026-09-10", "", LedgerKind.GOT, 270, PayMethod.UPI, PayTag.DELIVER, cover = 270, ref = 988),
            e("c1", "2026-09-12", "", LedgerKind.BILL, 340, ref = 993, note = "10 items · Wash & Iron"),
            e("c1", "2026-09-12", "", LedgerKind.GOT, 340, PayMethod.UPI, PayTag.DELIVER, cover = 340, ref = 993),
            e("c4", "2026-09-12", "", LedgerKind.BILL, 260, ref = 995, note = "9 items · Wash & Iron"),
            e("c4", "2026-09-12", "", LedgerKind.GOT, 200, PayMethod.CASH, PayTag.DELIVER, cover = 200, ref = 995),
            e("c8", "2026-09-15", "", LedgerKind.BILL, 1250, ref = 1001, note = "5 items · Dry Cleaning"),
            e("c2", "2026-09-18", "", LedgerKind.BILL, 600, ref = 1012, note = "10 items · Wash & Iron"),
            e("c2", "2026-09-18", "", LedgerKind.GOT, 400, PayMethod.CASH, PayTag.DELIVER, cover = 400, ref = 1012),
            e("c7", "2026-09-19", "", LedgerKind.BILL, 210, ref = 1008, note = "7 items · Wash & Iron"),
            e("c7", "2026-09-19", "", LedgerKind.GOT, 260, PayMethod.UPI, PayTag.DELIVER, cover = 210, toAdv = 50, ref = 1008),
            e("c5", T, "9:15 AM", LedgerKind.GOT, 60, PayMethod.CASH, PayTag.PRE, cover = 60, ref = 1042),
            e("c7", T, "10:05 AM", LedgerKind.GOT, 260, PayMethod.UPI, PayTag.PRE, cover = 260, ref = 1040),
            e("c9", T, "11:40 AM", LedgerKind.BILL, 240, ref = 1030),
            e("c9", T, "11:40 AM", LedgerKind.GOT, 240, PayMethod.UPI, PayTag.DELIVER, cover = 240, ref = 1030),
            e("c6", T, "12:20 PM", LedgerKind.GOT, 500, PayMethod.CASH, PayTag.RECEIVE, toOld = 500),
            e("c3", T, "1:05 PM", LedgerKind.GOT, 100, PayMethod.UPI, PayTag.RECEIVE, toAdv = 100),
            e("c12", T, "3:30 PM", LedgerKind.GOT, 290, PayMethod.CASH, PayTag.PRE, cover = 290, ref = 1035),
            e("c13", T, "4:45 PM", LedgerKind.BILL, 410, ref = 1027),
            e("c13", T, "4:45 PM", LedgerKind.GOT, 410, PayMethod.CASH, PayTag.DELIVER, cover = 410, ref = 1027),
            e("c14", T, "5:10 PM", LedgerKind.BILL, 185, ref = 1026),
            e("c14", T, "5:10 PM", LedgerKind.GOT, 185, PayMethod.UPI, PayTag.DELIVER, cover = 185, ref = 1026),
            e("c15", T, "6:00 PM", LedgerKind.BILL, 480, ref = 1024),
            e("c15", T, "6:00 PM", LedgerKind.GOT, 300, PayMethod.CASH, PayTag.DELIVER, cover = 300, ref = 1024),
        )
    }

    const val NEXT_ORDER = 1046
    const val NEXT_CUST = 17
    val closedDays = setOf("2026-09-24")

    fun state() = LaundryState(shop, services, customers, orders, ledger, closedDays)
}
