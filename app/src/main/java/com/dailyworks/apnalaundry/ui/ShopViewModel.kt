package com.dailyworks.apnalaundry.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyworks.apnalaundry.data.CmdResult
import android.net.Uri
import com.dailyworks.apnalaundry.data.LaundryRepository
import com.dailyworks.apnalaundry.data.LogoStore
import com.dailyworks.apnalaundry.data.sync.SyncApi
import com.dailyworks.apnalaundry.ui.screens.bill.BILL_PAGE_URL
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.Snapshot
import com.dailyworks.apnalaundry.data.sync.SyncScheduler
import com.dailyworks.apnalaundry.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
) : ViewModel() {

    private val empty = LaundryState(
        Shop("Shine Laundry", "9876543210", 50), emptyList(), emptyList(), emptyList(), emptyList(), emptySet(),
    )

    val state: StateFlow<LaundryState> =
        repo.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), empty)

    val hideAmounts: StateFlow<Boolean> =
        prefs.hideAmounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
    fun saveCount(id: Int, next: OrderStatus, lines: List<OrderLine>, deliveryDate: String = "") =
        launchCmd { repo.saveCount(id, next, lines, deliveryDate) }
    fun prepay(id: Int, method: PayMethod) = launchCmd { repo.prepay(id, method) }
    fun cancelOrder(id: Int, reason: String) = launchCmd { repo.cancelOrder(id, reason) }
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

    /** Resizes + uploads a picked logo; the id, or the failure for a toast. */
    suspend fun uploadLogo(uri: Uri): Result<String> =
        runCatching { logos.upload(uri) }.onSuccess { _logoTick.value++ }

    private var sampleUrl: String? = null

    /** The shop's sample-bill page (onboarding "Test on WhatsApp"); null offline. */
    suspend fun sampleBillUrl(): String? = sampleUrl
        ?: runCatching { "$BILL_PAGE_URL${api.sampleBillLink()}" }.getOrNull()?.also { sampleUrl = it }

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
        onDone: (LaundryRepository.SaveOrderResult) -> Unit,
    ) = viewModelScope.launch {
        invalidateUndo()
        val res = repo.saveOrder(
            editId, custId, pickup, delivery, pickupDate, pickupTime24, deliveryDate, deliveryTime24, ddAuto,
            fee, express, exAmt, discount, lines, quickAmount, quickPieces, serialNo = serialNo,
            exPct = exPct, discPct = discPct,
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
                CmdResult("That was changed on another device — please check and try again")
            }
            publish(r)
            sync.requestSync()
        }
}
