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
import com.clevertap.android.pushtemplates.PushTemplateNotificationHandler
import com.clevertap.android.sdk.CleverTapAPI
import com.clevertap.android.sdk.interfaces.NotificationHandler
import com.clevertap.android.sdk.pushnotification.fcm.CTFcmMessageHandler
import com.dailyworks.apnalaundry.MainActivity
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.R
import com.dailyworks.apnalaundry.core.tr
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging (project shweta-makeover, app/google-services.json).
 *
 * - CleverTap campaigns: CleverTap gets the FCM token and renders its own
 *   pushes (title, image, deep link, Push Templates) on the [CHANNEL_ID] channel.
 * - Anything else (Firebase console, our backend): a notification with the
 *   message's title/body that opens the app. Background "notification"
 *   messages are drawn by Firebase itself, using the manifest defaults.
 */
object Push {
    const val CHANNEL_ID = "general"
    /** New customer leads (1.0.22): high importance, sound — like a ride request. */
    const val LEADS_CHANNEL_ID = "leads"
    private const val TAG = "Push"

    /** App start: CleverTap's template renderer, the notification channel, and the token for CleverTap. */
    fun init(context: Context) {
        // Push Templates: CleverTap campaigns can use rich layouts (carousel,
        // timer, rating, …). CTFcmMessageHandler below renders through this.
        CleverTapAPI.setNotificationHandler(PushTemplateNotificationHandler() as NotificationHandler)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Re-creating it renames it: the next app start shows a changed language.
            val channel = NotificationChannel(CHANNEL_ID, tr("Updates"), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = tr("Tips and news from MyLaundry") }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            val leads = NotificationChannel(LEADS_CHANNEL_ID, tr("New customers"), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = tr("A customer near you is waiting — pay per customer"); enableVibration(true) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(leads)
        }
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> onToken(context, token) }
            .addOnFailureListener { Log.w(TAG, "FCM token unavailable", it) }
    }

    internal fun onToken(context: Context, token: String) {
        CleverTapAPI.getDefaultInstance(context)?.pushFcmRegistrationId(token, true)
        // Our own pushes (a new lead, 1.0.22): the server keeps the token per owner (data/leads/DeviceRegistrar).
        runCatching { org.koin.core.context.GlobalContext.getOrNull()?.get<com.dailyworks.apnalaundry.data.leads.DeviceRegistrar>()?.onToken(token) }
            .onFailure { Log.w(TAG, "device token not registered", it) }
    }

    /** A push that isn't CleverTap's: title + body, tap opens the app ([link]: a screen; [channel]: ours, default "general"). */
    internal fun show(context: Context, title: String?, body: String?, link: String? = null, channel: String? = null, tag: String? = null) {
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        val channelId = if (channel == LEADS_CHANNEL_ID) LEADS_CHANNEL_ID else CHANNEL_ID
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // Optional `link` data field: mylaundry://new-order etc. (DeepLinks).
        link?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }?.let { intent.action = Intent.ACTION_VIEW; intent.data = it }
        val open = PendingIntent.getActivity(
            context, 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .apply {
                if (channelId == LEADS_CHANNEL_ID) {
                    // Heads-up with sound on phones before channels (Android 7): the channel carries it from 8.
                    priority = NotificationCompat.PRIORITY_HIGH
                    setDefaults(NotificationCompat.DEFAULT_ALL)
                    setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                }
            }
            .build()
        if (canNotify(context)) {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(tag ?: System.currentTimeMillis().toString(), 0, n)
        }
    }

    /**
     * Keep the CleverTap profile's `notifications_enabled` current (any Android
     * version), and fire "Notifications Status Changed" when it differs from
     * the last status this install reported.
     */
    fun reportEnabled(context: Context) {
        val on = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val sp = context.getSharedPreferences("push", Context.MODE_PRIVATE)
        val last = if (sp.contains(KEY_LAST_ENABLED)) sp.getBoolean(KEY_LAST_ENABLED, false) else null
        if (on == last) return
        sp.edit().putBoolean(KEY_LAST_ENABLED, on).apply()
        Analytics.updateProfile(mapOf("notifications_enabled" to on))
        Analytics.notificationsStatusChanged(on)
    }

    private const val KEY_LAST_ENABLED = "last_enabled"

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
        // A new lead (1.0.22): the app is open — refresh the leads so Home's card shows it at once.
        if (message.data["type"] == "lead") {
            runCatching { org.koin.core.context.GlobalContext.getOrNull()?.get<com.dailyworks.apnalaundry.data.leads.LeadsRepository>() }.getOrNull()?.let { repo ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { runCatching { repo.refresh() } }
            }
        }
        // Foreground "notification" messages, and data-only ones with title/body.
        Push.show(
            applicationContext,
            message.notification?.title ?: message.data["title"],
            message.notification?.body ?: message.data["body"],
            message.data["link"],
            channel = message.data["channel"],
            tag = message.data["leadId"]?.let { "lead-$it" },
        )
    }
}

/**
 * Android 13+: ask for the notification permission (the system stops showing
 * the prompt after the owner says "Don't allow" twice). [onDone] runs once
 * it's answered — or straight away when there's nothing to ask.
 */
@Composable
fun AskNotificationPermission(where: String, onDone: () -> Unit = {}) {
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Analytics.notificationPermissionAnswered(granted, where, prompted = true)
        Push.reportEnabled(context)
        done()
    }
    LaunchedEffect(Unit) {
        if (Push.canNotify(context)) {
            Analytics.notificationPermissionAnswered(granted = true, where = where, prompted = false)
            done()
        } else {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
