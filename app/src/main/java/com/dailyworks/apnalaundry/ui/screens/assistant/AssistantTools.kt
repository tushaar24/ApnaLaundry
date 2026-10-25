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
    /**
     * Any status, backward too. Delivered carries the money got now ([payments],
     * empty = all to khata); the usual "Tell the customer?" WhatsApp question follows the tap.
     */
    data class StatusChange(
        val orderId: Int, val to: OrderStatus, val payments: List<Pair<PayMethod, Int>>,
        val deliveryDate: String, val reason: String,
    ) : AssistantCard
    /** Money got: on an open order ([orderId]) it's paid in advance, else it goes to the customer's khata. */
    data class Payment(val custId: String, val orderId: Int?, val amount: Int, val method: PayMethod) : AssistantCard
    /** Items, prices on this order, express, discount, dates, note — saved like Edit order. */
    /** Worked out again from [args] when tapped (AssistantTools.planEdit), so two edits of one order both stick. */
    data class EditOrder(val orderId: Int, val args: JsonObject, val changes: List<String>, val before: Int, val after: Int) : AssistantCard
    /** A rate-card change, worked out again from [args] when tapped (AssistantTools.planRate). */
    data class RateChange(val args: JsonObject, val isNew: Boolean, val summary: List<String>) : AssistantCard
    /** A new customer ([editId] null) or changed details. */
    data class SaveCustomer(val editId: String?, val name: String, val phone: String, val address: String, val oldBaaki: Int, val changes: List<String>) : AssistantCard
    data class OldBaaki(val custId: String, val amount: Int) : AssistantCard
    data class DeleteOrder(val orderId: Int) : AssistantCard
}

/** An order after the assistant's edit — what ShopViewModel.saveOrder(editId = …) takes. */
data class OrderEdit(
    /** Lines of services still on the rate card (saveOrder keeps deleted services' lines itself). */
    val lines: List<OrderLine>,
    val quickAmount: Int,
    val quickPieces: Int,
    val express: Boolean,
    val exPct: Int,
    val exAmt: Int,
    val discount: Int,
    val discPct: Int,
    val pickupDate: String,
    val deliveryDate: String,
    val ddAuto: Boolean,
    val note: String,
)

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

/** An order edit worked out against the shop as it is now. */
data class EditPlan(val order: Order, val edit: OrderEdit, val changes: List<String>, val before: Int, val after: Int)

/** A rate-card change: [service] = the whole service after it (new or edited); [deleteId] = delete that one. */
data class RatePlan(val service: Service?, val deleteId: String?, val isNew: Boolean, val summary: List<String>)

