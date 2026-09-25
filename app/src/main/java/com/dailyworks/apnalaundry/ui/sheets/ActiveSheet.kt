package com.dailyworks.apnalaundry.ui.sheets

import androidx.compose.runtime.Composable
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.OrderStatus
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.nav.AppNavigator

/** Which bottom sheet (if any) is currently open, plus its target. */
sealed interface ActiveSheet {
    data class Menu(val orderId: Int) : ActiveSheet
    data class Pay(val orderId: Int) : ActiveSheet
    data class Count(val orderId: Int, val next: OrderStatus) : ActiveSheet
    data class Reschedule(val orderId: Int, val kind: String) : ActiveSheet
    data class Cancel(val orderId: Int) : ActiveSheet
    data class CustomerForm(
        val editId: String?, val ctx: String,
        val prefillName: String = "", val prefillPhone: String = "",
        val needAddress: Boolean = false,
        val onSaved: (String) -> Unit = {},
    ) : ActiveSheet
    data class Receive(val custId: String) : ActiveSheet
    data class AddOld(val custId: String) : ActiveSheet
    data class Quick(val day: String, val onCreated: (Int) -> Unit = {}) : ActiveSheet
    data class BillView(val orderId: Int) : ActiveSheet
    data class Share(val text: String) : ActiveSheet
}

/** Renders whichever sheet is active. Screens just set/clear [active]. */
@Composable
fun SheetHost(
    active: ActiveSheet?,
    state: LaundryState,
    vm: ShopViewModel,
    navigator: AppNavigator,
    onOpen: (ActiveSheet) -> Unit,
    onDismiss: () -> Unit,
) {
    when (active) {
        null -> Unit
        is ActiveSheet.Menu -> OrderMenuSheet(state, active.orderId, navigator, onOpen, onDismiss)
        is ActiveSheet.Pay -> CollectPaymentSheet(state, active.orderId, vm, onDismiss)
        is ActiveSheet.Count -> CountClothesSheet(state, active.orderId, active.next, vm, onDismiss)
        is ActiveSheet.Reschedule -> RescheduleSheet(state, active.orderId, active.kind, vm, onDismiss)
        is ActiveSheet.Cancel -> CancelSheet(state, active.orderId, vm, onDismiss)
        is ActiveSheet.CustomerForm -> CustomerFormSheet(state, active, vm, onDismiss)
        is ActiveSheet.Receive -> ReceivePaymentSheet(state, active.custId, vm, onDismiss)
        is ActiveSheet.AddOld -> AddOldBaakiSheet(state, active.custId, vm, onDismiss)
        is ActiveSheet.Quick -> QuickOrderSheet(state, active.day, active.onCreated, vm, onDismiss)
        is ActiveSheet.BillView -> BillViewSheet(state, active.orderId, onDismiss)
        is ActiveSheet.Share -> ShareSummarySheet(active.text, vm, onDismiss)
    }
}
