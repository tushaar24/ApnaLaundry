package com.dailyworks.apnalaundry.ui.screens.assistant

import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.Money
import com.dailyworks.apnalaundry.domain.Customer
import com.dailyworks.apnalaundry.domain.EarningsMath
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LaundryState
import com.dailyworks.apnalaundry.domain.LedgerKind
import com.dailyworks.apnalaundry.domain.Order
import com.dailyworks.apnalaundry.domain.OrderLine
import com.dailyworks.apnalaundry.domain.OrderStatus
import com.dailyworks.apnalaundry.domain.PayMethod
import com.dailyworks.apnalaundry.domain.PricingMode
import com.dailyworks.apnalaundry.domain.Route
import com.dailyworks.apnalaundry.domain.Service
import com.dailyworks.apnalaundry.ui.Selectors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.abs

/** What a tool puts in the chat for the owner to check and tap — nothing is written or sent without that tap. */
sealed interface AssistantCard {
    /** A new order priced from the rate card, waiting for "Save order". [key] tracks its save. */
    data class NewOrder(val key: String, val draft: OrderDraft) : AssistantCard
    data class SendBill(val orderId: Int) : AssistantCard
    data class StatusUpdates(val orderIds: List<Int>) : AssistantCard
    data class Reminder(val custId: String) : AssistantCard
    /** Any message: to the customer's chat, or (no customer) WhatsApp asks whom — a chat or the owner's Status. */
    data class WhatsAppText(val text: String, val custId: String?) : AssistantCard
    /** Picked up / ready — the usual "Tell the customer?" WhatsApp question follows the tap. */
    data class MarkOrder(val orderId: Int, val to: OrderStatus, val deliveryDate: String) : AssistantCard
}

/**
 * A new order as the assistant understood it. Saved through the same
 * ShopViewModel.saveOrder as New order, so it prices, numbers and syncs the
 * same way. [custId] null = a new customer from [newName] / [newPhone].
 */
data class OrderDraft(
    val custId: String?,
    val newName: String,
    val newPhone: String,
    /** Set on the customer when saving (home pickup / delivery needs one); "" = keep theirs. */
    val address: String,
    val pickup: Route,
    val delivery: Route,
    val lines: List<OrderLine>,
    val deliveryDate: String,
    val ddAuto: Boolean,
    val express: Boolean,
    val exPct: Int,
    /** The express charge on these clothes (saveOrder stores it as given). */
    val exAmt: Int,
    val note: String,
    val total: Int,
)

/** A tool's answer for the model, and what (if anything) to show the owner. */
class ToolOutcome(val json: String, val card: AssistantCard? = null)

/**
 * The assistant's tools, run on the phone against the shop on screen. Reads
 * answer straight away; anything that writes or sends only makes an
 * [AssistantCard] for the owner to tap. Status words, khata and money follow
 * the app's own maths (LaundryMath, EarningsMath, StatusMessage).
 */
class AssistantTools(private val st: LaundryState, private val today: String = AppDate.TODAY) {

    fun run(name: String, rawArgs: String): ToolOutcome {
        val a = runCatching { Json.parseToJsonElement(rawArgs.ifBlank { "{}" }).jsonObject }.getOrNull()
            ?: return err("Arguments were not valid JSON")
        return try {
            when (name) {
                "find_customers" -> findCustomers(a)
                "customer_khata" -> customerKhata(a)
                "find_orders" -> findOrders(a)
                "order_details" -> orderDetails(a)
                "sales_report" -> salesReport(a)
                "baaki_list" -> baakiList(a)
                "rate_list" -> ToolOutcome(rateCard())
                "create_order" -> createOrder(a)
                "send_bill" -> sendBill(a)
                "send_status_update" -> sendStatus(a)
                "send_khata_reminder" -> reminder(a)
                "whatsapp_message" -> whatsappMessage(a)
                "mark_order" -> markOrder(a)
                else -> err("Unknown tool $name")
            }
        } catch (e: Exception) {
            err("Tool failed: ${e.message ?: e::class.simpleName}")
        }
    }

    // ---------------- reads ----------------

    private fun findCustomers(a: JsonObject): ToolOutcome {
        val q = a.str("query")
        if (q.isBlank()) return err("Give a name or phone number")
        val all = matchCustomers(q)
        // The owner said one customer's full name exactly: that's them — no "which one?".
        val exact = all.filter { norm(it.name) == norm(q) }
        val found = (if (exact.size == 1) exact else all).take(8)
        return ok {
            put("count", found.size)
            if (exact.size == 1) put("exact_match", true)
            putJsonArray("customers") { found.forEach { add(customerBrief(it)) } }
            if (found.isEmpty()) put("note", "No customer matches \"$q\". Check the spelling, or it may be a new customer.")
        }
    }

