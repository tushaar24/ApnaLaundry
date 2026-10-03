package com.dailyworks.apnalaundry.core

/**
 * Wall-clock source for the sync layer's `updatedAt` stamps (last-write-wins
 * key on the server). Monotonic within the process so two writes in the same
 * millisecond still order deterministically. Independent of [AppDate]'s pinned
 * demo calendar — sync needs real timestamps.
 */
object SyncClock {
    @Volatile private var last = 0L

    @Synchronized
    fun now(): Long {
        val t = maxOf(System.currentTimeMillis(), last + 1)
        last = t
        return t
    }
}
