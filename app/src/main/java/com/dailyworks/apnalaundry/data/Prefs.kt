package com.dailyworks.apnalaundry.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "apna_prefs")

/** The shop from before multi-shop — its id in the app and over the API. */
const val MAIN_SHOP = "main"

/** Small app-level flags + auth credentials + sync cursor, kept out of the domain DB. */
class Prefs(private val context: Context) {
    private val LOGGED_IN = booleanPreferencesKey("logged_in")
    private val SETUP_DONE = booleanPreferencesKey("setup_done")
    private val HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")
    // The guide (ui/tour): its TourStage name, unset = not started; the order it
    // follows; and its voice muted (kept on logout — it's about the phone's user).
    private val GUIDE_STAGE = stringPreferencesKey("guide_stage")
    private val GUIDE_ORDER = intPreferencesKey("guide_order")
    private val GUIDE_MUTED = booleanPreferencesKey("guide_muted")
    // A/B: this user's guide group from the server ("guide" / "no_guide"); unset = no guide.
    private val GUIDE_VARIANT = stringPreferencesKey("guide_variant")
    /** This phone just took a brand-new shop through first-time setup: the guide may start by itself. */
    private val GUIDE_NEW_SHOP = booleanPreferencesKey("guide_new_shop")
    // A/B: the first-time "add customer" flow from the server (CustomerFlow key); unset = the old flow.
    private val CUSTOMER_FLOW = stringPreferencesKey("customer_flow")
    // The assistant ("Ask" on Home) is on for this shop, as the server last said; unset = off.
    private val ASSISTANT_ON = booleanPreferencesKey("assistant_on")
    // Ask reads its answers out loud (phone's text-to-speech); unset = on.
    private val ASSISTANT_SPEAK = booleanPreferencesKey("assistant_speak")

    // Auth (opaque server tokens — see data/remote/TokenManager)
    private val ACCESS_TOKEN = stringPreferencesKey("access_token")
    private val ACCESS_EXPIRES_AT = longPreferencesKey("access_expires_at") // epoch ms
    private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    private val USER_ID = stringPreferencesKey("user_id")
    private val USER_PHONE = stringPreferencesKey("user_phone")
    private val DEVICE_ID = stringPreferencesKey("device_id") // stable installation id, survives logout

    // The subscription as of the last billing check — saved in the background
    // (app start, after paying) so the free-orders gate decides instantly.
    // Default: not subscribed.
    private val SUB_ACTIVE = booleanPreferencesKey("sub_active")
    private val SUB_ACTIVE_USER = stringPreferencesKey("sub_active_user") // whose status it is
    private val SUB_ENDS_AT = stringPreferencesKey("sub_ends_at") // ISO; paid / cancelled-grace period end
    private val FREE_ORDERS = intPreferencesKey("free_orders") // orders allowed without a plan (server)
    // Paywall test (2026-10-14, new shops on 1.0.12+): "hard_trial" | "free1" from the server.
    private val PAYWALL_GROUP = stringPreferencesKey("paywall_group")
    // A brand-new account just logged in: PaywallGate waits for the first billing check.
    private val PAYWALL_CHECK_PENDING = booleanPreferencesKey("paywall_check_pending")
    // "free1": the paywall after the first order + guide has been shown once.
    private val FIRST_ORDER_PAYWALL_SHOWN = booleanPreferencesKey("first_order_paywall_shown")

    // Sync cursor: the server checkpoint from the last successful pull — the
    // main shop's; each extra shop has its own "sync_checkpoint_<shop id>".
    private val SYNC_CHECKPOINT = longPreferencesKey("sync_checkpoint")
    private val SHOP_CHECKPOINT_PREFIX = "sync_checkpoint_"

    // Multi-shop: the owner's shops (JSON list, data/shops) and the one the app
    // is on; unset = "main", the shop from before multi-shop.
    private val SHOPS = stringPreferencesKey("shops")
    private val ACTIVE_SHOP = stringPreferencesKey("active_shop")

