package com.dailyworks.apnalaundry.ui.tour

import com.dailyworks.apnalaundry.ui.components.SUPPORT_PHONE_SHOWN

/**
 * The guide: one walk through the whole app on the shop's own first order —
 * take the order, mark it ready, give the clothes back and take the money,
 * then khata and earnings. Every card is also read out loud ([TourVoice]).
 *
 * Words are for a small-town shop owner: short sentences, the exact button
 * name in quotes, never "highlighted" / "spotlight" / colours — a big arrow
 * points at the button instead.
 */

/** The five parts the owner sees on every card ("Step 2 of 5 · Clothes ready"). */
enum class TourPart(val n: Int, val key: String, val label: String) {
    ORDER(1, "new_order", "New order"),
    READY(2, "ready", "Clothes ready"),
    DELIVER(3, "deliver", "Give clothes, take money"),
    KHATA(4, "khata", "Khata"),
    EARNINGS(5, "earnings", "Earnings");

    companion object { const val COUNT = 5 }
}

/** Where the shop is in the guide; saved in Prefs by name. */
enum class TourStage {
    ORDER,    // Home → New order → save (part 1)
    BILL,     // the new order's bill: send it on WhatsApp (part 1)
    STATUS,   // the guide's order: mark ready, then delivered + money (parts 2–3)
    KHATA,    // Customers → khata (part 4)
    EARNINGS, // Earnings (part 5)
    DONE,
}

/** One card of the guide: its words, and what the voice reads ([say]). */
data class TourStep(
    val id: String,
    val part: TourPart,
    val title: String,
    val body: String,
    val say: String = "$title. $body",
)

object TourSteps {
    // ---- part 1: new order ----
    fun homeNewOrder(button: String, firstOrder: Boolean) = TourStep(
        "home_new_order", TourPart.ORDER,
        if (firstOrder) "Let's add your first order" else "Let's add an order",
        "Tap ‘$button’.",
        say = (if (firstOrder) "Welcome! Let's add your first order together. " else "Let's add an order together. ") + "Tap $button.",
    )

    fun customer(hasCustomers: Boolean) = TourStep(
        "order_customer", TourPart.ORDER,
        "Who gave the clothes?",
        if (hasCustomers) "Type the customer's name to find them. New customer? Tap ‘Add new customer’."
        else "Tap ‘Add new customer’ and save their name and phone number.",
    )

    val customerForm = TourStep(
        "order_customer_form", TourPart.ORDER,
        "Save the customer",
        "Tap ‘Choose from contacts’, or type the name and phone number. Then tap ‘Save customer’.",
    )

    val route = TourStep(
        "order_route", TourPart.ORDER,
        "Shop or home?",
        "Customer came to your shop? Keep ‘At shop’. Going to their home? Tap ‘From home’ or ‘To home’.",
    )

    val dates = TourStep(
        "order_dates", TourPart.ORDER,
        "When will you give the clothes back?",
        "Pickup is today. Add the delivery date if you know it. You can leave it empty.",
    )

    val clothes = TourStep(
        "order_clothes", TourPart.ORDER,
        "Add the clothes",
        "Tap a service like ‘Wash & Iron’. Then tap + for each cloth. The bill adds up by itself.",
        say = "Add the clothes. Tap a service, like Wash and Iron. Then tap plus for each cloth. The bill adds up by itself.",
    )

    fun save(button: String, needsCustomer: Boolean, needsClothes: Boolean, needsAddress: Boolean) = TourStep(
        "order_save", TourPart.ORDER,
        "Save the order",
        when {
            needsCustomer -> "Choose the customer first. Tap ‘Go back’."
            needsClothes -> "Add at least one cloth first. Tap ‘Go back’."
            needsAddress -> "Add the customer's address first. Tap ‘Go back’, then tap ‘Add’ next to Address."
            else -> "Tap ‘$button’. Your bill will be ready."
        },
    )

    val billSend = TourStep(
        "bill_send", TourPart.ORDER,
        "Order saved! 🎉",
        "This is the bill. Tap ‘Send on WhatsApp’ to send it to the customer.",
        say = "Your order is saved! This is the bill. Tap Send on WhatsApp to send it to the customer.",
    )

    val billNext = TourStep(
        "bill_next", TourPart.READY,
        "Bill sent ✓",
        "Next, see what to do when the clothes are washed.",
        say = "Bill sent. Next, see what to do when the clothes are washed. Tap Go to Home.",
    )

    // ---- parts 2–3: the order's status, on its card on Home ----
    val pickedUp = TourStep(
        "status_picked_up", TourPart.READY,
        "Picked up the clothes?",
        "When you bring the clothes from the customer's home, tap ‘Mark picked up’.",
    )

    val markReady = TourStep(
        "status_ready", TourPart.READY,
        "Clothes washed?",
        "When the clothes are washed and ironed, tap ‘Mark ready’. Not yet? Tap ‘Later’.",
    )

    val readySheet = TourStep(
        "ready_sheet", TourPart.READY,
        "When will you give them back?",
        "Pick the day. Then tap ‘Mark ready’ at the bottom.",
    )

    val markDelivered = TourStep(
        "status_deliver", TourPart.DELIVER,
        "Giving the clothes back?",
        "When the customer takes the clothes, tap ‘Mark delivered’. Not yet? Tap ‘Later’.",
    )

    val paySheet = TourStep(
        "pay_sheet", TourPart.DELIVER,
        "Did the customer pay?",
        "Paid? Tap ‘Got cash’ or ‘Got UPI’. Will pay later? Tap ‘Nothing now · add all to khata’ at the bottom.",
        say = "Did the customer pay? If yes, tap Got cash, or Got U P I. Will pay later? Tap Nothing now, add all to khata, at the bottom.",
    )

    fun whatsAppPrompt(part: TourPart) = TourStep(
        "whatsapp_update", part,
        "Tell the customer",
        "Tap ‘Send on WhatsApp’ so the customer gets this message.",
    )

    // ---- part 4: khata ----
    val khataTab = TourStep(
        "khata_tab", TourPart.KHATA,
        "Who has to pay you?",
        "Tap ‘Customers’ below to see your khata.",
    )

    val khataTile = TourStep(
        "khata_tile", TourPart.KHATA,
        "This is your khata",
        "It shows the total money customers still have to pay you.",
    )

    val khataCustomer = TourStep(
        "khata_customer", TourPart.KHATA,
        "Each customer's khata",
        "Tap a customer to see what they owe. When they pay later, tap ‘Receive payment’ there.",
    )

    // ---- part 5: earnings ----
    val earningsTab = TourStep(
        "earnings_tab", TourPart.EARNINGS,
        "How much did you earn?",
        "Tap ‘Earnings’ below.",
    )

    val sales = TourStep(
        "earnings_sales", TourPart.EARNINGS,
        "Your sales",
        "This is the total of all your bills. Tap ‘This week’ or ‘This month’ to see more days.",
    )

    val money = TourStep(
        "earnings_money", TourPart.EARNINGS,
        "Money you got",
        "This is the money that came in. Cash and UPI are shown separately.",
        say = "Money you got. This is the money that came in. Cash and U P I are shown separately.",
    )

    val done = TourStep(
        "tour_done", TourPart.EARNINGS,
        "You are ready! 🎉",
        "Now you know the whole app. Need help? Call us on $SUPPORT_PHONE_SHOWN.",
        say = "You are ready! Now you know the whole app. Need help? Call us on 7 6 7 6 3, 6 3 7 9 3.",
    )
}
