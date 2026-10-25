package com.dailyworks.apnalaundry.ui.tour

import com.dailyworks.apnalaundry.core.i18n
import com.dailyworks.apnalaundry.core.tr
import com.dailyworks.apnalaundry.ui.components.SUPPORT_PHONE_SHOWN

/**
 * The guide: one walk through the whole app on the shop's own first order —
 * take the order, mark it ready, give the clothes back and take the money,
 * then khata and earnings. Every card is also read out loud ([TourVoice]).
 *
 * Words are for a small-town shop owner: short sentences, the exact button
 * name in quotes, never "highlighted" / "spotlight" / colours — a big arrow
 * points at the button instead.
 *
 * Cards are made when shown (getters / functions), so they speak the app's
 * language of that moment. Button names go in as placeholders, translated
 * through the button's own text, so the card names the button as the screen
 * shows it.
 */

/** The five parts the owner sees on every card ("Step 2 of 5 · Clothes ready"); [label] is English, shown through tr(). */
enum class TourPart(val n: Int, val key: String, val label: String) {
    ORDER(1, "new_order", i18n("New order")),
    READY(2, "ready", i18n("Clothes ready")),
    DELIVER(3, "deliver", i18n("Give clothes, take money")),
    KHATA(4, "khata", i18n("Khata")),
    EARNINGS(5, "earnings", i18n("Earnings"));

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
        if (firstOrder) tr("Let's add your first order") else tr("Let's add an order"),
        tr("Tap ‘{button}’.", "button" to button),
        say = if (firstOrder) tr("Welcome! Let's add your first order together. Tap {button}.", "button" to button)
        else tr("Let's add an order together. Tap {button}.", "button" to button),
    )

    fun customer(hasCustomers: Boolean) = TourStep(
        "order_customer", TourPart.ORDER,
        tr("Who gave the clothes?"),
        if (hasCustomers) tr("Type the customer's name to find them. New customer? Tap ‘{button}’.", "button" to tr("Add new customer"))
        else tr("Tap ‘{button}’ and save their name and phone number.", "button" to tr("Add new customer")),
    )

    val customerForm get() = TourStep(
        "order_customer_form", TourPart.ORDER,
        tr("Save the customer"),
        tr("Tap ‘{contacts}’, or type the name and phone number. Then tap ‘{save}’.",
            "contacts" to tr("Choose from contacts"), "save" to tr("Save customer"),
        ),
    )

    /** The first-customer test's v1 / v3 sheet: the same form, and it says the address can wait. */
    val customerFormAddressOptional get() = TourStep(
        "order_customer_form", TourPart.ORDER,
        tr("Save the customer"),
        tr("Tap ‘{contacts}’, or type the name and phone number. Address is optional. Then tap ‘{save}’.",
            "contacts" to tr("Choose from contacts"), "save" to tr("Save customer"),
        ),
    )

    val route get() = TourStep(
        "order_route", TourPart.ORDER,
        tr("Shop or home?"),
        tr("Customer came to your shop? Keep ‘{at_shop}’. Going to their home? Tap ‘{from_home}’ or ‘{to_home}’.",
            "at_shop" to tr("At shop"), "from_home" to tr("From home"), "to_home" to tr("To home"),
        ),
    )

    /** v2 (no address on the customer sheet): choosing home asks for the address next. */
    val routeAsksAddress get() = TourStep(
        "order_route", TourPart.ORDER,
        tr("Shop or home?"),
        tr("Customer came to your shop? Keep ‘{at_shop}’. Going to their home? Tap ‘{from_home}’ or ‘{to_home}’, then save their address.",
            "at_shop" to tr("At shop"), "from_home" to tr("From home"), "to_home" to tr("To home"),
        ),
    )

    /** v2's address sheet. */
    val addressForm get() = TourStep(
        "order_address_form", TourPart.ORDER,
        tr("Add the address"),
        tr("Type the house, area and a landmark. Then tap ‘{button}’.", "button" to tr("Save address")),
    )

    val dates get() = TourStep(
        "order_dates", TourPart.ORDER,
        tr("When will you give the clothes back?"),
        tr("Pickup is today. Add the delivery date if you know it. You can leave it empty."),
    )

    // ‘Wash & Iron’ is a default service: its name stays as the rate card shows it.
    /** Before the clothes, while the shop still has the sample rates: arrow at "Edit services". */
    val sampleRates get() = TourStep(
        "order_sample_rates", TourPart.ORDER,
        tr("These are sample rates"),
        tr("We filled in common prices to start. Your shop's prices may be different — tap ‘{button}’ to change them.", "button" to tr("Edit services")),
        say = tr("These are sample rates. We filled in common prices to start. Your shop's prices may be different. Tap {button} to change them.", "button" to tr("Edit services")),
    )

    val clothes get() = TourStep(
        "order_clothes", TourPart.ORDER,
        tr("Add the clothes"),
        tr("Tap a service like ‘{service}’. Then tap + for each cloth. The bill adds up by itself.\n\nWant to change a price, or add or delete a service? Tap ‘{button}’.",
            "service" to "Wash & Iron", "button" to tr("Edit services"),
        ),
        say = tr("Add the clothes. Tap a service, like {service}. Then tap plus for each cloth. The bill adds up by itself. Want to change a price, or add or delete a service? Tap {button}.",
            "service" to "Wash and Iron", "button" to tr("Edit services"),
        ),
    )

    fun save(button: String, needsCustomer: Boolean, needsClothes: Boolean, needsAddress: Boolean) = TourStep(
        "order_save", TourPart.ORDER,
        tr("Save the order"),
        when {
            needsCustomer -> tr("Choose the customer first. Tap ‘{button}’.", "button" to tr("Go back"))
            needsClothes -> tr("Add at least one cloth first. Tap ‘{button}’.", "button" to tr("Go back"))
            needsAddress -> tr("Add the customer's address first. Tap ‘{back}’, then tap ‘{add}’ next to {address}.",
                "back" to tr("Go back"), "add" to tr("Add"), "address" to tr("Address"),
            )
            else -> tr("Tap ‘{button}’. Your bill will be ready.", "button" to button)
        },
    )

    val billSend get() = TourStep(
        "bill_send", TourPart.ORDER,
        tr("Order saved!") + " 🎉",
        tr("This is the bill. Tap ‘{button}’ to send it to the customer.", "button" to tr("Send on WhatsApp")),
        say = tr("Your order is saved! This is the bill. Tap {button} to send it to the customer.", "button" to tr("Send on WhatsApp")),
    )

    val billNext get() = TourStep(
        "bill_next", TourPart.READY,
        tr("Bill sent") + " ✓",
        tr("Next, see what to do when the clothes are washed."),
        say = tr("Bill sent. Next, see what to do when the clothes are washed. Tap {button}.", "button" to tr("Go to Home")),
    )

    // ---- parts 2–3: the order's status, on its card on Home ----
    val pickedUp get() = TourStep(
        "status_picked_up", TourPart.READY,
        tr("Picked up the clothes?"),
        tr("When you bring the clothes from the customer's home, tap ‘{button}’.", "button" to tr("Mark picked up")),
    )

    val markReady get() = TourStep(
        "status_ready", TourPart.READY,
        tr("Clothes washed?"),
        tr("When the clothes are washed and ironed, tap ‘{button}’. Not yet? Tap ‘{later}’.",
            "button" to tr("Mark ready"), "later" to tr("Later"),
        ),
    )

    val readySheet get() = TourStep(
        "ready_sheet", TourPart.READY,
        tr("When will you give them back?"),
        tr("Pick the day. Then tap ‘{button}’ at the bottom.", "button" to tr("Mark ready")),
    )

    val markDelivered get() = TourStep(
        "status_deliver", TourPart.DELIVER,
        tr("Giving the clothes back?"),
        tr("When the customer takes the clothes, tap ‘{button}’. Not yet? Tap ‘{later}’.",
            "button" to tr("Mark delivered"), "later" to tr("Later"),
        ),
    )

    val paySheet get() = TourStep(
        "pay_sheet", TourPart.DELIVER,
        tr("Did the customer pay?"),
        tr("Paid? Tap ‘{cash}’ or ‘{upi}’. Will pay later? Tap ‘{khata}’ at the bottom.",
            "cash" to tr("Got cash"), "upi" to tr("Got UPI"), "khata" to tr("Nothing now · add all to khata"),
        ),
        say = tr("Did the customer pay? If yes, tap Got cash, or Got U P I. Will pay later? Tap Nothing now, add all to khata, at the bottom."),
    )

    fun whatsAppPrompt(part: TourPart) = TourStep(
        "whatsapp_update", part,
        tr("Tell the customer"),
        tr("Tap ‘{button}’ so the customer gets this message.", "button" to tr("Send on WhatsApp")),
    )

    // ---- part 4: khata ----
    val khataTab get() = TourStep(
        "khata_tab", TourPart.KHATA,
        tr("Who has to pay you?"),
        tr("Tap ‘{tab}’ below to see your khata.", "tab" to tr("Customers")),
    )

    val khataTile get() = TourStep(
        "khata_tile", TourPart.KHATA,
        tr("This is your khata"),
        tr("It shows the total money customers still have to pay you."),
    )

    val khataCustomer get() = TourStep(
        "khata_customer", TourPart.KHATA,
        tr("Each customer's khata"),
        tr("Tap a customer to see what they owe. When they pay later, tap ‘{button}’ there.", "button" to tr("Receive payment")),
    )

    // ---- part 5: earnings ----
    val earningsTab get() = TourStep(
        "earnings_tab", TourPart.EARNINGS,
        tr("How much did you earn?"),
        tr("Tap ‘{tab}’ below.", "tab" to tr("Earnings")),
    )

    val sales get() = TourStep(
        "earnings_sales", TourPart.EARNINGS,
        tr("Your sales"),
        tr("This is the total of all your bills. Tap ‘{week}’ or ‘{month}’ to see more days.",
            "week" to tr("This week"), "month" to tr("This month"),
        ),
    )

    val money get() = TourStep(
        "earnings_money", TourPart.EARNINGS,
        tr("Money you got"),
        tr("This is the money that came in. Cash and UPI are shown separately."),
        say = tr("Money you got. This is the money that came in. Cash and U P I are shown separately."),
    )

    val done get() = TourStep(
        "tour_done", TourPart.EARNINGS,
        tr("You are ready!") + " 🎉",
        tr("Now you know the whole app. Need help? Call us on {phone}.", "phone" to SUPPORT_PHONE_SHOWN),
        say = tr("You are ready! Now you know the whole app. Need help? Call us on {phone}.", "phone" to "7 6 7 6 3, 6 3 7 9 3"),
    )
}
