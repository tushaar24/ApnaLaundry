package com.dailyworks.apnalaundry.ui.screens.login

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

// Firebase OTP SMS autofill is Firebase phone auth's own (see FirebasePhoneAuth):
// its SMS carries the app hash and the SDK reads it and logs in. The server's own
// SMS (MSG91, a fixed DLT template) goes through SmsOtpConsent below.

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

/**
 * SMS User Consent: while on screen, waits (up to 5 min) for the server's OTP
 * SMS. When it lands, Android asks "Allow MyLaundry to read this message?" —
 * one tap on Allow and the 6-digit code goes to [onCode]. No SMS permission and
 * no app hash in the template needed. [key] restarts the wait (a resend).
 */
@Composable
fun SmsOtpConsent(key: Any, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val code by rememberUpdatedState(onCode)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val sms = res.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE) ?: return@rememberLauncherForActivityResult
        Regex("(?<!\\d)\\d{6}(?!\\d)").find(sms)?.value?.let(code)
    }
    DisposableEffect(key) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return
                val extras = intent.extras ?: return
                @Suppress("DEPRECATION")
                val status = (if (Build.VERSION.SDK_INT >= 33) extras.getParcelable(SmsRetriever.EXTRA_STATUS, Status::class.java)
                else extras.getParcelable(SmsRetriever.EXTRA_STATUS)) ?: return
                if (status.statusCode != CommonStatusCodes.SUCCESS) return
                @Suppress("DEPRECATION")
                val consent = (if (Build.VERSION.SDK_INT >= 33) extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT, Intent::class.java)
                else extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT)) ?: return
                // Only launch Play services' own consent screen, granting nothing.
                val target = consent.resolveActivity(ctx.packageManager)
                if (target?.packageName != "com.google.android.gms") return
                if (consent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) return
                runCatching { launcher.launch(consent) }
            }
        }
        val registered = runCatching {
            ContextCompat.registerReceiver(
                context, receiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
                SmsRetriever.SEND_PERMISSION, null, ContextCompat.RECEIVER_EXPORTED,
            )
            SmsRetriever.getClient(context).startSmsUserConsent(null)
        }.isSuccess
        onDispose { if (registered) runCatching { context.unregisterReceiver(receiver) } }
    }
}
