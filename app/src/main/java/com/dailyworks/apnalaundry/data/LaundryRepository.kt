package com.dailyworks.apnalaundry.data

import androidx.room.withTransaction
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.I18n
import com.dailyworks.apnalaundry.core.SyncClock
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.data.local.AppDatabase
import com.dailyworks.apnalaundry.data.local.CustomerEntity
import com.dailyworks.apnalaundry.data.local.LedgerEntity
import com.dailyworks.apnalaundry.data.local.OrderEntity
import com.dailyworks.apnalaundry.data.local.ServiceEntity
import com.dailyworks.apnalaundry.data.local.ShopEntity
import com.dailyworks.apnalaundry.domain.*
import com.dailyworks.apnalaundry.data.shops.ShopDirectory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
/** [wa]: a wa.me link with a status update for the customer — a dialog offers to send it. */
data class CmdResult(val toast: String, val undo: Snapshot? = null, val wa: String? = null)

/**
 * Reads and writes the shop on screen ([ShopDirectory.current]): each shop
 * has its own database, so every command below stays the one-shop code it
 * always was, and switching shops just points it at another file.
 */
class LaundryRepository(private val shops: ShopDirectory) {
    private val db: AppDatabase get() = shops.current
    private val shopDao get() = db.shopDao()
    private val serviceDao get() = db.serviceDao()
    private val customerDao get() = db.customerDao()
    private val orderDao get() = db.orderDao()
    private val ledgerDao get() = db.ledgerDao()
    private val dayCloseDao get() = db.dayCloseDao()

    @Volatile private var tsCounter = 100_000L

    /** The shop on screen, live; follows a shop switch. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<LaundryState> = shops.currentFlow.flatMapLatest { d ->
        val shopDays = combine(d.shopDao().observe(), d.dayCloseDao().observe()) { s, days -> s to days }
        combine(
            d.serviceDao().observe(), d.customerDao().observe(), d.orderDao().observe(), d.ledgerDao().observe(), shopDays,
        ) { services, customers, orders, ledger, sd ->
            val (shopE, daysE) = sd
            stateOf(shopE, services, customers, orders, ledger, daysE)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val shopFlow: Flow<Shop?> = shops.currentFlow.flatMapLatest { d -> d.shopDao().observe().map { it?.toDomain() } }

    /** Any shop's data, read once (Earnings across shops). */
    suspend fun stateOnce(shopId: String): LaundryState {
        val d = shops.db(shopId)
        return stateOf(
            d.shopDao().get(), d.serviceDao().getAll(), d.customerDao().getAll(),
            d.orderDao().getAll(), d.ledgerDao().getAll(), d.dayCloseDao().getAll(),
        )
    }

    private fun stateOf(
        shopE: ShopEntity?, services: List<ServiceEntity>, customers: List<CustomerEntity>,
        orders: List<OrderEntity>, ledger: List<LedgerEntity>, days: List<com.dailyworks.apnalaundry.data.local.DayCloseEntity>,
    ) = LaundryState(
        shop = shopE?.toDomain() ?: SeedData.shop,
        services = services.filter { !it.deleted }.map { it.toDomain() }.sortedBy { it.sortOrder },
        customers = customers.filter { !it.deleted }.map { it.toDomain() },
        orders = orders.filter { !it.deleted }.map { it.toDomain() },
        ledger = ledger.filter { !it.deleted }.map { it.toDomain() },
        closedDays = days.filter { !it.deleted }.map { it.date }.toSet(),
    )

