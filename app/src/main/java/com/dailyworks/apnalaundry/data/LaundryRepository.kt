package com.dailyworks.apnalaundry.data

import androidx.room.withTransaction
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.SyncClock
import com.dailyworks.apnalaundry.data.local.AppDatabase
import com.dailyworks.apnalaundry.data.local.CustomerEntity
import com.dailyworks.apnalaundry.data.local.LedgerEntity
import com.dailyworks.apnalaundry.data.local.OrderEntity
import com.dailyworks.apnalaundry.data.local.ServiceEntity
import com.dailyworks.apnalaundry.data.local.ShopEntity
import com.dailyworks.apnalaundry.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Snapshot of the mutable tables, used to power Undo. */
data class Snapshot(
    val orders: List<OrderEntity>,
    val ledger: List<LedgerEntity>,
    val customers: List<CustomerEntity>,
    val services: List<ServiceEntity>,
    val shop: ShopEntity?,
)

/** A completed command: user-facing toast text and an optional undo snapshot. */
data class CmdResult(val toast: String, val undo: Snapshot? = null)

class LaundryRepository(private val db: AppDatabase) {
    private val shopDao = db.shopDao()
    private val serviceDao = db.serviceDao()
    private val customerDao = db.customerDao()
    private val orderDao = db.orderDao()
    private val ledgerDao = db.ledgerDao()
    private val dayCloseDao = db.dayCloseDao()

    @Volatile private var tsCounter = 100_000L

    val state: Flow<LaundryState> = run {
        val shopDays = combine(shopDao.observe(), dayCloseDao.observe()) { s, d -> s to d }
        combine(
            serviceDao.observe(), customerDao.observe(), orderDao.observe(), ledgerDao.observe(), shopDays,
        ) { services, customers, orders, ledger, sd ->
            val (shopE, daysE) = sd
            LaundryState(
                shop = shopE?.toDomain() ?: Shop(SeedData.shop.name, SeedData.shop.phone, SeedData.shop.expressPct),
                services = services.map { it.toDomain() }.sortedBy { it.sortOrder },
                customers = customers.map { it.toDomain() },
                orders = orders.map { it.toDomain() },
                ledger = ledger.map { it.toDomain() },
                closedDays = daysE.map { it.date }.toSet(),
            )
        }
    }

    val shopFlow: Flow<Shop?> = shopDao.observe().map { it?.toDomain() }

    // ---------------- seeding / lifecycle ----------------
    /**
     * First-login bootstrap for a NEW account: shop defaults + the default
     * rate card only — no demo customers/orders. Existing accounts are
     * restored from the server by the initial sync instead (see
     * AuthRepository.verifyOtp).
     */
    suspend fun ensureSeeded(shopPhone: String? = null) {
        if (shopDao.get() != null) { refreshTsCounter(); return }
        db.withTransaction {
            shopDao.upsert(
                ShopEntity(
                    1, SeedData.DEFAULT_SHOP_NAME, shopPhone ?: "", SeedData.shop.expressPct, 1001, 1,
                    billPhone = shopPhone ?: "", onboardingStep = "intro",
                )
            )
            serviceDao.upsertAll(SeedData.services.map { it.toEntity() })
        }
        refreshTsCounter()
    }

    suspend fun hasShop(): Boolean = shopDao.get() != null

    /**
     * Onboarding (shop name + rate list) is behind this account. The server
     * keeps no flag and the seed is pushed at first login, so "has a shop"
     * isn't enough: a shop still on a placeholder name with no customers or
     * orders hasn't onboarded.
     */
    suspend fun isOnboarded(): Boolean {
        val shop = shopDao.get() ?: return false
        // Shops created since onboarding was tracked carry their step.
        if (shop.onboardingStep.isNotEmpty()) return shop.onboardingStep == "done"
        return !SeedData.isDefaultShopName(shop.name) ||
            customerDao.observeOnce().isNotEmpty() || orderDao.observeOnce().isNotEmpty()
    }

    suspend fun currentShopName(): String? = shopDao.get()?.name

