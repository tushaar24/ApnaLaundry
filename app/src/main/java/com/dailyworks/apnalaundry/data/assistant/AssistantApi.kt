package com.dailyworks.apnalaundry.data.assistant

import com.dailyworks.apnalaundry.BuildConfig
import com.dailyworks.apnalaundry.data.Prefs
import com.dailyworks.apnalaundry.data.remote.TokenManager
import com.dailyworks.apnalaundry.data.sync.NotLoggedInException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

@Serializable
data class AssistantConfig(val success: Boolean = false, val enabled: Boolean = false)

@Serializable
data class ToolCallFn(val name: String = "", val arguments: String = "{}")

@Serializable
data class ToolCall(val id: String = "", val type: String = "function", val function: ToolCallFn = ToolCallFn())

@Serializable
data class AssistantMessage(val content: String? = null, val tool_calls: List<ToolCall> = emptyList())

@Serializable
data class ChatResponse(
    val success: Boolean = false,
    val message: AssistantMessage? = null,
    val code: String? = null,
)

@Serializable
private data class ChatRequest(val messages: JsonArray, val tools: JsonArray)

@Serializable
data class TranscribeResponse(val success: Boolean = false, val transcript: String? = null, val code: String? = null)

@Serializable
private data class TranscribeRequest(val audio: String, val mime: String)

/** Why a call to the assistant failed, in words the owner sees. */
class AssistantException(val code: String) : Exception(code)

/**
 * The assistant ("Ask") over /api/laundry/assistant. The server only relays to
 * Sarvam AI (the key stays there); every tool runs on this phone
 * ([AssistantTools]). An older server has no such route — [refreshEnabled]
 * then saves "off" and Home shows no Ask button.
 */
class AssistantApi(
    private val client: HttpClient,
    private val tokens: TokenManager,
    private val prefs: Prefs,
) {
    private val base = BuildConfig.API_BASE_URL + "/assistant"

    /** Is the assistant on for this shop? Saved, so Home decides without the network. */
    suspend fun refreshEnabled() {
        val on = runCatching {
            val res = authed { t -> client.get("$base/config") { bearerAuth(t) } }
            res.status.value in 200..299 && res.body<AssistantConfig>().enabled
        }.getOrNull() ?: return // offline: keep what was saved
        prefs.setAssistantOn(on)
    }

    /** One round of the conversation: the reply text, or the tools it wants run. */
    suspend fun chat(messages: List<JsonObject>, tools: JsonArray): AssistantMessage {
        val res = authed { t ->
            client.post("$base/chat") {
                bearerAuth(t)
                contentType(ContentType.Application.Json)
                setBody(ChatRequest(JsonArray(messages), tools))
                timeout { requestTimeoutMillis = 40_000 }
            }
        }
        val body = runCatching { res.body<ChatResponse>() }.getOrNull()
        if (res.status.value !in 200..299 || body?.message == null) throw AssistantException(failure(res.status.value, body?.code))
        return body.message
    }

    /** A voice note (AAC in .m4a) to text — Hindi comes back in English letters. */
    suspend fun transcribe(audio: ByteArray): String {
        val b64 = android.util.Base64.encodeToString(audio, android.util.Base64.NO_WRAP)
        val res = authed { t ->
            client.post("$base/transcribe") {
                bearerAuth(t)
                contentType(ContentType.Application.Json)
                setBody(TranscribeRequest(b64, "audio/mp4"))
                timeout { requestTimeoutMillis = 40_000 }
            }
        }
        val body = runCatching { res.body<TranscribeResponse>() }.getOrNull()
        if (res.status.value !in 200..299 || body?.transcript == null) throw AssistantException(failure(res.status.value, body?.code))
        return body.transcript
    }

    private fun failure(status: Int, code: String?): String = when {
        code == "DAILY_LIMIT" -> "limit"
        code == "ASSISTANT_OFF" || status == 404 -> "off"
        else -> "server"
    }

    private suspend fun authed(block: suspend (String) -> HttpResponse): HttpResponse {
        val token = tokens.validAccessToken() ?: throw NotLoggedInException()
        val first = block(token)
        if (first.status.value != 401) return first
        val fresh = tokens.forceRefresh() ?: throw NotLoggedInException()
        return block(fresh)
    }
}
