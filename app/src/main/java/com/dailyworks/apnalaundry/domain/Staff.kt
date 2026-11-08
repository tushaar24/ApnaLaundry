package com.dailyworks.apnalaundry.domain

import com.dailyworks.apnalaundry.core.i18n

/**
 * Staff (1.0.20, docs/specs/staff.md): what an owner lets a staff member do. [key] is the server's
 * name for the switch; [label] and [hint] are shown through tr() (i18n marks them for translation).
 * Everyone can always see orders, mark picked up / ready, print tags and search customers.
 */
enum class StaffPermission(val key: String, val label: String, val hint: String) {
    TAKE_ORDERS("take_orders", i18n("Take orders"), i18n("New order, change clothes, add customers")),
    DELIVER("deliver", i18n("Mark delivered"), i18n("Hand over clothes. Without Collect money the bill goes to khata")),
    COLLECT_MONEY("collect_money", i18n("Collect money"), i18n("Cash or UPI from customers, khata payments, advance")),
    DISCOUNTS_CANCEL("discounts_cancel", i18n("Give discounts and cancel orders"), i18n("Change the discount, cancel an order")),
    DELETE_ORDERS("delete_orders", i18n("Delete orders"), i18n("Remove an order completely")),
    SEE_EARNINGS("see_earnings", i18n("See earnings"), i18n("The Earnings page and totals")),
    EDIT_RATES("edit_rates", i18n("Change rates"), i18n("Services and prices")),
    SEE_PHONES("see_phones", i18n("See customer phone numbers"), i18n("Call and WhatsApp customers")),
    EXPENSES("expenses", i18n("Expenses"), i18n("Add and see shop expenses"));

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

/** The occupation chips; picking one fills the switches (the owner can still change any). */
enum class StaffOccupation(val key: String, val label: String, val preset: Set<StaffPermission>) {
    COUNTER("counter", i18n("Counter"), setOf(StaffPermission.TAKE_ORDERS, StaffPermission.DELIVER, StaffPermission.COLLECT_MONEY, StaffPermission.SEE_PHONES)),
    WASHING("washing", i18n("Washing"), emptySet()),
    IRONING("ironing", i18n("Ironing"), emptySet()),
    DELIVERY("delivery", i18n("Delivery"), setOf(StaffPermission.DELIVER, StaffPermission.SEE_PHONES)),
    MANAGER(
        "manager", i18n("Manager"),
        setOf(
            StaffPermission.TAKE_ORDERS, StaffPermission.DELIVER, StaffPermission.COLLECT_MONEY, StaffPermission.DISCOUNTS_CANCEL,
            StaffPermission.SEE_EARNINGS, StaffPermission.EDIT_RATES, StaffPermission.SEE_PHONES,
        ),
    ),
    OTHER("other", i18n("Other"), emptySet());

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

/**
 * What the person may do on the shop on screen (1.0.20): everything on their own shop; on a shop
 * they work at as staff, the basics plus the owner's switches. Screens hide what [can] says no to.
 */
data class StaffAccess(
    val isStaff: Boolean = false,
    val permissions: Set<StaffPermission> = emptySet(),
    val staffId: String = "",
    val staffName: String = "",
    val ownerName: String = "",
    val shopName: String = "",
    val ownerPlanActive: Boolean = true,
) {
    fun can(p: StaffPermission) = !isStaff || p in permissions
    /** Owner-only things: shop details, bill design, plans, shops, staff, the Ask assistant. */
    val isOwner get() = !isStaff

    companion object { val OWNER = StaffAccess() }
}

/** A staff member, as the owner's Staff screens show them. */
data class StaffMember(
    val id: String,
    val name: String,
    val phone: String,
    val occupation: String,
    val permissions: Set<StaffPermission>,
    val lastSeenAt: Long?, // epoch ms; null = never opened the shop yet
    val todayOrders: Int = 0,
    val todayCollected: Int = 0,
)

/** One line of a staff member's 7-day log (server-made from what they synced). */
data class StaffActivity(
    val at: Long, val action: String, val orderId: Int?, val custId: String?, val amount: Int?, val method: String?, val detail: String,
)

data class StaffActivityPage(val ordersTaken: Int, val delivered: Int, val collected: Int, val rows: List<StaffActivity>)
