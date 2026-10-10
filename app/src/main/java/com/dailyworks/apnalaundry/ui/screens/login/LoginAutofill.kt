package com.dailyworks.apnalaundry.ui.screens.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity

// OTP SMS autofill is Firebase phone auth's own (see FirebasePhoneAuth): its
// SMS carries the app hash and the SDK reads it and logs in.

/**
 * Google's Phone Number Hint: a bottom sheet listing the SIM numbers on the
 * device; the picked one comes back as its last 10 digits. Returns a launcher
 * — calling it is a no-op on devices without Play services or a SIM number.
 */
@Composable
fun rememberPhoneHint(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val picked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        runCatching { Identity.getSignInClient(context).getPhoneNumberFromIntent(res.data) }
            .getOrNull()
            ?.filter { it.isDigit() }
            ?.takeLast(10)
            ?.takeIf { it.length == 10 }
            ?.let(picked)
    }
    return {
        runCatching {
            Identity.getSignInClient(context)
                .getPhoneNumberHintIntent(GetPhoneNumberHintIntentRequest.builder().build())
                .addOnSuccessListener { pi ->
                    runCatching { launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build()) }
                }
        }
    }
}