    private fun customerKhata(a: JsonObject): ToolOutcome {
        val c = customerArg(a) ?: return err("No such customer_id — use find_customers first")
        val bal = LaundryMath.balance(c.id, st.ledger, st.orders)
        val mine = st.orders.filter { it.custId == c.id }
        val open = mine.filter { LaundryMath.isOpen(it) }
        val entries = st.ledger.filter { it.custId == c.id }.sortedWith(compareByDescending<com.dailyworks.apnalaundry.domain.LedgerEntry> { it.date }.thenByDescending { it.ts }).take(10)
        return ok {
            put("customer_id", c.id); put("name", c.name); put("phone", c.phone)
            if (c.address.isNotBlank()) put("address", c.address)
            put("baaki", maxOf(0, bal))
            if (bal < 0) put("advance", -bal)
            put("khata", if (bal > 0) "owes ${Money.rupees(bal)}" else if (bal < 0) "${Money.rupees(bal)} advance with the shop" else "all clear")
            put("orders_total", Selectors.orderCount(st, c))
            put("billed_all_time", mine.filter { it.status == OrderStatus.DELIVERED }.sumOf { LaundryMath.amtOf(it) })
            putJsonArray("open_orders") { open.sortedByDescending { it.id }.take(10).forEach { add(orderBrief(it)) } }
            putJsonArray("recent_khata") {
                entries.forEach { e ->
                    add(buildJsonObject {
                        put("date", e.date)
                        put("entry", when (e.kind) {
                            LedgerKind.BILL -> "Bill #${orderNo(e.ref)}"
                            LedgerKind.OLD -> "Old baaki added"
                            LedgerKind.ADJ -> "Bill #${orderNo(e.ref)} changed"
                            LedgerKind.GOT -> "Got " + when (e.method) { PayMethod.UPI -> "UPI"; PayMethod.CASH -> "cash"; PayMethod.NONE -> "payment" }
                        })
                        put("amount", if (e.kind == LedgerKind.GOT) -e.amt else e.amt)
                    })
                }
            }
        }
    }

    private fun findOrders(a: JsonObject): ToolOutcome {
        var list = st.orders.asSequence()
        a.str("customer_id").takeIf { it.isNotBlank() }?.let { id -> list = list.filter { it.custId == id } }
        when (a.str("status").lowercase()) {
            "", "any", "all" -> Unit
            "open" -> list = list.filter { LaundryMath.isOpen(it) }
            else -> statusOf(a.str("status"))?.let { s -> list = list.filter { it.status == s } }
                ?: return err("status must be one of to_pick_up, received, ready, delivered, cancelled, open")
        }
        val from = a.date("date_from"); val to = a.date("date_to")
        val field = a.str("date_field").ifBlank { "created" }
        if (from != null || to != null) {
            val lo = from ?: "0000-00-00"; val hi = to ?: "9999-99-99"
            list = list.filter { o ->
                val d = when (field) {
                    "pickup" -> o.pickupDate
                    "delivery" -> o.deliveryDate
                    "delivered" -> o.doneDate
                    else -> o.createdOn
                }
                d.isNotBlank() && d >= lo && d <= hi
            }
        }
        if (a.bool("unpaid") == true) list = list.filter { LaundryMath.amtOf(it) - maxOf(it.paid, it.pre) > 0 && it.status != OrderStatus.CANCELLED }
        if (a.bool("bill_not_sent") == true) list = list.filter { !it.billSent && it.lines.isNotEmpty() && it.status != OrderStatus.CANCELLED }
        val all = list.sortedByDescending { it.id }.toList()
        val limit = (a.int("limit") ?: 15).coerceIn(1, 25)
        return ok {
            put("count", all.size)
            put("total_value", all.filter { it.status != OrderStatus.CANCELLED }.sumOf { LaundryMath.amtOf(it) })
            putJsonArray("orders") { all.take(limit).forEach { add(orderBrief(it)) } }
            if (all.size > limit) put("note", "Showing the newest $limit of ${all.size}")
        }
    }

