package com.dailyworks.mylaundry.support.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dailyworks.mylaundry.support.MainActivity
import com.dailyworks.mylaundry.support.SupportApp
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * A notification for each new WhatsApp message on the MyLaundry number (8 Nov 2026). The
 * server pushes it through Firebase (backend services/laundry-support-push.js) to every
 * phone signed in to the WhatsApp tab. While the app is in the background Android shows the
 * push itself, on the same channel; tapping it opens the chat ("waId" extra).
 */
object WaNotifier {
    const val CHANNEL = "whatsapp_messages"
    const val EXTRA_WA_ID = "waId"

    /** The chat on screen right now: no notification for it. */
    @Volatile var openChat: String? = null

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "WhatsApp messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New messages on the MyLaundry WhatsApp number"
            },
        )
    }

    fun show(context: Context, waId: String, title: String, body: String) {
        if (waId == openChat) return
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            waId.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_WA_ID, waId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify("wa-$waId", 0, n) // same tag as the server's pushes: one per chat
        } catch (_: SecurityException) {
            // Notifications not allowed.
        }
    }

    fun clear(context: Context, waId: String) = NotificationManagerCompat.from(context).cancel("wa-$waId", 0)
}

/**
 * "💰 New paying shop" (8 Nov 2026): the server pushes an owner's first-ever payment to every
 * support-app phone signed in to the WhatsApp tab. Tapping it opens the shop ("open_shop_id").
 */
object PaymentsNotifier {
    const val CHANNEL = "payments"

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "New paying shops", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A shop owner paid for the first time"
            },
        )
    }

    fun show(context: Context, userId: String, title: String, body: String) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            ("paid-$userId").hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(ReminderAlarms.EXTRA_OPEN_SHOP, userId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify("paid-$userId", 0, n)
        } catch (_: SecurityException) {
            // Notifications not allowed.
        }
    }
}

/** Registers this phone's Firebase token with the server (needs the WhatsApp sign-in). */
object WaPush {
    /** False until support/google-services.json is added and the app rebuilt. */
    fun available(context: Context) = FirebaseApp.getApps(context).isNotEmpty()

    fun register(context: Context) {
        if (!available(context)) return
        val graph = (context.applicationContext as SupportApp).graph
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val s = graph.prefs.current()
                if (s.waCookie == null) return@launch
                val token = FirebaseMessaging.getInstance().token.await()
                graph.wa.registerDevice(token, s.agentName, s.agentPhone)
            }
        }
    }

    /** On sign-out: stop pushes to this phone. */
    suspend fun unregister(context: Context) {
        if (!available(context)) return
        val graph = (context.applicationContext as SupportApp).graph
        runCatching { graph.wa.removeDevice(FirebaseMessaging.getInstance().token.await()) }
    }
}

class SupportMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = WaPush.register(applicationContext)

    // Only while the app is open — in the background Android shows the push itself.
    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        if (d["type"] == "paid") {
            PaymentsNotifier.show(
                applicationContext,
                d["open_shop_id"] ?: return,
                d["title"] ?: message.notification?.title ?: "New paying shop",
                d["body"] ?: message.notification?.body.orEmpty(),
            )
            return
        }
        if (d["type"] != "whatsapp") return
        val waId = d["waId"] ?: return
        WaNotifier.show(
            applicationContext,
            waId,
            d["title"] ?: message.notification?.title ?: "WhatsApp",
            d["body"] ?: message.notification?.body ?: "New message",
        )
    }
}
