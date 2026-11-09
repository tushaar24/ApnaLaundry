package com.dailyworks.mylaundry.support.data

import com.dailyworks.mylaundry.support.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.io.InputStream

class ApiException(message: String, val status: Int = 0) : Exception(message)

/**
 * Client for the backend's /api/laundry/support routes (open — no login, no key).
 */
class SupportApi(private val baseUrl: String = BuildConfig.API_BASE_URL) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
        }
    }

    // Recording PUTs can be tens of MB on a slow network: no request timeout.
    private val uploadClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = 120_000
        }
    }

    private suspend inline fun <reified T> HttpResponse.ok(): T {
        if (status.isSuccess()) return body()
        val msg = runCatching { json.decodeFromString(ErrorBody.serializer(), bodyAsText()).message }.getOrNull()
        throw ApiException(msg ?: "Server error (${status.value})", status.value)
    }

    private suspend inline fun <reified T> call(block: () -> HttpResponse): T =
        try {
            block().ok()
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("No connection — check internet and try again")
        }

    private fun url(path: String) = "$baseUrl$path"

    suspend fun shops(filters: ShopFilters, page: Int, pageSize: Int = 30): ShopsPage = call {
        client.get(url("/shops")) { filters.toQuery(page, pageSize).forEach { (k, v) -> parameter(k, v) } }
    }

    suspend fun shop(id: String): ShopDetail = call { client.get(url("/shops/$id")) }

    suspend fun agents(): List<String> = call<AgentsEnvelope> { client.get(url("/agents")) }.agents

    suspend fun tags(): List<TagDto> = call<TagsEnvelope> { client.get(url("/tags")) }.tags

    suspend fun createTag(name: String, color: String): TagDto = call<TagEnvelope> {
        client.post(url("/tags")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("name", JsonPrimitive(name)); put("color", JsonPrimitive(color)) })
        }
    }.tag

    suspend fun updateTag(id: String, name: String, color: String): TagDto = call<TagEnvelope> {
        client.patch(url("/tags/$id")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("name", JsonPrimitive(name)); put("color", JsonPrimitive(color)) })
        }
    }.tag

    suspend fun deleteTag(id: String) {
        call<OkBody> { client.delete(url("/tags/$id")) }
    }

    suspend fun calls(page: Int, agent: String? = null, tagId: String? = null, pageSize: Int = 30): CallsPage = call {
        client.get(url("/calls")) {
            parameter("page", page)
            parameter("pageSize", pageSize)
            parameter("withUnknown", 1) // incoming calls from numbers that aren't an owner's
            agent?.let { parameter("agent", it) }
            tagId?.let { parameter("tag", it) }
        }
    }

    suspend fun getCall(id: String): CallDto = call<CallEnvelope> { client.get(url("/calls/$id")) }.call

    suspend fun startCall(userId: String, phone: String, agentName: String, startedAtIso: String): CallDto =
        call<CallEnvelope> {
            client.post(url("/calls")) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("userId", JsonPrimitive(userId))
                    put("phone", JsonPrimitive(phone))
                    put("agentName", JsonPrimitive(agentName))
                    put("startedAt", JsonPrimitive(startedAtIso))
                })
            }
        }.call

    suspend fun reminders(fromIso: String?, toIso: String?, agent: String? = null, open: Boolean = true, userId: String? = null, tagIds: Set<String> = emptySet()): List<ReminderDto> =
        call<RemindersEnvelope> {
            client.get(url("/reminders")) {
                if (tagIds.isNotEmpty()) parameter("tag", tagIds.joinToString(","))
                fromIso?.let { parameter("from", it) }
                toIso?.let { parameter("to", it) }
                agent?.let { parameter("agent", it) }
                userId?.let { parameter("userId", it) }
                if (open) parameter("open", 1)
            }
        }.reminders

    suspend fun createReminder(userId: String, agentName: String, dueAtIso: String, tagIds: List<String>, note: String): ReminderDto =
        call<ReminderEnvelope> {
            client.post(url("/reminders")) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("userId", JsonPrimitive(userId))
                    put("agentName", JsonPrimitive(agentName))
                    put("dueAt", JsonPrimitive(dueAtIso))
                    put("tagIds", buildJsonArray { tagIds.forEach { add(JsonPrimitive(it)) } })
                    put("note", JsonPrimitive(note))
                })
            }
        }.reminder

    /** PATCH only the fields given. */
    suspend fun updateReminder(id: String, dueAtIso: String? = null, note: String? = null, tagIds: List<String>? = null, done: Boolean? = null, callId: String? = null): ReminderDto =
        call<ReminderEnvelope> {
            client.patch(url("/reminders/$id")) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    dueAtIso?.let { put("dueAt", JsonPrimitive(it)) }
                    note?.let { put("note", JsonPrimitive(it)) }
                    tagIds?.let { ids -> put("tagIds", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }) }
                    done?.let { put("done", JsonPrimitive(it)) }
                    callId?.let { put("callId", JsonPrimitive(it)) }
                })
            }
        }.reminder

    suspend fun reminderTags(): List<TagDto> = call<TagsEnvelope> { client.get(url("/reminder-tags")) }.tags

    // ── call queues (1.10): on the server, shared by every agent ──

    suspend fun queues(): List<QueueDto> = call<QueuesEnvelope> { client.get(url("/queues")) }.queues

    suspend fun queue(id: String): QueueDto = call<QueueEnvelope> { client.get(url("/queues/$id")) }.queue

    /** [keepState]: a phone's own queue moving up keeps who was called / skipped. */
    suspend fun createQueue(title: String, description: String, createdBy: String, items: List<QueueEntry> = emptyList(), keepState: Boolean = false): QueueDto =
        call<QueueEnvelope> {
            client.post(url("/queues")) {
                contentType(ContentType.Application.Json)
                setBody(NewQueueBody(title, description, createdBy, items, keepState))
            }
        }.queue

    suspend fun editQueue(id: String, title: String, description: String): QueueDto = call<QueueEnvelope> {
        client.patch(url("/queues/$id")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("title", JsonPrimitive(title)); put("description", JsonPrimitive(description)) })
        }
    }.queue

    suspend fun deleteQueue(id: String) {
        call<OkBody> { client.delete(url("/queues/$id")) }
    }

    suspend fun addToQueue(id: String, items: List<QueueEntry>): QueueDto = call<QueueEnvelope> {
        client.post(url("/queues/$id/items")) {
            contentType(ContentType.Application.Json)
            setBody(QueueItemsBody(items))
        }
    }.queue

    /** Skip ("skipped") or back in line ("pending"). */
    suspend fun setQueueItem(queueId: String, itemId: String, status: String): QueueDto = call<QueueEnvelope> {
        client.patch(url("/queues/$queueId/items/$itemId")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("status", JsonPrimitive(status)) })
        }
    }.queue

    suspend fun removeQueueItem(queueId: String, itemId: String): QueueDto =
        call<QueueEnvelope> { client.delete(url("/queues/$queueId/items/$itemId")) }.queue

    /** Everyone who didn't pick up → back in line. */
    suspend fun retryQueue(queueId: String): QueueDto = call<QueueEnvelope> { client.post(url("/queues/$queueId/retry")) }.queue

    /** Before calling: this agent takes them. ApiException 409 = another agent is calling them. */
    suspend fun startQueueItem(queueId: String, itemId: String, agentName: String) {
        call<OkBody> {
            client.post(url("/queues/$queueId/items/$itemId/start")) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("agentName", JsonPrimitive(agentName)) })
            }
        }
    }

    /** The call was placed (its notes later say whether they picked up; a reminder is done). */
    suspend fun placedQueueItem(queueId: String, itemId: String, callId: String, agentName: String) {
        call<OkBody> {
            client.post(url("/queues/$queueId/items/$itemId/placed")) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("callId", JsonPrimitive(callId)); put("agentName", JsonPrimitive(agentName)) })
            }
        }
    }

    /** The call couldn't be placed: back to how they were. */
    suspend fun releaseQueueItem(queueId: String, itemId: String) {
        call<OkBody> { client.post(url("/queues/$queueId/items/$itemId/release")) }
    }

    suspend fun createReminderTag(name: String, color: String): TagDto = call<TagEnvelope> {
        client.post(url("/reminder-tags")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("name", JsonPrimitive(name)); put("color", JsonPrimitive(color)) })
        }
    }.tag

    suspend fun updateReminderTag(id: String, name: String, color: String): TagDto = call<TagEnvelope> {
        client.patch(url("/reminder-tags/$id")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("name", JsonPrimitive(name)); put("color", JsonPrimitive(color)) })
        }
    }.tag

    suspend fun deleteReminderTag(id: String) {
        call<OkBody> { client.delete(url("/reminder-tags/$id")) }
    }

    suspend fun deleteReminder(id: String) {
        call<OkBody> { client.delete(url("/reminders/$id")) }
    }

    /** Asked while this phone rings: the incoming MyLaundry call ringing [agentPhone], if any. */
    suspend fun incoming(agentPhone: String, ringingNumber: String?): IncomingDto = call {
        client.get(url("/calls/incoming")) {
            parameter("agentPhone", agentPhone)
            ringingNumber?.let { parameter("from", it) }
        }
    }

    /** A call through MyLaundry: the server rings [agentPhone], then the shop owner. */
    suspend fun bridgeCall(userId: String, agentName: String, agentPhone: String): CallDto =
        call<CallEnvelope> {
            client.post(url("/calls/bridge")) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("userId", JsonPrimitive(userId))
                    put("agentName", JsonPrimitive(agentName))
                    put("agentPhone", JsonPrimitive(agentPhone))
                })
            }
        }.call

    /** PATCH only the fields given (null = leave unchanged). */
    suspend fun updateCall(
        id: String,
        endedAtIso: String? = null,
        durationSec: Int? = null,
        outcome: String? = null,
        note: String? = null,
        tagIds: List<String>? = null,
    ): CallDto = call<CallEnvelope> {
        client.patch(url("/calls/$id")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                endedAtIso?.let { put("endedAt", JsonPrimitive(it)) }
                durationSec?.let { put("durationSec", JsonPrimitive(it)) }
                outcome?.let { put("outcome", JsonPrimitive(it) as JsonElement) }
                note?.let { put("note", JsonPrimitive(it)) }
                tagIds?.let { ids -> put("tagIds", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }) }
            })
        }
    }.call

    suspend fun recordingUploadTarget(callId: String, ext: String): UploadTarget = call {
        client.post(url("/calls/$callId/recording-upload")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("ext", JsonPrimitive(ext)) })
        }
    }

    /** Streams the file to the signed Supabase URL (no support key — the token is in the URL). */
    suspend fun putRecording(uploadUrl: String, mime: String, length: Long, open: () -> InputStream) {
        val res = try {
            uploadClient.put(uploadUrl) {
                header("x-upsert", "true")
                setBody(object : OutgoingContent.ReadChannelContent() {
                    override val contentType = ContentType.parse(mime)
                    override val contentLength = length.takeIf { it > 0 }
                    override fun readFrom(): ByteReadChannel = open().toByteReadChannel()
                })
            }
        } catch (e: Exception) {
            throw ApiException("Upload failed — ${e.message ?: "network error"}")
        }
        if (!res.status.isSuccess()) throw ApiException("Upload failed (${res.status.value})", res.status.value)
    }

    suspend fun completeRecording(callId: String, path: String, bytes: Long, mime: String): CallDto =
        call<CallEnvelope> {
            client.post(url("/calls/$callId/recording-complete")) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("path", JsonPrimitive(path))
                    put("bytes", if (bytes > 0) JsonPrimitive(bytes) else JsonNull)
                    put("mime", JsonPrimitive(mime))
                })
            }
        }.call

    suspend fun recordingUrl(callId: String): String =
        call<RecordingUrl> { client.get(url("/calls/$callId/recording-url")) }.url
}