    private fun orderDetails(a: JsonObject): ToolOutcome {
        val o = orderArg(a) ?: return err("No order with that number")
        val c = Selectors.customer(st, o.custId)
        val total = LaundryMath.amtOf(o)
        val paid = if (o.status == OrderStatus.DELIVERED) o.paid else o.pre
        return ok {
            put("order_no", o.no())
            put("customer", c.name); put("customer_id", c.id); put("phone", c.phone)
            put("status", statusWord(o.status))
            if (o.status == OrderStatus.CANCELLED && o.cancelReason.isNotBlank()) put("cancel_reason", o.cancelReason)
            put("taken_on", o.createdOn)
            put("pickup", if (o.pickup == Route.HOME) "home pickup ${o.pickupDate} ${o.pickupTime}".trim() else "at shop")
            put("delivery", if (o.delivery == Route.HOME) "home delivery" else "customer collects at shop")
            if (o.deliveryDate.isNotBlank()) put("ready_by", o.deliveryDate)
            if (o.doneDate.isNotBlank()) put("delivered_on", o.doneDate)
            put("total", total); put("paid", paid); put("due", maxOf(0, total - paid))
            put("bill_sent", o.billSent)
            if (o.note.isNotBlank()) put("note", o.note)
            putJsonArray("items") {
                o.lines.forEach { l ->
                    add(buildJsonObject {
                        put("service", l.serviceName)
                        if (l.kg > 0) put("kg", l.kg) else { put("item", l.itemName); put("qty", l.qty); put("rate", l.price) }
                        put("amount", l.amt)
                    })
                }
            }
            if (o.lines.isEmpty()) put("items_note", "Clothes not counted yet")
        }
    }

    private fun salesReport(a: JsonObject): ToolOutcome {
        val from = a.date("from") ?: today
        val to = a.date("to") ?: today
        if (from > to) return err("from is after to")
        val e = EarningsMath.compute(st) { it.isNotBlank() && it >= from && it <= to }
        val taken = st.orders.filter { it.createdOn in from..to && it.status != OrderStatus.CANCELLED }
        val top = st.orders.filter { it.status == OrderStatus.DELIVERED && it.doneDate in from..to }
            .groupBy { it.custId }.mapValues { (_, os) -> os.sumOf { LaundryMath.amtOf(it) } }
            .entries.sortedByDescending { it.value }.take(5)
        return ok {
            put("from", from); put("to", to)
            put("money_received", e.received); put("cash", e.cash); put("upi", e.upi)
            put("work_delivered_value", e.work)
            put("orders_delivered", e.ordersDelivered)
            put("pieces", e.pieces); if (e.kg > 0) put("kg", e.kg)
            put("new_baaki_from_these_orders", e.baakiAdded)
            put("old_baaki_collected", e.oldIn)
            if (e.discounts > 0) put("discounts_given", e.discounts)
            put("orders_taken", taken.size)
            put("orders_taken_value", taken.sumOf { LaundryMath.amtOf(it) })
            putJsonArray("by_service") { e.byService.forEach { s -> add(buildJsonObject { put("name", s.label); put("amount", s.amount) }) } }
            putJsonArray("top_customers") {
                top.forEach { (id, amt) -> add(buildJsonObject { put("name", Selectors.customer(st, id).name); put("amount", amt) }) }
            }
            put("baaki_in_market_now", e.baakiMarket)
            put("customers_with_baaki_now", e.baakiCustomers)
            put("open_orders_now", st.orders.count { LaundryMath.isOpen(it) })
        }
    }

    private fun baakiList(a: JsonObject): ToolOutcome {
        val limit = (a.int("limit") ?: 10).coerceIn(1, 30)
        val dues = st.customers.map { it to LaundryMath.balance(it.id, st.ledger, st.orders) }.filter { it.second > 0 }.sortedByDescending { it.second }
        return ok {
            put("customers_with_baaki", dues.size)
            put("total_baaki", dues.sumOf { it.second })
            putJsonArray("customers") {
                dues.take(limit).forEach { (c, b) -> add(buildJsonObject { put("customer_id", c.id); put("name", c.name); put("phone", c.phone); put("baaki", b) }) }
            }
        }
    }

    /** The rate card, compact — also in the system prompt. */
    fun rateCard(maxChars: Int = 6000): String = buildString {
        for (s in st.services) {
            if (length > maxChars) { append("… more services: call rate_list"); break }
            append(s.id).append(": ").append(s.name)
            if (s.mode == PricingMode.WEIGHT) {
                append(" (by weight) — ₹").append(s.ratePerKg ?: 0).append("/kg")
                s.minKg?.takeIf { it > 0 }?.let { append(", min ").append(Selectors.trimKg(it)).append(" kg") }
            } else {
                append(" (per piece) — ")
                append(s.items.filter { (it.price ?: 0) > 0 }.joinToString(", ") { "${it.name} ₹${it.price}" })
            }
            s.readyInDays?.let { append(" · ready in ").append(it).append(if (it == 1) " day" else " days") }
            append('\n')
        }
    }.trim()

    // ---------------- writes (cards only) ----------------

