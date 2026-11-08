package com.dailyworks.mylaundry.support.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dailyworks.mylaundry.support.MainActivity
import com.dailyworks.mylaundry.support.SupportApp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Reminders on the phone (8 Nov 2026): an alarm + notification at each of this agent's open
 * reminders ("Call Sharma Laundry — asked to call after 5"). Reminders live on the server
 * (shared by the team); [sync] fetches this agent's next 8 days and sets an exact alarm for
 * each, after every change in the app, every 30 minutes and after a reboot.
 */
object ReminderAlarms {
    const val CHANNEL = "reminders"
    const val EXTRA_OPEN_SHOP = "open_shop_id"
    private const val SCHEDULED = "scheduled_reminder_ids"

    private fun prefs(context: Context) = context.getSharedPreferences("reminder_alarms", Context.MODE_PRIVATE)

    suspend fun sync(context: Context) {
        val graph = (context.applicationContext as SupportApp).graph
        val agent = graph.prefs.current().agentName
        if (agent.isBlank()) return
        val now = Instant.now()
        val due = graph.api.reminders(
            fromIso = now.minus(1, ChronoUnit.MINUTES).toString(),
            toIso = now.plus(8, ChronoUnit.DAYS).toString(),
            agent = agent,
        )
        val before = prefs(context).getStringSet(SCHEDULED, emptySet()).orEmpty()
        val keep = due.map { it.id }.toSet()
        (before - keep).forEach { cancel(context, it) }
        due.forEach { schedule(context, it) }
        prefs(context).edit().putStringSet(SCHEDULED, keep).apply()
    }

    /** Sync soon, off the UI (after creating / changing a reminder). */
    fun syncSoon(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminders-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderSyncWorker>().build(),
        )
    }

    /** Every 30 minutes, so reminders set on another phone ring here too. */
    fun keepSynced(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "reminders-sync", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderSyncWorker>(30, TimeUnit.MINUTES).build(),
        )
    }

    private fun intentFor(context: Context, r: ReminderDto? = null, id: String) =
        PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction("reminder:$id").apply {
                if (r != null) {
                    putExtra("id", r.id)
                    putExtra("userId", r.userId)
                    putExtra("title", if (r.isCall) "Call ${r.title}" else "Follow up: ${r.title}")
                    putExtra("text", r.note.ifBlank { "+91 ${r.phone.orEmpty()}" })
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun schedule(context: Context, r: ReminderDto) {
        val at = runCatching { Instant.parse(r.dueAt).toEpochMilli() }.getOrNull() ?: return
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = intentFor(context, r, r.id)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) // a few minutes late at worst
        }
    }

    private fun cancel(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java).cancel(intentFor(context, id = id))
    }

    fun show(context: Context, id: String, userId: String, title: String, text: String) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Calls and follow-ups you scheduled"
            },
        )
        val open = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_SHOP, userId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify("reminder-$id", 0, n)
        } catch (_: SecurityException) {
            // Notifications not allowed.
        }
    }
}

/** A reminder's alarm went off. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        ReminderAlarms.show(
            context, id,
            intent.getStringExtra("userId").orEmpty(),
            intent.getStringExtra("title") ?: "Reminder",
            intent.getStringExtra("text").orEmpty(),
        )
    }
}

/** A restart clears alarms: set them again. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ReminderAlarms.syncSoon(context)
    }
}

class ReminderSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        ReminderAlarms.sync(applicationContext)
        Result.success()
    } catch (_: ApiException) {
        Result.retry()
    }
}
