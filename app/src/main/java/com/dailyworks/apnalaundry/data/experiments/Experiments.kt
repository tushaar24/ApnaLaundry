package com.dailyworks.apnalaundry.data.experiments

import com.dailyworks.apnalaundry.BuildConfig
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.remote.TokenManager
import com.dailyworks.apnalaundry.data.sync.NotLoggedInException
import com.dailyworks.apnalaundry.data.sync.SyncHttpException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable

/** The shop's A/B test groups, as GET /api/laundry/experiments returns them. */
@Serializable
data class ExperimentGroups(val guide: String? = null)

@Serializable
data class ExperimentsResponse(val success: Boolean = false, val experiments: ExperimentGroups = ExperimentGroups())

/**
 * A/B tests decided by the server (a stable 50/50 hash of the user id there),
 * so a split can change without an app release. The group is saved in [Prefs]
 * — screens decide from it, never waiting on the network — and on the shop's
 * CleverTap profile (the server sets it too).
 *
 * guide: [GUIDE] = the step-by-step guide (ui/tour) for a new shop and the
 * Settings replay; [NO_GUIDE] = neither. Not fetched yet = no guide.
 */
class ExperimentsRepository(
    private val client: HttpClient,
    private val tokens: TokenManager,
    private val prefs: Prefs,
) {
    private val base = BuildConfig.API_BASE_URL

    /** Fetch and save the groups (app start, login); a failure keeps the saved ones. */
    suspend fun refresh() {
        val guide = runCatching { fetch().experiments.guide }.getOrNull() ?: return
        if (guide != GUIDE && guide != NO_GUIDE) return
        prefs.setGuideVariant(guide)
        Analytics.updateProfile(mapOf("Guide_Variant" to guide))
    }

    private suspend fun fetch(): ExperimentsResponse {
        val token = tokens.validAccessToken() ?: throw NotLoggedInException()
        var res: HttpResponse = client.get("$base/experiments") { bearerAuth(token) }
        if (res.status.value == 401) {
            val fresh = tokens.forceRefresh() ?: throw NotLoggedInException()
            res = client.get("$base/experiments") { bearerAuth(fresh) }
        }
        if (res.status.value !in 200..299) throw SyncHttpException(res.status.value)
        return res.body()
    }

    companion object {
        const val GUIDE = "guide"
        const val NO_GUIDE = "no_guide"
    }
}
