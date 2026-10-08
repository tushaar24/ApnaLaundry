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
    /** SAF tree the agent picked (the dialer's call-recordings folder), or null. */
    val recordingsTree: String? = null,
)

class Prefs(private val context: Context) {
    private object K {
        val agent = stringPreferencesKey("agent_name")
        val tree = stringPreferencesKey("recordings_tree")
        val pCall = stringPreferencesKey("pending_call_id")
        val pUser = stringPreferencesKey("pending_user_id")
        val pPhone = stringPreferencesKey("pending_phone")
        val pLabel = stringPreferencesKey("pending_label")
        val pStart = longPreferencesKey("pending_started_at")
    }

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(agentName = p[K.agent].orEmpty(), recordingsTree = p[K.tree])
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