    /** Wipe everything (logout). The next login pulls or reseeds. */
    suspend fun clearAll() = db.withTransaction {
        orderDao.clear(); ledgerDao.clear(); customerDao.clear(); serviceDao.clear(); dayCloseDao.clear(); shopDao.clear()
    }

    /** Ledger ts values are minted locally; pulled rows may carry higher ones. */
    suspend fun refreshTsCounter() {
        val maxTs = ledgerDao.getAll().maxOfOrNull { it.ts } ?: 0L
        tsCounter = maxOf(tsCounter, maxTs + 1)
    }

    // ---------------- snapshot / undo ----------------
    suspend fun snapshot(): Snapshot = Snapshot(
        orders = orderDao.getAll(),
        ledger = ledgerDao.getAll(),
        customers = customerDao.getAll(),
        services = serviceDao.getAll(),
        shop = shopDao.get(),
    )

    /**
     * Undo. Everything restored is re-stamped dirty so the rollback syncs;
     * rows created after the snapshot become tombstones (not plain deletes) —
     * otherwise the server copy would just resurrect them on the next pull.
     */
    suspend fun restore(s: Snapshot) = db.withTransaction {
        val now = SyncClock.now()
        fun <T, K> tombstones(current: List<T>, keep: Set<K>, key: (T) -> K, kill: (T) -> T): List<T> =
            current.filter { key(it) !in keep }.map(kill)

        val orderTombs = tombstones(orderDao.getAll(), s.orders.map { it.id }.toSet(), { it.id }) {
            it.copy(deleted = true, dirty = true, updatedAt = now)
        }
        val ledgerTombs = tombstones(ledgerDao.getAll(), s.ledger.map { it.id }.toSet(), { it.id }) {
            it.copy(deleted = true, dirty = true, updatedAt = now)
        }
        val custTombs = tombstones(customerDao.getAll(), s.customers.map { it.id }.toSet(), { it.id }) {
            it.copy(deleted = true, dirty = true, updatedAt = now)
        }
        val svcTombs = tombstones(serviceDao.getAll(), s.services.map { it.id }.toSet(), { it.id }) {
            it.copy(deleted = true, dirty = true, updatedAt = now)
        }

        orderDao.clear(); orderDao.upsertAll(s.orders.map { it.copy(dirty = true, updatedAt = now) } + orderTombs)
        ledgerDao.clear(); ledgerDao.insertAll(s.ledger.map { it.copy(dirty = true, updatedAt = now) } + ledgerTombs)
        customerDao.clear(); customerDao.upsertAll(s.customers.map { it.copy(dirty = true, updatedAt = now) } + custTombs)
        serviceDao.clear(); serviceDao.upsertAll(s.services.map { it.copy(dirty = true, updatedAt = now) } + svcTombs)
        s.shop?.let { shopDao.upsert(it.copy(dirty = true, updatedAt = now)) }
    }

    // ---------------- helpers ----------------
    private suspend fun current(): LaundryState {
        val shopE = shopDao.get()
        return LaundryState(
            shop = shopE?.toDomain() ?: Shop("Shine Laundry", "9876543210", 50),
            services = serviceDao.getAll().filter { !it.deleted }.map { it.toDomain() }.sortedBy { it.sortOrder },
            customers = customerDao.observeOnce().map { it.toDomain() },
            orders = orderDao.observeOnce().map { it.toDomain() },
            ledger = ledgerDao.observeOnce().map { it.toDomain() },
            closedDays = dayCloseDao.observeOnce().map { it.date }.toSet(),
        )
    }

    private fun firstName(name: String): String =
        if (name.startsWith("+")) name else name.substringBefore(" ")

    private fun nextTs(): Long = ++tsCounter
    private fun newLedgerId(): String = "e${nextTs()}"

    private suspend fun mkEntry(
        cust: String, kind: LedgerKind, amt: Int, method: PayMethod = PayMethod.NONE, tag: PayTag = PayTag.NONE,
        cover: Int = 0, toOld: Int = 0, toAdv: Int = 0, ref: Int? = null, note: String = "",
        date: String = AppDate.TODAY, time: String = AppDate.nowText(),
    ): LedgerEntry {
        val ts = nextTs()
        return LedgerEntry("e$ts", cust, date, time, ts, kind, amt, method, tag, cover, toOld, toAdv, ref, note)
    }

