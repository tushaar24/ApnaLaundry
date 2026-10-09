package com.dailyworks.apnalaundry

import android.app.Application
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.analytics.MetaEvents
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.sync.SyncScheduler
import com.dailyworks.apnalaundry.data.sync.SyncWorker
import com.dailyworks.apnalaundry.di.appModule
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ApnaLaundryApp : Application() {
    override fun onCreate() {
        // CleverTap activity-lifecycle hooks must register before super.onCreate()
        // (no-ops until the manifest account id/token are set).
        Analytics.registerLifecycle(this)
        super.onCreate()
        Analytics.init(this)
        MetaEvents.init(this)

        startKoin {
            androidContext(this@ApnaLaundryApp)
            modules(appModule)
        }

        // Hourly background sync (network-gated) so unpushed khata/order data
        // still reaches the server when the app isn't in the foreground.
        val periodicSync = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "laundry-sync", ExistingPeriodicWorkPolicy.KEEP, periodicSync,
        )

        // Catch-up sync on every launch. Seeding is no longer done here — a
        // new account seeds after its first login (AuthRepository.verifyOtp).
        val prefs: Prefs = get()
        val scheduler: SyncScheduler = get()
        CoroutineScope(Dispatchers.IO).launch {
            if (prefs.loggedIn.first()) scheduler.requestSync()
        }
    }
}
