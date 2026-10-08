package com.dailyworks.mylaundry.support.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.Instant

/**
 * One support call, end to end:
 *  1. [start]    — log it on the server (agent + time) and remember it locally
 *  2. dial       — [dialIntent] hands off to the phone's dialer, which records it
 *  3. [finalize] — once the phone is idle again: real talk time from the call
 *                  log, end time + duration saved, recording upload queued
 * Notes / outcome / tags are saved separately from the notes screen, any time.
 */
class CallFlow(
    private val context: Context,
    private val api: SupportApi,
    private val prefs: Prefs,
    private val monitor: CallMonitor,
    private val finder: RecordingFinder,
) {
    suspend fun start(userId: String, phone: String, label: String): PendingCall {
        val agent = prefs.current().agentName.ifBlank { throw ApiException("Set your name in Settings first") }
        val now = System.currentTimeMillis()
        val call = api.startCall(userId, phone, agent, Instant.ofEpochMilli(now).toString())
        val pending = PendingCall(call.id, userId, phone, label, now)
        prefs.setPending(pending)
        return pending
    }

    fun dialIntent(phone: String): Intent =
        Intent(Intent.ACTION_CALL, Uri.parse("tel:+91${phone.filter(Char::isDigit).takeLast(10)}"))

    /**
     * Logs a call that wasn't placed from this app (normal dialer, another
     * phone) so it gets the same after-call notes, outcome and tags. With a
     * recording [file], its start time and length come from the file
     * ([callStartFor]) and the file is uploaded; without one, it's logged now.
     */
    suspend fun logPastCall(userId: String, phone: String, file: Uri?): CallDto {
        val agent = prefs.current().agentName.ifBlank { throw ApiException("Set your name in Settings first") }
        if (file == null) {
            return api.startCall(userId, phone, agent, Instant.now().toString())
        }
        val info = finder.describe(file) ?: throw ApiException("Can't read that file — pick it again")
        val now = System.currentTimeMillis()
        val lengthMs = finder.durationMs(file)
        val startedAt = callStartFor(info.name, info.lastModifiedMs, lengthMs, now)
        val endedAt = startedAt + lengthMs
        val created = api.startCall(userId, phone, agent, Instant.ofEpochMilli(startedAt).toString())
        val call = api.updateCall(
            id = created.id,
            endedAtIso = Instant.ofEpochMilli(endedAt).toString(),
            durationSec = (lengthMs / 1000).toInt().takeIf { lengthMs > 0 },
        )
        RecordingUploads.enqueue(context, call.id, phone, startedAt, endedAt, fileUri = file)
        return call
    }

    /** Returns the updated call. Safe to call twice (second PATCH just repeats). */
    suspend fun finalize(p: PendingCall): CallDto {
        val now = System.currentTimeMillis()
        val log = monitor.lastCallTo(p.phone, p.startedAtMs)
        val endedAt = log?.endedAtMs?.coerceAtMost(now) ?: now
        val duration = log?.durationSec ?: ((now - p.startedAtMs) / 1000).toInt()
        val call = api.updateCall(
            id = p.callId,
            endedAtIso = Instant.ofEpochMilli(endedAt).toString(),
            durationSec = duration,
            // 0s of talk time in the call log = never picked up.
            outcome = if (log != null && log.durationSec == 0) Outcome.NO_ANSWER.name else null,
        )
        // An unanswered call has nothing to record.
        if (log == null || log.durationSec > 0) {
            RecordingUploads.enqueue(context, p.callId, p.phone, p.startedAtMs, endedAt)
        }
        prefs.clearPending(p.callId)
        return call
    }
}