    private suspend fun money(n: Int) = com.dailyworks.apnalaundry.core.Money.rupees(n)

    // ---------------- order status ----------------
    suspend fun markPickedUp(orderId: Int): CmdResult {
        val undo = snapshot()
        val st = current()
        val o = st.orders.first { it.id == orderId }
        val nm = firstName(st.customers.first { it.id == o.custId }.name)
        orderDao.upsert(o.copy(status = OrderStatus.RECEIVED).toEntity())
        Analytics.orderPickedUp(orderId)
        return CmdResult("Picked up · $nm", undo)
    }

    /** Ready: the owner also says when it'll be delivered (like payment on delivery). */
    suspend fun markReady(orderId: Int, deliveryDate: String): CmdResult {
        val undo = snapshot()
        val st = current()
        val o = st.orders.first { it.id == orderId }
        val nm = firstName(st.customers.first { it.id == o.custId }.name)
        val ready = o.copy(status = OrderStatus.READY)
        orderDao.upsert((if (deliveryDate.isNotBlank()) ready.copy(deliveryDate = deliveryDate, ddAuto = false) else ready).toEntity())
        Analytics.orderMarkedReady(orderId)
        return CmdResult("Marked ready · $nm", undo)
    }

    /** Count-clothes sheet: attach lines and advance to [next] (received or ready). */
    suspend fun saveCount(orderId: Int, next: OrderStatus, lines: List<OrderLine>, deliveryDate: String = ""): CmdResult {
        val undo = snapshot()
        val st = current()
        val o = st.orders.first { it.id == orderId }
        val nm = firstName(st.customers.first { it.id == o.custId }.name)
        val total = lines.sumOf { it.amt }
        // A % express / discount set when the pickup was booked applies now the clothes are counted.
        val priced = LaundryMath.withPctExtras(o.copy(lines = lines), st.shop.expressPct)
        var counted = o.copy(status = next, lines = lines, exAmt = priced.exAmt, discount = priced.discount, billSent = false)
        // Counted straight to ready: the delivery date asked in the sheet.
        if (next == OrderStatus.READY && deliveryDate.isNotBlank()) counted = counted.copy(deliveryDate = deliveryDate, ddAuto = false)
        orderDao.upsert(counted.toEntity())
        Analytics.clothesCounted(orderId, next.name, total)
        val head = if (next == OrderStatus.READY) "Marked ready · $nm" else "Picked up"
        return CmdResult("$head · bill of ${money(total)} made — send it from the order", undo)
    }

    suspend fun deliver(orderId: Int, amountReceived: Int, method: PayMethod): CmdResult {
        val undo = snapshot()
        val st = current()
        val o = st.orders.first { it.id == orderId }
        // Already delivered (double tap, stale sheet): never bill twice. A
        // cancelled order may be delivered — that revives it (no BILL exists).
        if (o.status == OrderStatus.DELIVERED) return CmdResult("Order #${o.no()} is already delivered")
        val total = LaundryMath.amtOf(o)
        val pre = o.pre
        val oldBal = LaundryMath.balance(o.custId, st.ledger, st.orders, o.id)
        val alloc = LaundryMath.deliverAllocation(total, pre, oldBal, amountReceived)
        val entries = mutableListOf<LedgerEntry>()
        entries += mkEntry(o.custId, LedgerKind.BILL, total, ref = o.id)
        if (amountReceived > 0) {
            entries += mkEntry(o.custId, LedgerKind.GOT, amountReceived, method, PayTag.DELIVER,
                cover = alloc.cover, toOld = alloc.toOld, toAdv = alloc.toAdv, ref = o.id)
        }
        db.withTransaction {
            ledgerDao.insertAll(entries.map { it.toEntity() })
            // Delivered today: the delivery date / time become now, so it shows under
            // today's Deliveries whatever was planned (or if no date was set).
            val now = AppDate.nowText()
            orderDao.upsert(
                o.copy(
                    status = OrderStatus.DELIVERED, cancelReason = "", doneAt = now, doneDate = AppDate.TODAY,
                    deliveryDate = AppDate.TODAY, deliveryTime = now, ddAuto = false, paid = alloc.paidToward,
                ).toEntity(),
            )
        }
        Analytics.orderDelivered(
            orderId = o.id, total = total, amountReceived = amountReceived, method = method,
            toKhata = maxOf(0, total - alloc.paidToward), fromAdvance = pre, lines = o.lines,
        )
        if (amountReceived > 0) {
            Analytics.paymentReceived(amountReceived, method, "delivery", o.custId, o.id)
        }
        val paidPart = if (amountReceived > 0) " · ${money(amountReceived)} ${if (method == PayMethod.UPI) "UPI" else "cash"}" else ""
        val balPart = when {
            alloc.newBalance > 0 -> " · ${money(alloc.newBalance)} baaki"
            alloc.newBalance < 0 -> " · ${money(alloc.newBalance)} advance"
            else -> " · all clear"
        }
        return CmdResult("Delivered$paidPart$balPart", undo)
    }

