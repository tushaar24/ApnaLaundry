package com.dailyworks.apnalaundry.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.clevertap.android.sdk.CleverTapAPI
import com.clevertap.android.sdk.pushnotification.fcm.CTFcmMessageHandler
import com.dailyworks.apnalaundry.MainActivity
import com.dailyworks.apnalaundry.R
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging (project shweta-makeover, app/google-services.json).
 *
 * - CleverTap campaigns: CleverTap gets the FCM token and renders its own
 *   pushes (title, image, deep link) on the [CHANNEL_ID] channel.
 * - Anything else (Firebase console, our backend): a notification with the
 *   message's title/body that opens the app. Background "notification"
 *   messages are drawn by Firebase itself, using the manifest defaults.
 */
object Push {
    const val CHANNEL_ID = "general"
    private const val TAG = "Push"

    /** App start: the notification channel, and hand the current token to CleverTap. */
    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Updates", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Tips and news from MyLaundry" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> onToken(context, token) }
            .addOnFailureListener { Log.w(TAG, "FCM token unavailable", it) }
    }

    internal fun onToken(context: Context, token: String) {
        CleverTapAPI.getDefaultInstance(context)?.pushFcmRegistrationId(token, true)
    }

    /** A push that isn't CleverTap's: title + body, tap opens the app. */
    internal fun show(context: Context, title: String?, body: String?) {
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        if (canNotify(context)) {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), n)
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = Push.onToken(applicationContext, token)

    override fun onMessageReceived(message: RemoteMessage) {
        val extras = android.os.Bundle().apply { message.data.forEach { (k, v) -> putString(k, v) } }
        if (CleverTapAPI.getNotificationInfo(extras).fromCleverTap) {
            CTFcmMessageHandler().createNotification(applicationContext, message)
            return
        }
        // Foreground "notification" messages, and data-only ones with title/body.
        Push.show(
            applicationContext,
            message.notification?.title ?: message.data["title"],
            message.notification?.body ?: message.data["body"],
        )
    }
}

/**
 * Android 13+: ask for the notification permission (the system stops showing
 * the prompt after the owner says "Don't allow" twice). [onDone] runs once
 * it's answered — or straight away when there's nothing to ask.
 */
@Composable
fun AskNotificationPermission(onDone: () -> Unit = {}) {
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { done() }
    LaunchedEffect(Unit) {
        if (Push.canNotify(context)) done() else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
