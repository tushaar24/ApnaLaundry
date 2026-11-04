package com.dailyworks.apnalaundry.data.attribution

import android.content.Context
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.dailyworks.apnalaundry.BuildConfig
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.remote.TokenManager
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.coroutines.resume

/**
 * Which ad brought this install (owner, 2026-11-04). Google Play keeps an install
 * referrer for the life of the install: a Meta app-install ad tapped in Facebook /
 * Instagram leaves its (encrypted) ad / ad set / campaign ids in it, a
 * mylaundry.work/open ad link its plain ad_id=… pairs (web/src/core/openApp.ts),
 * a plain Play install says organic. Read once per install, sent once per account
 * to POST /api/laundry/attribution/install — the server decrypts, looks the ad and
 * its creative up, saves it (laundry_install_attributions) and puts it on the
 * CleverTap profile (Meta_Ad_Name, Meta_Creative_Name…). Because Play keeps the
 * referrer, an install made before this version reports on its first open too.
 *
 * Never in the way: runs after login in the background, failures retry on the
 * next app start, and a phone without Play (no referrer service) reports that once.
 * The language link's own read (core/InstallLanguage.kt) stays as it is.
 */
@Serializable
data class InstallReport(
    val status: String, // ok | not_supported | unavailable | error
    val referrer: String? = null,
    val referrerClickAt: Long? = null, // epoch ms, device clock
    val installBeginAt: Long? = null,
    val referrerClickServerAt: Long? = null, // epoch ms, Play's clock
    val installBeginServerAt: Long? = null,
    val installVersion: String? = null,
    val googlePlayInstant: Boolean = false,
    val appVersion: Int = BuildConfig.VERSION_CODE,
)

class InstallAttributionRepository(
    context: Context,
    private val client: HttpClient,
    private val tokens: TokenManager,
    private val prefs: Prefs,
) {
    private val app = context.applicationContext
    private val store = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val base = BuildConfig.API_BASE_URL

    /** App start / login: read (once per install) and send (once per account). Quiet on failure. */
    suspend fun reportIfNeeded() {
        val userId = prefs.userId.first() ?: return
        if (store.getBoolean(KEY_REPORTED + userId, false)) return
        val report = saved() ?: read()?.also { save(it) } ?: return
        val ok = runCatching { send(report) }.getOrElse { e ->
            Log.w(TAG, "install attribution not sent: ${e.message}")
            false
        }
        if (ok) store.edit().putBoolean(KEY_REPORTED + userId, true).apply()
    }

    private suspend fun send(report: InstallReport): Boolean {
        val token = tokens.validAccessToken() ?: return false
        var res: HttpResponse = client.post("$base/attribution/install") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(report)
        }
        if (res.status.value == 401) {
            val fresh = tokens.forceRefresh() ?: return false
            res = client.post("$base/attribution/install") {
                contentType(ContentType.Application.Json)
                bearerAuth(fresh)
                setBody(report)
            }
        }
        return res.status.value in 200..299
    }

    /** Play's answer, read once and kept (it never changes for an install). Null = ask Play. */
    private fun saved(): InstallReport? {
        val status = store.getString(KEY_STATUS, null) ?: return null
        return InstallReport(
            status = status,
            referrer = store.getString(KEY_REFERRER, null),
            referrerClickAt = store.getLong(KEY_CLICK_AT, 0L).takeIf { it > 0 },
            installBeginAt = store.getLong(KEY_INSTALL_AT, 0L).takeIf { it > 0 },
            referrerClickServerAt = store.getLong(KEY_CLICK_SERVER_AT, 0L).takeIf { it > 0 },
            installBeginServerAt = store.getLong(KEY_INSTALL_SERVER_AT, 0L).takeIf { it > 0 },
            installVersion = store.getString(KEY_INSTALL_VERSION, null),
            googlePlayInstant = store.getBoolean(KEY_INSTANT, false),
        )
    }

    private fun save(r: InstallReport) {
        store.edit()
            .putString(KEY_STATUS, r.status)
            .putString(KEY_REFERRER, r.referrer)
            .putLong(KEY_CLICK_AT, r.referrerClickAt ?: 0L)
            .putLong(KEY_INSTALL_AT, r.installBeginAt ?: 0L)
            .putLong(KEY_CLICK_SERVER_AT, r.referrerClickServerAt ?: 0L)
            .putLong(KEY_INSTALL_SERVER_AT, r.installBeginServerAt ?: 0L)
            .putString(KEY_INSTALL_VERSION, r.installVersion)
            .putBoolean(KEY_INSTANT, r.googlePlayInstant)
            .apply()
    }

    /** Ask Play. Null only when the service never answered (try again next start). */
    private suspend fun read(): InstallReport? {
        val client = runCatching { InstallReferrerClient.newBuilder(app).build() }.getOrNull()
            ?: return InstallReport(status = "error")
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(code: Int) {
                        val report = when (code) {
                            InstallReferrerClient.InstallReferrerResponse.OK -> runCatching {
                                val d = client.installReferrer
                                InstallReport(
                                    status = "ok",
                                    referrer = d.installReferrer,
                                    referrerClickAt = d.referrerClickTimestampSeconds * 1000,
                                    installBeginAt = d.installBeginTimestampSeconds * 1000,
                                    referrerClickServerAt = d.referrerClickTimestampServerSeconds * 1000,
                                    installBeginServerAt = d.installBeginTimestampServerSeconds * 1000,
                                    installVersion = d.installVersion,
                                    googlePlayInstant = d.googlePlayInstantParam,
                                )
                            }.getOrElse { InstallReport(status = "error") }
                            InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> InstallReport(status = "not_supported")
                            InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> InstallReport(status = "unavailable")
                            else -> InstallReport(status = "error")
                        }
                        runCatching { client.endConnection() }
                        if (cont.isActive) cont.resume(report)
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                cont.invokeOnCancellation { runCatching { client.endConnection() } }
                runCatching { client.startConnection(listener) }.onFailure {
                    if (cont.isActive) cont.resume(InstallReport(status = "error"))
                }
            }
        }
    }

    private companion object {
        const val TAG = "InstallAttribution"
        const val PREFS = "install_attribution"
        const val KEY_STATUS = "status"
        const val KEY_REFERRER = "referrer"
        const val KEY_CLICK_AT = "click_at"
        const val KEY_INSTALL_AT = "install_at"
        const val KEY_CLICK_SERVER_AT = "click_server_at"
        const val KEY_INSTALL_SERVER_AT = "install_server_at"
        const val KEY_INSTALL_VERSION = "install_version"
        const val KEY_INSTANT = "instant"
        const val KEY_REPORTED = "reported_" // + user id
    }
}
