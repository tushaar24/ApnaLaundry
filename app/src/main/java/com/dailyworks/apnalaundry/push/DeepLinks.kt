package com.dailyworks.apnalaundry.push

import android.content.Intent
import com.dailyworks.apnalaundry.core.AppLanguage
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
 * Language links (1.0.16+, owner 2026-10-25): the app switches to the link's
 * language — see [language] and docs/language-links.md:
 *
 *   https://mylaundry.work/open/hi             the app in Hindi (en hi mr gu bn ta te kn ml pa or)
 *   https://mylaundry.work/open/hi/new-order   in Hindi, on New order
 *   mylaundry://hi · mylaundry://hi/new-order  the same in-app
 *   …?lang=hi                                  on any of the links above
 *
 * Older apps read /open/hi as Home and ignore ?lang. Without the app, the
 * site's page sends the owner to the Play Store with referrer lang=hi, and the
 * first open reads it (core/InstallLanguage.kt).
 *
 * MainActivity hands every incoming intent to [offer]; AppNavGraph opens the
 * pending screen once the owner is logged in and set up (so a "take your first
 * order" tap before setup opens New order right after it).
 */
object DeepLinks {
    const val SCHEME = "mylaundry"
    const val WEB_HOST = "mylaundry.work"
    // "assistant": Ask — Home instead where it isn't on.
    val SCREENS = setOf("new-order", "home", "customers", "earnings", "subscription", "assistant")

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    fun offer(intent: Intent?) {
        val uri = intent?.data ?: return
        val screen = when {
            uri.scheme == SCHEME -> uri.host?.lowercase()?.let { host ->
                if (AppLanguage.of(host) != null) uri.pathSegments.firstOrNull()?.lowercase() else host
            }
            uri.scheme == "https" && uri.host == WEB_HOST -> when (uri.pathSegments.firstOrNull()) {
                "open" -> {
                    val rest = uri.pathSegments.drop(1).map { it.lowercase() }
                    val afterLang = if (rest.firstOrNull()?.let(AppLanguage::of) != null) rest.drop(1) else rest
                    afterLang.firstOrNull()?.takeIf { it in SCREENS } ?: "home"
                }
                "pay" -> "subscription"
                else -> null
            }
            else -> null
        } ?: return
        if (screen in SCREENS) _pending.value = screen
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

    /** Take the pending screen (null when there's none). */
    fun consume(): String? = _pending.value.also { _pending.value = null }
}