    suspend fun prepay(orderId: Int, method: PayMethod): CmdResult {
        val undo = snapshot()
        val st = current()
        val o = st.orders.first { it.id == orderId }
        // Only what's still unpaid, and only before delivery — never charge twice.
        val due = LaundryMath.amtOf(o) - o.pre
        if (!LaundryMath.isOpen(o) || due <= 0) return CmdResult("Nothing left to collect on order #${o.no()}")
        db.withTransaction {
            ledgerDao.insert(mkEntry(o.custId, LedgerKind.GOT, due, method, PayTag.PRE, cover = due, ref = o.id).toEntity())
            orderDao.upsert(o.copy(pre = o.pre + due).toEntity())
        }
        Analytics.paymentReceived(due, method, "prepay", o.custId, o.id)
        return CmdResult("Got ${money(due)} ${if (method == PayMethod.UPI) "UPI" else "cash"} · order fully paid", undo)
    }

    suspend fun cancelOrder(orderId: Int, reason: String): CmdResult {
        val undo = snapshot()
        val st = current()
        val o = st.orders.first { it.id == orderId }
        val nm = firstName(st.customers.first { it.id == o.custId }.name)
        db.withTransaction {
            val base = if (o.status == OrderStatus.DELIVERED) reverseDelivery(o) else o
            orderDao.upsert(base.copy(status = OrderStatus.CANCELLED, cancelReason = reason).toEntity())
        }
        Analytics.orderCancelled(orderId, reason)
        return CmdResult((if (o.status == OrderStatus.CREATED) "Pickup cancelled" else "Order cancelled") + " · $nm", undo)
    }

    /**
     * Change-status sheet: move an order to any state. Steps that need input
     * (count clothes, delivery date, payment, cancel reason) go through their
     * own sheets and commands; this does the direct writes, backward moves
     * included. Leaving DELIVERED first takes the bill off the khata.
     */
    suspend fun setStatus(orderId: Int, target: OrderStatus): CmdResult {
        val undo = snapshot()
        val st = current()
        val o = st.orders.first { it.id == orderId }
        if (o.status == target) return CmdResult("Order #${o.no()} is already there")
        // Delivering bills and collects — that path is deliver(), never this.
        if (target == OrderStatus.DELIVERED) return CmdResult("Use Mark delivered for order #${o.no()}")
        val nm = firstName(st.customers.first { it.id == o.custId }.name)
        val unDelivered = o.status == OrderStatus.DELIVERED
        db.withTransaction {
            val base = if (unDelivered) reverseDelivery(o) else o
            orderDao.upsert(base.copy(status = target, cancelReason = "").toEntity())
        }
        Analytics.orderStatusChanged(orderId, o.status.name, target.name)
        val word = when (target) {
            OrderStatus.CREATED -> "To pick up"; OrderStatus.RECEIVED -> "Received"
            OrderStatus.READY -> "Ready"; OrderStatus.DELIVERED -> "Delivered"; OrderStatus.CANCELLED -> "Cancelled"
        }
        return CmdResult("Moved to $word · $nm" + if (unDelivered) " · bill taken off the khata" else "", undo)
    }