/** A tool can't do what was asked; the message goes back to the model (or to the owner when tapped). */
class PlanException(message: String) : Exception(message)

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
                "change_status" -> changeStatus(a)
                "record_payment" -> recordPayment(a)
                "edit_order" -> editOrder(a)
                "edit_rate_card" -> editRateCard(a)
                "save_customer" -> saveCustomer(a)
                "add_old_baaki" -> addOldBaaki(a)
                "delete_order" -> deleteOrder(a)
                else -> err("Unknown tool $name")
            }
        } catch (e: PlanException) {
            err(e.message ?: "Can't do that")
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
        val cust = customerArg(a)
        if (cust != null) list = list.filter { it.custId == cust.id }
        else if (a.str("customer_id").isNotBlank()) return err("No such customer_id — use find_customers")
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
        // The customer: an existing one (by id, number or name — taken automatically),
        // else a new one, which needs a name and a 10-digit number.
        val newName = a.str("customer_name").ifBlank { a.str("new_customer_name") }.trim()
        val newPhone = a.str("customer_phone").ifBlank { a.str("new_customer_phone") }.filter { it.isDigit() }.takeLast(10)
        val cust: Customer? = when (val l = lookup(a)) {
            is Lookup.One -> l.c
            // Same name as others but a number none of them has: a new customer.
            is Lookup.Many -> return err(manyText(l.cs, newName.ifBlank { newPhone }))
            // Their number is new: another person with that name — a new customer (said in the result).
            is Lookup.OtherNumber, Lookup.None, Lookup.NotGiven -> null
        }
        val sameName = (lookup(a) as? Lookup.OtherNumber)?.cs.orEmpty()
        if (cust == null) {
            if (a.str("customer_id").isNotBlank() && newName.isBlank()) return err("No such customer_id — use find_customers first")
            if (newName.isBlank()) return err("Which customer? Give their name (and number if new)")
            if (newPhone.length != 10) return err("\"$newName\" is not in the app yet — a new customer. Ask for their 10-digit mobile number.")
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
            put("customer_is", if (cust != null) "already in the app (…${cust.phone.takeLast(4)}) — taken automatically" else "new — the app adds them on Save")
            if (cust == null && sameName.isNotEmpty()) {
                put("note", "Another customer has this name: " + sameName.joinToString("; ") { "${it.name} …${it.phone.takeLast(4)}" } + " — the number differs, so this is a new customer. Mention it.")
            }
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

    /**
     * Any status, as the Change status sheet: forward or back. Delivered needs
     * the money got now (or "all on khata"); leaving delivered takes the bill off
     * the khata (LaundryRepository.setStatus).
     */
    private fun changeStatus(a: JsonObject): ToolOutcome {
        val o = orderArg(a) ?: return err("No order with that number")
        val to = statusOf(a.str("to")) ?: return err("to must be one of to_pick_up, received, ready, delivered, cancelled")
        if (o.status == to) return err("Order #${o.no()} is already ${statusWord(to)}")
        if (to == OrderStatus.CREATED && o.pickup != Route.HOME) return err("Only a home-pickup order can go back to to_pick_up; use received")
        val needsClothes = o.lines.isEmpty() && (
            to == OrderStatus.DELIVERED || (to == OrderStatus.READY && LaundryMath.isOpen(o)) ||
                (to == OrderStatus.RECEIVED && o.status == OrderStatus.CREATED)
            )
        if (needsClothes) return err("Order #${o.no()} has no clothes counted yet. Ask which clothes, add them with edit_order, then change the status.")
        val c = Selectors.customer(st, o.custId)
        var payments = emptyList<Pair<PayMethod, Int>>()
        val out = buildJsonObject {
            put("order_no", o.no()); put("customer", c.name); put("from", statusWord(o.status)); put("to", statusWord(to))
            if (o.status == OrderStatus.DELIVERED) put("note", "Its bill comes off the khata (payments stay as paid in advance)")
        }.toMutableMap()
        if (to == OrderStatus.DELIVERED) {
            val total = LaundryMath.amtOf(o)
            val billDue = maxOf(0, total - o.pre)
            val oldBal = LaundryMath.balance(o.custId, st.ledger, st.orders, o.id)
            val method = methodOf(a.str("method"))
            val cash = a.int("cash_amount"); val upi = a.int("upi_amount"); val amount = a.int("amount")
            payments = when {
                cash != null || upi != null -> listOf(PayMethod.CASH to (cash ?: 0), PayMethod.UPI to (upi ?: 0)).filter { it.second > 0 }
                a.bool("paid_full") == true -> if (billDue > 0) listOf(method to billDue) else emptyList()
                amount != null -> if (amount > 0) listOf(method to amount) else emptyList()
                a.bool("nothing_paid") == true || billDue == 0 -> emptyList()
                else -> return err(
                    "Ask how much the customer paid now and cash or UPI — or if it all goes on khata. Bill due ${Money.rupees(billDue)}" +
                        (if (oldBal > 0) ", old baaki ${Money.rupees(oldBal)}" else "") + ".",
                )
            }
            val got = payments.sumOf { it.second }
            val alloc = LaundryMath.deliverAllocation(total, o.pre, oldBal, got)
            out["bill_total"] = JsonPrimitive(total)
            out["got_now"] = JsonPrimitive(got)
            out["khata_after"] = JsonPrimitive(
                when {
                    alloc.newBalance > 0 -> "${Money.rupees(alloc.newBalance)} baaki"
                    alloc.newBalance < 0 -> "${Money.rupees(alloc.newBalance)} advance"
                    else -> "all clear"
                },
            )
        }
        val dd = a.date("delivery_date")?.takeIf { it >= today } ?: ""
        val reason = a.str("reason").trim().take(100)
        out["shown_to_owner"] = JsonPrimitive(true)
        out["button"] = JsonPrimitive(statusButton(o.status, to))
        out["next"] = JsonPrimitive("Not changed yet. Tell them (\"you\"): check and tap '${statusButton(o.status, to)}'.")
        return ToolOutcome(JsonObject(out).toString(), AssistantCard.StatusChange(o.id, to, payments, dd, reason))
    }

    /** "Paid" / "part paid": on an open order it's paid in advance; otherwise it goes to the khata. */
    private fun recordPayment(a: JsonObject): ToolOutcome {
        val o = a.str("order_no").takeIf { it.isNotBlank() }?.let { findOrder(it) ?: return err("No order #$it") }
        val c = o?.let { Selectors.customer(st, it.custId) } ?: customerArg(a)
            ?: return err("Whose payment? Give order_no or customer_id (find_customers)")
        if (o?.status == OrderStatus.CANCELLED) return err("Order #${o.no()} is cancelled — take it as a khata payment with customer_id instead")
        val method = methodOf(a.str("method"))
        val open = o != null && LaundryMath.isOpen(o)
        val bal = LaundryMath.balance(c.id, st.ledger, st.orders)
        val full = when {
            o != null && open -> LaundryMath.amtOf(o) - o.pre
            o != null -> LaundryMath.amtOf(o) - o.paid // delivered with some left
            else -> bal
        }
        val amount = if (a.bool("paid_full") == true) full else a.int("amount")
            ?: return err("How much did they pay (₹)? Due: ${Money.rupees(maxOf(0, full))}")
        if (amount <= 0) return err(if (a.bool("paid_full") == true) "Nothing is due — already paid" else "Amount must be more than ₹0")
        return ToolOutcome(buildJsonObject {
            put("shown_to_owner", true); put("customer", c.name); put("amount", amount); put("method", method.name.lowercase())
            if (o != null) put("order_no", o.no())
            put("goes_to", if (open) "paid in advance on order #${o!!.no()}" else "the customer's khata")
            put("khata_after", (bal - amount).let { if (it > 0) "${Money.rupees(it)} baaki" else if (it < 0) "${Money.rupees(it)} advance" else "all clear" })
            put("next", "Not recorded yet. Tell them (\"you\"): check cash / UPI and tap the button.")
        }.toString(), AssistantCard.Payment(c.id, if (open) o!!.id else null, amount, method))
    }

    /** Add / remove clothes, this order's rates, express, discount, dates, note — like Edit order. */
    fun planEdit(a: JsonObject): EditPlan {
        val o = orderArg(a) ?: fail("No order with that number")
        if (o.status == OrderStatus.CANCELLED) fail("Order #${o.no()} is cancelled — change its status first")
        val onCard = st.services.map { it.id }.toSet()
        val lines = o.lines.filter { !it.isQuick && it.serviceId in onCard }.toMutableList()
        val quick = o.lines.firstOrNull { it.isQuick }
        val changes = mutableListOf<String>()
        val used = mutableListOf<Service>()

        for (el in (a["add_items"] as? JsonArray).orEmpty()) {
            val x = el as? JsonObject ?: continue
            val qty = x.int("qty") ?: 1
            if (qty <= 0) continue
            val name = x.str("item")
            val svc = pieceService(x.str("service_id"), name) ?: fail(pieceServiceError(x.str("service_id"), name))
            val item = matchItem(svc, name)
                ?: fail("\"$name\" is not on ${svc.name}'s rate card. Items: " + svc.items.filter { (it.price ?: 0) > 0 }.joinToString(", ") { it.name } + ". To add it there use edit_rate_card.")
            val i = lines.indexOfFirst { it.serviceId == svc.id && it.itemName == item.name && it.kg == 0.0 }
            val rate = x.int("rate")?.takeIf { it > 0 } ?: if (i >= 0) lines[i].price else item.price ?: 0
            if (i >= 0) {
                val l = lines[i]; lines[i] = l.copy(qty = l.qty + qty, price = rate, amt = (l.qty + qty) * rate)
            } else lines += OrderLine(svc.id, svc.name, item.name, qty, rate, item.price ?: 0, 0.0, qty * rate)
            used += svc
            changes += "+ ${item.name} × $qty (${svc.name}) ₹${qty * rate}"
        }
        for (el in (a["remove_items"] as? JsonArray).orEmpty()) {
            val x = el as? JsonObject ?: continue
            val i = lineIndex(lines, x.str("service_id"), x.str("item")) ?: fail("No \"${x.str("item")}\" on order #${o.no()}. It has: " + lines.joinToString(", ") { "${it.itemName} × ${it.qty} (${it.serviceName})" })
            val l = lines[i]
            val n = x.int("qty")
            if (n == null || n >= l.qty || l.kg > 0) { lines.removeAt(i); changes += "− ${l.itemName} (${l.serviceName})" }
            else { lines[i] = l.copy(qty = l.qty - n, amt = (l.qty - n) * l.price); changes += "− ${l.itemName} × $n (${l.serviceName})" }
        }
        for (el in (a["set_rates"] as? JsonArray).orEmpty()) {
            val x = el as? JsonObject ?: continue
            val rate = x.int("rate") ?: continue
            val i = lineIndex(lines, x.str("service_id"), x.str("item")) ?: fail("No \"${x.str("item")}\" on order #${o.no()}")
            val l = lines[i]
            if (l.kg > 0) fail("${l.serviceName} is by weight — its rate is per kg on the rate card")
            lines[i] = l.copy(price = rate, amt = l.qty * rate)
            changes += "${l.itemName} rate ₹${l.price} → ₹$rate (this order)"
        }
        for (el in (a["weights"] as? JsonArray).orEmpty()) {
            val x = el as? JsonObject ?: continue
            val kg = x.dbl("kg") ?: continue
            val svc = st.services.firstOrNull { it.id == x.str("service_id") && it.mode == PricingMode.WEIGHT }
                ?: st.services.filter { it.mode == PricingMode.WEIGHT }.singleOrNull()
                ?: fail("Which by-weight service? " + st.services.filter { it.mode == PricingMode.WEIGHT }.joinToString(", ") { "${it.id} (${it.name})" })
            val i = lines.indexOfFirst { it.serviceId == svc.id && it.kg > 0 }
            if (kg <= 0) { if (i >= 0) { lines.removeAt(i); changes += "− ${svc.name} by weight" }; continue }
            val rate = svc.ratePerKg ?: 0
            val l = OrderLine(svc.id, svc.name, "By weight", if (i >= 0) lines[i].qty else 0, rate, rate, kg, LaundryMath.weightAmount(kg, rate, svc.minKg ?: 0.0), clothes = if (i >= 0) lines[i].clothes else emptyList())
            if (i >= 0) lines[i] = l else lines += l
            used += svc
            changes += "${svc.name} ${Selectors.trimKg(kg)} kg"
        }

        var express = o.express; var exPct = o.exPct; var exAmt = o.exAmt
        a.bool("express")?.let { on ->
            if (on != o.express) {
                express = on; exPct = if (on) st.shop.expressPct else 0; exAmt = 0
                changes += if (on) "Express on (${st.shop.expressPct}%)" else "Express off"
            }
        }
        var discount = o.discount; var discPct = o.discPct
        a.int("discount_pct")?.let { p -> discPct = p.coerceIn(0, 100); discount = 0; changes += "Discount $discPct%" }
        a.int("discount")?.let { d -> if (a.int("discount_pct") == null) { discount = maxOf(0, d); discPct = 0; changes += "Discount ₹$discount" } }
        var pickupDate = o.pickupDate
        a.date("pickup_date")?.let { pickupDate = it; changes += "Pickup ${AppDate.short(it)}" }
        var deliveryDate = o.deliveryDate; var ddAuto = o.ddAuto
        a.date("delivery_date")?.let { deliveryDate = it; ddAuto = false; changes += "Ready by ${AppDate.short(it)}" }
        if (deliveryDate.isBlank() && used.isNotEmpty()) {
            // First clothes on a pickup order: the ready date from the longest ready time, as New order does.
            used.filter { it.readyInDays != null }.maxByOrNull { it.readyInDays ?: 0 }?.let { deliveryDate = AppDate.add(today, it.readyInDays ?: 0); ddAuto = true }
        }
        var note = o.note
        if (a.containsKey("note")) { note = a.str("note").trim().take(500); changes += if (note.isBlank()) "Note removed" else "Note: $note" }
        if (changes.isEmpty()) fail("Nothing to change — say what to add, remove or change")

        val kept = o.lines.filter { !it.isQuick && it.serviceId !in onCard }
        val after = LaundryMath.withPctExtras(
            o.copy(
                lines = kept + listOfNotNull(quick) + lines, express = express, exPct = exPct, exAmt = if (express) exAmt else 0,
                discount = discount, discPct = discPct,
            ),
            st.shop.expressPct,
        )
        val before = LaundryMath.amtOf(o)
        val total = LaundryMath.amtOf(after)
        val edit = OrderEdit(
            lines = lines, quickAmount = quick?.amt ?: 0, quickPieces = quick?.qty ?: 0,
            express = express, exPct = exPct, exAmt = after.exAmt, discount = after.discount, discPct = discPct,
            pickupDate = pickupDate, deliveryDate = deliveryDate, ddAuto = ddAuto, note = note,
        )
        return EditPlan(o, edit, changes, before, total)
    }

    private fun editOrder(a: JsonObject): ToolOutcome {
        val p = planEdit(a)
        val o = p.order
        return ToolOutcome(buildJsonObject {
            put("shown_to_owner", true); put("order_no", o.no()); put("customer", Selectors.customer(st, o.custId).name)
            putJsonArray("changes") { p.changes.forEach { add(JsonPrimitive(it)) } }
            put("total_before", p.before); put("total_after", p.after)
            if (o.status == OrderStatus.DELIVERED && p.after != p.before) put("khata", "the customer's khata changes by ${if (p.after > p.before) "+" else "−"}${Money.rupees(abs(p.after - p.before))}")
            put("next", "Not saved yet. Tell them (\"you\"): check and tap Save changes.")
        }.toString(), AssistantCard.EditOrder(o.id, a, p.changes, p.before, p.after))
    }

    /** Prices, items, new services, weight rates, ready days, names, deleting a service. */
    fun planRate(a: JsonObject): RatePlan {
        val action = a.str("action")
        fun svc(): Service? {
            val id = a.str("service_id")
            return st.services.firstOrNull { it.id == id } ?: id.takeIf { it.isNotBlank() }?.let { q -> st.services.firstOrNull { norm(it.name) == norm(q) } ?: st.services.filter { norm(it.name).contains(norm(q)) }.singleOrNull() }
        }
        val services = st.services.joinToString(", ") { "${it.id} (${it.name})" }
        fun card(s: Service?, del: String?, isNew: Boolean, summary: List<String>) = RatePlan(s, del, isNew, summary)

        return when (action) {
            "set_price", "add_item" -> {
                val s = svc() ?: fail("Which service? $services")
                if (s.mode != PricingMode.PIECE) fail("${s.name} is by weight — use set_weight_rate")
                val name = a.str("item").trim()
                val price = a.int("price") ?: fail("What price (₹)?")
                if (name.isBlank() || price <= 0) fail("Give the item and a price above ₹0")
                val hit = s.items.indexOfFirst { singular(norm(it.name)) == singular(norm(name)) }
                    .takeIf { it >= 0 } ?: matchItemIndex(s, name)
                if (hit != null) {
                    val old = s.items[hit]
                    val items = s.items.toMutableList().also { it[hit] = old.copy(price = price) }
                    card(s.copy(items = items), null, false, listOf("${s.name} · ${old.name}: " + (old.price?.takeIf { it > 0 }?.let { "₹$it → " } ?: "") + "₹$price"))
                } else {
                    val nice = name.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                    card(s.copy(items = s.items + com.dailyworks.apnalaundry.domain.ServiceItem(nice, price)), null, false, listOf("${s.name} · new item $nice ₹$price"))
                }
            }
            "remove_item" -> {
                val s = svc() ?: fail("Which service? $services")
                val i = matchItemIndex(s, a.str("item")) ?: fail("\"${a.str("item")}\" isn't on ${s.name}")
                val items = s.items.toMutableList().also { it[i] = it[i].copy(price = null) }
                card(s.copy(items = items), null, false, listOf("${s.name} · ${s.items[i].name} removed (not offered)"))
            }
            "set_weight_rate" -> {
                val s = svc()?.takeIf { it.mode == PricingMode.WEIGHT } ?: st.services.filter { it.mode == PricingMode.WEIGHT }.singleOrNull()
                    ?: fail("Which by-weight service? $services")
                val rate = a.int("rate_per_kg") ?: s.ratePerKg ?: fail("What rate per kg?")
                val min = a.dbl("min_kg") ?: s.minKg
                card(s.copy(ratePerKg = rate, minKg = min), null, false, listOf("${s.name}: ₹${s.ratePerKg ?: 0}/kg → ₹$rate/kg" + (min?.let { " · min ${Selectors.trimKg(it)} kg" } ?: "")))
            }
            "set_ready_days" -> {
                val s = svc() ?: fail("Which service? $services")
                val d = a.int("ready_in_days")?.takeIf { it in 0..60 }
                card(s.copy(readyInDays = d), null, false, listOf("${s.name}: ready in " + (d?.let { "$it day" + if (it == 1) "" else "s" } ?: "— (no ready time)")))
            }
            "rename_service" -> {
                val s = svc() ?: fail("Which service? $services")
                val n = a.str("name").trim().take(40).ifBlank { fail("New name?") }
                card(s.copy(name = n), null, false, listOf("${s.name} → $n"))
            }
            "add_service" -> {
                val n = a.str("name").trim().take(40).ifBlank { fail("What is the service called?") }
                if (st.services.any { norm(it.name) == norm(n) }) fail("$n is already on the rate card")
                val weight = a.str("mode") == "weight"
                val given = (a["items"] as? JsonArray).orEmpty().mapNotNull { el ->
                    val x = el as? JsonObject ?: return@mapNotNull null
                    val nm = x.str("item").trim(); val p = x.int("price")
                    if (nm.isBlank() || p == null || p <= 0) null else com.dailyworks.apnalaundry.domain.ServiceItem(nm.replaceFirstChar { it.uppercase() }, p)
                }
                if (!weight && given.isEmpty()) fail("Which items and prices? e.g. Shirt ₹40, Pant ₹40")
                if (weight && (a.int("rate_per_kg") ?: 0) <= 0) fail("What rate per kg?")
                // Like Add service: the usual clothes listed too, no price (not offered) until set.
                val base = st.services.firstOrNull { it.mode == PricingMode.PIECE }?.items.orEmpty()
                    .filter { b -> given.none { singular(norm(it.name)) == singular(norm(b.name)) } }
                    .map { com.dailyworks.apnalaundry.domain.ServiceItem(it.name, null) }
                val s = Service(
                    id = "s${System.currentTimeMillis()}", name = n,
                    mode = if (weight) PricingMode.WEIGHT else PricingMode.PIECE,
                    ratePerKg = if (weight) a.int("rate_per_kg") else null, minKg = if (weight) a.dbl("min_kg") else null,
                    readyInDays = a.int("ready_in_days")?.takeIf { it in 0..60 }, lockedToPiece = false,
                    items = if (weight) emptyList() else given + base, sortOrder = st.services.size,
                )
                card(s, null, true, listOf("New service: $n") + if (weight) listOf("₹${s.ratePerKg}/kg" + (s.minKg?.let { ", min ${Selectors.trimKg(it)} kg" } ?: "")) else given.map { "${it.name} ₹${it.price}" })
            }
            "delete_service" -> {
                val s = svc() ?: fail("Which service? $services")
                if (st.services.size <= 1) fail("Keep at least one service")
                card(null, s.id, false, listOf("Delete ${s.name} (old orders keep it)"))
            }
            else -> fail("action must be set_price, add_item, remove_item, set_weight_rate, set_ready_days, rename_service, add_service or delete_service")
        }
    }

    private fun editRateCard(a: JsonObject): ToolOutcome {
        val p = planRate(a)
        return ToolOutcome(buildJsonObject {
            put("shown_to_owner", true)
            putJsonArray("change") { p.summary.forEach { add(JsonPrimitive(it)) } }
            put("next", "Not saved yet. Tell them (\"you\"): check and tap the button. Old orders keep their prices.")
        }.toString(), AssistantCard.RateChange(a, p.isNew, p.summary))
    }

    /** A new customer, or changes to one (name, phone, address). */
    private fun saveCustomer(a: JsonObject): ToolOutcome {
        val c = customerArg(a)
        if (a.str("customer_id").isNotBlank() && c == null) return err("No such customer_id")
        val name = a.str("name").trim().ifBlank { c?.name.orEmpty() }
        val phoneIn = a.str("phone").filter { it.isDigit() }.takeLast(10)
        val phone = phoneIn.ifBlank { c?.phone.orEmpty() }
        val address = if (a.containsKey("address")) a.str("address").trim() else c?.address.orEmpty()
        if (name.isBlank()) return err("Customer's name?")
        if (phone.length != 10) return err("A 10-digit mobile number is needed")
        st.customers.firstOrNull { it.phone == phone && it.id != c?.id }?.let { return err("$phone is already saved for ${it.name} (${it.id})") }
        val changes = if (c == null) listOfNotNull("New customer: $name · ${Selectors.fmtPhone(phone)}", address.takeIf { it.isNotBlank() }?.let { "Address: $it" })
        else listOfNotNull(
            if (name != c.name) "Name: ${c.name} → $name" else null,
            if (phone != c.phone) "Phone: ${Selectors.fmtPhone(c.phone)} → ${Selectors.fmtPhone(phone)}" else null,
            if (address != c.address) "Address: ${address.ifBlank { "(none)" }}" else null,
        )
        if (changes.isEmpty()) return err("Nothing to change")
        val baaki = if (c == null) maxOf(0, a.int("old_baaki") ?: 0) else 0
        return ToolOutcome(buildJsonObject {
            put("shown_to_owner", true); putJsonArray("change") { changes.forEach { add(JsonPrimitive(it)) } }
            if (baaki > 0) put("old_baaki", baaki)
            put("next", "Not saved yet. Tell them (\"you\"): check and tap Save.")
        }.toString(), AssistantCard.SaveCustomer(c?.id, name, phone, address, baaki, changes + listOfNotNull(if (baaki > 0) "Old baaki ₹$baaki" else null)))
    }

    private fun addOldBaaki(a: JsonObject): ToolOutcome {
        val c = customerArg(a) ?: return err("No such customer_id — use find_customers first")
        val amt = a.int("amount")?.takeIf { it > 0 } ?: return err("How much old baaki (₹)?")
        return ToolOutcome(buildJsonObject {
            put("shown_to_owner", true); put("customer", c.name); put("amount", amt)
            put("next", "Not added yet. Tell them (\"you\"): tap the button.")
        }.toString(), AssistantCard.OldBaaki(c.id, amt))
    }

    private fun deleteOrder(a: JsonObject): ToolOutcome {
        val o = orderArg(a) ?: return err("No order with that number")
        return ToolOutcome(buildJsonObject {
            put("shown_to_owner", true); put("order_no", o.no()); put("customer", Selectors.customer(st, o.custId).name)
            put("note", "Deletes the order and its bill and payments from the khata for good (Undo right after)")
            put("next", "Not deleted yet. Tell them (\"you\"): tap Delete order only if sure.")
        }.toString(), AssistantCard.DeleteOrder(o.id))
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

    /**
     * The customer a tool is about: by customer_id, else by customer_name (the
     * model skipped find_customers) — one clear match, or the choices come back.
     */
    private fun customerArg(a: JsonObject): Customer? = when (val l = lookup(a)) {
        is Lookup.One -> l.c
        is Lookup.Many -> fail(manyText(l.cs, a.str("customer_name").ifBlank { a.str("customer_phone") }))
        Lookup.None -> fail("No customer called \"${a.str("customer_name").ifBlank { a.str("customer_phone") }}\". Check the name, or it's a new customer.")
        Lookup.NotGiven -> null
        is Lookup.OtherNumber -> fail(
            l.cs.joinToString("; ") { "${it.name} is saved with number …${it.phone.takeLast(4)}" } +
                ", not ${a.str("customer_phone")}. Same person? Ask the owner (or use their customer_id).",
        )
    }

    private sealed interface Lookup {
        data class One(val c: Customer) : Lookup
        data class Many(val cs: List<Customer>) : Lookup
        data object None : Lookup
        data object NotGiven : Lookup
        /** The name is saved, but under another number than the one given. */
        data class OtherNumber(val cs: List<Customer>) : Lookup
    }

    /**
     * Finds the customer the owner means: customer_id, else their number
     * (customer_phone / new_customer_phone — the full 10 digits, or the last 4+),
     * else their name (customer_name / new_customer_name; one exact full-name
     * match wins). An existing customer is always taken over making a new one.
     */
    private fun lookup(a: JsonObject): Lookup {
        a.str("customer_id").takeIf { it.isNotBlank() }?.let { id -> st.customers.firstOrNull { it.id == id }?.let { return Lookup.One(it) } }
        val digits = a.str("customer_phone").ifBlank { a.str("new_customer_phone") }.filter { it.isDigit() }.takeLast(10)
        val name = a.str("customer_name").ifBlank { a.str("new_customer_name") }.trim()
        if (digits.length >= 4) {
            val hits = st.customers.filter { it.phone == digits || it.phone.endsWith(digits) }
            hits.singleOrNull()?.let { return Lookup.One(it) }
            if (hits.size > 1) {
                // Last digits shared: the name decides.
                hits.filter { norm(it.name) == norm(name) }.singleOrNull()?.let { return Lookup.One(it) }
                return Lookup.Many(hits)
            }
            if (name.isEmpty()) return Lookup.None
            if (digits.length == 10) return when (val n = byName(name)) {
                // A full number nobody has, a name someone has: likely another person of that name.
                is Lookup.One -> Lookup.OtherNumber(listOf(n.c))
                is Lookup.Many -> Lookup.OtherNumber(n.cs)
                else -> Lookup.None
            }
        }
        if (name.isEmpty()) return Lookup.NotGiven
        return byName(name)
    }

    private fun byName(name: String): Lookup {
        val all = matchCustomers(name)
        val exact = all.filter { norm(it.name) == norm(name) }
        return when {
            exact.size == 1 -> Lookup.One(exact[0])
            exact.size > 1 -> Lookup.Many(exact)
            all.size == 1 -> Lookup.One(all[0])
            all.isEmpty() -> Lookup.None
            else -> Lookup.Many(all)
        }
    }

    private fun manyText(cs: List<Customer>, said: String) =
        "Several customers match \"$said\": " + cs.take(6).joinToString("; ") { "${it.name} (…${it.phone.takeLast(4)}, ${it.id})" } + ". Ask which one (or their number)."

    private fun orderArg(a: JsonObject): Order? = findOrder(a.str("order_no"))

    /** "#1005", "1005", or an owner's serial ("A-12"); the newest if a serial repeats. */
    private fun findOrder(raw: String): Order? {
        val no = raw.trim().removePrefix("#").trim()
        if (no.isEmpty()) return null
        return st.orders.filter { it.no().equals(no, ignoreCase = true) }.maxByOrNull { it.id }
            ?: no.toIntOrNull()?.let { id -> st.orders.firstOrNull { it.id == id } }
    }

    /** "upi", "gpay", "phonepe", "paytm", "online" = UPI; anything else = cash. */
    private fun methodOf(s: String): PayMethod {
        val m = s.lowercase()
        return if (listOf("upi", "gpay", "google", "phonepe", "paytm", "online", "qr").any { m.contains(it) }) PayMethod.UPI else PayMethod.CASH
    }

    /** A line on an order by item name (a by-weight line by its service's name), optionally in one service. */
    private fun lineIndex(lines: List<OrderLine>, svc: String, item: String): Int? {
        val q = singular(norm(item))
        val cand = lines.withIndex().filter { (_, l) -> svc.isBlank() || l.serviceId == svc || norm(l.serviceName).contains(norm(svc)) }
        if (q.isEmpty()) return cand.singleOrNull()?.index
        cand.firstOrNull { singular(norm(it.value.itemName)) == q }?.let { return it.index }
        cand.filter { norm(it.value.itemName).contains(q) || q.contains(singular(norm(it.value.itemName))) }.singleOrNull()?.let { return it.index }
        return cand.filter { it.value.kg > 0 && norm(it.value.serviceName).contains(q) }.singleOrNull()?.index
    }

    private fun matchItemIndex(s: Service, raw: String): Int? = matchItem(s, raw)?.let { m -> s.items.indexOf(m).takeIf { it >= 0 } }

    private fun orderNo(id: Int?): String = id?.let { i -> st.orders.firstOrNull { it.id == i }?.no() ?: i.toString() } ?: "?"

    private fun ok(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = ToolOutcome(clip(buildJsonObject(block).toString()))
    private fun fail(msg: String): Nothing = throw PlanException(msg)
    private fun err(msg: String) = ToolOutcome(buildJsonObject { put("error", msg) }.toString())
    private fun clip(s: String) = if (s.length <= 6000) s else s.take(6000) + "…(cut)"

    companion object {
        private val HONORIFICS = setOf("ji", "bhai", "bhaiya", "bhaiyya", "didi", "aunty", "uncle", "sir", "madam", "mam", "ka", "ki", "ke", "ko")

        /** The status card's button — the same words the model is told to use. */
        fun statusButton(from: OrderStatus, to: OrderStatus) = when (to) {
            OrderStatus.DELIVERED -> "Mark delivered"
            OrderStatus.CANCELLED -> "Cancel order"
            OrderStatus.CREATED -> "Move to To pick up"
            OrderStatus.RECEIVED -> if (from == OrderStatus.CREATED) "Mark picked up" else "Move to Received"
            OrderStatus.READY -> "Mark ready"
        }

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
        private const val CUST_NAME = "Or just the customer's name as the owner said it — the app finds them"
        private const val CUST_PHONE = "Or their number (full, or the last 4 digits) — the app finds them"

        /** The tools, in the OpenAI / Sarvam function-calling shape. */
        val SCHEMA: JsonArray = buildJsonArray {
            add(fn("find_customers", "Find customers by name (spelling may be rough) or phone digits. Returns customer_id, phone, baaki, open orders.",
                obj("query" to s("Name or part of the phone number"), required = listOf("query"))))
            add(fn("customer_khata", "A customer's khata: baaki (what they owe) or advance, open orders, recent khata entries, address.",
                obj("customer_id" to s("From find_customers"), "customer_name" to s(CUST_NAME), "customer_phone" to s(CUST_PHONE))))
            add(fn("find_orders", "List orders, newest first. Filter by customer, status, dates. Use for 'which orders are ready', 'today's pickups', 'unpaid orders', 'bills not sent'.",
                obj(
                    "customer_id" to s("Only this customer's orders"),
                    "customer_name" to s(CUST_NAME), "customer_phone" to s(CUST_PHONE),
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
            add(fn("create_order", "Prepare a NEW order for the owner to check and save. Prices come from the rate card. A customer not in the app yet is added by this order itself — don't call save_customer for them. Not saved until the owner taps Save order.",
                obj(
                    "customer_id" to s("Existing customer, from find_customers"),
                    "customer_name" to s("The customer's name as the owner said it. The app checks if they're already saved (by number, then name) and takes them; if not, they're added as new"),
                    "customer_phone" to s("Their mobile, if the owner said it (full 10 digits, or the last 4). Needed only for a new customer"),
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
                obj("customer_id" to s("From find_customers"), "customer_name" to s(CUST_NAME), "customer_phone" to s(CUST_PHONE))))
            add(fn("whatsapp_message", "Show a button to send any WhatsApp message the owner asked for (offer, greeting, note, a Status post). With customer_id it opens that customer's chat; without, WhatsApp lets the owner pick chats or My Status.",
                obj("text" to s("The full message, ready to send"), "customer_id" to s("Optional: one customer"), "customer_name" to s(CUST_NAME), "customer_phone" to s(CUST_PHONE), required = listOf("text"))))
            add(fn("change_status", "Change an order's status to ANY state, forward or back (e.g. delivered back to ready). Cancel / cancel kar do / mana kar diya = to cancelled. Delivered needs the money got now: amount + method, or paid_full, or cash_amount + upi_amount, or nothing_paid (all on khata). The app then offers to tell the customer on WhatsApp.",
                obj(
                    "order_no" to s("Order number"),
                    "to" to e("New status. received = picked up / at the shop", "to_pick_up", "received", "ready", "delivered", "cancelled"),
                    "amount" to i("Delivered: money got now, ₹"),
                    "method" to e("Delivered: how they paid", "cash", "upi"),
                    "paid_full" to b("Delivered: paid the whole bill"),
                    "cash_amount" to i("Delivered, split: cash part"),
                    "upi_amount" to i("Delivered, split: UPI part"),
                    "nothing_paid" to b("Delivered: nothing paid now, all goes on khata"),
                    "delivery_date" to s("Ready: delivery date $DATE (optional)"),
                    "reason" to s("Cancelled: why (optional)"),
                    required = listOf("order_no", "to"),
                )))
            add(fn("record_payment", "Record money got: 'paid' / 'part paid' on an order (before delivery it's paid in advance on the order), or a khata payment from a customer.",
                obj(
                    "order_no" to s("The order it's for (optional)"),
                    "customer_id" to s("Or the customer, for a khata payment"),
                    "customer_name" to s(CUST_NAME), "customer_phone" to s(CUST_PHONE),
                    "amount" to i("₹ got"),
                    "paid_full" to b("Paid everything due"),
                    "method" to e("Cash or UPI (default cash)", "cash", "upi"),
                )))
            add(fn("edit_order", "Change an existing order: add or remove clothes, this order's rate for an item, by-weight kg, express, discount, pickup / ready date, note. Re-prices it like Edit order (a delivered order's khata follows).",
                obj(
                    "order_no" to s("Order number"),
                    "add_items" to arr("Clothes to add", obj("service_id" to s("Service id"), "item" to s("Item from the rate card"), "qty" to i("Pieces"), "rate" to i("Optional: special ₹ rate on this order"), required = listOf("item", "qty"))),
                    "remove_items" to arr("Clothes to take off", obj("service_id" to s("Service id (optional)"), "item" to s("Item"), "qty" to i("How many; leave out = all"), required = listOf("item"))),
                    "set_rates" to arr("This order's rate for an item already on it", obj("service_id" to s("Service id (optional)"), "item" to s("Item"), "rate" to i("New ₹ rate"), required = listOf("item", "rate"))),
                    "weights" to arr("By-weight kg (0 = remove)", obj("service_id" to s("By-weight service id"), "kg" to n("kg"), required = listOf("kg"))),
                    "express" to b("Express on / off"),
                    "discount" to i("Discount ₹ (0 = none)"),
                    "discount_pct" to i("Or discount %"),
                    "pickup_date" to s("New pickup date $DATE"),
                    "delivery_date" to s("New ready-by / delivery date $DATE"),
                    "note" to s("New note (\"\" = remove)"),
                    required = listOf("order_no"),
                )))
            add(fn("edit_rate_card", "Change the rate card: set_price (an item's price; adds it if missing), add_item, remove_item, set_weight_rate, set_ready_days, rename_service, add_service (a new service), delete_service. Old orders keep their prices.",
                obj(
                    "action" to e("What to change", "set_price", "add_item", "remove_item", "set_weight_rate", "set_ready_days", "rename_service", "add_service", "delete_service"),
                    "service_id" to s("The service (id or name); not for add_service"),
                    "item" to s("Item name"),
                    "price" to i("₹ per piece"),
                    "name" to s("add_service / rename_service: service name"),
                    "mode" to e("add_service: per piece or by weight", "piece", "weight"),
                    "items" to arr("add_service: items with prices", obj("item" to s("Item"), "price" to i("₹"), required = listOf("item", "price"))),
                    "rate_per_kg" to i("₹ per kg"),
                    "min_kg" to n("Minimum kg"),
                    "ready_in_days" to i("Days until ready"),
                    required = listOf("action"),
                )))
            add(fn("save_customer", "Add a new customer (name + 10-digit phone, optional address and old baaki), or change a customer's name / phone / address (with customer_id).",
                obj(
                    "customer_id" to s("To change an existing customer"),
                    "name" to s("Name"), "phone" to s("10-digit mobile"), "address" to s("Address"),
                    "old_baaki" to i("New customer only: baaki from before the app, ₹"),
                )))
            add(fn("add_old_baaki", "Add old baaki (money owed from before / from the notebook) to a customer's khata.",
                obj("customer_id" to s("From find_customers"), "customer_name" to s(CUST_NAME), "customer_phone" to s(CUST_PHONE), "amount" to i("₹"), required = listOf("amount"))))
            add(fn("delete_order", "Erase an order and its bill for good (its khata entries go too) — only when the owner says delete / mita do / hata do / made by mistake. NOT for cancel: a cancelled order is change_status to cancelled (the order stays).",
                obj("order_no" to s("Order number"), required = listOf("order_no"))))
        }
    }
}
