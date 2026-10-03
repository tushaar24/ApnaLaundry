package com.dailyworks.apnalaundry.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Debounced foreground sync trigger. Every local mutation calls [requestSync];
 * a burst of writes collapses into one sync a couple of seconds later.
 * Periodic/background retry is SyncWorker's job.
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

    fun requestSync() {
        requests.tryEmit(Unit)
    }
}
