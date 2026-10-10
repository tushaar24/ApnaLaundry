package com.dailyworks.apnalaundry.data

import android.net.Uri
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.Customer
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.domain.OrderStatus
import com.dailyworks.apnalaundry.domain.Route

/**
 * The WhatsApp update a customer gets when their order changes status
 * (asterisks render bold). The owner sends it from the toast after the
 * change — nothing is sent by itself. Mirror of web domain/statusMessage.ts.
 */
object StatusMessage {
    fun text(shopName: String, customerName: String, o: Order): String {
        val first = if (customerName.startsWith("+")) customerName else customerName.substringBefore(" ")
        val hi = "Hi $first, "
        val no = "#${o.no()}"
        val shop = "*$shopName*"
        return when (o.status) {
            OrderStatus.CREATED ->
                hi + "we'll pick up your clothes for order $no" + (if (o.pickupDate.isNotBlank()) " on ${AppDate.plain(o.pickupDate)}" else "") + ". — $shop"
            OrderStatus.RECEIVED ->
                hi + (if (o.pickup == Route.HOME) "we've picked up your clothes for order $no — $shop." else "we've received your clothes at $shop (order $no).") +
                    (if (o.deliveryDate.isNotBlank()) " They'll be ready by ${AppDate.plain(o.deliveryDate)}." else " We'll message you when they're ready.")
            OrderStatus.READY ->
                hi + "your clothes are ready at $shop (order $no)." +
                    (if (o.lines.isNotEmpty()) " Total *${Money.rupees(LaundryMath.amtOf(o))}*." else "") +
                    when {
                        o.delivery != Route.HOME -> " You can collect them anytime."
                        o.deliveryDate.isNotBlank() -> " We'll deliver them on ${AppDate.plain(o.deliveryDate)}."
                        else -> " We'll deliver them soon."
                    }
            OrderStatus.DELIVERED -> hi + "your order $no has been delivered. Thank you for choosing $shop!"
            OrderStatus.CANCELLED -> hi + "your order $no at $shop has been cancelled."
        }
    }

    /** wa.me link that opens the customer's chat with the update typed in. */
    fun link(shopName: String, c: Customer, o: Order): String {
        val digits = c.phone.filter { it.isDigit() }.takeLast(10)
        val t = Uri.encode(text(shopName, c.name, o))
        return if (digits.length == 10) "https://wa.me/91$digits?text=$t" else "https://wa.me/?text=$t"
    }
}
