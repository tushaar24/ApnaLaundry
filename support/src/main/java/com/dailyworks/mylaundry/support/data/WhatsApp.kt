package com.dailyworks.mylaundry.support.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

// The WhatsApp support inbox of admin.mylaundry.work (the MyLaundry number), in the app
// (8 Nov 2026). The app signs in with the admin site's own login and uses the same
// /api/whatsapp/* the site's inbox page uses — the messages stay behind that login.

/** The admin login is missing or expired: show the sign-in. */
class WaSignedOut : Exception("Sign in to WhatsApp again")

@Serializable
data class WaLast(
    val direction: String? = null,
    val type: String? = null,
    val status: String? = null,
    val at: String? = null,
    val preview: String? = null,
)

@Serializable
data class WaConversation(
    val waId: String,
    val phone: String? = null,
    val contactName: String? = null,
    val userId: String? = null,
    val ownerName: String? = null,
    val shopName: String? = null,
    val isTest: Boolean = false,
    val unread: Int = 0,
    val windowOpen: Boolean = false,
    val last: WaLast = WaLast(),
) {
    val title get() = shopName ?: contactName ?: ownerName ?: "+$waId"
}

@Serializable
internal data class WaConversations(val conversations: List<WaConversation> = emptyList())

@Serializable
data class WaContact(
    val waId: String,
    val phone: String? = null,
    val contactName: String? = null,
    val userId: String? = null,
    val ownerName: String? = null,
    val shopName: String? = null,
    val isTest: Boolean = false,
    /** Free-form replies only within 24 hours of their last message; otherwise a template. */
    val windowOpen: Boolean = false,
) {
    val title get() = shopName ?: contactName ?: ownerName ?: "+$waId"
}

@Serializable
data class WaButton(val type: String? = null, val text: String? = null, val url: String? = null)

@Serializable
data class WaRendered(
    val name: String? = null,
    val header: String? = null,
    val body: String? = null,
    val footer: String? = null,
    val buttons: List<WaButton> = emptyList(),
)

@Serializable
data class WaLocation(val lat: Double? = null, val lng: Double? = null, val name: String? = null, val address: String? = null)

@Serializable
data class WaReaction(val emoji: String? = null)

@Serializable
data class WaMessage(
    val id: String,
    val direction: String,
    val type: String,
    val body: String? = null,
    val status: String? = null, // sent | delivered | read | failed
    val errorTitle: String? = null,
    val at: String? = null,
    val mediaId: String? = null,
    val mediaMime: String? = null,
    val filename: String? = null,
    val template: WaRendered? = null,
    val location: WaLocation? = null,
    val reaction: WaReaction? = null,
) {
    val outbound get() = direction == "outbound"
}

@Serializable
data class WaThread(val contact: WaContact, val messages: List<WaMessage> = emptyList())

@Serializable
data class WaTemplate(
    val name: String,
    val language: String,
    val category: String? = null,
    val header: String? = null,
    val body: String = "",
    val footer: String? = null,
) {
    /** How many {{n}} values it needs. */
    val params: Int
        get() = Regex("""\{\{(\d+)\}\}""").findAll("${header.orEmpty()} $body").maxOfOrNull { it.groupValues[1].toInt() } ?: 0
}

@Serializable internal data class WaTemplates(val templates: List<WaTemplate> = emptyList())
@Serializable internal data class WaSent(val message: WaMessage)
@Serializable internal data class WaUnread(val messages: Int = 0, val chats: Int = 0)
@Serializable internal data class WaError(val error: String? = null)