    /**
     * A new shop's first data, in its own database: the name, the bill
     * details of the shop on screen (not its address — another place), and a
     * copy of its services to start from. Onboarding is skipped (the owner
     * already did it once). Everything is dirty, so the next sync sends it up.
     * [billPhone]: the new shop's own number on its bills ("" = the login
     * number); null = the same as the shop on screen.
     */
    suspend fun seedShop(shopId: String, name: String, billPhone: String? = null) {
        val from = shopDao.get()
        val services = serviceDao.getAll().filter { !it.deleted }
        val now = SyncClock.now()
        val target = shops.db(shopId)
        target.withTransaction {
            val base = from ?: ShopEntity(1, name, "", SeedData.shop.expressPct, 1001, 1)
            target.shopDao().upsert(
                base.copy(
                    name = name, address = "", nextOrder = 1001, nextCust = 1,
                    billPhone = billPhone ?: base.billPhone,
                    onboardingStep = "done", updatedAt = now, dirty = true,
                )
            )
            target.serviceDao().upsertAll(services.map { it.copy(updatedAt = now, dirty = true) })
        }
    }

    /** A shop's number on its bills — its own, else the login number ("" before the shop exists here). */
    suspend fun billPhoneOf(shopId: String): String =
        shops.db(shopId).shopDao().get()?.let { it.billPhone.ifBlank { it.phone } }.orEmpty()

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
        // (rows created after the snapshot; already-deleted ones are skipped below)

        val orderTombs = tombstones(orderDao.getAll().filter { !it.deleted }, s.orders.map { it.id }.toSet(), { it.id }) {
            it.copy(deleted = true, dirty = true, updatedAt = now)
        }
        val ledgerTombs = tombstones(ledgerDao.getAll().filter { !it.deleted }, s.ledger.map { it.id }.toSet(), { it.id }) {
            it.copy(deleted = true, dirty = true, updatedAt = now)
        }
        val custTombs = tombstones(customerDao.getAll().filter { !it.deleted }, s.customers.map { it.id }.toSet(), { it.id }) {
            it.copy(deleted = true, dirty = true, updatedAt = now)
        }
        val svcTombs = tombstones(serviceDao.getAll().filter { !it.deleted }, s.services.map { it.id }.toSet(), { it.id }) {
            it.copy(deleted = true, dirty = true, updatedAt = now)
        }