    /**
     * Un-deliver: the BILL (and any ADJ) khata entries become tombstones, so
     * the bill is gone; payments stay in the khata and are counted on the
     * order as paid-in-advance, so delivering again never bills twice.
     */
    private suspend fun reverseDelivery(o: Order): Order {
        val now = SyncClock.now()
        val rows = ledgerDao.getAll().filter { it.ref == o.id && !it.deleted }
        rows.filter { it.kind == "BILL" || it.kind == "ADJ" }.forEach {
            ledgerDao.insert(it.copy(deleted = true, dirty = true, updatedAt = now))
        }
        val pre = rows.filter { it.kind == "GOT" }.sumOf { it.cover }
        return o.copy(pre = pre, paid = 0, doneAt = "", doneDate = "")
    }

    /**
     * Deletes an order and its bill for good: the order and every khata entry
     * made for it (bill, payments, edits) become tombstones, so the customer's
     * balance is as if it never existed. Undo restores it all.
     */
    suspend fun deleteOrder(orderId: Int): CmdResult {
        val undo = snapshot()
        val st = current()
        val o = st.orders.first { it.id == orderId }
        val nm = firstName(st.customers.first { it.id == o.custId }.name)
        val now = SyncClock.now()
        db.withTransaction {
            orderDao.get(orderId)?.let { orderDao.upsert(it.copy(deleted = true, dirty = true, updatedAt = now)) }
            ledgerDao.getAll().filter { it.ref == orderId && !it.deleted }.forEach {
                ledgerDao.insert(it.copy(deleted = true, dirty = true, updatedAt = now))
            }
        }
        return CmdResult("Bill #${o.no()} deleted · $nm", undo)
    }

    /** kind = "pickup" or "drop". */
    suspend fun reschedule(orderId: Int, kind: String, dateIso: String, time24: String, notify: Boolean): CmdResult {
        val undo = snapshot()
        val st = current()
        val o = st.orders.first { it.id == orderId }
        val time12 = AppDate.to12h(time24)
        val (updated, msg) = if (kind == "pickup") {
            var u = o.copy(pickupDate = dateIso, pickupTime = time12)
            if (o.deliveryDate.isNotBlank() && o.ddAuto) {
                val shift = AppDate.daysBetween(o.pickupDate, dateIso)
                u = u.copy(deliveryDate = AppDate.add(o.deliveryDate, shift))
            }
            u to "Pickup moved to ${AppDate.short(dateIso)}" + if (time12.isNotBlank()) " · $time12" else ""
        } else {
            val u = o.copy(deliveryDate = dateIso, deliveryTime = time12, ddAuto = false)
            val prefix = if (o.deliveryDate.isNotBlank()) "Delivery moved to " else "Delivery date set: "
            u to prefix + AppDate.short(dateIso) + if (time12.isNotBlank()) " · $time12" else ""
        }
        orderDao.upsert(updated.toEntity())
        Analytics.orderRescheduled(orderId, if (kind == "pickup") "pickup" else "delivery", notify)
        return CmdResult(msg, undo)
    }

    suspend fun sendBill(orderId: Int): CmdResult {
        val st = current()
        val o = st.orders.first { it.id == orderId }
        val nm = firstName(st.customers.first { it.id == o.custId }.name)
        orderDao.upsert(o.copy(billSent = true).toEntity())
        Analytics.billSent(orderId)
        return CmdResult("Opening WhatsApp · bill #${current().orders.firstOrNull { it.id == orderId }?.no() ?: orderId} to $nm")
    }

