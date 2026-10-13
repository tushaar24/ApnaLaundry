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

/** One of the owner's shops. [id] [MAIN_SHOP] = the shop from before multi-shop. */
@Serializable
data class ShopRef(val id: String, val name: String = "")

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

    /** The shop on screen's database (what the repository reads and writes). */
    val current: AppDatabase get() = db(_active.value)
    val currentFlow: Flow<AppDatabase> = _active.map { db(it) }

    /** Every shop, the main one first; always at least the main one. */
    val shops: StateFlow<List<ShopRef>> = prefs.shopsJson.map(::decode)
        .stateIn(scope, SharingStarted.Eagerly, listOf(ShopRef(MAIN_SHOP)))

    suspend fun list(): List<ShopRef> = decode(prefs.shopsJson.first())

    fun db(id: String): AppDatabase = dbs.getOrPut(id) { AppDatabase.build(context, fileOf(id)) }

    suspend fun switchTo(id: String) {
        _active.value = id
        prefs.setActiveShop(id)
    }

    /** Saves the list (main kept first); if the shop on screen is gone, back to the main one. */
    suspend fun save(list: List<ShopRef>) {
        val main = list.firstOrNull { it.id == MAIN_SHOP } ?: ShopRef(MAIN_SHOP)
        val all = listOf(main) + list.filter { it.id != MAIN_SHOP }.distinctBy { it.id }
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
        save(list().filter { it.id != id })
        dbs.remove(id)?.close()
        context.deleteDatabase(fileOf(id))
        prefs.setSyncCheckpoint(id, 0L)
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

    private fun decode(raw: String?): List<ShopRef> {
        val list = raw?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() }.orEmpty()
        val main = list.firstOrNull { it.id == MAIN_SHOP } ?: ShopRef(MAIN_SHOP)
        return listOf(main) + list.filter { it.id != MAIN_SHOP }
    }

    private fun fileOf(id: String): String =
        if (id == MAIN_SHOP) AppDatabase.NAME else "shop_" + id.filter { it.isLetterOrDigit() || it == '-' } + ".db"
}
