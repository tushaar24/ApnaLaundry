package com.dailyworks.apnalaundry.push

import android.content.Intent
import android.net.Uri
import com.dailyworks.apnalaundry.core.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * mylaundry://<screen>[/<id>][?…] links — set as a CleverTap campaign's "On click"
 * deep link (or a `link` data field on other pushes) so a tap lands on the screen
 * the nudge is about. Every screen of the app (1.0.18; the first five since 1.0.8):
 *
 *   mylaundry://home                      Orders (Home)
 *   mylaundry://new-order[?cust=<id>]     New order (for that customer)
 *   mylaundry://customers[?filter=baaki]  Customers (filter: all | baaki | …, the tab's own filters)
 *   mylaundry://customer/<id>             one customer's khata
 *   mylaundry://combined-bill/<id>        one customer's combined bill
 *   mylaundry://order/<id>                one order (id = the app's order number, e.g. 1012)
 *   mylaundry://bill/<id>                 that order's bill
 *   mylaundry://earnings[?period=today|week|month|all]   Earnings, on that period
 *   mylaundry://demo                      Home with the demo video playing
 *   mylaundry://bill-details              Bill design with the details form open (phone, address, GSTIN, UPI, terms…)
 *   mylaundry://subscription[?plan=annual|monthly]   Plans (that plan pre-selected if offered)
 *   mylaundry://rates   (= services)      Services & prices
 *   mylaundry://add-service               Add a service
 *   mylaundry://settings                  Settings
 *   mylaundry://bill-design               Bill design
 *   mylaundry://add-shop                  Add a shop
 *   mylaundry://assistant                 Ask (Home where it isn't on)
 *
 * The same screens as https App Links, for WhatsApp buttons and SMS (which
 * can't carry a custom scheme) — verified through mylaundry.work's
 * /.well-known/assetlinks.json, so a tap opens the app with no browser:
 *
 *   https://mylaundry.work/open/<screen>[/<id>][?…]   that screen (anything else: Home)
 *   https://mylaundry.work/pay/<code>                 Plans / subscription
 *
 * Language links (1.0.16+, owner 2026-10-25): the app switches to the link's
 * language — see [language] and docs/language-links.md:
 *
 *   https://mylaundry.work/open/hi             the app in Hindi (en hi mr gu bn ta te kn ml pa or)
 *   https://mylaundry.work/open/hi/new-order   in Hindi, on New order
 *   mylaundry://hi · mylaundry://hi/new-order  the same in-app
 *   …?lang=hi                                  on any of the links above
 *
 * Older apps read /open/hi as Home and ignore ?lang; apps before 1.0.18 open
 * Home for the screens they don't know. Without the app, the site's page sends
 * the owner to the Play Store with referrer lang=hi, and the first open reads
 * it (core/InstallLanguage.kt).
 *
 * MainActivity hands every incoming intent to [offer]; AppNavGraph opens the
 * pending target once the owner is logged in and set up (so a "take your first
 * order" tap before setup opens New order right after it).
 */
object DeepLinks {
    const val SCHEME = "mylaundry"
    const val WEB_HOST = "mylaundry.work"

    /** Where a link points: the screen, an optional id, and the query it carried. */
    data class Target(val screen: String, val id: String = "", val params: Map<String, String> = emptyMap()) {
        /** "order/1012" for analytics. */
        val label: String get() = if (id.isEmpty()) screen else "$screen/$id"
    }

    private val ALIASES = mapOf(
        "services" to "rates", "orders" to "home", "khata" to "customers", "plans" to "subscription", "ask" to "assistant",
        "shop" to "settings", "demo-video" to "demo", "video" to "demo", "bill-fields" to "bill-details", "bill-setup" to "bill-design",
    )
    val SCREENS = setOf(
        "home", "new-order", "customers", "customer", "combined-bill", "order", "bill", "earnings",
        "subscription", "rates", "add-service", "settings", "bill-design", "bill-details", "add-shop", "assistant", "demo",
    )
    private val WITH_ID = setOf("customer", "combined-bill", "order", "bill")

    private val _pending = MutableStateFlow<Target?>(null)
    val pending: StateFlow<Target?> = _pending

    fun offer(intent: Intent?) {
        val uri = intent?.data ?: return
        val target = parse(uri) ?: return
        _pending.value = target
    }

    /** The target a URI names, or null when it isn't one of ours. Exposed for tests. */
    fun parse(uri: Uri): Target? {
        val parts: List<String> = when {
            uri.scheme == SCHEME -> listOfNotNull(uri.host?.lowercase()) + uri.pathSegments.map { it.lowercase() }
            uri.scheme == "https" && uri.host == WEB_HOST -> when (uri.pathSegments.firstOrNull()?.lowercase()) {
                "open" -> uri.pathSegments.drop(1).map { it.lowercase() }
                "pay" -> listOf("subscription")
                else -> return null
            }
            else -> return null
        }
        val afterLang = if (parts.firstOrNull()?.let(AppLanguage::of) != null) parts.drop(1) else parts
        val raw = afterLang.firstOrNull() ?: "home"
        val screen = ALIASES[raw] ?: raw
        if (screen !in SCREENS) return Target("home")
        val id = if (screen in WITH_ID) afterLang.getOrNull(1).orEmpty() else ""
        if (screen in WITH_ID && id.isEmpty()) return Target("home")
        val params = buildMap {
            for (k in listOf("plan", "filter", "cust", "from", "period")) uri.getQueryParameter(k)?.trim()?.takeIf { it.isNotEmpty() }?.let { put(k, it.lowercase()) }
        }
        return Target(screen, id, params)
    }

    /** The app language a link carries: mylaundry.work/open/hi…, mylaundry://hi…, or ?lang=hi on any link. */
    fun language(intent: Intent?): AppLanguage? = runCatching {
        val uri = intent?.data ?: return@runCatching null
        val code = uri.getQueryParameter("lang") ?: when {
            uri.scheme == SCHEME -> uri.host
            uri.scheme == "https" && uri.host == WEB_HOST && uri.pathSegments.firstOrNull() == "open" -> uri.pathSegments.getOrNull(1)
            else -> null
        }
        AppLanguage.of(code?.lowercase())
    }.getOrNull()

    /** Take the pending target (null when there's none). */
    fun consume(): Target? = _pending.value.also { _pending.value = null }

    // ---- hints a screen reads once after it opens (set by AppNavGraph when it routes a link) ----
    //   plan           "annual" | "monthly"  → Plans pre-selects it (PaywallViewModel)
    //   earnings_period "today" | "week" | "month" | "all" → Earnings opens on it
    //   demo           "1" → Home opens the demo video
    //   bill_details   "1" → Bill design opens its details form
    private val hints = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun setHint(key: String, value: String?) { if (value.isNullOrBlank()) hints.remove(key) else hints[key] = value }
    fun takeHint(key: String): String? = hints.remove(key)
    fun setPlanHint(plan: String?) = setHint("plan", plan?.takeIf { it == "annual" || it == "monthly" })
    fun takePlanHint(): String? = takeHint("plan")
}
