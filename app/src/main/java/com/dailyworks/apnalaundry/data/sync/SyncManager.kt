package com.dailyworks.apnalaundry.data.sync

import androidx.room.withTransaction
import com.dailyworks.apnalaundry.data.LaundryRepository
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.local.AppDatabase
import com.dailyworks.apnalaundry.data.shops.ShopDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The offline-first sync engine. The Room DB stays the source of truth for
 * the UI; this pushes dirty rows and pulls server changes when online.
 *
 * One sync = push then pull (sync v2, 1.0.20; docs/specs/staff.md):
 *  - push: send every dirty row; on success clear the dirty flag, guarded by
 *    `updatedAt` so rows edited mid-flight stay dirty. The last push to reach the
 *    server wins. Rows it kept its own copy of (`refused`) are put back here.
 *  - pull?afterSeq=<the shop's server sequence>: every row the server wrote since,
 *    applied unless the local row still has unsent changes (those go up next round).
 *    Before the first sequence (or against an older server) it pulls by the time
 *    checkpoint, as before.
 *
 * Multi-shop: every shop syncs — the one on screen first — each with its own
 * database, checkpoint and X-Laundry-Shop; one shop failing doesn't stop the
 * others. A one-shop owner syncs exactly as before (main shop, no header).
 */
class SyncManager(
    private val shops: ShopDirectory,
    private val prefs: Prefs,
    private val api: SyncApi,
    private val repo: LaundryRepository,
    private val idPool: IdPool? = null,
) {
    sealed interface Result {
        data object Success : Result
        data object Skipped : Result
        /** [authDead]: the session itself is gone (not a transient network failure). */
        data class Error(val message: String, val authDead: Boolean = false) : Result
    }

    private val mutex = Mutex()

    /**
     * [block] with no sync running: a wipe (logout, another account) must not race a pull still in
     * flight, which would write the old account's rows and sync positions back after it.
     */
    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }

    suspend fun syncNow(): Result = runLocked {
        forEachShop { pushOnce(it); pullOnce(it); topUpIds(it) }
        // A staff member's number switch changed: the re-pull it needs, right away.
        if (sync2Pending) { sync2Pending = false; forEachShop { pullOnce(it) } }
    }
    @Volatile private var sync2Pending = false

    /** Staff (1.0.20): keeps this phone's reserved order / customer numbers topped up. */
    private suspend fun topUpIds(shop: String) {
        val s = shops.db(shop).shopDao().get() ?: return
        idPool?.topUp(shop, s.nextOrder, s.nextCust)
    }

    /**
     * Push only — for a brand-new account right after seeding, where a pull
     * can't return anything and would only add a round trip.
     */
    suspend fun pushNow(): Result = runLocked { forEachShop { pushOnce(it) } }

    /** The shop on screen first; a failing shop doesn't stop the rest (its error is reported). */
    private suspend fun forEachShop(work: suspend (String) -> Unit) {
        val ids = (listOf(shops.activeId.value) + shops.list().map { it.id }).distinct()
        var failed: Exception? = null
        for (id in ids) {
            try {
                work(id)
            } catch (e: NotLoggedInException) {
                throw e
            } catch (e: StaffRemovedException) {
                // Staff (1.0.20): the owner removed this phone from that shop — it leaves the phone.
                val name = shops.ref(e.shop)?.name.orEmpty()
                shops.remove(e.shop)
                shops.staffEvents.tryEmit(com.dailyworks.apnalaundry.data.shops.StaffEvent.Removed(name))
            } catch (e: OwnerPlanEndedException) {
                // Not an error: the shop shows "ask the owner to renew" until the plan is back.
                shops.updateStaff(e.shop) { it.copy(ownerPlanActive = false) }
            } catch (e: Exception) {
                if (failed == null) failed = e
            }
        }
        failed?.let { throw it }
    }

    private suspend fun runLocked(work: suspend () -> Unit): Result = mutex.withLock {
        if (!prefs.loggedIn.first()) return Result.Skipped
        try {
            work()
            Result.Success
        } catch (e: NotLoggedInException) {
            // Staff (1.0.20): a phone that was only someone's staff — its login died because the owner
            // removed it (or deleted the shop). That shop's data leaves the phone now, not at the next login.
            val main = shops.list().firstOrNull()
            if (main?.staff != null) {
                shops.forgetExtraShops()
                repo.clearAll()
                prefs.resetSyncState()
                prefs.setSetupDone(false)
                shops.staffEvents.tryEmit(com.dailyworks.apnalaundry.data.shops.StaffEvent.Removed(main.name))
            }
            Result.Error("Not logged in", authDead = true)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Sync failed")
        }
    }

    /**
     * [skipLocked]: leave out staff shops whose owner's plan has ended — their changes can't go up
     * until the owner renews, so they mustn't block a logout.
     */
    suspend fun hasPendingChanges(skipLocked: Boolean = false): Boolean =
        shops.list().filter { !skipLocked || it.staff?.planOn() != false }.any { hasPendingChanges(shops.db(it.id)) }

    private suspend fun hasPendingChanges(db: AppDatabase): Boolean {
        val shopDirty = db.shopDao().get()?.dirty == true
        return shopDirty
            || db.serviceDao().dirtyRows().isNotEmpty()
            || db.customerDao().dirtyRows().isNotEmpty()
            || db.orderDao().dirtyRows().isNotEmpty()
            || db.ledgerDao().dirtyRows().isNotEmpty()
            || db.dayCloseDao().dirtyRows().isNotEmpty()
            || db.expenseDao().dirtyRows().isNotEmpty()
    }

    private suspend fun pullOnce(shop: String) {
        // Once per shop after the update that added expenses: pull from the start, since older
        // builds (or this one before the update) moved the checkpoint past expenses they ignored.
        // A database with no shop row has nothing yet (new staff shop, wiped data): everything,
        // whatever the saved positions say.
        val fresh = shops.db(shop).shopDao().get() == null
        val since = if (prefs.expensesPulled(shop) && !fresh) prefs.syncCheckpoint(shop) else 0L
        // Sync v2: by the server sequence once there is one; the first pull (and any pull from a
        // server before sync v2, which sends no seq) goes by the time checkpoint.
        val seq = if (prefs.expensesPulled(shop) && !fresh) prefs.syncSeq(shop) else 0L
        val res = api.pull(since, shop, afterSeq = seq)
        if (!res.success) throw IllegalStateException(res.message ?: "Pull failed")
        applyPull(shops.db(shop), res.changes, onScreen = shop == shops.activeId.value)
        // A staff login: the owner's current switches (and the plan is on, or the pull would be 402).
        var phonesChanged = false
        res.staff?.let { st ->
            val before = shops.ref(shop)?.staff
            phonesChanged = before != null && (before.permissions["see_phones"] == true) != (st.permissions["see_phones"] == true)
            shops.updateStaff(shop) {
                it.copy(permissions = st.permissions, ownerPlanActive = st.ownerPlanActive, ownerPlanUntil = st.ownerPlanUntil, name = st.name.ifBlank { it.name })
            }
        }
        // An owner's shop: whether it has staff (then it numbers orders from reserved blocks).
        if (shops.ref(shop)?.staff == null) res.hasStaff?.let { prefs.setShopHasStaff(shop, it) }
        prefs.setSyncCheckpoint(shop, res.checkpoint)
        if (res.seq > 0) prefs.setSyncSeq(shop, res.seq)
        prefs.setExpensesPulled(shop)
        if (phonesChanged) {
            // Customer numbers shown / hidden now: pull every customer again (the server masks them).
            prefs.setSyncCheckpoint(shop, 0L)
            prefs.setSyncSeq(shop, 0L)
            sync2Pending = true
        }
        if (shop == shops.activeId.value) repo.refreshTsCounter() // pulled ledger rows may carry higher ts values
    }

    /** Fires after a pull applies another device's changes (ends a pending Undo). */
    val remoteChanges = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Server rows over local ones — except a local row with unsent changes, which goes up next
     * round (and, being pushed last, wins). [newer]: a row really changed elsewhere, not our own
     * push coming back.
     */
    private suspend fun applyPull(db: AppDatabase, ch: SyncChanges, onScreen: Boolean) {
        if (ch.isEmpty) return
        val newer = applyRows(db, ch)
        if (newer && onScreen) remoteChanges.tryEmit(Unit)
    }

    /** Writes [ch] into [db] (skipping locally dirty rows); whether any row was newer than ours. */
    private suspend fun applyRows(db: AppDatabase, ch: SyncChanges, overDirty: Boolean = false): Boolean {
        var newer = false
        fun take(localDirty: Boolean?, localUpdatedAt: Long?, remote: Long): Boolean {
            if (localDirty == true && !overDirty) return false
            if (localUpdatedAt == null || remote > localUpdatedAt) newer = true
            return true
        }
        db.withTransaction {
            ch.shop?.let { dto ->
                val local = db.shopDao().get()
                if (take(local?.dirty, local?.updatedAt, dto.updatedAt)) db.shopDao().upsert(dto.toEntity())
            }
            for (dto in ch.services) {
                val local = db.serviceDao().get(dto.id)
                if (take(local?.dirty, local?.updatedAt, dto.updatedAt)) db.serviceDao().upsert(dto.toEntity())
            }
            for (dto in ch.customers) {
                val local = db.customerDao().get(dto.id)
                if (take(local?.dirty, local?.updatedAt, dto.updatedAt)) db.customerDao().upsert(dto.toEntity())
            }
            for (dto in ch.orders) {
                val local = db.orderDao().get(dto.id)
                if (take(local?.dirty, local?.updatedAt, dto.updatedAt)) db.orderDao().upsert(dto.toEntity())
            }
            for (dto in ch.ledger) {
                val local = db.ledgerDao().get(dto.id)
                if (take(local?.dirty, local?.updatedAt, dto.updatedAt)) db.ledgerDao().insert(dto.toEntity())
            }
            for (dto in ch.dayCloses) {
                val local = db.dayCloseDao().get(dto.date)
                if (take(local?.dirty, local?.updatedAt, dto.updatedAt)) db.dayCloseDao().upsert(dto.toEntity())
            }
            for (dto in ch.expenses) {
                val local = db.expenseDao().get(dto.id)
                if (take(local?.dirty, local?.updatedAt, dto.updatedAt)) db.expenseDao().upsert(dto.toEntity())
            }
        }
        return newer
    }

    private suspend fun pushOnce(shopId: String) {
        val db = shops.db(shopId)
        val shop = db.shopDao().get()?.takeIf { it.dirty }
        val services = db.serviceDao().dirtyRows()
        val customers = db.customerDao().dirtyRows()
        val orders = db.orderDao().dirtyRows()
        val ledger = db.ledgerDao().dirtyRows()
        val dayCloses = db.dayCloseDao().dirtyRows()
        val expenses = db.expenseDao().dirtyRows()

        val changes = SyncChanges(
            shop = shop?.toDto(),
            services = services.map { it.toDto() },
            customers = customers.map { it.toDto() },
            orders = orders.map { it.toDto() },
            ledger = ledger.map { it.toDto() },
            dayCloses = dayCloses.map { it.toDto() },
            expenses = expenses.map { it.toDto() },
        )
        if (changes.isEmpty) return

        val res = api.push(changes, shopId)
        if (!res.success) throw IllegalStateException(res.message ?: "Push failed")

        // Clear dirty flags, each guarded by updatedAt so anything edited
        // while the push was in flight stays dirty for the next round.
        shop?.let { db.shopDao().clearDirty(it.updatedAt) }
        services.forEach { db.serviceDao().clearDirty(it.id, it.updatedAt) }
        customers.forEach { db.customerDao().clearDirty(it.id, it.updatedAt) }
        orders.forEach { db.orderDao().clearDirty(it.id, it.updatedAt) }
        ledger.forEach { db.ledgerDao().clearDirty(it.id, it.updatedAt) }
        dayCloses.forEach { db.dayCloseDao().clearDirty(it.date, it.updatedAt) }
        expenses.forEach { db.expenseDao().clearDirty(it.id, it.updatedAt) }

        // Sync v2: rows the server kept its own copy of — put that copy back here. Not the shop row:
        // a staff phone's counters always come back, and the next pull brings the shop anyway.
        val refused = refusedChanges(res.refused).copy(shop = null)
        if (!refused.isEmpty) {
            applyRows(db, refused, overDirty = true)
            if (shops.ref(shopId)?.staff != null) {
                val rows = refused.services.size + refused.customers.size + refused.orders.size + refused.ledger.size +
                    refused.dayCloses.size + refused.expenses.size
                if (rows > 0) com.dailyworks.apnalaundry.analytics.Analytics.staffChangesRefused(rows)
            }
            if (shopId == shops.activeId.value) remoteChanges.tryEmit(Unit)
        }
    }
}
