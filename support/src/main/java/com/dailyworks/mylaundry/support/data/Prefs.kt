package com.dailyworks.mylaundry.support.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val Context.store by preferencesDataStore("support_prefs")

/** The call the agent just dialed, kept until it is finalized (survives process death). */
data class PendingCall(
    val callId: String,
    val userId: String,
    val phone: String,
    val label: String,
    val startedAtMs: Long,
)

/** Local, per-phone settings: who the agent is and where the dialer saves recordings. */
data class Settings(
    val agentName: String = "",
    /** The agent's 10-digit mobile: calls through MyLaundry ring it first. */
    val agentPhone: String = "",
    /** SAF tree the agent picked (the dialer's call-recordings folder), or null. */
    val recordingsTree: String? = null,
    /** The admin.mylaundry.work session ("al_dash=…") the WhatsApp tab uses; null = signed out. */
    val waCookie: String? = null,
)

private val queueJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

class Prefs(private val context: Context) {
    private object K {
        val agent = stringPreferencesKey("agent_name")
        val phone = stringPreferencesKey("agent_phone")
        val wa = stringPreferencesKey("wa_cookie")
        val queue = stringPreferencesKey("call_queue")
        val tree = stringPreferencesKey("recordings_tree")
        val pCall = stringPreferencesKey("pending_call_id")
        val pUser = stringPreferencesKey("pending_user_id")
        val pPhone = stringPreferencesKey("pending_phone")
        val pLabel = stringPreferencesKey("pending_label")
        val pStart = longPreferencesKey("pending_started_at")
    }

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(agentName = p[K.agent].orEmpty(), agentPhone = p[K.phone].orEmpty(), recordingsTree = p[K.tree], waCookie = p[K.wa])
    }

    val pendingCall: Flow<PendingCall?> = context.store.data.map(::pending)

    private fun pending(p: Preferences): PendingCall? {
        val id = p[K.pCall] ?: return null
        return PendingCall(
            callId = id,
            userId = p[K.pUser].orEmpty(),
            phone = p[K.pPhone].orEmpty(),
            label = p[K.pLabel].orEmpty(),
            startedAtMs = p[K.pStart] ?: 0L,
        )
    }

    suspend fun current(): Settings = settings.first()
    suspend fun currentPending(): PendingCall? = pendingCall.first()

    suspend fun setAgentName(name: String) = context.store.edit { it[K.agent] = name.trim() }

    suspend fun setAgentPhone(phone: String) = context.store.edit { it[K.phone] = phone.filter(Char::isDigit).takeLast(10) }

    /** The agent's call queue (JSON list of [QueueItem]). */
    val queue: Flow<List<QueueItem>> = context.store.data.map { p ->
        // One entry per key (the screen's list is keyed by it).
        p[K.queue]?.let { runCatching { queueJson.decodeFromString<List<QueueItem>>(it) }.getOrNull() }.orEmpty().distinctBy { it.key }
    }

    suspend fun setQueue(items: List<QueueItem>) = context.store.edit {
        if (items.isEmpty()) it.remove(K.queue) else it[K.queue] = queueJson.encodeToString(items.distinctBy { q -> q.key })
    }

    suspend fun updateQueue(change: (List<QueueItem>) -> List<QueueItem>) = context.store.edit {
        val now = it[K.queue]?.let { j -> runCatching { queueJson.decodeFromString<List<QueueItem>>(j) }.getOrNull() }.orEmpty()
        val next = change(now).distinctBy { q -> q.key }
        if (next.isEmpty()) it.remove(K.queue) else it[K.queue] = queueJson.encodeToString(next)
    }

    /**
     * The queue entry called as [callId] got [outcome] (the agent's pick, or the server's /
     * dialer's guess): not reached → no_answer (Call again), anything else → called.
     * Returns the entry (before, after) when its state changed, else null.
     */
    suspend fun queueCallOutcome(callId: String, outcome: String): Pair<QueueItem, QueueItem>? {
        var changed: Pair<QueueItem, QueueItem>? = null
        updateQueue { q ->
            q.map {
                if (it.callId != callId || it.status !in setOf("called", "no_answer")) return@map it
                val next = it.copy(status = if (outcome in NOT_REACHED) "no_answer" else "called")
                if (next != it) changed = it to next
                next
            }
        }
        return changed
    }

    suspend fun setWaCookie(cookie: String?) = context.store.edit {
        if (cookie == null) it.remove(K.wa) else it[K.wa] = cookie
    }

    suspend fun setRecordingsTree(uri: String?) = context.store.edit {
        if (uri == null) it.remove(K.tree) else it[K.tree] = uri
    }

    suspend fun setPending(c: PendingCall) = context.store.edit {
        it[K.pCall] = c.callId
        it[K.pUser] = c.userId
        it[K.pPhone] = c.phone
        it[K.pLabel] = c.label
        it[K.pStart] = c.startedAtMs
    }

    suspend fun clearPending(callId: String) = context.store.edit {
        if (it[K.pCall] == callId) {
            listOf(K.pCall, K.pUser, K.pPhone, K.pLabel).forEach(it::remove)
            it.remove(K.pStart)
        }
    }
}