    private fun createOrder(a: JsonObject): ToolOutcome {
        // The customer: an existing one, or a new one (name + 10-digit phone).
        var cust = customerArg(a)
        val newName = a.str("new_customer_name").trim()
        val newPhone = a.str("new_customer_phone").filter { it.isDigit() }.takeLast(10)
        if (cust == null && a.str("customer_id").isNotBlank()) return err("No such customer_id — use find_customers first")
        if (cust == null) {
            if (newName.isBlank()) return err("Which customer? Use find_customers, or give new_customer_name and new_customer_phone for a new one")
            if (newPhone.length != 10) return err("A new customer needs a 10-digit mobile number — ask the owner for it")
            st.customers.firstOrNull { it.phone == newPhone }?.let { cust = it } // already saved: use them
        }
        val pickup = if (a.str("pickup") == "home") Route.HOME else Route.SHOP
        val delivery = if (a.str("delivery") == "home") Route.HOME else Route.SHOP
        val address = a.str("address").trim()
        if ((pickup == Route.HOME || delivery == Route.HOME) && (cust?.address.isNullOrBlank()) && address.isBlank()) {
            return err("Home pickup / delivery needs the customer's address — ask for it and pass address")
        }

        val lines = mutableListOf<OrderLine>()
        val used = mutableListOf<Service>()
        a["items"]?.let { el ->
            for (it in (el as? JsonArray).orEmpty()) {
                val o = it as? JsonObject ?: continue
                val qty = o.int("qty") ?: 1
                if (qty <= 0) continue
                val itemName = o.str("item")
                val svc = pieceService(o.str("service_id"), itemName) ?: return err(pieceServiceError(o.str("service_id"), itemName))
                val item = matchItem(svc, itemName)
                    ?: return err("\"$itemName\" is not on ${svc.name}'s rate card. Items: " + svc.items.filter { (it.price ?: 0) > 0 }.joinToString(", ") { it.name })
                val price = item.price ?: 0
                val prev = lines.indexOfFirst { l -> l.serviceId == svc.id && l.itemName == item.name && l.kg == 0.0 }
                if (prev >= 0) {
                    val l = lines[prev]; lines[prev] = l.copy(qty = l.qty + qty, amt = (l.qty + qty) * l.price)
                } else lines += OrderLine(svc.id, svc.name, item.name, qty, price, price, 0.0, qty * price)
                used += svc
            }
        }
        a["weights"]?.let { el ->
            for (it in (el as? JsonArray).orEmpty()) {
                val o = it as? JsonObject ?: continue
                val kg = o.dbl("kg") ?: continue
                if (kg <= 0) continue
                val id = o.str("service_id")
                val svc = st.services.firstOrNull { s -> s.id == id && s.mode == PricingMode.WEIGHT }
                    ?: st.services.filter { s -> s.mode == PricingMode.WEIGHT }.singleOrNull()
                    ?: return err("Which by-weight service? Options: " + st.services.filter { s -> s.mode == PricingMode.WEIGHT }.joinToString(", ") { s -> "${s.id} (${s.name})" })
                val rate = svc.ratePerKg ?: 0
                lines += OrderLine(svc.id, svc.name, "By weight", 0, rate, rate, kg, LaundryMath.weightAmount(kg, rate, svc.minKg ?: 0.0))
                used += svc
            }
        }
        if (lines.isEmpty() && pickup != Route.HOME) {
            return err("No clothes — say which items and how many (or kg), or make it a home pickup to count later")
        }

        // Delivery date: the owner's, else the longest ready time of the services used (as New order does).
        val asked = a.date("delivery_date")
        val tat = used.filter { it.readyInDays != null }.maxByOrNull { it.readyInDays ?: 0 }
        val auto = tat?.let { AppDate.add(today, it.readyInDays ?: 0) } ?: ""
        val (dd, ddAuto) = when {
            asked != null && asked >= today -> asked to false
            else -> auto to auto.isNotBlank()
        }
        val express = a.bool("express") == true
        val exPct = if (express) st.shop.expressPct else 0
        val priced = priced(lines, express, exPct)
        val total = LaundryMath.amtOf(priced)
        val draft = OrderDraft(
            custId = cust?.id, newName = if (cust == null) newName else "", newPhone = if (cust == null) newPhone else "",
            address = address, pickup = pickup, delivery = delivery, lines = lines,
            deliveryDate = dd, ddAuto = ddAuto, express = express, exPct = exPct, exAmt = priced.exAmt,
            note = a.str("note").trim().take(500), total = total,
        )
        val card = AssistantCard.NewOrder("d${System.nanoTime()}", draft)
        return ToolOutcome(buildJsonObject {
            put("shown_to_owner", true)
            put("saved", false)
            put("customer", cust?.name ?: "$newName (new customer)")
            put("items", lines.joinToString(", ") { if (it.kg > 0) "${it.serviceName} ${Selectors.trimKg(it.kg)} kg" else "${it.itemName} × ${it.qty} (${it.serviceName})" }.ifBlank { "pickup — clothes counted later" })
            put("total", total)
            if (dd.isNotBlank()) put("ready_by", dd)
            put("next", "NOT saved yet. Tell them (\"you\"): check it and tap Save order.")
        }.toString(), card)
    }

