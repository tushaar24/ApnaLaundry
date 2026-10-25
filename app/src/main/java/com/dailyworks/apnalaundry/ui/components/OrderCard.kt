package com.dailyworks.apnalaundry.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.dailyworks.apnalaundry.core.tr
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
    /** Search results span all dates: say which day in the meta line. */
    showDate: Boolean = false,
    /** On the action button ("Mark ready" …) — e.g. the guide pointing at it. */
    actionModifier: Modifier = Modifier,
) {
    val o = order
    val c = Selectors.customer(state, o.custId)
    val counted = o.lines.isNotEmpty()
    val home = if (pickupTab) o.pickup == Route.HOME else o.delivery == Route.HOME
    val amt = LaundryMath.amtOf(o)
    val addr = c.address.ifBlank { tr("address not saved") }

    val where = if (pickupTab) {
        if (home) tr("Pick up from {address}", "address" to addr) + (if (o.pickupTime.isNotBlank()) " · ${o.pickupTime}" else "") else tr("Walk-in at shop")
    } else {
        if (home) tr("Deliver to {address}", "address" to addr) + (if (o.deliveryTime.isNotBlank()) " · ${o.deliveryTime}" else "")
        else if (o.deliveryTime.isNotBlank()) tr("Customer collects at shop · by {time}", "time" to o.deliveryTime)
        else tr("Customer collects at shop")
    }
    val showWhere = home || (!pickupTab && o.deliveryTime.isNotBlank())

    val warn = when {
        !pickupTab && o.deliveryDate.isBlank() && o.status in listOf(OrderStatus.CREATED, OrderStatus.RECEIVED, OrderStatus.READY) -> tr("No delivery date · tap ⋯ to set")
        late -> tr("Late · was due {date}", "date" to AppDate.plain(if (pickupTab) o.pickupDate else o.deliveryDate))
        !pickupTab && o.status == OrderStatus.CREATED -> tr("Not picked up yet")
        !pickupTab && o.status == OrderStatus.RECEIVED -> tr("Not ready yet")
        else -> ""
    }

    // payment label
    val (payLabel, payFg) = if (o.status == OrderStatus.DELIVERED) {
        when {
            o.paid >= amt -> tr("Paid") to Tokens.BlueText
            o.paid > 0 -> tr("Part paid") to Tokens.OrangeText
            else -> tr("In khata") to Tokens.OrangeText
        }
    } else {
        when {
            o.pre >= amt && amt > 0 -> tr("Paid") to Tokens.BlueText
            o.pre > 0 -> tr("{amount} more to collect", "amount" to Money.rupees(amt - o.pre)) to Tokens.OrangeText
            else -> tr("Unpaid") to Tokens.OrangeText
        }
    }

    val actLabel = when (o.status) {
        OrderStatus.CREATED -> tr("Mark picked up")
        OrderStatus.RECEIVED -> tr("Mark ready")
        OrderStatus.READY -> tr("Mark delivered")
        else -> ""
    }
    val canAct = actLabel.isNotEmpty()
    val doneText = when (o.status) {
        OrderStatus.DELIVERED -> tr("Delivered") + (if (o.doneAt.isNotBlank()) " · ${o.doneAt}" else "")
        // The reason is saved in English; the usual ones show in the app's language.
        OrderStatus.CANCELLED -> tr("Cancelled") + (if (o.cancelReason.isNotBlank()) " · ${tr(o.cancelReason)}" else "")
        else -> ""
    }

    val whenText = when {
        !showDate -> ""
        pickupTab -> tr("Pickup {date}", "date" to AppDate.plain(o.pickupDate)) + " · "
        o.deliveryDate.isNotBlank() -> tr("Delivery {date}", "date" to AppDate.plain(o.deliveryDate)) + " · "
        else -> tr("No delivery date") + " · "
    }
    val meta = "#${o.no()} · " + whenText + if (counted) "${Selectors.itemsLabel(o)} · ${Selectors.svcLabel(o)}"
    else if (o.pickup == Route.HOME && o.status == OrderStatus.CREATED) tr("Clothes will be counted at pickup")
    else (if (o.pieces > 0) tr("{n} pieces", "n" to o.pieces) + " · " else "") + tr("Bill not made yet")

    val cardBorder = if (late) Tokens.OrangeBorder else Tokens.CardBorder

    AppCard(borderColor = cardBorder) {
        Column(Modifier.fillMaxWidth().tap(onClick = onOpen).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // A long name gets "…" — the EXPRESS tag and the amount always show.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.name, Modifier.weight(1f, fill = false), style = fig(16, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (o.express) ExpressTag()
                }
                if (counted) {
                    Spacer(Modifier.width(8.dp))
                    Text(Money.rupees(amt), style = fig(16, FontWeight.Bold), maxLines = 1, softWrap = false)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text(meta, style = fig(13, color = Tokens.Muted), modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (counted && o.status != OrderStatus.CANCELLED) {
                    // Capped, so "₹1,250 more to collect" can't squeeze the order details into a thin strip.
                    Text(payLabel, style = fig(13, FontWeight.Bold, payFg), textAlign = TextAlign.End, maxLines = 2, modifier = Modifier.widthIn(max = 120.dp))
                }
            }
            if (o.note.isNotBlank()) {
                Text(
                    tr("Note: {note}", "note" to o.note),
                    style = fig(13, FontWeight.SemiBold, androidx.compose.ui.graphics.Color(0xFF6B5500)),
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().rounded(6.dp).background(androidx.compose.ui.graphics.Color(0xFFFFF8DC))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (showWhere) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (home) Icons.Outlined.Home else Icons.Outlined.Storefront, null, tint = Tokens.InkSecondary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Text(where, style = fig(14, color = Tokens.InkSecondary), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (warn.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(7.dp).rounded(999.dp).background(Tokens.Orange))
                    Text(warn, style = fig(13, FontWeight.Bold, Tokens.OrangeText), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canAct) {
                    Box(
                        Modifier.weight(1f).then(actionModifier).heightIn(min = 48.dp).rounded(12.dp).background(Tokens.BlueLight).tap(onClick = onAct)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(actLabel, style = fig(15, FontWeight.Bold, Tokens.Blue), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Box(Modifier.weight(1f).height(48.dp).rounded(12.dp).background(Tokens.Bg).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        Text(doneText, style = fig(14, FontWeight.SemiBold, Tokens.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (home && canAct) {
                    IconSquare(Icons.Filled.Call, tr("Call")) { onCall() }
                }
                IconSquare(Icons.Filled.MoreVert, tr("More")) { onMore() }
            }
        }
    }
}

@Composable
fun ExpressTag() {
    Box(Modifier.rounded(999.dp).background(Tokens.OrangeLight).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(tr("EXPRESS"), style = fig(11, FontWeight.Bold, Tokens.OrangeText).copy(letterSpacing = 0.04.em))
    }
}

@Composable
private fun IconSquare(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).rounded(12.dp).background(Tokens.Card).border(1.dp, Tokens.CardBorder, RoundedCornerShape(12.dp)).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, cd, tint = Tokens.InkSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun StatusPill(label: String, bg: Color, fg: Color) {
    Box(Modifier.rounded(999.dp).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, style = fig(12, FontWeight.Bold, fg), maxLines = 1, softWrap = false)
    }
}
