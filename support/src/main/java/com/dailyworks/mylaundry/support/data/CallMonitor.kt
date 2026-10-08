package com.dailyworks.mylaundry.support.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CallLog
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/** What the call log says about a finished call. */
data class CallLogEntry(val durationSec: Int, val endedAtMs: Long)

/**
 * Phone-state helpers for the call flow: whether a call is in progress (to
 * know when the agent hung up) and the real talk time from the call log.
 * Both degrade gracefully when the permission was refused.
 */
class CallMonitor(private val context: Context) {

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    val canReadPhoneState get() = granted(Manifest.permission.READ_PHONE_STATE)
    val canReadCallLog get() = granted(Manifest.permission.READ_CALL_LOG)

    /** true while ringing or off-hook. Emits nothing useful without READ_PHONE_STATE. */
    @SuppressLint("MissingPermission")
    fun inCall(): Flow<Boolean?> {
        if (!canReadPhoneState) return flowOf(null)
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return flowOf(null)
        return callbackFlow {
            val busy = { state: Int -> state != TelephonyManager.CALL_STATE_IDLE }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        trySend(busy(state))
                    }
                }
                tm.registerTelephonyCallback(context.mainExecutor, cb)
                trySend(busy(tm.callStateForSubscription))
                awaitClose { tm.unregisterTelephonyCallback(cb) }
            } else {
                @Suppress("DEPRECATION")
                val l = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        trySend(busy(state))
                    }
                }
                @Suppress("DEPRECATION")
                tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
                @Suppress("DEPRECATION")
                awaitClose { tm.listen(l, PhoneStateListener.LISTEN_NONE) }
            }
        }.distinctUntilChanged()
    }

    /**
     * The outgoing call-log entry for [phone] placed at/after [startedAtMs]
     * (allowing a minute of clock slack), or null if unknown.
     */
    fun lastCallTo(phone: String, startedAtMs: Long): CallLogEntry? {
        if (!canReadCallLog) return null
        val last10 = phone.filter(Char::isDigit).takeLast(10)
        if (last10.length < 10) return null
        return runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE),
                "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.TYPE} = ?",
                arrayOf((startedAtMs - 60_000).toString(), CallLog.Calls.OUTGOING_TYPE.toString()),
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val number = c.getString(0).orEmpty().filter(Char::isDigit)
                    if (number.endsWith(last10)) {
                        val date = c.getLong(1)
                        val dur = c.getInt(2)
                        return@use CallLogEntry(durationSec = dur, endedAtMs = date + dur * 1000L)
                    }
                }
                null
            }
        }.getOrNull()
    }
}
