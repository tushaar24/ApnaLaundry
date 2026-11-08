package com.dailyworks.apnalaundry.data.shops

import com.dailyworks.apnalaundry.data.sync.StaffContextWire

import com.dailyworks.apnalaundry.data.MAIN_SHOP

import com.dailyworks.apnalaundry.data.LaundryRepository
import com.dailyworks.apnalaundry.data.sync.PlanRequiredException
import com.dailyworks.apnalaundry.data.sync.SyncApi

/** The server side of multi-shop: the owner's shop list, and adding a shop. */
class ShopsRepository(
    private val api: SyncApi,
    private val dir: ShopDirectory,
    private val repo: LaundryRepository,
) {
    /**
     * The owner's shops from the server (app start, login), so a new phone
     * syncs every shop. Offline — or a server from before multi-shop — keeps
     * the saved list (just the main shop for everyone until they add one).
     */
    suspend fun refresh() {
        // Someone who is only staff has no shops of their own (the server would say 403).
        if (dir.ref(MAIN_SHOP)?.staff != null) return
        val server = runCatching { api.listShops() }.getOrNull() ?: return
        val local = dir.list()
        // Deleted on another phone: drop it here too (its data and file). Staff entries aren't
        // the owner's shops — they come from [refreshStaff].
        local.filter { l -> l.id != MAIN_SHOP && l.staff == null && server.none { it.id == l.id } }
            .forEach { dir.remove(it.id) }
        // The main shop's name may be blank on the server before its first sync.
        dir.save(server.map { s -> s.copy(name = s.name.ifBlank { local.firstOrNull { it.id == s.id }?.name.orEmpty() }) })
    }

    /**
     * Staff (1.0.20): where this phone works as staff, from the server (app start; login gives
     * the same list). New ones are announced ("Sharma Laundry added you as staff"), ones the
     * owner removed leave the phone. Offline / an older server: nothing changes.
     */
    suspend fun refreshStaff() {
        val res = runCatching { api.contexts(MAIN_SHOP) }.getOrNull() ?: return
        applyStaff(res.contexts, announce = true)
    }

    /** Saves the staff entries from [contexts] (login, [refreshStaff]); returns the new entries. */
    suspend fun applyStaff(contexts: List<StaffContextWire>, announce: Boolean, asMain: Boolean = false): List<ShopRef> {
        val mainStaff = dir.ref(MAIN_SHOP)?.staff
        val before = dir.list().mapNotNull { it.staff?.staffId }.toSet()
        var entries = contexts.filter { !it.token.isNullOrBlank() }.map { c ->
            ShopRef(
                id = staffShopId(c.staffId), name = c.shopName,
                staff = StaffCtx(c.staffId, c.token!!, c.name, c.ownerName, c.permissions, c.ownerPlanActive),
            )
        }
        // Only staff: the main slot keeps the shop it already holds (its data is in the main database).
        if (mainStaff != null && !asMain) {
            val main = entries.firstOrNull { it.staff?.staffId == mainStaff.staffId } ?: return emptyList() // removed: the next sync logs out
            entries = listOf(main) + entries.filter { it !== main }
        }
        dir.saveStaff(entries, mainStaff = asMain || mainStaff != null)
        val added = entries.filter { it.staff!!.staffId !in before }
        if (announce) added.forEach { dir.staffEvents.tryEmit(StaffEvent.Added(dir.ref(it.id) ?: it)) }
        return added
    }

    /**
     * Deletes an extra shop — on the server (soft; needs internet) and its data
     * on this phone. The main shop can't be deleted.
     */
    suspend fun delete(id: String) {
        api.deleteShop(id)
        dir.remove(id)
        repo.refreshTsCounter()
    }

    /**
     * Adds a shop — the server refuses without a plan ([PlanRequiredException])
     * — seeds it from the shop on screen (with its own [billPhone], if given),
     * and switches to it. Its data goes up with the next sync.
     */
    suspend fun add(name: String, billPhone: String? = null): ShopRef {
        val shop = api.addShop(name)
        dir.save(dir.list() + shop)
        repo.seedShop(shop.id, shop.name, billPhone)
        dir.switchTo(shop.id)
        repo.refreshTsCounter()
        return shop
    }
}