        // Only rows this command changed go back (re-stamped so the rollback
        // syncs). Untouched rows keep their own updatedAt — re-stamping them
        // would let a stale copy beat a newer change from another device
        // (e.g. bring back an order deleted on the website).
        val curOrders = orderDao.getAll().associateBy { it.id }
        val curLedger = ledgerDao.getAll().associateBy { it.id }
        val curCusts = customerDao.getAll().associateBy { it.id }
        val curSvcs = serviceDao.getAll().associateBy { it.id }
        orderDao.upsertAll(s.orders.filter { it.copy(dirty = false, updatedAt = 0) != curOrders[it.id]?.copy(dirty = false, updatedAt = 0) }
            .map { it.copy(dirty = true, updatedAt = now) } + orderTombs)
        ledgerDao.insertAll(s.ledger.filter { it.copy(dirty = false, updatedAt = 0) != curLedger[it.id]?.copy(dirty = false, updatedAt = 0) }
            .map { it.copy(dirty = true, updatedAt = now) } + ledgerTombs)
        customerDao.upsertAll(s.customers.filter { it.copy(dirty = false, updatedAt = 0) != curCusts[it.id]?.copy(dirty = false, updatedAt = 0) }
            .map { it.copy(dirty = true, updatedAt = now) } + custTombs)
        serviceDao.upsertAll(s.services.filter { it.copy(dirty = false, updatedAt = 0) != curSvcs[it.id]?.copy(dirty = false, updatedAt = 0) }
            .map { it.copy(dirty = true, updatedAt = now) } + svcTombs)
        val curShop = shopDao.get()
        s.shop?.let { if (it.copy(dirty = false, updatedAt = 0) != curShop?.copy(dirty = false, updatedAt = 0)) shopDao.upsert(it.copy(dirty = true, updatedAt = now)) }
    }

    // ---------------- helpers ----------------
    /**
     * Read → change → write of the one shop row, inside a transaction. Room runs
     * transactions one at a time, so two edits that land together (the name step
     * saves the name and the next step back to back) can't write over each other.
     * Before this, the step write could put the stale placeholder name back.
     */
    private suspend fun editShop(change: (ShopEntity) -> ShopEntity?): ShopEntity? = db.withTransaction {
        val e = shopDao.get() ?: return@withTransaction null
        change(e)?.also { shopDao.upsert(it) }
    }

    private suspend fun current(): LaundryState {
        val shopE = shopDao.get()
        return LaundryState(
            shop = shopE?.toDomain() ?: SeedData.shop,
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

    /** The customer's status update as a wa.me link (for the toast's WhatsApp button). */
    private fun statusWa(st: LaundryState, o: Order): String? =
        st.customers.firstOrNull { it.id == o.custId }?.let { StatusMessage.link(st.shop.name, it, o) }

    /** Writes an existing order — never over a deleted one (it stays gone everywhere). */
    private suspend fun putOrder(e: OrderEntity) {
        if (orderDao.get(e.id)?.deleted == true) return
        orderDao.upsert(e)
    }

    // ---------------- order status ----------------
    suspend fun markPickedUp(orderId: Int): CmdResult {
        val undo = snapshot()
        val st = current()
        val o = st.orders.first { it.id == orderId }
        val nm = firstName(st.customers.first { it.id == o.custId }.name)
        val after = o.copy(status = OrderStatus.RECEIVED)
        putOrder(after.toEntity())
        Analytics.orderPickedUp(orderId)
        return CmdResult(tr("Picked up · {name}", "name" to nm), undo, statusWa(st, after))
    }

    /** Ready: the owner also says when it'll be delivered (like payment on delivery). */
    suspend fun markReady(orderId: Int, deliveryDate: String): CmdResult {
        val undo = snapshot()
        val st = current()
        val o = st.orders.first { it.id == orderId }
        val nm = firstName(st.customers.first { it.id == o.custId }.name)
        val ready = o.copy(status = OrderStatus.READY)
        val after = if (deliveryDate.isNotBlank()) ready.copy(deliveryDate = deliveryDate, ddAuto = false) else ready
        putOrder(after.toEntity())
        Analytics.orderMarkedReady(orderId)
        return CmdResult(tr("Marked ready · {name}", "name" to nm), undo, statusWa(st, after))
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
        putOrder(counted.toEntity())
        Analytics.clothesCounted(orderId, next.name, total)
        val msg = if (next == OrderStatus.READY) tr("Marked ready · {name} · bill of {amount} made — send it from the order", "name" to nm, "amount" to money(total))
        else tr("Picked up · bill of {amount} made — send it from the order", "amount" to money(total))
        return CmdResult(msg, undo, statusWa(st, counted))
    }

    suspend fun deliver(orderId: Int, amountReceived: Int, method: PayMethod): CmdResult =
        deliverSplit(orderId, if (amountReceived > 0) listOf(method to amountReceived) else emptyList())

    /**
     * Delivers with the money received in one or more parts (e.g. part cash,
     * part UPI). Each part is its own GOT entry, so Earnings' cash / UPI split
     * stays right; the allocation (this bill → old baaki → advance) is worked out
     * on the total and filled in part by part.
     */
    suspend fun deliverSplit(orderId: Int, payments: List<Pair<PayMethod, Int>>): CmdResult {
        val parts = payments.filter { it.second > 0 }
        val amountReceived = parts.sumOf { it.second }
        val method = parts.maxByOrNull { it.second }?.first ?: PayMethod.NONE
        val undo = snapshot()
        val st = current()
        val o = st.orders.first { it.id == orderId }
        // Already delivered (double tap, stale sheet): never bill twice. A
        // cancelled order may be delivered — that revives it (no BILL exists).
        if (o.status == OrderStatus.DELIVERED) return CmdResult(tr("Order #{no} is already delivered", "no" to o.no()))
        val total = LaundryMath.amtOf(o)
        val pre = o.pre
        val oldBal = LaundryMath.balance(o.custId, st.ledger, st.orders, o.id)
        val alloc = LaundryMath.deliverAllocation(total, pre, oldBal, amountReceived)
        val entries = mutableListOf<LedgerEntry>()
        entries += mkEntry(o.custId, LedgerKind.BILL, total, ref = o.id)
        var coverLeft = alloc.cover
        var oldLeft = alloc.toOld
        for ((m, amt) in parts) {
            val cover = minOf(amt, coverLeft); coverLeft -= cover
            val toOld = minOf(amt - cover, oldLeft); oldLeft -= toOld
            entries += mkEntry(o.custId, LedgerKind.GOT, amt, m, PayTag.DELIVER,
                cover = cover, toOld = toOld, toAdv = amt - cover - toOld, ref = o.id)
        }
        // Delivered today: the delivery date / time become now, so it shows under
        // today's Deliveries whatever was planned (or if no date was set).
        val now = AppDate.nowText()
        val delivered = o.copy(
            status = OrderStatus.DELIVERED, cancelReason = "", doneAt = now, doneDate = AppDate.TODAY,
            deliveryDate = AppDate.TODAY, deliveryTime = now, ddAuto = false, paid = alloc.paidToward,
        )
        db.withTransaction {
            ledgerDao.insertAll(entries.map { it.toEntity() })
            // Delivered today: the delivery date / time become now, so it shows under
            // today's Deliveries whatever was planned (or if no date was set).
            putOrder(delivered.toEntity())
        }
        Analytics.orderDelivered(
            orderId = o.id, total = total, amountReceived = amountReceived, method = method,
            toKhata = maxOf(0, total - alloc.paidToward), fromAdvance = pre, lines = o.lines,
        )
        parts.forEach { (m, amt) -> Analytics.paymentReceived(amt, m, "delivery", o.custId, o.id) }
        val paidPart = parts.joinToString(" + ") { (m, amt) ->
            val rs = com.dailyworks.apnalaundry.core.Money.rupees(amt)
            if (m == PayMethod.UPI) tr("{amount} UPI", "amount" to rs) else tr("{amount} cash", "amount" to rs)
        }
        val balPart = when {
            alloc.newBalance > 0 -> tr("{amount} baaki", "amount" to money(alloc.newBalance))
            alloc.newBalance < 0 -> tr("{amount} advance", "amount" to money(alloc.newBalance))
            else -> tr("all clear")
        }
        val msg = if (parts.isEmpty()) tr("Delivered · {balance}", "balance" to balPart)
        else tr("Delivered · {paid} · {balance}", "paid" to paidPart, "balance" to balPart)
        return CmdResult(msg, undo, statusWa(st, delivered))
    }

    suspend fun prepay(orderId: Int, method: PayMethod): CmdResult {
        val undo = snapshot()
        val st = current()
        val o = st.orders.first { it.id == orderId }
        // Only what's still unpaid, and only before delivery — never charge twice.
        val due = LaundryMath.amtOf(o) - o.pre
        if (!LaundryMath.isOpen(o) || due <= 0) return CmdResult(tr("Nothing left to collect on order #{no}", "no" to o.no()))
        db.withTransaction {
            ledgerDao.insert(mkEntry(o.custId, LedgerKind.GOT, due, method, PayTag.PRE, cover = due, ref = o.id).toEntity())
            putOrder(o.copy(pre = o.pre + due).toEntity())
        }
        Analytics.paymentReceived(due, method, "prepay", o.custId, o.id)
        return CmdResult(
            if (method == PayMethod.UPI) tr("Got {amount} UPI · order fully paid", "amount" to money(due))
            else tr("Got {amount} cash · order fully paid", "amount" to money(due)),
            undo,
        )
    }

    /**
     * Part (or full) payment on an order before delivery (the assistant: "Ramesh
     * ne 200 de diye"). Up to what's still due goes on the order as paid in
     * advance (a PRE entry, like [prepay]); anything over it is a khata payment —
     * old baaki first, then advance.
     */
    suspend fun prepayPart(orderId: Int, amount: Int, method: PayMethod): CmdResult {
        val undo = snapshot()
        val st = current()
        val o = st.orders.first { it.id == orderId }
        val due = LaundryMath.amtOf(o) - o.pre
        if (!LaundryMath.isOpen(o) || amount <= 0) return CmdResult(tr("Nothing to record on order #{no}", "no" to o.no()))
        val onOrder = minOf(amount, maxOf(0, due))
        val extra = amount - onOrder
        db.withTransaction {
            if (onOrder > 0) {
                ledgerDao.insert(mkEntry(o.custId, LedgerKind.GOT, onOrder, method, PayTag.PRE, cover = onOrder, ref = o.id).toEntity())
                putOrder(o.copy(pre = o.pre + onOrder).toEntity())
            }
            if (extra > 0) {
                // The order itself is paid off, so its total no longer counts against the rest.
                val oldBal = LaundryMath.balance(o.custId, st.ledger, st.orders, o.id)
                val (toOld, toAdv) = LaundryMath.receiveAllocation(oldBal, extra)
                ledgerDao.insert(mkEntry(o.custId, LedgerKind.GOT, extra, method, PayTag.RECEIVE, toOld = toOld, toAdv = toAdv).toEntity())
            }
        }
        if (onOrder > 0) Analytics.paymentReceived(onOrder, method, "prepay", o.custId, o.id)
        if (extra > 0) Analytics.paymentReceived(extra, method, "khata", o.custId, null)
        val left = due - onOrder
        val got = if (method == PayMethod.UPI) tr("Got {amount} UPI", "amount" to money(amount)) else tr("Got {amount} cash", "amount" to money(amount))
        val onOrderPart = if (left > 0) tr("order #{no} {amount} still due", "no" to o.no(), "amount" to money(left))
        else tr("order #{no} fully paid", "no" to o.no())
        return CmdResult(
            "$got · $onOrderPart" + (if (extra > 0) " · " + tr("{amount} to khata", "amount" to money(extra)) else ""),
            undo,
        )
    }

    suspend fun cancelOrder(orderId: Int, reason: String): CmdResult {
        val undo = snapshot()
        val st = current()
        val o = st.orders.first { it.id == orderId }
        val nm = firstName(st.customers.first { it.id == o.custId }.name)
        val after = db.withTransaction {
            val base = if (o.status == OrderStatus.DELIVERED) reverseDelivery(o) else o
            base.copy(status = OrderStatus.CANCELLED, cancelReason = reason).also { putOrder(it.toEntity()) }
        }
        Analytics.orderCancelled(orderId, reason)
        return CmdResult(
            if (o.status == OrderStatus.CREATED) tr("Pickup cancelled · {name}", "name" to nm) else tr("Order cancelled · {name}", "name" to nm),
            undo, statusWa(st, after),
        )
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
        if (o.status == target) return CmdResult(tr("Order #{no} is already there", "no" to o.no()))
        // Delivering bills and collects — that path is deliver(), never this.
        if (target == OrderStatus.DELIVERED) return CmdResult(tr("Use Mark delivered for order #{no}", "no" to o.no()))
        val nm = firstName(st.customers.first { it.id == o.custId }.name)
        val unDelivered = o.status == OrderStatus.DELIVERED
        val after = db.withTransaction {
            val base = if (unDelivered) reverseDelivery(o) else o
            base.copy(status = target, cancelReason = "").also { putOrder(it.toEntity()) }
        }
        Analytics.orderStatusChanged(orderId, o.status.name, target.name)
        val word = when (target) {
            OrderStatus.CREATED -> tr("To pick up"); OrderStatus.RECEIVED -> tr("Received")
            OrderStatus.READY -> tr("Ready"); OrderStatus.DELIVERED -> tr("Delivered"); OrderStatus.CANCELLED -> tr("Cancelled")
        }
        val msg = if (unDelivered) tr("Moved to {status} · {name} · bill taken off the khata", "status" to word, "name" to nm)
        else tr("Moved to {status} · {name}", "status" to word, "name" to nm)
        return CmdResult(msg, undo, statusWa(st, after))
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
            // "Last order …" follows the newest order still there (or none).
            val latest = st.orders.filter { it.custId == o.custId && it.id != orderId }.maxByOrNull { it.createdOn }
            customerDao.get(o.custId)?.let { c ->
                val (label, rank) = when {
                    latest == null -> "—" to 9
                    latest.createdOn == AppDate.TODAY -> "Today" to 0
                    // Stored (and synced) — stays English whatever the app's language.
                    else -> I18n.english { AppDate.plain(latest.createdOn) } to AppDate.daysBetween(latest.createdOn, AppDate.TODAY).coerceIn(1, 8)
                }
                if (c.lastLabel != label || c.agoRank != rank) customerDao.upsert(c.copy(lastLabel = label, agoRank = rank, dirty = true, updatedAt = now))
            }
        }
        return CmdResult(tr("Bill #{no} deleted · {name}", "no" to o.no(), "name" to nm), undo)
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
            val date = AppDate.short(dateIso)
            u to if (time12.isNotBlank()) tr("Pickup moved to {date} · {time}", "date" to date, "time" to time12)
            else tr("Pickup moved to {date}", "date" to date)
        } else {
            val u = o.copy(deliveryDate = dateIso, deliveryTime = time12, ddAuto = false)
            val date = AppDate.short(dateIso)
            u to if (o.deliveryDate.isNotBlank()) {
                if (time12.isNotBlank()) tr("Delivery moved to {date} · {time}", "date" to date, "time" to time12)
                else tr("Delivery moved to {date}", "date" to date)
            } else {
                if (time12.isNotBlank()) tr("Delivery date set: {date} · {time}", "date" to date, "time" to time12)
                else tr("Delivery date set: {date}", "date" to date)
            }
        }
        putOrder(updated.toEntity())
        Analytics.orderRescheduled(orderId, if (kind == "pickup") "pickup" else "delivery", notify)
        return CmdResult(msg, undo)
    }

    suspend fun sendBill(orderId: Int): CmdResult {
        val st = current()
        val o = st.orders.first { it.id == orderId }
        val nm = firstName(st.customers.first { it.id == o.custId }.name)
        putOrder(o.copy(billSent = true).toEntity())
        Analytics.billSent(orderId)
        val no = current().orders.firstOrNull { it.id == orderId }?.no() ?: orderId
        return CmdResult(tr("Opening WhatsApp · bill #{no} to {name}", "no" to no, "name" to nm))
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
            after > 0 -> tr("{amount} still baaki", "amount" to money(after))
            after < 0 -> tr("{amount} advance", "amount" to money(after))
            else -> tr("all clear")
        }
        return CmdResult(
            if (method == PayMethod.UPI) tr("Got {amount} UPI · {balance}", "amount" to money(amount), "balance" to tail)
            else tr("Got {amount} cash · {balance}", "amount" to money(amount), "balance" to tail),
            undo,
        )
    }

    suspend fun addOldBaaki(custId: String, amount: Int): CmdResult {
        val undo = snapshot()
        ledgerDao.insert(mkEntry(custId, LedgerKind.OLD, amount).toEntity())
        Analytics.oldBaakiAdded(amount, custId)
        return CmdResult(tr("Added {amount} old baaki", "amount" to money(amount)), undo)
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
        val id = db.withTransaction {
            val shopE = shopDao.get()!!
            val id = "c${shopE.nextCust}"
            customerDao.upsert(Customer(id, name, phone, address, 0, "—", 9).toEntity())
            shopDao.upsert(shopE.copy(nextCust = shopE.nextCust + 1, dirty = true, updatedAt = SyncClock.now()))
            id
        }
        if (oldBaaki > 0) ledgerDao.insert(mkEntry(id, LedgerKind.OLD, oldBaaki).toEntity())
        Analytics.customerAdded(if (fromList) "list" else "order", oldBaaki > 0)
        val res = if (fromList)
            CmdResult(
                if (oldBaaki > 0) tr("{name} added with {amount} baaki", "name" to name, "amount" to money(oldBaaki))
                else tr("{name} added", "name" to name),
                undo,
            )
        else null
        return id to res
    }

    // ---------------- rate card ----------------
    suspend fun upsertService(service: Service) = serviceDao.upsert(service.toEntity())

    /**
     * Services-from-scratch setup: drop the seeded sample services while their
     * prices are still the samples (the owner never set them). Quiet: no
     * "Service Deleted" events, so the A/B's edit counts stay the owner's own.
     */
    suspend fun removeSampleServices() {
        val current = serviceDao.getAll().filter { !it.deleted }.map { it.toDomain() }
        if (!SeedData.ratesUnchanged(current)) return
        val sampleIds = SeedData.services.map { it.id }.toSet()
        current.filter { it.id in sampleIds }.forEach { serviceDao.setDeleted(it.id, true, SyncClock.now()) }
    }

    /** [allowEmpty]: the services-from-scratch group may delete its last service (New order then offers to add one). */
    suspend fun deleteService(id: String, allowEmpty: Boolean = false): CmdResult {
        val undo = snapshot()
        val services = serviceDao.getAll().filter { !it.deleted }
        if (services.size <= 1 && !allowEmpty) return CmdResult(tr("Keep at least one service"))
        val sv = services.first { it.id == id }
        serviceDao.setDeleted(id, true, SyncClock.now())
        Analytics.serviceDeleted(id)
        return CmdResult(tr("{service} deleted · old orders keep it", "service" to sv.name), undo)
    }

    suspend fun updateShop(name: String, expressPct: Int) {
        editShop { it.copy(name = name, expressPct = expressPct, dirty = true, updatedAt = SyncClock.now()) }
        if (name.isNotBlank()) Analytics.updateProfile(mapOf("Name" to name))
    }

    /** Saves the shop name and/or bill details (onboarding "Your bill", Settings). */
    suspend fun updateShopDetails(name: String? = null, details: BillDetails.Fields? = null) {
        editShop { e ->
            var n = e.copy(dirty = true, updatedAt = SyncClock.now())
            if (name != null) n = n.copy(name = name)
            if (details != null) n = n.copy(
                billPhone = details.billPhone, address = details.address, gstin = details.gstin, email = details.email,
                upiId = details.upiId, logoId = details.logoId, termsJson = encodeTerms(details.terms),
                termsCustom = details.termsCustom, billTemplate = details.billTemplate,
            )
            n
        } ?: return
        if (!name.isNullOrBlank()) Analytics.updateProfile(mapOf("Name" to name))
    }

    /**
     * The last-used GST setting. Every change on a NEW order saves it straight
     * away, so the next order opens the same way (edits of an old order don't).
     */
    suspend fun setGstDefaults(on: Boolean, pct: Double, mode: String) {
        editShop { e ->
            if (e.gstOn == on && e.gstPct == pct && e.gstMode == mode) null
            else e.copy(gstOn = on, gstPct = pct, gstMode = mode, dirty = true, updatedAt = SyncClock.now())
        }
    }

    /** Records onboarding progress so a killed app (or another device) resumes there. */
    suspend fun setOnboardingStep(step: String) {
        editShop { e ->
            if (e.onboardingStep == step) null
            else e.copy(onboardingStep = step, dirty = true, updatedAt = SyncClock.now())
        }
    }

    suspend fun onboardingStep(): String = shopDao.get()?.onboardingStep ?: ""

    /** Orders the shop has now, deleted ones not counted (for the free-orders limit). */
    suspend fun liveOrderCount(): Int = orderDao.countLive()

    /** Orders not deleted across every shop (the free orders are per owner, not per shop). */
    suspend fun liveOrderCountAllShops(): Int = shops.list().sumOf { shops.db(it.id).orderDao().countLive() }

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
        note: String = "",
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
                gstOn = gstOn && gstPct > 0, gstPct = gstPct, gstMode = gstMode, note = note.trim().take(500),
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
                putOrder(updated.toEntity())
                if (o.status == OrderStatus.DELIVERED && diff != 0) {
                    ledgerDao.insert(mkEntry(o.custId, LedgerKind.ADJ, diff, ref = o.id).toEntity())
                }
            }
            val toast = when {
                diff == 0 -> tr("Order #{no} updated", "no" to updated.no())
                o.status == OrderStatus.DELIVERED -> tr("Order #{no} updated · new total {amount} · khata {change}",
                    "no" to updated.no(), "amount" to money(after),
                    "change" to (if (diff > 0) "+" else "−") + money(kotlin.math.abs(diff)),
                )
                else -> tr("Order #{no} updated · new total {amount}", "no" to updated.no(), "amount" to money(after))
            }
            trackOrderSaved(updated, isEdit = true)
            return SaveOrderResult(o.id, goToBill = false, toast = toast, undo = undo, edited = true)
        }

        // create
        val status = if (pickup == Route.SHOP) OrderStatus.RECEIVED else OrderStatus.CREATED
        // The number is taken and bumped in one transaction, with the shop read
        // inside it, so a concurrent shop edit is never written over.
        val order = db.withTransaction {
            val shopE = shopDao.get()!!
            val id = shopE.nextOrder
            val order = Order(
                id = id, custId = fields.custId, pickup = fields.pickup, delivery = fields.delivery,
                pickupDate = fields.pickupDate, pickupTime = fields.pickupTime, deliveryDate = fields.deliveryDate,
                deliveryTime = fields.deliveryTime, ddAuto = fields.ddAuto, status = status, cancelReason = "",
                fee = fields.fee, express = fields.express, exAmt = fields.exAmt, discount = fields.discount,
                pre = 0, paid = 0, doneAt = "", doneDate = "", createdOn = AppDate.TODAY, billSent = false,
                pieces = 0, lines = fields.lines,
                serialNo = serialNo.trim(), exPct = if (fields.express) exPct else 0, discPct = discPct,
                gstOn = gstOn && gstPct > 0, gstPct = gstPct, gstMode = gstMode, note = note.trim().take(500),
            )
            orderDao.upsert(order.toEntity())
            shopDao.upsert(shopE.copy(nextOrder = id + 1, dirty = true, updatedAt = SyncClock.now()))
            touchCustomer(custId)
            order
        }
        val id = order.id
        val count = orderDao.countLive()
        trackOrderSaved(order, isEdit = false, ordersCount = count)
        trackOrderMilestone(count)
        return if (fields.lines.isEmpty()) {
            val toast = if (pickup == Route.HOME) {
                if (order.pickupTime.isNotBlank()) tr("Pickup scheduled for {name} · {time}", "name" to nm, "time" to order.pickupTime)
                else tr("Pickup scheduled for {name}", "name" to nm)
            } else tr("Order #{no} saved for {name} · add clothes when ready", "no" to order.no(), "name" to nm)
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
    private fun trackOrderMilestone(count: Int) {
        val today = java.time.LocalDate.now().toString()
        val props = mutableMapOf<String, Any>("orders_count" to count, "last_order_date" to today)
        if (count == 1) props["first_order_date"] = today
        Analytics.updateProfile(props)
    }

    private fun trackOrderSaved(o: Order, isEdit: Boolean, ordersCount: Int? = null) {
        var pieces = 0; var kg = 0.0
        o.lines.forEach { if (it.kg > 0) kg += it.kg else pieces += it.qty }
        Analytics.orderSaved(
            orderId = o.id, isEdit = isEdit, hasBill = o.lines.isNotEmpty(), pickup = o.pickup.name,
            delivery = o.delivery.name, express = o.express, discount = o.discount, fee = o.fee,
            pieces = pieces, kg = kg, servicesCount = o.lines.map { it.serviceId }.distinct().size,
            total = LaundryMath.amtOf(o), quickBill = o.lines.any { it.isQuick }, ordersCount = ordersCount,
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
