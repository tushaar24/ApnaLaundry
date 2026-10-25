package com.dailyworks.apnalaundry.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyworks.apnalaundry.data.CmdResult
import android.net.Uri
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.data.LaundryRepository
import com.dailyworks.apnalaundry.data.LogoStore
import com.dailyworks.apnalaundry.data.sync.SyncApi
import com.dailyworks.apnalaundry.ui.screens.bill.BILL_PAGE_URL
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.shops.ShopDirectory
import com.dailyworks.apnalaundry.data.shops.ShopRef
import com.dailyworks.apnalaundry.data.shops.ShopsRepository
import kotlinx.coroutines.flow.flatMapLatest
import com.dailyworks.apnalaundry.data.experiments.CustomerFlow
import com.dailyworks.apnalaundry.data.experiments.ExperimentsRepository
import com.dailyworks.apnalaundry.data.Snapshot
import com.dailyworks.apnalaundry.data.sync.SyncScheduler
import com.dailyworks.apnalaundry.domain.*
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.ui.tour.TourPart
import com.dailyworks.apnalaundry.ui.tour.TourStage
import com.dailyworks.apnalaundry.ui.tour.TourStep
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ToastState(val text: String, val hasUndo: Boolean)

/**
 * The single source of shared, authenticated-app state: the reactive [LaundryState],
 * a global toast with Undo, and the hide-amounts flag. Screens keep their own transient
 * form state locally and call these commands.
 */
