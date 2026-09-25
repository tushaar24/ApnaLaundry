package com.dailyworks.apnalaundry.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyworks.apnalaundry.data.CmdResult
import com.dailyworks.apnalaundry.data.LaundryRepository
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.Snapshot
import com.dailyworks.apnalaundry.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel() {

    private val empty = LaundryState(
        Shop("Shine Laundry", "9876543210", "21:00", 50), emptyList(), emptyList(), emptyList(), emptyList(), emptySet(),
    )

    val state: StateFlow<LaundryState> =
        repo.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), empty)

    val hideAmounts: StateFlow<Boolean> =
        prefs.hideAmounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _toast = MutableStateFlow<ToastState?>(null)
    val toast: StateFlow<ToastState?> = _toast
    private var undoSnapshot: Snapshot? = null
    private var toastJob: Job? = null

    fun toggleHideAmounts() = viewModelScope.launch { prefs.setHideAmounts(!hideAmounts.value) }

    private fun publish(r: CmdResult) {
        undoSnapshot = r.undo
        _toast.value = ToastState(r.toast, r.undo != null)
        toastJob?.cancel()
        toastJob = viewModelScope.launch { delay(7000); _toast.value = null }
    }

    fun showInfo(text: String) = publish(CmdResult(text, null))
    fun dismissToast() { _toast.value = null; toastJob?.cancel() }

    fun undo() {
        val snap = undoSnapshot ?: return
        viewModelScope.launch {
            repo.restore(snap)
            undoSnapshot = null
            _toast.value = null
        }
    }

    // ---- fire-and-forget commands ----
    fun markPickedUp(id: Int) = launchCmd { repo.markPickedUp(id) }
    fun markReady(id: Int) = launchCmd { repo.markReady(id) }
    fun deliver(id: Int, amount: Int, method: PayMethod) = launchCmd { repo.deliver(id, amount, method) }
    fun saveCount(id: Int, next: OrderStatus, lines: List<OrderLine>) = launchCmd { repo.saveCount(id, next, lines) }
    fun prepay(id: Int, method: PayMethod) = launchCmd { repo.prepay(id, method) }
    fun cancelOrder(id: Int, reason: String) = launchCmd { repo.cancelOrder(id, reason) }
    fun reschedule(id: Int, kind: String, date: String, time24: String, notify: Boolean) =
        launchCmd { repo.reschedule(id, kind, date, time24, notify) }
    fun sendBill(id: Int) = launchCmd { repo.sendBill(id) }
    fun receivePayment(custId: String, amount: Int, method: PayMethod) = launchCmd { repo.receivePayment(custId, amount, method) }
    fun addOldBaaki(custId: String, amount: Int) = launchCmd { repo.addOldBaaki(custId, amount) }
    fun deleteService(id: String) = launchCmd { repo.deleteService(id) }

    fun upsertService(service: Service) = viewModelScope.launch { repo.upsertService(service) }
    fun updateShop(name: String, closeTime: String, expressPct: Int) =
        viewModelScope.launch { repo.updateShop(name, closeTime, expressPct) }

    fun restart() = viewModelScope.launch {
        repo.resetToSeed()
        prefs.logoutAndReset()
    }

    // ---- commands whose result the caller needs ----
    fun saveCustomer(
        editId: String?, name: String, phone: String, address: String, oldBaaki: Int, fromList: Boolean,
        onDone: (String) -> Unit,
    ) = viewModelScope.launch {
        val (id, res) = repo.saveCustomer(editId, name, phone, address, oldBaaki, fromList)
        res?.let { publish(it) }
        onDone(id)
    }

    fun createQuick(
        existingCustId: String?, typedInput: String, amount: Int, pieces: Int, paidMethod: PayMethod?, day: String,
        onDone: (Int) -> Unit = {},
    ) = viewModelScope.launch {
        val (id, res) = repo.createQuick(existingCustId, typedInput, amount, pieces, paidMethod, day)
        publish(res)
        onDone(id)
    }

    fun saveOrder(
        editId: Int?, custId: String, pickup: Route, delivery: Route, pickupDate: String, pickupTime24: String,
        deliveryDate: String, deliveryTime24: String, ddAuto: Boolean, fee: Int, express: Boolean, exAmt: Int,
        discount: Int, lines: List<OrderLine>, quickAmount: Int, quickPieces: Int,
        onDone: (LaundryRepository.SaveOrderResult) -> Unit,
    ) = viewModelScope.launch {
        val res = repo.saveOrder(
            editId, custId, pickup, delivery, pickupDate, pickupTime24, deliveryDate, deliveryTime24, ddAuto,
            fee, express, exAmt, discount, lines, quickAmount, quickPieces,
        )
        res.toast?.let { publish(CmdResult(it, res.undo)) }
        onDone(res)
    }

    private fun launchCmd(block: suspend () -> CmdResult) = viewModelScope.launch { publish(block()) }
}
