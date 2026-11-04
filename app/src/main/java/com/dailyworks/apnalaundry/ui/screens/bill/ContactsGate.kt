package com.dailyworks.apnalaundry.ui.screens.bill

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.dailyworks.apnalaundry.analytics.Analytics
import kotlinx.coroutines.CompletableDeferred

/**
 * Whether bills may look the customer up in the phone's contacts (READ_CONTACTS),
 * asked the first time a bill is sent and never nagged again after a "no":
 * `val contactsAllowed = rememberContactsAllowed()` … `contactsAllowed()` inside
 * the send coroutine, which waits for the system dialog when it has to show one.
 * Granted → the picture goes to saved numbers, text + link to unsaved ones;
 * denied → every bill goes as a picture, as before.
 */
@Composable
fun rememberContactsAllowed(): suspend () -> Boolean {
    val context = LocalContext.current
    val waiting = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Analytics.contactsPermissionAnswered(granted)
        waiting[0]?.complete(granted)
        waiting[0] = null
    }
    return remember(launcher) {
        suspend {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) true
            else {
                val prefs = context.getSharedPreferences("bill_share", Context.MODE_PRIVATE)
                if (prefs.getBoolean("contacts_asked", false)) false
                else {
                    prefs.edit().putBoolean("contacts_asked", true).apply()
                    val d = CompletableDeferred<Boolean>()
                    waiting[0] = d
                    launcher.launch(Manifest.permission.READ_CONTACTS)
                    d.await()
                }
            }
        }
    }
}
