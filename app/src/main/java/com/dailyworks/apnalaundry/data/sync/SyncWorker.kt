package com.dailyworks.apnalaundry.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Background sync: retries unpushed changes when connectivity returns even if
 * the app was killed, and keeps pulls reasonably fresh. Scheduled periodically
 * (network-constrained) from ApnaLaundryApp.
 */
class SyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params), KoinComponent {

    private val syncManager: SyncManager by inject()

    override suspend fun doWork(): Result = when (syncManager.syncNow()) {
        is SyncManager.Result.Error -> if (runAttemptCount < 3) Result.retry() else Result.success()
        else -> Result.success()
    }
}
