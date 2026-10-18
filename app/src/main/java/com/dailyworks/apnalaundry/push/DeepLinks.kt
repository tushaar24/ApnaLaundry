package com.dailyworks.apnalaundry.push

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * mylaundry://<screen> links — set as a CleverTap campaign's "On click" deep
 * link (or a `link` data field on other pushes) so a tap lands on the screen
 * the nudge is about:
 *
 *   mylaundry://new-order     New order
 *   mylaundry://home          Orders (Home)
 *   mylaundry://customers     Customers
 *   mylaundry://earnings      Earnings
 *   mylaundry://subscription  Plans / subscription
 *
 * The same screens as https App Links, for WhatsApp buttons and SMS (which
 * can't carry a custom scheme) — verified through mylaundry.work's
 * /.well-known/assetlinks.json, so a tap opens the app with no browser:
 *
 *   https://mylaundry.work/open/<screen>   that screen (anything else: Home)
 *   https://mylaundry.work/pay/<code>      Plans / subscription
 *
 * MainActivity hands every incoming intent to [offer]; AppNavGraph opens the
 * pending screen once the owner is logged in and set up (so a "take your first
 * order" tap before setup opens New order right after it).
 */
object DeepLinks {
    const val SCHEME = "mylaundry"
    const val WEB_HOST = "mylaundry.work"
    val SCREENS = setOf("new-order", "home", "customers", "earnings", "subscription")

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    fun offer(intent: Intent?) {
        val uri = intent?.data ?: return
        val screen = when {
            uri.scheme == SCHEME -> uri.host?.lowercase()
            uri.scheme == "https" && uri.host == WEB_HOST -> when (uri.pathSegments.firstOrNull()) {
                "open" -> uri.pathSegments.getOrNull(1)?.lowercase()?.takeIf { it in SCREENS } ?: "home"
                "pay" -> "subscription"
                else -> null
            }
            else -> null
        } ?: return
        if (screen in SCREENS) _pending.value = screen
    }

    /** Take the pending screen (null when there's none). */
    fun consume(): String? = _pending.value.also { _pending.value = null }
}
