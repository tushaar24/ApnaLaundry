package com.dailyworks.apnalaundry.data

import android.net.Uri
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.domain.Customer
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.domain.OrderStatus
import com.dailyworks.apnalaundry.domain.Route

/**
 * The WhatsApp update a customer gets when their order changes status
 * (asterisks render bold). The owner sends it from the toast after the
 * change — nothing is sent by itself. Mirror of web domain/statusMessage.ts
 * (in the app's language; the English is word for word the web's).
 */
object StatusMessage {
    fun text(shopName: String, customerName: String, o: Order): String {
        val first = if (customerName.startsWith("+")) customerName else customerName.substringBefore(" ")
        val no = o.no()
        return when (o.status) {
            OrderStatus.CREATED ->
                if (o.pickupDate.isNotBlank()) tr("Hi {name}, we'll pick up your clothes for order #{no} on {date}. — *{shop}*", "name" to first, "no" to no, "date" to AppDate.plain(o.pickupDate), "shop" to shopName)
                else tr("Hi {name}, we'll pick up your clothes for order #{no}. — *{shop}*", "name" to first, "no" to no, "shop" to shopName)
            OrderStatus.RECEIVED ->
                (if (o.pickup == Route.HOME) tr("Hi {name}, we've picked up your clothes for order #{no} — *{shop}*.", "name" to first, "no" to no, "shop" to shopName)
                else tr("Hi {name}, we've received your clothes at *{shop}* (order #{no}).", "name" to first, "no" to no, "shop" to shopName)) + " " +
                    (if (o.deliveryDate.isNotBlank()) tr("They'll be ready by {date}.", "date" to AppDate.plain(o.deliveryDate)) else tr("We'll message you when they're ready."))
            OrderStatus.READY ->
                tr("Hi {name}, your clothes are ready at *{shop}* (order #{no}).", "name" to first, "no" to no, "shop" to shopName) +
                    (if (o.lines.isNotEmpty()) " " + tr("Total *{amount}*.", "amount" to Money.rupees(LaundryMath.amtOf(o))) else "") + " " +
                    when {
                        o.delivery != Route.HOME -> tr("You can collect them anytime.")
                        o.deliveryDate.isNotBlank() -> tr("We'll deliver them on {date}.", "date" to AppDate.plain(o.deliveryDate))
                        else -> tr("We'll deliver them soon.")
                    }
            OrderStatus.DELIVERED -> tr("Hi {name}, your order #{no} has been delivered. Thank you for choosing *{shop}*!", "name" to first, "no" to no, "shop" to shopName)
            OrderStatus.CANCELLED -> tr("Hi {name}, your order #{no} at *{shop}* has been cancelled.", "name" to first, "no" to no, "shop" to shopName)
        }
    }

    /** wa.me link that opens the customer's chat with the update typed in. */
    fun link(shopName: String, c: Customer, o: Order): String {
        val digits = c.phone.filter { it.isDigit() }.takeLast(10)
        val t = Uri.encode(text(shopName, c.name, o))
        return if (digits.length == 10) "https://wa.me/91$digits?text=$t" else "https://wa.me/?text=$t"
    }
}