    // ---------------- khata ----------------
    suspend fun receivePayment(custId: String, amount: Int, method: PayMethod): CmdResult {
        val undo = snapshot()
        val st = current()
        val oldBal = LaundryMath.balance(custId, st.ledger, st.orders)
        val (toOld, toAdv) = LaundryMath.receiveAllocation(oldBal, amount)
        ledgerDao.insert(mkEntry(custId, LedgerKind.GOT, amount, method, PayTag.RECEIVE, toOld = toOld, toAdv = toAdv).toEntity())
        Analytics.paymentReceived(amount, method, "khata", custId, null)
        val after = oldBal - amount
        val tail = when {
            after > 0 -> "${money(after)} still baaki"
            after < 0 -> "${money(after)} advance"
            else -> "all clear"
        }
        return CmdResult("Got ${money(amount)} ${if (method == PayMethod.UPI) "UPI" else "cash"} · $tail", undo)
    }

    suspend fun addOldBaaki(custId: String, amount: Int): CmdResult {
        val undo = snapshot()
        ledgerDao.insert(mkEntry(custId, LedgerKind.OLD, amount).toEntity())
        Analytics.oldBaakiAdded(amount, custId)
        return CmdResult("Added ${money(amount)} old baaki", undo)
    }

    // ---------------- customers ----------------
    /** Returns the customer id (new or existing on edit). */
    suspend fun saveCustomer(
        editId: String?, name: String, phone: String, address: String, oldBaaki: Int, fromList: Boolean,
    ): Pair<String, CmdResult?> {
        val undo = snapshot()
        if (editId != null) {
            val st = current()
            val c = st.customers.first { it.id == editId }
            customerDao.upsert(c.copy(name = name, phone = phone, address = address).toEntity())
            return editId to null
        }
        val shopE = shopDao.get()!!
        val id = "c${shopE.nextCust}"
        customerDao.upsert(Customer(id, name, phone, address, 0, "—", 9).toEntity())
        shopDao.upsert(shopE.copy(nextCust = shopE.nextCust + 1, dirty = true, updatedAt = SyncClock.now()))
        if (oldBaaki > 0) ledgerDao.insert(mkEntry(id, LedgerKind.OLD, oldBaaki).toEntity())
        Analytics.customerAdded(if (fromList) "list" else "order", oldBaaki > 0)
        val res = if (fromList)
            CmdResult("$name added" + if (oldBaaki > 0) " with ${money(oldBaaki)} baaki" else "", undo)
        else null
        return id to res
    }

    // ---------------- rate card ----------------
    suspend fun upsertService(service: Service) = serviceDao.upsert(service.toEntity())

    suspend fun deleteService(id: String): CmdResult {
        val undo = snapshot()
        val services = serviceDao.getAll().filter { !it.deleted }
        if (services.size <= 1) return CmdResult("Keep at least one service")
        val sv = services.first { it.id == id }
        serviceDao.setDeleted(id, true, SyncClock.now())
        Analytics.serviceDeleted(id)
        return CmdResult("${sv.name} deleted · old orders keep it", undo)
    }

    suspend fun updateShop(name: String, expressPct: Int) {
        val e = shopDao.get()!!
        shopDao.upsert(e.copy(name = name, expressPct = expressPct, dirty = true, updatedAt = SyncClock.now()))
        if (name.isNotBlank()) Analytics.updateProfile(mapOf("Name" to name))
    }

    /** Saves the shop name and/or bill details (onboarding "Your bill", Settings). */
    suspend fun updateShopDetails(name: String? = null, details: BillDetails.Fields? = null) {
        val e = shopDao.get() ?: return
        var n = e.copy(dirty = true, updatedAt = SyncClock.now())
        if (name != null) n = n.copy(name = name)
        if (details != null) n = n.copy(
            billPhone = details.billPhone, address = details.address, gstin = details.gstin, email = details.email,
            upiId = details.upiId, logoId = details.logoId, termsJson = encodeTerms(details.terms),
            termsCustom = details.termsCustom, billTemplate = details.billTemplate,
        )
        shopDao.upsert(n)
        if (!name.isNullOrBlank()) Analytics.updateProfile(mapOf("Name" to name))
    }

    /**
     * The last-used GST setting. Every change on a NEW order saves it straight
     * away, so the next order opens the same way (edits of an old order don't).
     */
    suspend fun setGstDefaults(on: Boolean, pct: Double, mode: String) {
        val e = shopDao.get() ?: return
        if (e.gstOn == on && e.gstPct == pct && e.gstMode == mode) return
        shopDao.upsert(e.copy(gstOn = on, gstPct = pct, gstMode = mode, dirty = true, updatedAt = SyncClock.now()))
    }

