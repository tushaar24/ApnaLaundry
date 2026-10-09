package com.dailyworks.apnalaundry.domain

import kotlinx.serialization.Serializable

/** Pure domain models. Mapped to/from Room entities in the data layer. */

enum class PricingMode { PIECE, WEIGHT }

enum class OrderStatus { CREATED, RECEIVED, READY, DELIVERED, CANCELLED }

enum class Route { SHOP, HOME }

enum class LedgerKind { BILL, OLD, ADJ, GOT }

/** Payment allocation tag on a `GOT` ledger entry. */
enum class PayTag { PRE, DELIVER, RECEIVE, NONE }

enum class PayMethod { CASH, UPI, NONE }

@Serializable
data class ServiceItem(
    val name: String,
    val price: Int?, // null / blank = not offered by this service
)

data class Service(
    val id: String,
    val name: String,
    val mode: PricingMode,
    val ratePerKg: Int?,     // for WEIGHT
    val minKg: Double?,      // for WEIGHT
    val readyInDays: Int?,   // null = no ready time
    val lockedToPiece: Boolean,
    val items: List<ServiceItem>,
    val sortOrder: Int,
)

data class Customer(
    val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val pastOrders: Int,     // orders from the notebook, before the app
    val lastLabel: String,   // "Today", "Yesterday", "23 Sep", "—"
    val agoRank: Int,        // for sorting recents (0 = most recent)
)

@Serializable
data class OrderLine(
    val serviceId: String,       // service id, or "quick" for a lump bill
    val serviceName: String,     // snapshot so deleting a service never rewrites history
    val itemName: String,        // item name, or "By weight" / "Clothes (not itemised)"
    val qty: Int,
    val price: Int,              // per-piece price used on this order
    val base: Int,               // rate-card price at the time (for the "rate ₹25" note)
    val kg: Double,              // >0 for weight lines
    val amt: Int,
    val isQuick: Boolean = false,
)

data class Order(
    val id: Int,
    val custId: String,
    val pickup: Route,
    val delivery: Route,
    val pickupDate: String,
    val pickupTime: String,
    val deliveryDate: String,        // "" = no date
    val deliveryTime: String,
    val ddAuto: Boolean,             // delivery date was auto-filled from ready time
    val status: OrderStatus,
    val cancelReason: String,
    val fee: Int,                    // pickup / delivery charge
    val express: Boolean,
    val exAmt: Int,
    val discount: Int,
    val pre: Int,                    // prepaid before delivery
    val paid: Int,                   // total paid toward this bill
    val doneAt: String,              // time string when delivered
    val doneDate: String,            // iso date delivered
    val createdOn: String,
    val billSent: Boolean,
    val pieces: Int,                 // optional piece count for quick bills
    val lines: List<OrderLine>,
)

data class LedgerEntry(
    val id: String,
    val custId: String,
    val date: String,
    val time: String,
    val ts: Long,                    // stable ordering within a day
    val kind: LedgerKind,
    val amt: Int,                    // adj can be negative
    val method: PayMethod,
    val tag: PayTag,
    val cover: Int,                  // portion that paid this bill
    val toOld: Int,                  // portion that cleared old baaki
    val toAdv: Int,                  // portion kept as advance
    val ref: Int?,                   // order id
    val note: String,
)

data class Shop(
    val name: String,
    val phone: String,
    val expressPct: Int,
    // Optional bill details + the chosen design (see domain/BillDetails.kt).
    val billPhone: String = "",
    val address: String = "",
    val gstin: String = "", // printed only when valid
    val upiId: String = "", // QR printed only when valid
    val logoId: String = "",
    val terms: List<String> = emptyList(), // preset ids
    val termsCustom: String = "",
    val billTemplate: String = BillDetails.CLASSIC,
    /** "" = a shop from before onboarding was tracked; else intro|name|services|bill|done. */
    val onboardingStep: String = "",
)

data class DayClose(
    val date: String,
    val closedAt: Long,
    val cashCounted: Int?,
)

/** Everything the domain layer needs to compute derived values. */
data class LaundryState(
    val shop: Shop,
    val services: List<Service>,
    val customers: List<Customer>,
    val orders: List<Order>,
    val ledger: List<LedgerEntry>,
    val closedDays: Set<String>,
)
