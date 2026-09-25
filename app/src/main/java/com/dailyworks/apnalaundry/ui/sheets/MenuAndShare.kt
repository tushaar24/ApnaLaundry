package com.dailyworks.apnalaundry.ui.sheets

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.OrderStatus
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.ShopViewModel
import com.dailyworks.apnalaundry.ui.components.AppBottomSheet
import com.dailyworks.apnalaundry.ui.components.PrimaryButton
import com.dailyworks.apnalaundry.ui.components.fig
import com.dailyworks.apnalaundry.ui.components.rounded
import com.dailyworks.apnalaundry.ui.components.tap
import com.dailyworks.apnalaundry.ui.nav.AppNavigator
import com.dailyworks.apnalaundry.ui.theme.Tokens

@Composable
private fun MenuRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).tap(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = if (danger) Tokens.OrangeText else Tokens.InkSecondary, modifier = Modifier.size(22.dp))
        Text(label, style = fig(16, FontWeight.SemiBold, if (danger) Tokens.OrangeText else Tokens.Ink))
    }
}

@Composable
fun OrderMenuSheet(
    state: LaundryState, orderId: Int, navigator: AppNavigator,
    onOpen: (ActiveSheet) -> Unit, onDismiss: () -> Unit,
) {
    val o = Selectors.order(state, orderId) ?: return onDismiss()
    val c = Selectors.customer(state, o.custId)
    val context = LocalContext.current

    AppBottomSheet(title = c.name, subtitle = "#${o.id} · +91 ${Selectors.fmtPhone(c.phone)}", onDismiss = onDismiss) {
        Column {
            MenuRow(Icons.Outlined.Visibility, "View order details") { onDismiss(); navigator.openOrder(o.id) }
            if (o.lines.isNotEmpty()) MenuRow(Icons.AutoMirrored.Outlined.ReceiptLong, "View / send bill") { onDismiss(); navigator.openBill(o.id) }
            MenuRow(Icons.Outlined.Edit, "Edit order / bill") { onDismiss(); navigator.openNewOrder(editId = o.id, from = "home") }
            if (o.status == OrderStatus.CREATED || o.status == OrderStatus.RECEIVED)
                MenuRow(Icons.Outlined.Check, "Mark delivered now") { onOpen(ActiveSheet.Pay(o.id)) }
            if (o.status == OrderStatus.CREATED)
                MenuRow(Icons.Outlined.CalendarMonth, "Reschedule pickup") { onOpen(ActiveSheet.Reschedule(o.id, "pickup")) }
            if (o.status == OrderStatus.CREATED || o.status == OrderStatus.RECEIVED || o.status == OrderStatus.READY)
                MenuRow(Icons.Outlined.LocalShipping, if (o.deliveryDate.isNotBlank()) "Reschedule delivery" else "Set delivery date") { onOpen(ActiveSheet.Reschedule(o.id, "drop")) }
            MenuRow(Icons.Outlined.Person, "Open customer khata") { onDismiss(); navigator.openCustomer(o.custId, "home") }
            MenuRow(Icons.Filled.Call, "Call ${Selectors.firstName(c.name)}") {
                onDismiss()
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+91${c.phone}"))) }
            }
            if (o.status == OrderStatus.CREATED)
                MenuRow(Icons.Outlined.Cancel, "Cancel this pickup", danger = true) { onOpen(ActiveSheet.Cancel(o.id)) }
        }
    }
}

@Composable
fun ShareSummarySheet(text: String, vm: ShopViewModel, onDismiss: () -> Unit) {
    AppBottomSheet(title = "Share summary", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth().rounded(14.dp).background(Tokens.Card).padding(14.dp)) {
                Text(text, style = fig(14, FontWeight.SemiBold, Tokens.InkSecondary))
            }
            PrimaryButton("Send on WhatsApp", height = 54.dp) {
                vm.showInfo("Opening WhatsApp with this message…"); onDismiss()
            }
        }
    }
}