class ShopViewModel(
    private val repo: LaundryRepository,
    private val prefs: Prefs,
    private val sync: SyncScheduler,
    val logos: LogoStore,
    private val api: SyncApi,
    private val shopDir: ShopDirectory,
    private val shopsRepo: ShopsRepository,
) : ViewModel() {

    private val empty = LaundryState(
        SeedData.shop, emptyList(), emptyList(), emptyList(), emptyList(), emptySet(),
    )

    /**
     * True once the database has answered. Until then [state] is the [empty]
     * placeholder, whose zero orders must not be mistaken for a brand-new shop.
     */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    val state: StateFlow<LaundryState> =
        repo.state.onEach { _loaded.value = true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), empty)

    val hideAmounts: StateFlow<Boolean> =
        prefs.hideAmounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ---- the guide (ui/tour): new order → ready → delivered + money → khata → earnings ----

    /** A/B (server-assigned, 50/50): this shop gets the guide at all. Unknown = no. */
    val guideOn: StateFlow<Boolean> = prefs.guideVariant.map { it == ExperimentsRepository.GUIDE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** A/B (server-assigned, new accounts on 1.0.15+): how New order adds the first customer. */
    val customerFlow: StateFlow<CustomerFlow> = prefs.customerFlow.map { CustomerFlow.of(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CustomerFlow.LEGACY)

    /**
     * Where the shop is in the guide. It starts by itself only for a brand-new
     * shop right after its first-time setup on this phone (and still no
     * orders) — never after a reinstall / login on a new phone (old orders
     * still syncing), for an old shop that happens to have no orders, or for
     * an added shop. Otherwise only from Settings → How to use the app.
     * Off entirely for the no-guide group.
     */
    val tour: StateFlow<TourStage> =
        combine(prefs.tourStage, state, loaded, guideOn, prefs.guideNewShop) { saved, s, ready, on, newShop ->
            if (!on) return@combine TourStage.DONE
            saved?.let { runCatching { TourStage.valueOf(it) }.getOrNull() }
                ?: if (newShop && ready && s.orders.isEmpty()) TourStage.ORDER else TourStage.DONE
        }.stateIn(viewModelScope, SharingStarted.Eagerly, TourStage.DONE)

    /** The order the guide follows from ready to delivered: the one saved during it. */
    val tourOrderId: StateFlow<Int?> = prefs.tourOrder.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** "Later" on the order's card: no guide on Home for the rest of this app session. */
    private val _tourPaused = MutableStateFlow(false)
    val tourPaused: StateFlow<Boolean> = _tourPaused

    /** The guide's order, else the oldest order still open; null = none left to follow. */
    fun tourOrder(s: LaundryState, id: Int? = tourOrderId.value): Order? =
        s.orders.firstOrNull { it.id == id && it.status != OrderStatus.CANCELLED }
            ?: s.orders.filter { LaundryMath.isOpen(it) }.minByOrNull { it.id }

    init {
        // Parts 2–3 follow the guide's order, however its status is changed:
        // ready = part 2 done; delivered = part 3 done, on to khata. No order
        // left to follow (cancelled / deleted) also moves on to khata.
        viewModelScope.launch {
            var last: OrderStatus? = null
            combine(tour, state, tourOrderId, loaded) { t, s, id, ok ->
                if (t != TourStage.STATUS || !ok) null else tourOrder(s, id)?.status ?: OrderStatus.CANCELLED
            }.distinctUntilChanged().collect { st ->
                when (st) {
                    OrderStatus.READY ->
                        if (last == OrderStatus.CREATED || last == OrderStatus.RECEIVED) partDone(TourPart.READY)
                    OrderStatus.DELIVERED -> { partDone(TourPart.DELIVER); prefs.setTourStage(TourStage.KHATA.name) }
                    OrderStatus.CANCELLED -> prefs.setTourStage(TourStage.KHATA.name)
                    else -> Unit
                }
                last = st
            }
        }
    }

    fun setTour(stage: TourStage) = viewModelScope.launch { prefs.setTourStage(stage.name) }

    /** A new shop's first look at the guide starts it (once). */
    fun tourAutoStart() = viewModelScope.launch {
        if (prefs.tourStage.first() != null) return@launch
        prefs.setTourStage(TourStage.ORDER.name)
        Analytics.tourStarted("auto")
    }

    /** Settings → How to use the app: the guide from the top. */
    fun tourReplay() = viewModelScope.launch {
        _tourPaused.value = false
        prefs.setTourStage(TourStage.ORDER.name)
        Analytics.tourStarted("settings")
    }

    /** The guide's order was saved: part 1 done, on to its bill. */
    fun tourOrderSaved(orderId: Int) = viewModelScope.launch {
        prefs.setTourOrder(orderId)
        prefs.setTourStage(TourStage.BILL.name)
        partDone(TourPart.ORDER)
    }

    fun tourLater(step: TourStep) {
        Analytics.tourLater(step.id, step.part.n)
        _tourPaused.value = true
    }

    fun tourSkip(step: TourStep) {
        Analytics.tourSkipped(step.id, step.part.n)
        setTour(TourStage.DONE)
    }

    /** Khata seen: part 4 done, on to earnings. */
    fun tourKhataDone() { partDone(TourPart.KHATA); setTour(TourStage.EARNINGS) }

    /** The last card's Finish. */
    fun tourFinished() {
        partDone(TourPart.EARNINGS)
        Analytics.tourCompleted()
        setTour(TourStage.DONE)
    }

    private fun partDone(part: TourPart) = Analytics.tourPartCompleted(part.n, part.key)

    // ---- multi-shop (paid): each shop its own data; the app shows one at a time ----

    /** Every shop, the main one first. */
    val shops: StateFlow<List<ShopRef>> = shopDir.shops

    /** The shop on screen. */
    val activeShopId: StateFlow<String> = shopDir.activeId

    init {
        // Keep the shop list's names in step with each shop's own name (renamed
        // in Settings / bill details, or pulled from another phone).
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            shopDir.activeId.flatMapLatest { id -> shopDir.db(id).shopDao().observe().map { id to it?.name } }
                .collect { (id, name) -> if (!name.isNullOrBlank()) shopDir.rename(id, name) }
        }
        viewModelScope.launch {
            shopDir.shops.map { it.size }.distinctUntilChanged().collect { Analytics.updateProfile(mapOf("Shop_Count" to it)) }
        }
    }

    /** The owner's shops from the server (app start / login). */
    fun refreshShops() = viewModelScope.launch { shopsRepo.refresh() }

    fun switchShop(id: String) = viewModelScope.launch {
        if (id == shopDir.activeId.value) return@launch
        invalidateUndo()
        dismissToast()
        dismissWaPrompt()
        shopDir.switchTo(id)
        repo.refreshTsCounter()
        Analytics.shopSwitched(shopDir.shops.value.size)
        sync.requestSync()
    }

    /** Deletes an extra shop; [onDone]: null = deleted, else what went wrong. */
    fun deleteShop(id: String, onDone: (String?) -> Unit) = viewModelScope.launch {
        val r = runCatching { shopsRepo.delete(id) }
        r.onSuccess {
            invalidateUndo()
            Analytics.shopDeleted(shopDir.shops.value.size)
            onDone(null)
        }.onFailure { onDone(tr("Couldn't delete the shop — check internet and try again")) }
    }

    /**
     * Switches to [id] (if needed) and waits until the screen state shows that
     * shop, then [then] — e.g. opening its details, which read the shop once.
     */
    fun openShop(id: String, then: () -> Unit) = viewModelScope.launch {
        if (id != shopDir.activeId.value) {
            switchShop(id).join()
            val target = repo.stateOnce(id).shop
            kotlinx.coroutines.withTimeoutOrNull(2_000) { state.first { it.shop == target } }
        }
        then()
    }

    /**
     * Adds a shop (the server refuses without a plan) and switches to it.
     * [billPhone]: its number on bills ("" = the login number).
     * [onDone]: null = added; else what went wrong ("plan" = needs a plan).
     */
    fun addShop(name: String, billPhone: String, onDone: (String?) -> Unit) = viewModelScope.launch {
        val r = runCatching { shopsRepo.add(name, billPhone) }
        r.onSuccess {
            invalidateUndo()
            Analytics.shopAdded(shopDir.shops.value.size)
            sync.requestSync()
            onDone(null)
        }.onFailure { e ->
            onDone(if (e is com.dailyworks.apnalaundry.data.sync.PlanRequiredException) "plan" else tr("Couldn't add the shop — check internet and try again"))
        }
    }

    /** Each shop's number on its bills, for "Your shops". */
    suspend fun shopPhones(ids: List<String>): Map<String, String> = ids.associateWith { repo.billPhoneOf(it) }

    /** Bumps when the shop logo finishes loading, so bills redraw with it. */
    private val _logoTick = MutableStateFlow(0)
    val logoTick: StateFlow<Int> = _logoTick

    init {
        // Bills draw synchronously when shared, so keep the shop logo loaded.
        viewModelScope.launch {
            repo.state.map { it.shop.logoId }.distinctUntilChanged().collect { id ->
                if (id.isNotEmpty() && logos.load(id) != null) _logoTick.value++
            }
        }
    }

    /** After a status change: a wa.me link the "Tell the customer?" dialog offers to open. */
    private val _waPrompt = MutableStateFlow<String?>(null)
    val waPrompt: StateFlow<String?> = _waPrompt
    fun dismissWaPrompt() { _waPrompt.value = null }

    private val _toast = MutableStateFlow<ToastState?>(null)
    val toast: StateFlow<ToastState?> = _toast
    private var undoSnapshot: Snapshot? = null
    private var toastJob: Job? = null

    fun toggleHideAmounts() = viewModelScope.launch { prefs.setHideAmounts(!hideAmounts.value) }

    private fun publish(r: CmdResult) {
        undoSnapshot = r.undo
        _toast.value = ToastState(r.toast, r.undo != null)
        toastJob?.cancel()
        // Undo toasts stay long enough to act on; plain notices are brief.
        toastJob = viewModelScope.launch { delay(if (r.undo != null) 7000 else 2500); _toast.value = null }
        // A status change: ask whether to tell the customer on WhatsApp.
        if (r.wa != null) _waPrompt.value = r.wa
    }

    fun showInfo(text: String) = publish(CmdResult(text, null))

    /**
     * A new change (or another device's pulled changes) ends the previous
     * command's Undo: restoring its snapshot would wipe that change. Keeps the
     * toast text, without its Undo button.
     */
    private fun invalidateUndo() {
        if (undoSnapshot == null) return
        undoSnapshot = null
        _toast.value = _toast.value?.copy(hasUndo = false)
    }

    init {
        viewModelScope.launch { sync.remoteChanges.collect { invalidateUndo() } }
    }
    fun dismissToast() { _toast.value = null; toastJob?.cancel() }

    fun undo() {
        val snap = undoSnapshot ?: return
        viewModelScope.launch {
            repo.restore(snap)
            undoSnapshot = null
            _toast.value = null
            sync.requestSync()
        }
    }

    // ---- fire-and-forget commands ----
    fun markPickedUp(id: Int) = launchCmd { repo.markPickedUp(id) }
    fun markReady(id: Int, deliveryDate: String) = launchCmd { repo.markReady(id, deliveryDate) }
    fun deliver(id: Int, amount: Int, method: PayMethod) = launchCmd { repo.deliver(id, amount, method) }
    /** Part cash, part UPI. */
    fun deliverSplit(id: Int, cash: Int, upi: Int) =
        launchCmd { repo.deliverSplit(id, listOf(PayMethod.CASH to cash, PayMethod.UPI to upi)) }
    fun saveCount(id: Int, next: OrderStatus, lines: List<OrderLine>, deliveryDate: String = "") =
        launchCmd { repo.saveCount(id, next, lines, deliveryDate) }
    fun prepay(id: Int, method: PayMethod) = launchCmd { repo.prepay(id, method) }
    /** Part / any amount before delivery (the assistant); extra beyond the order goes to the khata. */
    fun prepayPart(id: Int, amount: Int, method: PayMethod) = launchCmd { repo.prepayPart(id, amount, method) }
    fun cancelOrder(id: Int, reason: String) = launchCmd { repo.cancelOrder(id, reason) }
    fun setStatus(id: Int, target: OrderStatus) = launchCmd { repo.setStatus(id, target) }
    fun deleteOrder(id: Int) = launchCmd { repo.deleteOrder(id) }
    fun reschedule(id: Int, kind: String, date: String, time24: String, notify: Boolean) =
        launchCmd { repo.reschedule(id, kind, date, time24, notify) }
    fun sendBill(id: Int) = launchCmd { repo.sendBill(id) }
    fun receivePayment(custId: String, amount: Int, method: PayMethod) = launchCmd { repo.receivePayment(custId, amount, method) }
    fun addOldBaaki(custId: String, amount: Int) = launchCmd { repo.addOldBaaki(custId, amount) }
    fun deleteService(id: String) = launchCmd { repo.deleteService(id) }

    fun upsertService(service: Service) = viewModelScope.launch { invalidateUndo(); repo.upsertService(service); sync.requestSync() }
    fun updateShop(name: String, expressPct: Int) =
        viewModelScope.launch { invalidateUndo(); repo.updateShop(name, expressPct); sync.requestSync() }

    fun updateShopDetails(name: String? = null, details: BillDetails.Fields? = null) =
        viewModelScope.launch { invalidateUndo(); repo.updateShopDetails(name, details); sync.requestSync() }

    fun setOnboardingStep(step: String) =
        viewModelScope.launch { repo.setOnboardingStep(step); sync.requestSync() }

    /** The last-used GST setting (every change on a new order). */
    fun setGstDefaults(on: Boolean, pct: Double, mode: String) =
        viewModelScope.launch { repo.setGstDefaults(on, pct, mode); sync.requestSync() }

    /** Resizes + uploads a picked logo; the id, or the failure for a toast. */
    suspend fun uploadLogo(uri: Uri): Result<String> =
        runCatching { logos.upload(uri) }.onSuccess { _logoTick.value++; com.dailyworks.apnalaundry.analytics.Analytics.logoUploaded() }

    private val billUrls = mutableMapOf<String, String>()

    /**
     * An order's public bill page, for the WhatsApp message ("Download PDF:").
     * Cached once fetched; a failure (offline, order not synced yet) isn't
     * cached, so the next try asks again. Never waits more than 3 s.
     */
    suspend fun billUrl(orderId: Int): String? {
        val shop = shopDir.activeId.value
        val key = "$shop:$orderId" // order ids repeat across shops
        return billUrls[key] ?: kotlinx.coroutines.withTimeoutOrNull(3_000) {
            runCatching { "$BILL_PAGE_URL${api.billLink(orderId, shop)}" }.getOrNull()
        }?.also { billUrls[key] = it }
    }

    private val sampleUrls = mutableMapOf<String, String>()

    /** The shop's sample-bill page (onboarding "Test on WhatsApp"); null offline. */
    suspend fun sampleBillUrl(): String? {
        val shop = shopDir.activeId.value
        return sampleUrls[shop] ?: runCatching { "$BILL_PAGE_URL${api.sampleBillLink(shop)}" }.getOrNull()?.also { sampleUrls[shop] = it }
    }

    /** The login number (bill phone default, "Test on WhatsApp" target). */
    suspend fun loginPhone(): String = prefs.userPhone.first() ?: state.value.shop.phone

    // ---- commands whose result the caller needs ----
    fun saveCustomer(
        editId: String?, name: String, phone: String, address: String, oldBaaki: Int, fromList: Boolean,
        onDone: (String) -> Unit,
    ) = viewModelScope.launch {
        invalidateUndo()
        val (id, res) = repo.saveCustomer(editId, name, phone, address, oldBaaki, fromList)
        res?.let { publish(it) }
        sync.requestSync()
        onDone(id)
    }

    fun saveOrder(
        editId: Int?, custId: String, pickup: Route, delivery: Route, pickupDate: String, pickupTime24: String,
        deliveryDate: String, deliveryTime24: String, ddAuto: Boolean, fee: Int, express: Boolean, exAmt: Int,
        discount: Int, lines: List<OrderLine>, quickAmount: Int, quickPieces: Int,
        serialNo: String = "",
        exPct: Int = 0,
        discPct: Int = 0,
        gstOn: Boolean = false, gstPct: Double = 0.0, gstMode: String = "excl",
        note: String = "",
        onDone: (LaundryRepository.SaveOrderResult) -> Unit,
    ) = viewModelScope.launch {
        invalidateUndo()
        val res = repo.saveOrder(
            editId, custId, pickup, delivery, pickupDate, pickupTime24, deliveryDate, deliveryTime24, ddAuto,
            fee, express, exAmt, discount, lines, quickAmount, quickPieces, serialNo = serialNo,
            exPct = exPct, discPct = discPct, gstOn = gstOn, gstPct = gstPct, gstMode = gstMode, note = note,
        )
        res.toast?.let { publish(CmdResult(it, res.undo)) }
        sync.requestSync()
        onDone(res)
    }

    private fun launchCmd(block: suspend () -> CmdResult) =
        viewModelScope.launch {
            invalidateUndo()
            // The order / customer may have been deleted on another device while
            // its screen or sheet was open — tell the owner instead of crashing.
            val r = try { block() } catch (e: NoSuchElementException) {
                CmdResult(tr("That was changed on another device — please check and try again"))
            }
            publish(r)
            sync.requestSync()
        }
}
