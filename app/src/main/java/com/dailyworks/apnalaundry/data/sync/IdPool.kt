package com.dailyworks.apnalaundry.data.sync

import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.shops.ShopDirectory

/**
 * Staff (1.0.20, docs/specs/staff.md): order and customer numbers for a shop several phones work
 * in. Each phone holds a block the server reserved for it (POST /ids/reserve), so an owner's and
 * a staff phone never hand out the same number — even offline (a block of 20 covers a shift).
 *
 * Used only where it matters: a shop worked as staff, or an owner's shop that has staff
 * ([Prefs.shopHasStaff], set by the Staff screens). Elsewhere, and when a block runs out offline,
 * the shop's own counter numbers as before.
 */
class IdPool(private val prefs: Prefs, private val api: SyncApi, private val shops: ShopDirectory) {

    private suspend fun enabled(shop: String) = shops.ref(shop)?.staff != null || prefs.shopHasStaff(shop)

    /** The next reserved [kind] ("order" | "customer") number for [shop], or null (use the counter). */
    suspend fun take(shop: String, kind: String): Int? {
        if (!enabled(shop)) return null
        val (next, end) = prefs.idBlock(shop, kind) ?: return null
        if (next >= end) return null
        prefs.setIdBlock(shop, kind, next + 1, end)
        return next
    }

    /**
     * Tops up [shop]'s blocks when 5 or fewer numbers are left (each sync). [floorOrder] /
     * [floorCust]: this phone's own next numbers, so a block never starts below what it has used.
     */
    suspend fun topUp(shop: String, floorOrder: Int, floorCust: Int) {
        if (!enabled(shop)) return
        for ((kind, floor) in listOf("order" to floorOrder, "customer" to floorCust)) {
            val left = prefs.idBlock(shop, kind)?.let { (next, end) -> end - next } ?: 0
            if (left > 5) continue
            val block = runCatching { api.reserveIds(kind, 20, floor, shop) }.getOrNull() ?: continue
            prefs.setIdBlock(shop, kind, block.first, block.last + 1)
        }
    }
}
