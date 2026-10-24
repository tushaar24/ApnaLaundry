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
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/** The shop's A/B test groups, as GET /api/laundry/experiments returns them. */
@Serializable
data class ExperimentGroups(val guide: String? = null, val customerFlow: String? = null)

/**
 * A/B (2026-10-24, brand-new accounts on 1.0.15+): how a new shop's first
 * customer is added on New order. [LEGACY] = not in the test, the 1.0.14 flow:
 * the New customer sheet opens by itself unless the guide is on (then the
 * guide points at "Add new customer").
 * - [AUTO_SHEET] v1: the sheet (name, phone, address) opens by itself, guide or not.
 * - [NO_ADDRESS] v2: the sheet opens by itself with only name + phone; home
 *   pickup / delivery asks for the address in its own sheet.
 * - [TAP_TO_ADD] v3: never opens by itself; the owner taps "Add new customer".
 */
enum class CustomerFlow(val key: String?) {
    LEGACY(null),
    AUTO_SHEET("v1_auto_sheet"),
    NO_ADDRESS("v2_no_address"),
    TAP_TO_ADD("v3_tap_to_add");

    companion object {
        fun of(key: String?): CustomerFlow = entries.firstOrNull { it.key != null && it.key == key } ?: LEGACY
    }
}

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
 *
 * customerFlow: a [CustomerFlow] key, or absent = not in that test (the old flow).
 */
class ExperimentsRepository(
    private val client: HttpClient,
    private val tokens: TokenManager,
    private val prefs: Prefs,
) {
    private val base = BuildConfig.API_BASE_URL

    /** Fetch and save the groups (app start, login); a failure keeps the saved ones. */
    suspend fun refresh() {
        val groups = runCatching { fetch().experiments }.getOrNull() ?: return
        val guide = groups.guide
        if (guide == GUIDE || guide == NO_GUIDE) {
            prefs.setGuideVariant(guide)
            Analytics.updateProfile(mapOf("Guide_Variant" to guide))
        }
        // Absent (not in the test, or the server stopped it) = the old flow.
        val flow = CustomerFlow.of(groups.customerFlow).key
        prefs.setCustomerFlow(flow)
        Analytics.setCustomerFlow(flow)
    }

    /** App start: stamp the saved customer-flow group on events before the fetch answers. */
    suspend fun restore() = Analytics.setCustomerFlow(prefs.customerFlow.first())

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
