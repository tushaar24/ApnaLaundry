package com.dailyworks.apnalaundry.data.shops

import android.content.Context
import com.dailyworks.apnalaundry.data.MAIN_SHOP
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.local.AppDatabase
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Shops an owner can run without a plan: their first shop + one more. A 3rd
 * shop and more need a plan (the server checks too: POST /shops).
 */
const val FREE_SHOPS = 2

/**
 * Staff (1.0.20, docs/specs/staff.md): this phone works in someone else's shop as staff. [token]
 * is the staff member's own login for it (the server acts as the owner on that one shop, within
 * [permissions]). Comes from login / GET /me/contexts; the pull keeps [permissions] fresh.
 */
@Serializable
data class StaffCtx(
    val staffId: String,
    val token: String,
    val name: String = "", // the staff member
    val ownerName: String = "",
    val permissions: Map<String, Boolean> = emptyMap(),
    val ownerPlanActive: Boolean = true,
    /** The owner's plan ends at this time (epoch ms): the shop locks then, even offline. null = renews by itself. */
    val ownerPlanUntil: Long? = null,
) {
    fun can(p: com.dailyworks.apnalaundry.domain.StaffPermission) = permissions[p.key] == true

    /** Whether staff may work now: the server hasn't said the plan ended, and its known end hasn't passed. */
    fun planOn(now: Long = System.currentTimeMillis()) = ownerPlanActive && (ownerPlanUntil == null || now < ownerPlanUntil)
}

/**
 * One shop this phone works on. [id] [MAIN_SHOP] = the shop from before multi-shop (or, for
 * someone who is only staff, their staff shop). [staff] set = a staff entry: its own token and
 * switches; null = the owner's own shop.
 */
@Serializable
data class ShopRef(val id: String, val name: String = "", val staff: StaffCtx? = null)

/** Staff (1.0.20): things to tell the person — removed from a shop, added to one. */
sealed interface StaffEvent {
    data class Removed(val shopName: String) : StaffEvent
    data class Added(val ref: ShopRef) : StaffEvent
}

/** The local id of a staff entry (its own database file and sync checkpoint). */
fun staffShopId(staffId: String) = "staff-$staffId"

/**
 * The owner's shops and which one the app is on (multi-shop, paid). Each shop
 * has its own Room database — the main shop keeps the original file, so a
 * one-shop owner (every shop before 1.0.11) is untouched — and its own sync
 * checkpoint; the server keeps each shop's rows apart (X-Laundry-Shop).
 * Customers, khata, services, bills and counters are per shop by construction.
 */