    private fun sendBill(a: JsonObject): ToolOutcome {
        val o = orderArg(a) ?: return err("No order with that number")
        if (o.lines.isEmpty()) return err("Order #${o.no()} has no bill yet — its clothes aren't counted")
        val c = Selectors.customer(st, o.custId)
        return ToolOutcome(buildJsonObject {
            put("shown_to_owner", true); put("order_no", o.no()); put("customer", c.name); put("total", LaundryMath.amtOf(o))
            put("next", "Nothing sent yet. Tell them (\"you\"): tap Send bill on WhatsApp.")
            if (c.phone.length != 10) put("warning", "No 10-digit phone saved — WhatsApp will ask whom to send to")
        }.toString(), AssistantCard.SendBill(o.id))
    }

    private fun sendStatus(a: JsonObject): ToolOutcome {
        val nos = a["order_nos"]?.let { el -> (el as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } }.orEmpty()
            .ifEmpty { listOfNotNull(a.str("order_no").takeIf { it.isNotBlank() }) }
        if (nos.isEmpty()) return err("Give order_nos")
        val orders = nos.distinct().map { no -> findOrder(no) ?: return err("No order #$no") }.take(15)
        return ToolOutcome(buildJsonObject {
            put("shown_to_owner", true); put("orders", orders.size)
            putJsonArray("updates") { orders.forEach { o -> add(buildJsonObject { put("order_no", o.no()); put("customer", Selectors.customer(st, o.custId).name); put("status", statusWord(o.status)) }) } }
            put("next", "Nothing sent yet. Tell them (\"you\"): tap Send under each order — it opens that customer's WhatsApp chat with the update typed in.")
        }.toString(), AssistantCard.StatusUpdates(orders.map { it.id }))
    }

    private fun reminder(a: JsonObject): ToolOutcome {
        val c = customerArg(a) ?: return err("No such customer_id — use find_customers first")
        val bal = LaundryMath.balance(c.id, st.ledger, st.orders)
        if (bal <= 0) return err("${c.name} has no baaki (${if (bal < 0) "${Money.rupees(bal)} advance" else "all clear"})")
        return ToolOutcome(buildJsonObject {
            put("shown_to_owner", true); put("customer", c.name); put("baaki", bal)
            put("next", "Nothing sent yet. Tell them (\"you\"): tap Remind on WhatsApp.")
        }.toString(), AssistantCard.Reminder(c.id))
    }

    private fun whatsappMessage(a: JsonObject): ToolOutcome {
        val text = a.str("text").trim()
        if (text.isBlank()) return err("Give the message text")
        val c = customerArg(a)
        if (a.str("customer_id").isNotBlank() && c == null) return err("No such customer_id")
        return ToolOutcome(buildJsonObject {
            put("shown_to_owner", true)
            put("to", c?.name ?: "the owner picks a chat or their WhatsApp Status")
            put("next", "Nothing sent yet. Tell them (\"you\"): tap Open WhatsApp" + if (c == null) ", then pick chats or My Status." else ".")
        }.toString(), AssistantCard.WhatsAppText(text.take(1500), c?.id))
    }

    private fun markOrder(a: JsonObject): ToolOutcome {
        val o = orderArg(a) ?: return err("No order with that number")
        val to = when (a.str("to").lowercase()) {
            "picked_up", "received" -> OrderStatus.RECEIVED
            "ready" -> OrderStatus.READY
            else -> return err("to must be picked_up or ready. Delivering (with payment) and cancelling are done on the order screen.")
        }
        if (o.status == to) return err("Order #${o.no()} is already ${statusWord(to)}")
        if (!LaundryMath.isOpen(o)) return err("Order #${o.no()} is ${statusWord(o.status)} — change it on the order screen")
        if (to == OrderStatus.RECEIVED && o.status != OrderStatus.CREATED) return err("Order #${o.no()} is already at the shop (${statusWord(o.status)})")
        if (o.lines.isEmpty()) return err("Order #${o.no()} has no clothes counted — open the order and count them first")
        val dd = a.date("delivery_date")?.takeIf { it >= today } ?: ""
        return ToolOutcome(buildJsonObject {
            put("shown_to_owner", true); put("order_no", o.no()); put("customer", Selectors.customer(st, o.custId).name); put("to", statusWord(to))
            put("next", "Not changed yet. Tell them (\"you\"): tap the button below; the app then offers to tell the customer on WhatsApp.")
        }.toString(), AssistantCard.MarkOrder(o.id, to, dd))
    }

    // ---------------- helpers ----------------