    val loggedIn: Flow<Boolean> = context.dataStore.data.map { it[LOGGED_IN] ?: false }
    val setupDone: Flow<Boolean> = context.dataStore.data.map { it[SETUP_DONE] ?: false }
    val hideAmounts: Flow<Boolean> = context.dataStore.data.map { it[HIDE_AMOUNTS] ?: false }
    val accessToken: Flow<String?> = context.dataStore.data.map { it[ACCESS_TOKEN] }
    val accessExpiresAt: Flow<Long> = context.dataStore.data.map { it[ACCESS_EXPIRES_AT] ?: 0L }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[REFRESH_TOKEN] }
    val userId: Flow<String?> = context.dataStore.data.map { it[USER_ID] }
    val tourStage: Flow<String?> = context.dataStore.data.map { it[GUIDE_STAGE] }
    val tourOrder: Flow<Int?> = context.dataStore.data.map { it[GUIDE_ORDER] }
    val tourMuted: Flow<Boolean> = context.dataStore.data.map { it[GUIDE_MUTED] ?: false }
    val guideVariant: Flow<String?> = context.dataStore.data.map { it[GUIDE_VARIANT] }
    val guideNewShop: Flow<Boolean> = context.dataStore.data.map { it[GUIDE_NEW_SHOP] ?: false }
    val customerFlow: Flow<String?> = context.dataStore.data.map { it[CUSTOMER_FLOW] }
    val assistantOn: Flow<Boolean> = context.dataStore.data.map { it[ASSISTANT_ON] ?: false }
    val assistantSpeak: Flow<Boolean> = context.dataStore.data.map { it[ASSISTANT_SPEAK] ?: true }
    val userPhone: Flow<String?> = context.dataStore.data.map { it[USER_PHONE] }
    val lastSyncCheckpoint: Flow<Long> = context.dataStore.data.map { it[SYNC_CHECKPOINT] ?: 0L }
    val shopsJson: Flow<String?> = context.dataStore.data.map { it[SHOPS] }
    val activeShop: Flow<String> = context.dataStore.data.map { it[ACTIVE_SHOP] ?: MAIN_SHOP }

    suspend fun setLoggedIn(v: Boolean) { context.dataStore.edit { it[LOGGED_IN] = v } }
    suspend fun setSetupDone(v: Boolean) { context.dataStore.edit { it[SETUP_DONE] = v } }
    suspend fun setHideAmounts(v: Boolean) { context.dataStore.edit { it[HIDE_AMOUNTS] = v } }
    suspend fun setTourStage(v: String) { context.dataStore.edit { it[GUIDE_STAGE] = v } }
    suspend fun setTourOrder(id: Int) { context.dataStore.edit { it[GUIDE_ORDER] = id } }
    suspend fun setTourMuted(v: Boolean) { context.dataStore.edit { it[GUIDE_MUTED] = v } }
    suspend fun setGuideVariant(v: String) { context.dataStore.edit { it[GUIDE_VARIANT] = v } }
    suspend fun setGuideNewShop(v: Boolean) { context.dataStore.edit { it[GUIDE_NEW_SHOP] = v } }
    suspend fun setCustomerFlow(v: String?) { context.dataStore.edit { if (v == null) it.remove(CUSTOMER_FLOW) else it[CUSTOMER_FLOW] = v } }
    suspend fun setAssistantOn(v: Boolean) { context.dataStore.edit { it[ASSISTANT_ON] = v } }
    suspend fun setAssistantSpeak(v: Boolean) { context.dataStore.edit { it[ASSISTANT_SPEAK] = v } }
    suspend fun setLastSyncCheckpoint(v: Long) { context.dataStore.edit { it[SYNC_CHECKPOINT] = v } }
    suspend fun setShops(json: String) { context.dataStore.edit { it[SHOPS] = json } }
    suspend fun setActiveShop(id: String) { context.dataStore.edit { it[ACTIVE_SHOP] = id } }

    private fun checkpointKey(shop: String) =
        if (shop == MAIN_SHOP) SYNC_CHECKPOINT else longPreferencesKey(SHOP_CHECKPOINT_PREFIX + shop)
    suspend fun syncCheckpoint(shop: String): Long = context.dataStore.data.first()[checkpointKey(shop)] ?: 0L
    suspend fun setSyncCheckpoint(shop: String, v: Long) { context.dataStore.edit { it[checkpointKey(shop)] = v } }

    suspend fun setCredentials(access: String, accessExpiresAt: Long, refresh: String, userId: String, phone: String) {
        context.dataStore.edit {
            it[ACCESS_TOKEN] = access
            it[ACCESS_EXPIRES_AT] = accessExpiresAt
            it[REFRESH_TOKEN] = refresh
            it[USER_ID] = userId
            it[USER_PHONE] = phone
        }
    }

    suspend fun setAccessPair(access: String, accessExpiresAt: Long, refresh: String) {
        context.dataStore.edit {
            it[ACCESS_TOKEN] = access
            it[ACCESS_EXPIRES_AT] = accessExpiresAt
            it[REFRESH_TOKEN] = refresh
        }
    }

    /**
     * Drops the tokens (dead session) but KEEPS user id/phone — the next
     * login compares them to decide whether local data belongs to the same
     * account (AuthRepository.verifyOtp) or must be wiped first.
     */
    suspend fun clearCredentials() {
        context.dataStore.edit {
            it.remove(ACCESS_TOKEN); it.remove(ACCESS_EXPIRES_AT); it.remove(REFRESH_TOKEN)
        }
    }

    /** Save what the server just said about the logged-in user's subscription. */
    suspend fun saveSubscription(active: Boolean, endsAtIso: String?, freeOrders: Int, paywallGroup: String? = null) {
        context.dataStore.edit { p ->
            val u = p[USER_ID] ?: return@edit
            p[SUB_ACTIVE] = active
            p[SUB_ACTIVE_USER] = u
            if (active && !endsAtIso.isNullOrBlank()) p[SUB_ENDS_AT] = endsAtIso else p.remove(SUB_ENDS_AT)
            p[FREE_ORDERS] = freeOrders
            if (paywallGroup.isNullOrBlank()) p.remove(PAYWALL_GROUP) else p[PAYWALL_GROUP] = paywallGroup
            p.remove(PAYWALL_CHECK_PENDING) // a check has answered
        }
    }

    /** The paywall-test group for the logged-in user, as the server last said (null = not in it). */
    val paywallGroup: Flow<String?> = context.dataStore.data.map { p ->
        if (p[SUB_ACTIVE_USER] != null && p[SUB_ACTIVE_USER] == p[USER_ID]) p[PAYWALL_GROUP] else null
    }
    val paywallCheckPending: Flow<Boolean> = context.dataStore.data.map { it[PAYWALL_CHECK_PENDING] ?: false }
    suspend fun setPaywallCheckPending(v: Boolean) { context.dataStore.edit { if (v) it[PAYWALL_CHECK_PENDING] = true else it.remove(PAYWALL_CHECK_PENDING) } }
    val firstOrderPaywallShown: Flow<Boolean> = context.dataStore.data.map { it[FIRST_ORDER_PAYWALL_SHOWN] ?: false }
    suspend fun setFirstOrderPaywallShown() { context.dataStore.edit { it[FIRST_ORDER_PAYWALL_SHOWN] = true } }

    /** Free orders for the logged-in user, as the server last said; [default] until it has. */
    suspend fun freeOrders(default: Int): Int {
        val p = context.dataStore.data.first()
        return if (p[SUB_ACTIVE_USER] != null && p[SUB_ACTIVE_USER] == p[USER_ID]) p[FREE_ORDERS] ?: default else default
    }

    /**
     * The saved subscription gives access right now: active, for this user, and
     * its end date (when known) not passed. False until a check has saved one.
     */
    suspend fun subscriptionActive(): Boolean {
        val p = context.dataStore.data.first()
        val u = p[USER_ID] ?: return false
        if (p[SUB_ACTIVE] != true || p[SUB_ACTIVE_USER] != u) return false
        val ends = p[SUB_ENDS_AT]?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        return ends == null || System.currentTimeMillis() < ends
    }

    /** Stable per-install device id (HealthProduct's device_installation_id). */
    suspend fun deviceId(): String {
        context.dataStore.data.first()[DEVICE_ID]?.let { return it }
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { prefs ->
            if (prefs[DEVICE_ID] == null) prefs[DEVICE_ID] = id
        }
        return context.dataStore.data.first()[DEVICE_ID] ?: id
    }

    suspend fun logoutAndReset() {
        context.dataStore.edit {
            it[LOGGED_IN] = false; it[SETUP_DONE] = false; it[HIDE_AMOUNTS] = false
            it[SYNC_CHECKPOINT] = 0L
            it.remove(SHOPS); it.remove(ACTIVE_SHOP)
            it.asMap().keys.filter { k -> k.name.startsWith(SHOP_CHECKPOINT_PREFIX) }.forEach { k -> it.remove(k) }
            it.remove(GUIDE_STAGE); it.remove(GUIDE_ORDER); it.remove(GUIDE_VARIANT); it.remove(GUIDE_NEW_SHOP)
            it.remove(CUSTOMER_FLOW); it.remove(ASSISTANT_ON)
            it.remove(ACCESS_TOKEN); it.remove(ACCESS_EXPIRES_AT); it.remove(REFRESH_TOKEN)
            it.remove(USER_ID); it.remove(USER_PHONE)
            it.remove(SUB_ACTIVE); it.remove(SUB_ACTIVE_USER); it.remove(SUB_ENDS_AT); it.remove(FREE_ORDERS)
            it.remove(PAYWALL_GROUP); it.remove(PAYWALL_CHECK_PENDING); it.remove(FIRST_ORDER_PAYWALL_SHOWN)
            // DEVICE_ID survives — it identifies the installation, not the user.
        }
    }
}
