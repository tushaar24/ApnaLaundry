package com.dailyworks.apnalaundry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.domain.OrderStatus
import com.dailyworks.apnalaundry.domain.Route
import com.dailyworks.apnalaundry.ui.Selectors
import com.dailyworks.apnalaundry.ui.theme.Tokens

@Composable
fun OrderCard(
    state: LaundryState,
    order: Order,
    pickupTab: Boolean,
    late: Boolean = false,
    onOpen: () -> Unit,
    onAct: () -> Unit,
    onMore: () -> Unit,
    onCall: () -> Unit,
) {
    val o = order
    val c = Selectors.customer(state, o.custId)
    val counted = o.lines.isNotEmpty()
    val home = if (pickupTab) o.pickup == Route.HOME else o.delivery == Route.HOME
    val amt = LaundryMath.amtOf(o)
    val addr = c.address.ifBlank { "address not saved" }

    val where = if (pickupTab) {
        if (home) "Pick up from $addr" + (if (o.pickupTime.isNotBlank()) " · ${o.pickupTime}" else "") else "Walk-in at shop"
    } else {
        if (home) "Deliver to $addr" + (if (o.deliveryTime.isNotBlank()) " · ${o.deliveryTime}" else "")
        else "Customer collects at shop" + (if (o.deliveryTime.isNotBlank()) " · by ${o.deliveryTime}" else "")
    }
    val showWhere = home || (!pickupTab && o.deliveryTime.isNotBlank())

    val warn = when {
        !pickupTab && o.deliveryDate.isBlank() && o.status in listOf(OrderStatus.CREATED, OrderStatus.RECEIVED, OrderStatus.READY) -> "No delivery date · tap ⋯ to set"
        late -> "Late · was due ${AppDate.plain(if (pickupTab) o.pickupDate else o.deliveryDate)}"
        !pickupTab && o.status == OrderStatus.CREATED -> "Not picked up yet"
        !pickupTab && o.status == OrderStatus.RECEIVED -> "Not ready yet"
        else -> ""
    }

    // payment label
    val (payLabel, payFg) = if (o.status == OrderStatus.DELIVERED) {
        when {
            o.paid >= amt -> "Paid" to Tokens.BlueText
            o.paid > 0 -> "Part paid" to Tokens.OrangeText
            else -> "In khata" to Tokens.OrangeText
        }
    } else {
        when {
            o.pre >= amt && amt > 0 -> "Paid" to Tokens.BlueText
            o.pre > 0 -> "${Money.rupees(amt - o.pre)} more to collect" to Tokens.OrangeText
            else -> "Unpaid" to Tokens.OrangeText
        }
    }

    val actLabel = when (o.status) {
        OrderStatus.CREATED -> "Mark picked up"
        OrderStatus.RECEIVED -> "Mark ready"
        OrderStatus.READY -> "Mark delivered"
        else -> ""
    }
    val canAct = actLabel.isNotEmpty()
    val doneText = when (o.status) {
        OrderStatus.DELIVERED -> "Delivered" + (if (o.doneAt.isNotBlank()) " · ${o.doneAt}" else "")
        OrderStatus.CANCELLED -> "Cancelled" + (if (o.cancelReason.isNotBlank()) " · ${o.cancelReason}" else "")
        else -> ""
    }

    val meta = "#${o.id} · " + if (counted) "${Selectors.itemsLabel(o)} · ${Selectors.svcLabel(o)}"
    else if (o.pickup == Route.HOME && o.status == OrderStatus.CREATED) "Clothes will be counted at pickup"
    else (if (o.pieces > 0) "${o.pieces} pieces · " else "") + "Bill not made yet"

    val cardBorder = if (late) Tokens.OrangeBorder else Tokens.CardBorder

    AppCard(borderColor = cardBorder) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth().tap(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.name, style = fig(17, FontWeight.Bold), maxLines = 1)
                    if (o.express) ExpressTag()
                }
                if (counted) Text(Money.rupees(amt), style = fig(17, FontWeight.Bold))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(meta, style = fig(13, color = Tokens.Muted), modifier = Modifier.weight(1f))
                if (counted && o.status != OrderStatus.CANCELLED) Text(payLabel, style = fig(13, FontWeight.Bold, payFg))
            }
            if (showWhere) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (home) Icons.Outlined.Home else Icons.Outlined.Storefront, null, tint = Tokens.InkSecondary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Text(where, style = fig(14, color = Tokens.InkSecondary))
                }
            }
            if (warn.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(7.dp).rounded(999.dp).background(Tokens.Orange))
                    Text(warn, style = fig(13, FontWeight.Bold, Tokens.OrangeText))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canAct) {
                    Box(Modifier.weight(1f).height(46.dp).rounded(12.dp).background(Tokens.BlueLight).tap(onClick = onAct), contentAlignment = Alignment.Center) {
                        Text(actLabel, style = fig(15, FontWeight.Bold, Tokens.Blue))
                    }
                } else {
                    Box(Modifier.weight(1f).height(46.dp).rounded(12.dp).background(Tokens.Bg).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        Text(doneText, style = fig(14, FontWeight.SemiBold, Tokens.Muted))
                    }
                }
                if (home && canAct) {
                    IconSquare(Icons.Filled.Call, "Call") { onCall() }
                }
                IconSquare(Icons.Filled.MoreVert, "More") { onMore() }
            }
        }
    }
}

@Composable
fun ExpressTag() {
    Box(Modifier.rounded(999.dp).background(Tokens.OrangeLight).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text("EXPRESS", style = fig(11, FontWeight.Bold, Tokens.OrangeText).copy(letterSpacing = 0.04.em))
    }
}

@Composable
private fun IconSquare(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).rounded(12.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(12.dp)).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, cd, tint = Tokens.InkSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun StatusPill(label: String, bg: Color, fg: Color) {
    Box(Modifier.rounded(999.dp).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, style = fig(12, FontWeight.Bold, fg))
    }
}
