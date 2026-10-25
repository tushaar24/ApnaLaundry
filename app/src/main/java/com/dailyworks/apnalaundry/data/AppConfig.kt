package com.dailyworks.apnalaundry.data

import com.dailyworks.apnalaundry.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class AppConfigResponse(val success: Boolean = false, val demoVideoUrl: String? = null)

/**
 * Settings the server sends every app (GET /api/laundry/app-config, no login):
 * the demo video (Home's "Demo", the first-order help). The last good value is
 * saved on the phone; with none yet, or the server unreachable / older, the
 * built-in [DEFAULT_DEMO_VIDEO] plays.
 */
class AppConfigRepository(private val client: HttpClient, private val prefs: Prefs) {
    private val base = BuildConfig.API_BASE_URL

    /** The demo video to play: the server's latest saved, else the built-in one. */
    val demoVideoUrl: Flow<String> = prefs.demoVideoUrl.map { saved ->
        saved?.takeIf { youTubeId(it) != null } ?: DEFAULT_DEMO_VIDEO
    }

    /** App start: ask the server; a failure (offline, older server) keeps what's saved. */
    suspend fun refresh() {
        val url = runCatching {
            val res = client.get("$base/app-config")
            if (res.status.value in 200..299) res.body<AppConfigResponse>().demoVideoUrl else null
        }.getOrNull()?.trim()
        if (!url.isNullOrEmpty() && youTubeId(url) != null) prefs.setDemoVideoUrl(url)
    }

    companion object {
        const val DEFAULT_DEMO_VIDEO = "https://youtu.be/Z5Si-Uw8euo"
    }
}

/** The 11-character video id in a YouTube link (youtu.be/…, watch?v=…, embed/, shorts/, live/); null if none. */
fun youTubeId(url: String): String? =
    Regex("(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:[^#]*&)?v=|embed/|shorts/|live/|v/))([A-Za-z0-9_-]{11})")
        .find(url.trim())?.groupValues?.get(1)
