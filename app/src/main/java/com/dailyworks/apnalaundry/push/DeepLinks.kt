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
 * MainActivity hands every incoming intent to [offer]; AppNavGraph opens the
 * pending screen once the owner is logged in and set up (so a "take your first
 * order" tap before setup opens New order right after it).
 */
object DeepLinks {
    const val SCHEME = "mylaundry"
    val SCREENS = setOf("new-order", "home", "customers", "earnings", "subscription")

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    fun offer(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != SCHEME) return
        val screen = uri.host?.lowercase() ?: return
        if (screen in SCREENS) _pending.value = screen
    }

    /** Take the pending screen (null when there's none). */
    fun consume(): String? = _pending.value.also { _pending.value = null }
}