    /** The order as it will save: express from its %, the shop's GST — for the total on the card. */
    private fun priced(lines: List<OrderLine>, express: Boolean, exPct: Int): Order {
        val o = Order(
            id = 0, custId = "", pickup = Route.SHOP, delivery = Route.SHOP, pickupDate = today, pickupTime = "",
            deliveryDate = "", deliveryTime = "", ddAuto = false, status = OrderStatus.RECEIVED, cancelReason = "",
            fee = 0, express = express, exAmt = 0, discount = 0, pre = 0, paid = 0, doneAt = "", doneDate = "",
            createdOn = today, billSent = false, pieces = 0, lines = lines, exPct = exPct,
            gstOn = st.shop.gstOn && st.shop.gstPct > 0, gstPct = st.shop.gstPct, gstMode = st.shop.gstMode,
        )
        return LaundryMath.withPctExtras(o, st.shop.expressPct)
    }

    private fun pieceService(id: String, item: String): Service? {
        val piece = st.services.filter { it.mode == PricingMode.PIECE }
        piece.firstOrNull { it.id == id }?.let { return it }
        if (id.isNotBlank()) piece.firstOrNull { norm(it.name) == norm(id) || norm(it.name).contains(norm(id)) }?.let { return it }
        return piece.filter { matchItem(it, item) != null }.singleOrNull()
    }

    private fun pieceServiceError(id: String, item: String): String {
        val has = st.services.filter { it.mode == PricingMode.PIECE && matchItem(it, item) != null }
        return when {
            has.size > 1 -> "\"$item\" is in several services: ${has.joinToString(", ") { "${it.id} (${it.name})" }} — ask the owner which one"
            id.isNotBlank() -> "No per-piece service \"$id\". Services: " + st.services.joinToString(", ") { "${it.id} (${it.name})" }
            else -> "\"$item\" is not on the rate card. Services: " + st.services.joinToString(", ") { "${it.id} (${it.name})" }
        }
    }

    private fun matchItem(s: Service, raw: String): com.dailyworks.apnalaundry.domain.ServiceItem? {
        val q = singular(norm(raw))
        if (q.isEmpty()) return null
        val items = s.items.filter { (it.price ?: 0) > 0 }
        items.firstOrNull { singular(norm(it.name)) == q }?.let { return it }
        items.firstOrNull { singular(norm(it.name)).startsWith(q) || q.startsWith(singular(norm(it.name))) }?.let { return it }
        items.filter { norm(it.name).contains(q) }.singleOrNull()?.let { return it }
        return items.filter { lev(singular(norm(it.name)), q) <= (if (q.length >= 6) 2 else 1) }.singleOrNull()
    }