class WhatsAppApi(private val prefs: Prefs, private val baseUrl: String = "https://admin.mylaundry.work") {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client = HttpClient(OkHttp) {
        // Sign-in answers 303 + the session cookie: read it, don't follow.
        followRedirects = false
        engine { config { followRedirects(false); followSslRedirects(false) } }
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 60_000
        }
    }

    /** Signs in with the admin site's username + password; keeps its session cookie (90 days). */
    suspend fun signIn(username: String, password: String) {
        val res = try {
            client.post("$baseUrl/login") {
                setBody(FormDataContent(Parameters.build { append("username", username.trim()); append("password", password) }))
            }
        } catch (e: Exception) {
            throw ApiException("No connection — check internet and try again")
        }
        val cookie = res.headers.getAll(HttpHeaders.SetCookie).orEmpty()
            .map { it.substringBefore(';') }
            .firstOrNull { it.startsWith("al_dash=") && it.length > "al_dash=".length }
        when {
            cookie != null -> prefs.setWaCookie(cookie)
            res.status.value == 429 -> throw ApiException("Too many tries — wait 15 minutes")
            else -> throw ApiException("Wrong username or password")
        }
    }

    suspend fun signOut() = prefs.setWaCookie(null)

    init {
        // Every request carries the admin session (when there is one); 401 = sign in again.
        client.plugin(HttpSend).intercept { request ->
            prefs.current().waCookie?.let { request.header(HttpHeaders.Cookie, it) }
            execute(request)
        }
    }

    private suspend inline fun <reified T> HttpResponse.ok(): T {
        if (status.value == 401) {
            prefs.setWaCookie(null)
            throw WaSignedOut()
        }
        if (status.isSuccess()) return body()
        val msg = runCatching { json.decodeFromString(WaError.serializer(), bodyAsText()).error }.getOrNull()
        throw ApiException(msg ?: "Server error (${status.value})", status.value)
    }

    private suspend inline fun <reified T> call(block: () -> HttpResponse): T =
        try {
            block().ok()
        } catch (e: ApiException) {
            throw e
        } catch (e: WaSignedOut) {
            throw e
        } catch (e: Exception) {
            throw ApiException("No connection — check internet and try again")
        }

    suspend fun conversations(search: String = ""): List<WaConversation> =
        call<WaConversations> { client.get("$baseUrl/api/whatsapp/conversations") { parameter("search", search) } }.conversations

    suspend fun thread(waId: String): WaThread = call { client.get("$baseUrl/api/whatsapp/thread/$waId") }

    suspend fun markRead(waId: String) {
        call<Map<String, Int>> { client.post("$baseUrl/api/whatsapp/thread/$waId/read") }
    }

    suspend fun unread(): Int = call<WaUnread> { client.get("$baseUrl/api/whatsapp/unread") }.chats

    suspend fun send(to: String, text: String): WaMessage = call<WaSent> {
        client.post("$baseUrl/api/whatsapp/send") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("to", JsonPrimitive(to)); put("text", JsonPrimitive(text)) })
        }
    }.message

    suspend fun sendMedia(to: String, bytes: ByteArray, mime: String, filename: String, caption: String): WaMessage = call<WaSent> {
        client.post("$baseUrl/api/whatsapp/send-media") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("to", to)
                        append("caption", caption)
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, mime)
                                append(HttpHeaders.ContentDisposition, "filename=\"${filename.replace("\"", "")}\"")
                            },
                        )
                    },
                ),
            )
        }
    }.message

    suspend fun templates(): List<WaTemplate> =
        call<WaTemplates> { client.get("$baseUrl/api/whatsapp/templates") }.templates

    suspend fun sendTemplate(to: String, t: WaTemplate, params: List<String>): WaMessage = call<WaSent> {
        client.post("$baseUrl/api/whatsapp/send-template") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("to", JsonPrimitive(to))
                put("name", JsonPrimitive(t.name))
                put("language", JsonPrimitive(t.language))
                put("params", JsonArray(params.map { JsonPrimitive(it) }))
            })
        }
    }.message

    /** A photo / file in a chat (through the admin site, which fetches it from Meta). */
    suspend fun media(id: String): ByteArray {
        val res = try {
            client.get("$baseUrl/api/whatsapp/media/$id")
        } catch (e: WaSignedOut) {
            throw e
        } catch (e: Exception) {
            throw ApiException("No connection — check internet and try again")
        }
        if (res.status.value == 401) { prefs.setWaCookie(null); throw WaSignedOut() }
        if (res.status.value == 410) throw ApiException("No longer available on WhatsApp")
        if (!res.status.isSuccess()) throw ApiException("Couldn't load it (${res.status.value})")
        return res.readRawBytes()
    }

    /** This phone's Firebase token, so the server pushes new WhatsApp messages here. */
    suspend fun registerDevice(token: String, agentName: String, agentPhone: String) {
        call<Map<String, Boolean>> {
            client.post("$baseUrl/api/devices") {
                    contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("token", JsonPrimitive(token))
                    put("agentName", JsonPrimitive(agentName))
                    put("agentPhone", JsonPrimitive(agentPhone))
                })
            }
        }
    }

    suspend fun removeDevice(token: String) {
        call<Map<String, Boolean>> {
            client.post("$baseUrl/api/devices/remove") {
                    contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("token", JsonPrimitive(token)) })
            }
        }
    }
}
