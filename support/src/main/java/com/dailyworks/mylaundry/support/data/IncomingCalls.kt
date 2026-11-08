package com.dailyworks.mylaundry.support.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dailyworks.mylaundry.support.MainActivity
import com.dailyworks.mylaundry.support.SupportApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant

/**
 * Who is calling (8 Nov 2026). When an owner calls the MyLaundry number, the server rings
 * the agents' phones from that number. The moment this phone rings, ask the server whether
 * an incoming MyLaundry call is ringing for this agent, and if so show who it is — shop,
 * plan, orders, last call — before the agent picks up. (On picking up, Vobiz also says the
 * shop's name.) Tapping the notification opens the call's notes.
 */
class IncomingCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (intent.getStringExtra(TelephonyManager.EXTRA_STATE) != TelephonyManager.EXTRA_STATE_RINGING) return
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        // With call-log access Android sends RINGING twice (without, then with the number):
        // wait for the one with the number. Without it, the number never comes.
        val canReadNumber = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
        if (number == null && canReadNumber) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRingAt < 15_000) return
        lastRingAt = now

        val graph = (context.applicationContext as SupportApp).graph
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val agentPhone = graph.prefs.current().agentPhone
                if (agentPhone.length != 10) return@launch
                // The server logs the call a moment before Vobiz rings this phone; try a few times.
                val found = withTimeoutOrNull(8_000) {
                    repeat(3) {
                        val r = runCatching { graph.api.incoming(agentPhone, number) }.getOrNull()
                        if (r?.call != null) return@withTimeoutOrNull r
                        delay(1_500)
                    }
                    null
                }
                val call = found?.call ?: return@launch
                IncomingNotifier.show(context, call, found.owner)
            } finally {
                done.finish()
            }
        }
    }

    private companion object {
        var lastRingAt = 0L
    }
}

object IncomingNotifier {
    private const val CHANNEL = "incoming_calls"
    private const val ID = 4242
    const val EXTRA_CALL_ID = "open_call_id"

    fun show(context: Context, call: CallDto, owner: OwnerSummary?) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Who is calling", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shows which shop is calling the MyLaundry number"
            },
        )
        val who = owner?.shopName ?: owner?.name ?: call.shopName ?: call.ownerName
        val title = if (who != null) "$who is calling" else "New number +91 ${call.phone} is calling"
        val text = if (owner != null) summary(owner) else "Not a MyLaundry shop yet"
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_CALL_ID, call.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60_000L)
            .build()
        try {
            nm.notify(ID, n)
        } catch (_: SecurityException) {
            // Notifications not allowed.
        }
    }

    /** "Raju (owner) · Paying · 34 orders · last call 2 days ago by Vishal". */
    private fun summary(o: OwnerSummary): String {
        val plan = when (o.subscription.state) {
            "paid" -> "Paying"
            "trial" -> "On trial"
            "cancelled" -> "Cancelled plan"
            "halted" -> "Payment failing"
            "expired" -> "Plan expired"
            else -> "Free"
        }
        val last = o.calls.lastAt?.let { at ->
            val days = runCatching { Duration.between(Instant.parse(at), Instant.now()).toDays() }.getOrNull()
            val ago = when (days) { null -> null; 0L -> "today"; 1L -> "yesterday"; else -> "$days days ago" }
            ago?.let { "last call $it" + (o.calls.lastAgent?.let { a -> " by $a" } ?: "") }
        }
        return listOfNotNull(o.name, plan, "${o.orders} orders", last).joinToString(" · ")
    }
}