class ShopDirectory(private val context: Context, private val prefs: Prefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dbs = ConcurrentHashMap<String, AppDatabase>()
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(ShopRef.serializer())

    // Read once at start (a tiny DataStore file), so the very first screen
    // already shows the right shop instead of flashing the main one.
    private val _active = MutableStateFlow(runBlocking { prefs.activeShop.first() })

    /** The shop on screen. */
    val activeId: StateFlow<String> = _active

    /** Staff (1.0.20): removed from / added to a shop — the app tells the person. */
    val staffEvents = kotlinx.coroutines.flow.MutableSharedFlow<StaffEvent>(extraBufferCapacity = 4)

    /** The shop on screen's database (what the repository reads and writes). */
    val current: AppDatabase get() = db(_active.value)
    val currentFlow: Flow<AppDatabase> = _active.map { db(it) }

    /** Every shop, the main one first; always at least the main one. */
    val shops: StateFlow<List<ShopRef>> = prefs.shopsJson.map(::decode)
        .stateIn(scope, SharingStarted.Eagerly, runBlocking { decode(prefs.shopsJson.first()) })

    suspend fun list(): List<ShopRef> = decode(prefs.shopsJson.first())

    /** A shop entry by id (null = gone). */
    fun ref(id: String): ShopRef? = shops.value.firstOrNull { it.id == id }

    /** The shop on screen's entry — its [ShopRef.staff] says whether this is staff mode. */
    val activeRef: StateFlow<ShopRef> = combine(_active, shops) { id, list -> list.firstOrNull { it.id == id } ?: list.first() }
        .stateIn(scope, SharingStarted.Eagerly, shops.value.firstOrNull { it.id == _active.value } ?: shops.value.first())

    fun db(id: String): AppDatabase = dbs.getOrPut(id) { AppDatabase.build(context, fileOf(id)) }

    suspend fun switchTo(id: String) {
        _active.value = id
        prefs.setActiveShop(id)
    }

    /**
     * Saves the owner's shop list (main kept first); the staff entries this phone has stay (they
     * come from [saveStaff]). If the shop on screen is gone, back to the main one.
     */
    suspend fun save(list: List<ShopRef>) {
        val current = list()
        // Someone who is only staff: the main slot is their staff shop — never overwritten here.
        val main = current.first().takeIf { it.staff != null } ?: list.firstOrNull { it.id == MAIN_SHOP } ?: ShopRef(MAIN_SHOP)
        val staffEntries = current.filter { it.id != MAIN_SHOP && it.staff != null }
        val all = listOf(main) + (list.filter { it.id != MAIN_SHOP } + staffEntries).distinctBy { it.id }
        write(all)
    }

    /**
     * The staff entries (login, GET /me/contexts): [mainStaff] = the main slot is a staff shop
     * (this phone has no shop of its own). Returns the ids of staff entries that went away (their
     * databases are deleted), so the caller can tell the person.
     */
    suspend fun saveStaff(entries: List<ShopRef>, mainStaff: Boolean): List<String> {
        val current = list()
        val owner = current.filter { it.staff == null && (it.id != MAIN_SHOP || !mainStaff) }
        val all = if (mainStaff && entries.isNotEmpty()) {
            listOf(entries.first().copy(id = MAIN_SHOP)) + owner.filter { it.id != MAIN_SHOP } + entries.drop(1)
        } else {
            (owner.ifEmpty { listOf(ShopRef(MAIN_SHOP)) }) + entries
        }
        val gone = current.filter { it.staff != null && it.id != MAIN_SHOP && all.none { a -> a.id == it.id } }.map { it.id }
        write(all)
        gone.forEach { id -> dbs.remove(id)?.close(); context.deleteDatabase(fileOf(id)); prefs.clearShopState(id) }
        return gone
    }

    /** One entry's staff details changed (new switches from a pull, the owner's plan). */
    suspend fun updateStaff(id: String, f: (StaffCtx) -> StaffCtx) {
        val list = list()
        if (list.none { it.id == id && it.staff != null }) return
        write(list.map { if (it.id == id && it.staff != null) it.copy(staff = f(it.staff)) else it })
    }

    private suspend fun write(all: List<ShopRef>) {
        prefs.setShops(json.encodeToString(ser, all))
        if (all.none { it.id == _active.value }) switchTo(MAIN_SHOP)
    }

    /** A shop's name changed on this phone (Settings, bill details): keep the list in step. */
    suspend fun rename(id: String, name: String) {
        val list = list()
        if (list.any { it.id == id && it.name != name }) save(list.map { if (it.id == id) it.copy(name = name) else it })
    }

    /**
     * A deleted shop leaves this phone: off the list (back to the main shop if
     * it was open), its database file and sync checkpoint gone. Never the main one.
     */
    suspend fun remove(id: String) {
        if (id == MAIN_SHOP) return
        if (_active.value == id) switchTo(MAIN_SHOP)
        write(list().filter { it.id != id }) // not save(): that keeps staff entries, and this may be one
        dbs.remove(id)?.close()
        context.deleteDatabase(fileOf(id))
        prefs.clearShopState(id) // its sync positions, number blocks and flags too: nothing of it is left
    }

    /**
     * Logout / another account: back to the main shop and every extra shop's
     * database deleted (the main one is cleared by the repository).
     */
    suspend fun forgetExtraShops() {
        switchTo(MAIN_SHOP)
        list().filter { it.id != MAIN_SHOP }.forEach { s ->
            dbs.remove(s.id)?.close()
            context.deleteDatabase(fileOf(s.id))
        }
        prefs.setShops(json.encodeToString(ser, listOf(ShopRef(MAIN_SHOP))))
    }

    /** Any staff entry on this phone (the main slot included). */
    fun hasStaffEntries(): Boolean = shops.value.any { it.staff != null }

    private fun decode(raw: String?): List<ShopRef> {
        val list = raw?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() }.orEmpty()
        val main = list.firstOrNull { it.id == MAIN_SHOP } ?: ShopRef(MAIN_SHOP)
        return listOf(main) + list.filter { it.id != MAIN_SHOP }
    }

    private fun fileOf(id: String): String =
        if (id == MAIN_SHOP) AppDatabase.NAME else "shop_" + id.filter { it.isLetterOrDigit() || it == '-' } + ".db"
}
