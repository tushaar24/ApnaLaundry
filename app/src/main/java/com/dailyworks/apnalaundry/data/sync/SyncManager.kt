package com.dailyworks.apnalaundry.data.sync

import androidx.room.withTransaction
import com.dailyworks.apnalaundry.data.LaundryRepository
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The offline-first sync engine. The Room DB stays the source of truth for
 * the UI; this pushes dirty rows and pulls server changes when online.
 *
 * One sync = pull then push:
 *  - pull?since=<checkpoint>: apply each server row unless the local copy has
 *    a newer `updatedAt` (same last-write-wins rule the server uses on push,
 *    so a dirty local edit is never clobbered by an older server copy).
 *  - push: send every dirty row; on success clear the dirty flag, guarded by
 *    `updatedAt` so rows edited mid-flight stay dirty.
 *
 * Known limits (single-device-per-account assumption, documented in
 * docs/specs): shop counters merge last-write-wins, and two devices minting
 * ids concurrently can collide.
 */
class SyncManager(
    private val db: AppDatabase,
    private val prefs: Prefs,
    private val api: SyncApi,
    private val repo: LaundryRepository,
) {
    sealed interface Result {
        data object Success : Result
        data object Skipped : Result
        /** [authDead]: the session itself is gone (not a transient network failure). */
        data class Error(val message: String, val authDead: Boolean = false) : Result
    }

    private val mutex = Mutex()

    suspend fun syncNow(): Result = runLocked {
        pullOnce()
        pushOnce()
    }

    /**
     * Push only — for a brand-new account right after seeding, where a pull
     * can't return anything and would only add a round trip.
     */
    suspend fun pushNow(): Result = runLocked { pushOnce() }

    private suspend fun runLocked(work: suspend () -> Unit): Result = mutex.withLock {
        if (!prefs.loggedIn.first()) return Result.Skipped
        try {
            work()
            Result.Success
        } catch (e: NotLoggedInException) {
            Result.Error("Not logged in", authDead = true)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Sync failed")
        }
    }

    suspend fun hasPendingChanges(): Boolean {
        val shopDirty = db.shopDao().get()?.dirty == true
        return shopDirty
            || db.serviceDao().dirtyRows().isNotEmpty()
            || db.customerDao().dirtyRows().isNotEmpty()
            || db.orderDao().dirtyRows().isNotEmpty()
            || db.ledgerDao().dirtyRows().isNotEmpty()
            || db.dayCloseDao().dirtyRows().isNotEmpty()
    }

    private suspend fun pullOnce() {
        val since = prefs.lastSyncCheckpoint.first()
        val res = api.pull(since)
        if (!res.success) throw IllegalStateException(res.message ?: "Pull failed")
        applyPull(res.changes)
        prefs.setLastSyncCheckpoint(res.checkpoint)
        repo.refreshTsCounter() // pulled ledger rows may carry higher ts values
    }

    /** Fires after a pull applies another device's changes (ends a pending Undo). */
    val remoteChanges = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private suspend fun applyPull(ch: SyncChanges) {
        if (ch.isEmpty) return
        // Whether any pulled row was newer than ours — not just our own push echoed back.
        var newer = false
        db.withTransaction {
            ch.shop?.let { dto ->
                val local = db.shopDao().get()
                if (local == null || dto.updatedAt > local.updatedAt) { db.shopDao().upsert(dto.toEntity()); newer = true }
            }
            for (dto in ch.services) {
                val local = db.serviceDao().get(dto.id)
                if (local == null || dto.updatedAt > local.updatedAt) { db.serviceDao().upsert(dto.toEntity()); newer = true }
            }
            for (dto in ch.customers) {
                val local = db.customerDao().get(dto.id)
                if (local == null || dto.updatedAt > local.updatedAt) { db.customerDao().upsert(dto.toEntity()); newer = true }
            }
            for (dto in ch.orders) {
                val local = db.orderDao().get(dto.id)
                if (local == null || dto.updatedAt > local.updatedAt) { db.orderDao().upsert(dto.toEntity()); newer = true }
            }
            for (dto in ch.ledger) {
                val local = db.ledgerDao().get(dto.id)
                if (local == null || dto.updatedAt > local.updatedAt) { db.ledgerDao().insert(dto.toEntity()); newer = true }
            }
            for (dto in ch.dayCloses) {
                val local = db.dayCloseDao().get(dto.date)
                if (local == null || dto.updatedAt > local.updatedAt) { db.dayCloseDao().upsert(dto.toEntity()); newer = true }
            }
        }
        if (newer) remoteChanges.tryEmit(Unit)
    }

    private suspend fun pushOnce() {
        val shop = db.shopDao().get()?.takeIf { it.dirty }
        val services = db.serviceDao().dirtyRows()
        val customers = db.customerDao().dirtyRows()
        val orders = db.orderDao().dirtyRows()
        val ledger = db.ledgerDao().dirtyRows()
        val dayCloses = db.dayCloseDao().dirtyRows()

        val changes = SyncChanges(
            shop = shop?.toDto(),
            services = services.map { it.toDto() },
            customers = customers.map { it.toDto() },
            orders = orders.map { it.toDto() },
            ledger = ledger.map { it.toDto() },
            dayCloses = dayCloses.map { it.toDto() },
        )
        if (changes.isEmpty) return

        val res = api.push(changes)
        if (!res.success) throw IllegalStateException(res.message ?: "Push failed")

        // Clear dirty flags, each guarded by updatedAt so anything edited
        // while the push was in flight stays dirty for the next round.
        shop?.let { db.shopDao().clearDirty(it.updatedAt) }
        services.forEach { db.serviceDao().clearDirty(it.id, it.updatedAt) }
        customers.forEach { db.customerDao().clearDirty(it.id, it.updatedAt) }
        orders.forEach { db.orderDao().clearDirty(it.id, it.updatedAt) }
        ledger.forEach { db.ledgerDao().clearDirty(it.id, it.updatedAt) }
        dayCloses.forEach { db.dayCloseDao().clearDirty(it.date, it.updatedAt) }
    }
}