    /** Records onboarding progress so a killed app (or another device) resumes there. */
    suspend fun setOnboardingStep(step: String) {
        val e = shopDao.get() ?: return
        if (e.onboardingStep == step) return
        shopDao.upsert(e.copy(onboardingStep = step, dirty = true, updatedAt = SyncClock.now()))
    }

    suspend fun onboardingStep(): String = shopDao.get()?.onboardingStep ?: ""

    // ---------------- new / edit order ----------------
    data class SaveOrderResult(val orderId: Int, val goToBill: Boolean, val toast: String?, val undo: Snapshot?, val edited: Boolean)

    suspend fun saveOrder(
        editId: Int?, custId: String, pickup: Route, delivery: Route, pickupDate: String, pickupTime24: String,
        deliveryDate: String, deliveryTime24: String, ddAuto: Boolean, fee: Int, express: Boolean, exAmt: Int,
        discount: Int, lines: List<OrderLine>, quickAmount: Int, quickPieces: Int,
        serialNo: String = "",
        exPct: Int = 0, // 0 = exAmt is a fixed ₹ amount
        discPct: Int = 0, // 0 = discount is a fixed ₹ amount
        gstOn: Boolean = false, gstPct: Double = 0.0, gstMode: String = Gst.EXCL,
    ): SaveOrderResult {
        val undo = snapshot()
        val st = current()
        val anyHome = pickup == Route.HOME || delivery == Route.HOME
        val fields = OrderFields(
            custId, pickup, delivery, pickupDate, AppDate.to12h(pickupTime24), deliveryDate, AppDate.to12h(deliveryTime24),
            ddAuto, if (anyHome) fee else 0, express, if (express) exAmt else 0, discount, lines,
        )
        val nm = firstName(st.customers.first { it.id == custId }.name)

        if (editId != null) {
            val o = st.orders.first { it.id == editId }
            // keep lines of deleted services + any quick line
            val kept = o.lines.filter { !it.isQuick && st.services.none { s -> s.id == it.serviceId } }.toMutableList()
            if (quickAmount > 0) kept += quickLine(quickAmount, quickPieces)
            val newLines = kept + lines
            var updated = o.copy(
                custId = fields.custId, pickup = fields.pickup, delivery = fields.delivery, pickupDate = fields.pickupDate,
                pickupTime = fields.pickupTime, deliveryDate = fields.deliveryDate, deliveryTime = fields.deliveryTime,
                ddAuto = fields.ddAuto, fee = fields.fee, express = fields.express, exAmt = fields.exAmt,
                discount = fields.discount, lines = newLines, serialNo = serialNo.trim(),
                exPct = if (fields.express) exPct else 0, discPct = discPct,
                gstOn = gstOn && gstPct > 0, gstPct = gstPct, gstMode = gstMode,
            )
            updated = LaundryMath.withPctExtras(updated, st.shop.expressPct)
            if (updated.status == OrderStatus.CREATED && newLines.isNotEmpty() && pickup == Route.SHOP) {
                updated = updated.copy(status = OrderStatus.RECEIVED)
            }
            val before = LaundryMath.amtOf(o)
            val after = LaundryMath.amtOf(updated)
            val diff = after - before
            if (diff != 0) updated = updated.copy(billSent = false)
            db.withTransaction {
                orderDao.upsert(updated.toEntity())
                if (o.status == OrderStatus.DELIVERED && diff != 0) {
                    ledgerDao.insert(mkEntry(o.custId, LedgerKind.ADJ, diff, ref = o.id).toEntity())
                }
            }
            val khataPart = if (o.status == OrderStatus.DELIVERED && diff != 0) " · khata ${if (diff > 0) "+" else "−"}${money(kotlin.math.abs(diff))}" else ""
            val totalPart = if (diff != 0) " · new total ${money(after)}" else ""
            trackOrderSaved(updated, isEdit = true)
            return SaveOrderResult(o.id, goToBill = false, toast = "Order #${updated.no()} updated$totalPart$khataPart", undo = undo, edited = true)
        }

        // create
        val shopE = shopDao.get()!!
        val id = shopE.nextOrder
        val status = if (pickup == Route.SHOP) OrderStatus.RECEIVED else OrderStatus.CREATED
        val order = Order(
            id = id, custId = fields.custId, pickup = fields.pickup, delivery = fields.delivery,
            pickupDate = fields.pickupDate, pickupTime = fields.pickupTime, deliveryDate = fields.deliveryDate,
            deliveryTime = fields.deliveryTime, ddAuto = fields.ddAuto, status = status, cancelReason = "",
            fee = fields.fee, express = fields.express, exAmt = fields.exAmt, discount = fields.discount,
            pre = 0, paid = 0, doneAt = "", doneDate = "", createdOn = AppDate.TODAY, billSent = false,
            pieces = 0, lines = fields.lines,
            serialNo = serialNo.trim(), exPct = if (fields.express) exPct else 0, discPct = discPct,
            gstOn = gstOn && gstPct > 0, gstPct = gstPct, gstMode = gstMode,
        )
        db.withTransaction {
            orderDao.upsert(order.toEntity())
            shopDao.upsert(shopE.copy(nextOrder = id + 1, dirty = true, updatedAt = SyncClock.now()))
            touchCustomer(custId)
        }
        trackOrderSaved(order, isEdit = false)
        trackOrderMilestone()
        return if (fields.lines.isEmpty()) {
            val toast = if (pickup == Route.HOME)
                "Pickup scheduled for $nm" + (if (order.pickupTime.isNotBlank()) " · ${order.pickupTime}" else "")
            else "Order #${order.no()} saved for $nm · add clothes when ready"
            SaveOrderResult(id, goToBill = false, toast = toast, undo = undo, edited = false)
        } else {
            SaveOrderResult(id, goToBill = true, toast = null, undo = undo, edited = false)
        }
    }

