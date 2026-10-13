package com.dailyworks.apnalaundry.analytics

import com.dailyworks.apnalaundry.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Firebase Crashlytics (project shweta-makeover). Collection is off in debug
 * builds so local crashes don't land in the console. Crashes carry the owner's
 * user id (same as CleverTap's Identity) so a report can be matched to a shop.
 */
object CrashReporting {
    fun init() {
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
    }

    /** The logged-in owner, or null after logout. */
    fun setUser(userId: String?) {
        FirebaseCrashlytics.getInstance().setUserId(userId ?: "")
    }
}
