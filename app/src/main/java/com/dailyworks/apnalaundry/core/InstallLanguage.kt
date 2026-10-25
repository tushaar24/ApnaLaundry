package com.dailyworks.apnalaundry.core

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.dailyworks.apnalaundry.analytics.Analytics

/**
 * A language link tapped on a phone without the app (mylaundry.work/open/hi →
 * the Play Store with `referrer=lang=hi`, web/src/core/openApp.ts): the first
 * open reads Play's install referrer once and switches the app to that language
 * (owner, 2026-10-25). Skipped when a language was already picked on this phone
 * — also when a link set one; nothing happens without a `lang` in the referrer.
 */
object InstallLanguage {
    private const val PREFS = "app_language" // I18n's
    private const val KEY_CHECKED = "install_referrer_checked"

    fun check(activity: Activity) {
        val prefs = activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_CHECKED, false) || I18n.chosen(activity)) return
        val client = runCatching { InstallReferrerClient.newBuilder(activity.applicationContext).build() }.getOrNull() ?: return
        runCatching {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(code: Int) {
                    val referrer = if (code == InstallReferrerClient.InstallReferrerResponse.OK) {
                        runCatching { client.installReferrer.installReferrer }.getOrNull()
                    } else null
                    runCatching { client.endConnection() }
                    prefs.edit().putBoolean(KEY_CHECKED, true).apply()
                    val lang = parse(referrer) ?: return
                    activity.runOnUiThread {
                        // They may have picked one in the meantime: theirs wins.
                        if (I18n.chosen(activity)) return@runOnUiThread
                        val from = I18n.language
                        I18n.set(activity, lang)
                        Analytics.languageChanged(from.code, lang.code, "install_link")
                        if (from != lang && !activity.isFinishing && !activity.isDestroyed) activity.recreate()
                    }
                }

                override fun onInstallReferrerServiceDisconnected() {}
            })
        }
    }

    /** `lang=hi` (maybe among utm_… pairs) → Hindi. */
    fun parse(referrer: String?): AppLanguage? = referrer?.split('&')?.firstNotNullOfOrNull { pair ->
        val (k, v) = pair.split('=', limit = 2).takeIf { it.size == 2 } ?: return@firstNotNullOfOrNull null
        if (Uri.decode(k) == "lang") AppLanguage.of(Uri.decode(v).trim().lowercase()) else null
    }
}
