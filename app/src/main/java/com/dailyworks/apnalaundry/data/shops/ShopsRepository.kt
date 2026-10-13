package com.dailyworks.apnalaundry.data.shops

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
        val server = runCatching { api.listShops() }.getOrNull() ?: return
        val local = dir.list()
        // Deleted on another phone: drop it here too (its data and file).
        local.filter { l -> l.id != com.dailyworks.apnalaundry.data.MAIN_SHOP && server.none { it.id == l.id } }
            .forEach { dir.remove(it.id) }
        // The main shop's name may be blank on the server before its first sync.
        dir.save(server.map { s -> s.copy(name = s.name.ifBlank { local.firstOrNull { it.id == s.id }?.name.orEmpty() }) })
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
     * — seeds it from the shop on screen, and switches to it. Its data goes up
     * with the next sync.
     */
    suspend fun add(name: String): ShopRef {
        val shop = api.addShop(name)
        dir.save(dir.list() + shop)
        repo.seedShop(shop.id, shop.name)
        dir.switchTo(shop.id)
        repo.refreshTsCounter()
        return shop
    }
}
