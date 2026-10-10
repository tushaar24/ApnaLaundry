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
 * SMS Retriever: while on screen, waits (up to 5 min) for the OTP SMS and
 * reads it with no prompt and no SMS permission; the 6-digit code goes to
 * [onCode]. Play services only hands over an SMS that ends with this app's
 * 11-char hash — Play-signed builds: L0M29zKHAPt (debug / sideloaded builds
 * have their own hash, so they don't autofill). [key] restarts the wait (a
 * resend). The OTP's WhatsApp copy isn't covered — that one is typed or pasted.
 */
@Composable
fun SmsOtpRetriever(key: Any, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val code by rememberUpdatedState(onCode)
    DisposableEffect(key) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return
                val extras = intent.extras ?: return
                @Suppress("DEPRECATION")
                val status = (if (Build.VERSION.SDK_INT >= 33) extras.getParcelable(SmsRetriever.EXTRA_STATUS, Status::class.java)
                else extras.getParcelable(SmsRetriever.EXTRA_STATUS)) ?: return
                if (status.statusCode != CommonStatusCodes.SUCCESS) return
                val sms = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE) ?: return
                Regex("(?<!\\d)\\d{6}(?!\\d)").find(sms)?.value?.let(code)
            }
        }
        val registered = runCatching {
            ContextCompat.registerReceiver(
                context, receiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
                SmsRetriever.SEND_PERMISSION, null, ContextCompat.RECEIVER_EXPORTED,
            )
            SmsRetriever.getClient(context).startSmsRetriever()
        }.isSuccess
        onDispose { if (registered) runCatching { context.unregisterReceiver(receiver) } }
    }
}