    // ---------------- small helpers ----------------
    private suspend fun touchCustomer(custId: String) {
        val c = customerDao.observeOnce().firstOrNull { it.id == custId } ?: return
        customerDao.upsert(c.copy(lastLabel = "Today", agoRank = 0, dirty = true, updatedAt = SyncClock.now()))
    }

    /** Profile props for segments/journeys, e.g. "paid but no order after 2 days". */
    private suspend fun trackOrderMilestone() {
        val count = orderDao.observeOnce().size
        val today = java.time.LocalDate.now().toString()
        val props = mutableMapOf<String, Any>("orders_count" to count, "last_order_date" to today)
        if (count == 1) props["first_order_date"] = today
        Analytics.updateProfile(props)
    }

    private fun trackOrderSaved(o: Order, isEdit: Boolean) {
        var pieces = 0; var kg = 0.0
        o.lines.forEach { if (it.kg > 0) kg += it.kg else pieces += it.qty }
        Analytics.orderSaved(
            orderId = o.id, isEdit = isEdit, hasBill = o.lines.isNotEmpty(), pickup = o.pickup.name,
            delivery = o.delivery.name, express = o.express, discount = o.discount, fee = o.fee,
            pieces = pieces, kg = kg, servicesCount = o.lines.map { it.serviceId }.distinct().size,
            total = LaundryMath.amtOf(o), quickBill = o.lines.any { it.isQuick },
        )
    }

    private fun quickLine(amount: Int, pieces: Int) = OrderLine(
        serviceId = "quick", serviceName = "Not itemised", itemName = "Clothes (not itemised)",
        qty = pieces, price = 0, base = 0, kg = 0.0, amt = amount, isQuick = true,
    )

    data class OrderFields(
        val custId: String, val pickup: Route, val delivery: Route, val pickupDate: String, val pickupTime: String,
        val deliveryDate: String, val deliveryTime: String, val ddAuto: Boolean, val fee: Int, val express: Boolean,
        val exAmt: Int, val discount: Int, val lines: List<OrderLine>,
    )
}