    private fun matchCustomers(raw: String): List<Customer> {
        val digits = raw.filter { it.isDigit() }
        if (digits.length >= 4) return st.customers.filter { it.phone.contains(digits) }
        val q = norm(raw.lowercase().split(Regex("\\s+")).filterNot { it in HONORIFICS }.joinToString(" "))
        if (q.isEmpty()) return emptyList()
        fun score(c: Customer): Int {
            val n = norm(c.name)
            val tokens = c.name.lowercase().split(Regex("\\s+")).map { norm(it) }.filter { it.isNotEmpty() }
            return when {
                n == q -> 100
                n.startsWith(q) -> 80
                tokens.any { it.startsWith(q) } -> 70
                n.contains(q) -> 60
                tokens.any { lev(it, q) <= (if (q.length >= 5) 2 else 1) } -> 40
                else -> 0
            }
        }
        return st.customers.map { it to score(it) }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Customer, Int>> { it.second }.thenBy { it.first.agoRank })
            .map { it.first }
    }

    private fun customerBrief(c: Customer): JsonObject = buildJsonObject {
        val bal = LaundryMath.balance(c.id, st.ledger, st.orders)
        put("customer_id", c.id); put("name", c.name); put("phone", c.phone)
        put("baaki", maxOf(0, bal)); if (bal < 0) put("advance", -bal)
        put("open_orders", st.orders.count { it.custId == c.id && LaundryMath.isOpen(it) })
        put("last_order", c.lastLabel)
    }

    private fun orderBrief(o: Order): JsonObject = buildJsonObject {
        val total = LaundryMath.amtOf(o)
        val paid = if (o.status == OrderStatus.DELIVERED) o.paid else o.pre
        put("order_no", o.no())
        put("customer", Selectors.customer(st, o.custId).name)
        put("status", statusWord(o.status))
        put("items", Selectors.itemsLabel(o).ifBlank { "not counted" })
        put("total", total)
        if (o.status != OrderStatus.CANCELLED) put("due", maxOf(0, total - paid))
        put("taken_on", o.createdOn)
        if (o.deliveryDate.isNotBlank() && LaundryMath.isOpen(o)) put("ready_by", o.deliveryDate)
        if (o.doneDate.isNotBlank()) put("delivered_on", o.doneDate)
        if (o.lines.isNotEmpty()) put("bill_sent", o.billSent)
    }

    private fun customerArg(a: JsonObject): Customer? = a.str("customer_id").takeIf { it.isNotBlank() }?.let { id -> st.customers.firstOrNull { it.id == id } }

    private fun orderArg(a: JsonObject): Order? = findOrder(a.str("order_no"))

    /** "#1005", "1005", or an owner's serial ("A-12"); the newest if a serial repeats. */
    private fun findOrder(raw: String): Order? {
        val no = raw.trim().removePrefix("#").trim()
        if (no.isEmpty()) return null
        return st.orders.filter { it.no().equals(no, ignoreCase = true) }.maxByOrNull { it.id }
            ?: no.toIntOrNull()?.let { id -> st.orders.firstOrNull { it.id == id } }
    }

    private fun orderNo(id: Int?): String = id?.let { i -> st.orders.firstOrNull { it.id == i }?.no() ?: i.toString() } ?: "?"

    private fun ok(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = ToolOutcome(clip(buildJsonObject(block).toString()))
    private fun err(msg: String) = ToolOutcome(buildJsonObject { put("error", msg) }.toString())
    private fun clip(s: String) = if (s.length <= 6000) s else s.take(6000) + "…(cut)"

    companion object {
        private val HONORIFICS = setOf("ji", "bhai", "bhaiya", "bhaiyya", "didi", "aunty", "uncle", "sir", "madam", "mam", "ka", "ki", "ke", "ko")

        fun statusWord(s: OrderStatus) = when (s) {
            OrderStatus.CREATED -> "to_pick_up"; OrderStatus.RECEIVED -> "received"; OrderStatus.READY -> "ready"
            OrderStatus.DELIVERED -> "delivered"; OrderStatus.CANCELLED -> "cancelled"
        }

        fun statusOf(w: String): OrderStatus? = when (w.lowercase().replace(' ', '_')) {
            "to_pick_up", "pickup", "to_pickup", "created" -> OrderStatus.CREATED
            "received", "picked_up", "in_progress", "at_shop" -> OrderStatus.RECEIVED
            "ready" -> OrderStatus.READY
            "delivered", "done" -> OrderStatus.DELIVERED
            "cancelled", "canceled" -> OrderStatus.CANCELLED
            else -> null
        }

        private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        private fun singular(s: String) = when {
            s.length > 4 && s.endsWith("es") && !s.endsWith("ses") -> s.dropLast(2)
            s.length > 3 && s.endsWith("s") -> s.dropLast(1)
            else -> s
        }

        private fun lev(a: String, b: String): Int {
            if (abs(a.length - b.length) > 3) return 99
            val d = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                var prev = d[0]; d[0] = i
                for (j in 1..b.length) {
                    val tmp = d[j]
                    d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                    prev = tmp
                }
            }
            return d[b.length]
        }

        private fun JsonObject.str(k: String): String = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
        private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() ?: it.contentOrNull?.trim()?.toIntOrNull() }
        private fun JsonObject.dbl(k: String): Double? = (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.trim()?.toDoubleOrNull() }
        private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.lowercase()?.let { s -> s == "true" || s == "yes" } }
        /** An ISO date (yyyy-MM-dd), else null. */
        private fun JsonObject.date(k: String): String? = str(k).trim().takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) && runCatching { AppDate.parse(it) }.isSuccess }

        private fun obj(vararg props: Pair<String, JsonElement>, required: List<String> = emptyList()) = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
            if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
        }
        private fun s(desc: String) = buildJsonObject { put("type", "string"); put("description", desc) }
        private fun e(desc: String, vararg values: String) = buildJsonObject {
            put("type", "string"); put("description", desc); putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } }
        }
        private fun i(desc: String) = buildJsonObject { put("type", "integer"); put("description", desc) }
        private fun n(desc: String) = buildJsonObject { put("type", "number"); put("description", desc) }
        private fun b(desc: String) = buildJsonObject { put("type", "boolean"); put("description", desc) }
        private fun arr(desc: String, item: JsonObject) = buildJsonObject { put("type", "array"); put("description", desc); put("items", item) }

        private fun fn(name: String, desc: String, params: JsonObject) = buildJsonObject {
            put("type", "function")
            putJsonObject("function") { put("name", name); put("description", desc); put("parameters", params) }
        }

        private const val DATE = "yyyy-MM-dd"

        /** The tools, in the OpenAI / Sarvam function-calling shape. */
        val SCHEMA: JsonArray = buildJsonArray {
            add(fn("find_customers", "Find customers by name (spelling may be rough) or phone digits. Returns customer_id, phone, baaki, open orders.",
                obj("query" to s("Name or part of the phone number"), required = listOf("query"))))
            add(fn("customer_khata", "A customer's khata: baaki (what they owe) or advance, open orders, recent khata entries, address.",
                obj("customer_id" to s("From find_customers"), required = listOf("customer_id"))))
            add(fn("find_orders", "List orders, newest first. Filter by customer, status, dates. Use for 'which orders are ready', 'today's pickups', 'unpaid orders', 'bills not sent'.",
                obj(
                    "customer_id" to s("Only this customer's orders"),
                    "status" to e("Order status, or open = not delivered or cancelled", "to_pick_up", "received", "ready", "delivered", "cancelled", "open", "any"),
                    "date_from" to s("From date, $DATE"),
                    "date_to" to s("To date, $DATE"),
                    "date_field" to e("Which date the range is on: created = order taken, pickup, delivery = ready-by date, delivered = actually delivered", "created", "pickup", "delivery", "delivered"),
                    "unpaid" to b("Only orders with money still due"),
                    "bill_not_sent" to b("Only orders whose bill was not sent yet"),
                    "limit" to i("Max orders to return, default 15"),
                )))
            add(fn("order_details", "One order in full: customer, status, items with rates, total, paid, due, dates, bill sent.",
                obj("order_no" to s("Order number, e.g. 1005"), required = listOf("order_no"))))
            add(fn("sales_report", "Money and work for a date range: money received (cash / UPI), work delivered, orders taken, by service, top customers, baaki in market.",
                obj("from" to s("Start date, $DATE"), "to" to s("End date, $DATE"), required = listOf("from", "to"))))
            add(fn("baaki_list", "Customers who owe money (baaki), biggest first, with the total.",
                obj("limit" to i("How many, default 10"))))
            add(fn("rate_list", "The shop's full rate card (services, items, prices, ready time).", obj()))
            add(fn("create_order", "Prepare a NEW order for the owner to check and save. Prices come from the rate card. It is not saved until the owner taps Save order.",
                obj(
                    "customer_id" to s("Existing customer, from find_customers"),
                    "new_customer_name" to s("Only for a customer not in the app yet"),
                    "new_customer_phone" to s("10-digit mobile, needed for a new customer"),
                    "items" to arr("Per-piece clothes", obj(
                        "service_id" to s("Service id from the rate card, e.g. the iron / wash / dry clean service"),
                        "item" to s("Item name from the rate card, e.g. Shirt"),
                        "qty" to i("How many pieces"),
                        required = listOf("item", "qty"),
                    )),
                    "weights" to arr("By-weight services", obj("service_id" to s("By-weight service id"), "kg" to n("Weight in kg"), required = listOf("kg"))),
                    "pickup" to e("Clothes come to the shop, or the shop picks up from home (default shop)", "shop", "home"),
                    "delivery" to e("Customer collects, or the shop delivers home (default shop)", "shop", "home"),
                    "address" to s("Customer's address, needed for home pickup / delivery if they have none"),
                    "delivery_date" to s("Ready-by date $DATE if the owner said one; else the app sets it from the ready time"),
                    "express" to b("Express / urgent order (adds the shop's express charge)"),
                    "note" to s("Owner's note, e.g. starch the shirts"),
                )))
            add(fn("send_bill", "Show a button to send an order's bill picture to the customer on WhatsApp.",
                obj("order_no" to s("Order number"), required = listOf("order_no"))))
            add(fn("send_status_update", "Show WhatsApp buttons to send customers their order's status update (e.g. 'your clothes are ready'). One or many orders.",
                obj("order_nos" to arr("Order numbers", buildJsonObject { put("type", "string") }), required = listOf("order_nos"))))
            add(fn("send_khata_reminder", "Show a button to send a customer a WhatsApp reminder of their baaki.",
                obj("customer_id" to s("From find_customers"), required = listOf("customer_id"))))
            add(fn("whatsapp_message", "Show a button to send any WhatsApp message the owner asked for (offer, greeting, note, a Status post). With customer_id it opens that customer's chat; without, WhatsApp lets the owner pick chats or My Status.",
                obj("text" to s("The full message, ready to send"), "customer_id" to s("Optional: one customer"), required = listOf("text"))))
            add(fn("mark_order", "Show a button to mark an order picked up or ready (the app then offers to tell the customer on WhatsApp). Not for delivering or cancelling.",
                obj("order_no" to s("Order number"), "to" to e("New status", "picked_up", "ready"), "delivery_date" to s("Optional ready-by / delivery date $DATE"), required = listOf("order_no", "to"))))
        }
    }
}
