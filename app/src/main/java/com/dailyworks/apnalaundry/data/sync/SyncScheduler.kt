package com.dailyworks.apnalaundry.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Debounced foreground sync trigger. Every local mutation calls [requestSync];
 * a burst of writes collapses into one sync a couple of seconds later.
 * While the app is visible ([onForeground]) it also syncs on open and every
 * minute, so edits made on another device (or the website) show up promptly —
 * mirror of the web's visible-tab polling. Background retry is SyncWorker's job.
 */
@OptIn(FlowPreview::class)
class SyncScheduler(private val syncManager: SyncManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        scope.launch {
            requests.debounce(2_000).collect { syncManager.syncNow() }
        }
    }

    /** Another device's changes were just pulled in. */
    val remoteChanges get() = syncManager.remoteChanges

    fun requestSync() {
        requests.tryEmit(Unit)
    }

    private var foreground: Job? = null

    /** App came to the foreground: sync now, then every minute until [onBackground]. */
    fun onForeground() {
        if (foreground?.isActive == true) return
        foreground = scope.launch {
            while (isActive) {
                syncManager.syncNow()
                delay(FOREGROUND_INTERVAL_MS)
            }
        }
    }

    fun onBackground() {
        foreground?.cancel()
        foreground = null
    }

    private companion object {
        const val FOREGROUND_INTERVAL_MS = 60_000L
    }
}
