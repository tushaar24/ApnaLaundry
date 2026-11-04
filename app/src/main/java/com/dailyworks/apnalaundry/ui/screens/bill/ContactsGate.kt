package com.dailyworks.apnalaundry.ui.screens.bill

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.experiments.ExperimentsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

/**
 * The Contacts permission at a bill send ([key] = analytics `contacts` on Bill Sent /
 * Combined Bill Sent); [allowed] = the customer's number may be looked up.
 */
enum class ContactsCheck(val key: String, val allowed: Boolean) {
    /** Already allowed (at an earlier send or in "Choose from contacts"). */
    GRANTED("granted", true),
    /** Asked at this send: yes. */
    GRANTED_NOW("granted_now", true),
    /** Asked at this send: no. */
    DENIED_NOW("denied_now", false),
    /** Asked at an earlier send on this phone and not allowed: never asked again. */
    DENIED("denied", false),
    /** The server switch says don't ask (no_ask, or never fetched). */
    OFF("off", false),
}

/**
 * Whether bills may look the customer up in the phone's contacts (READ_CONTACTS),
 * asked the first time a bill is sent and never nagged again after a "no" — only
 * when the server's billContactsAsk switch says "ask" (GET /experiments, saved in
 * [Prefs]; read at each send). A permission already given is used either way.
 * `val contactsCheck = rememberContactsCheck("bill")` … `contactsCheck()` inside
 * the send coroutine, which waits for the system dialog when it has to show one.
 * Allowed → the picture goes to saved numbers, text + link to unsaved ones;
 * otherwise every bill goes as a picture, as before. [screen] = where the send is
 * (bill | order_detail | combined_bill | assistant), for analytics.
 */
@Composable
fun rememberContactsCheck(screen: String): suspend () -> ContactsCheck {
    val context = LocalContext.current
    val prefs: Prefs = koinInject()
    val waiting = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val askedAt = remember { LongArray(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Analytics.contactsPermissionAnswered(granted, "bill_send", screen, SystemClock.elapsedRealtime() - askedAt[0])
        waiting[0]?.complete(granted)
        waiting[0] = null
    }
    return remember(launcher) {
        suspend {
            val share = context.getSharedPreferences("bill_share", Context.MODE_PRIVATE)
            when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED -> ContactsCheck.GRANTED
                share.getBoolean("contacts_asked", false) -> ContactsCheck.DENIED
                prefs.billContactsAsk.first() != ExperimentsRepository.CONTACTS_ASK -> ContactsCheck.OFF
                else -> {
                    share.edit().putBoolean("contacts_asked", true).apply()
                    val d = CompletableDeferred<Boolean>()
                    waiting[0] = d
                    askedAt[0] = SystemClock.elapsedRealtime()
                    Analytics.contactsPermissionAsked("bill_send", screen)
                    launcher.launch(Manifest.permission.READ_CONTACTS)
                    if (d.await()) ContactsCheck.GRANTED_NOW else ContactsCheck.DENIED_NOW
                }
            }
        }
    }
}
