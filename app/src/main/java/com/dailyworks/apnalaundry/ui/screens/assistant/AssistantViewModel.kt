package com.dailyworks.apnalaundry.ui.screens.assistant

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.data.LaundryRepository
import com.dailyworks.apnalaundry.data.assistant.AssistantApi
import com.dailyworks.apnalaundry.data.assistant.AssistantException
import com.dailyworks.apnalaundry.data.assistant.AssistantMessage
import com.dailyworks.apnalaundry.data.billing.BillingRepository
import com.dailyworks.apnalaundry.data.shops.FREE_SHOPS
import com.dailyworks.apnalaundry.data.shops.ShopDirectory
import com.dailyworks.apnalaundry.data.sync.NotLoggedInException
import com.dailyworks.apnalaundry.domain.LaundryMath
import com.dailyworks.apnalaundry.domain.LaundryState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The assistant's conversation: the owner asks (typed or a voice note), the
 * model (Sarvam, through our server) asks for tools, [AssistantTools] answers
 * them from the shop's own data on this phone, and the reply comes back with
 * any cards to tap. Lives as long as the Ask screen.
 */
class AssistantViewModel(
    private val api: AssistantApi,
    private val repo: LaundryRepository,
    private val billing: BillingRepository,
    private val shopDir: ShopDirectory,
    private val recorder: VoiceRecorder,
) : ViewModel() {

    enum class Who { OWNER, ASSISTANT, ERROR }

    data class Bubble(val id: Long, val who: Who, val text: String, val cards: List<AssistantCard> = emptyList())

    sealed interface Voice {
        data object Idle : Voice
        data class Recording(val startedAt: Long) : Voice
        /** The note is being turned into words. */
        data object Hearing : Voice
    }

    sealed interface Draft {
        data object Saving : Draft
        data class Saved(val orderId: Int) : Draft
        data object Cancelled : Draft
        /** Free orders used up — the owner needs a plan first. */
        data object NeedsPlan : Draft
    }

    private val _bubbles = MutableStateFlow<List<Bubble>>(emptyList())
    val bubbles: StateFlow<List<Bubble>> = _bubbles

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _voice = MutableStateFlow<Voice>(Voice.Idle)
    val voice: StateFlow<Voice> = _voice

    /** New-order cards by key: saving / saved / cancelled. Absent = waiting for the owner. */
    private val _drafts = MutableStateFlow<Map<String, Draft>>(emptyMap())
    val drafts: StateFlow<Map<String, Draft>> = _drafts

    /** Cards the owner already used (sent, marked) — they show "Done ✓". */
    private val _used = MutableStateFlow<Set<String>>(emptySet())
    val used: StateFlow<Set<String>> = _used

    private val shop = repo.state.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The conversation in the chat-completions shape: user / assistant / tool messages. */
    private val history = mutableListOf<JsonObject>()
    /** What the owner did from cards in this chat ("Order #1005 saved for Ramesh") — told to the model. */
    private val done = mutableListOf<String>()
    private var nextId = 1L
    private var voiceJob: Job? = null

    fun ask(text: String, via: String) {
        val q = text.trim().take(1000)
        if (q.isEmpty() || _busy.value) return
        add(Who.OWNER, q)
        Analytics.assistantAsked(via, q.length)
        val turnStart = history.size
        history += msg("user", q)
        _busy.value = true
        viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            val cards = mutableListOf<AssistantCard>()
            val tools = mutableListOf<String>()
            var rounds = 0
            try {
                var answer: String? = null
                while (true) {
                    rounds++
                    val st = shop.filterNotNull().first()
                    val runner = AssistantTools(st)
                    val reply = api.chat(listOf(system(st, runner)) + window(turnStart), AssistantTools.SCHEMA)
                    if (reply.tool_calls.isEmpty() || rounds >= MAX_ROUNDS) { answer = reply.content; break }
                    history += toolCalls(reply, rounds)
                    reply.tool_calls.forEachIndexed { i, call ->
                        tools += call.function.name
                        val out = runner.run(call.function.name, call.function.arguments)
                        out.card?.let { c -> cards += c; Analytics.assistantCardShown(cardName(c)) }
                        history += buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", call.id.ifBlank { "call_${rounds}_$i" })
                            put("content", out.json)
                        }
                    }
                }
                val text = clean(answer).ifBlank {
                    if (cards.isNotEmpty()) "Check it below and tap the button." else "Sorry, I couldn't get that. Please ask again in simple words."
                }
                history += msg("assistant", text)
                add(Who.ASSISTANT, text, cards)
                Analytics.assistantAnswered(rounds, tools.joinToString(","), cards.size, SystemClock.elapsedRealtime() - started)
            } catch (e: Exception) {
                // Forget the failed turn so the next question starts clean.
                while (history.size > turnStart) history.removeAt(history.size - 1)
                add(Who.ERROR, errorText(e))
                Analytics.assistantFailed(reason(e))
            } finally {
                _busy.value = false
            }
        }
    }

    // ---------------- voice ----------------

    /** Starts a voice note (mic permission already granted). */
    fun startVoice() {
        if (_busy.value || _voice.value != Voice.Idle) return
        if (!recorder.start()) {
            add(Who.ERROR, "Couldn't start the mic. Close any app using it and try again.")
            Analytics.assistantVoice(0, false, "mic")
            return
        }
        _voice.value = Voice.Recording(SystemClock.elapsedRealtime())
        voiceJob = viewModelScope.launch { delay(MAX_VOICE_MS); stopVoice() }
    }

    /** Stops the note and asks with what was said. */
    fun stopVoice() {
        val rec = _voice.value as? Voice.Recording ?: return
        voiceJob?.cancel()
        val secs = ((SystemClock.elapsedRealtime() - rec.startedAt) / 1000).toInt()
        val audio = recorder.stop()
        if (audio == null) {
            _voice.value = Voice.Idle
            add(Who.ERROR, "Didn't hear anything. Tap the mic, speak, then tap Stop.")
            Analytics.assistantVoice(secs, false, "short")
            return
        }
        _voice.value = Voice.Hearing
        viewModelScope.launch {
            val heard = try {
                api.transcribe(audio).trim()
            } catch (e: Exception) {
                _voice.value = Voice.Idle
                add(Who.ERROR, errorText(e))
                Analytics.assistantVoice(secs, false, reason(e))
                return@launch
            }
            _voice.value = Voice.Idle
            if (heard.isEmpty()) {
                add(Who.ERROR, "Couldn't make out the words. Please say it again, a little slower.")
                Analytics.assistantVoice(secs, false, "empty")
            } else {
                Analytics.assistantVoice(secs, true, "")
                ask(heard, "voice")
            }
        }
    }

    fun cancelVoice() {
        voiceJob?.cancel()
        recorder.cancel()
        if (_voice.value is Voice.Recording) _voice.value = Voice.Idle
    }

    // ---------------- cards ----------------

    /**
     * The free-orders rule of New order (OrderLimitGate): with a plan, or
     * within the free orders, a new order may be saved.
     */
    suspend fun orderAllowed(): Boolean {
        val made = repo.liveOrderCountAllShops()
        val planEnded = shopDir.list().size > FREE_SHOPS
        if (billing.savedActive() || (!planEnded && made < billing.savedFreeOrders())) return true
        billing.refreshSaved() // paid on the website / another phone since?
        return billing.savedActive() || (!planEnded && made < billing.savedFreeOrders())
    }

    fun draft(key: String, d: Draft) = _drafts.update { it + (key to d) }

    fun orderSaved(key: String, orderId: Int, customer: String, total: Int) {
        draft(key, Draft.Saved(orderId))
        val no = shop.value?.orders?.firstOrNull { it.id == orderId }?.no() ?: orderId.toString()
        done += "Saved order #$no for $customer (₹$total)"
        Analytics.assistantCardUsed("new_order")
    }

    /** A card's button was used; [what] is told to the model on the next question. */
    fun used(key: String, card: String, what: String) {
        _used.update { it + key }
        done += what
        Analytics.assistantCardUsed(card)
    }

    fun clear() {
        if (_busy.value) return
        cancelVoice()
        history.clear(); done.clear()
        _bubbles.value = emptyList(); _drafts.value = emptyMap(); _used.value = emptySet()
    }

    override fun onCleared() {
        recorder.cancel()
    }

    // ---------------- helpers ----------------

    private fun add(who: Who, text: String, cards: List<AssistantCard> = emptyList()) {
        _bubbles.update { it + Bubble(nextId++, who, text, cards) }
    }

    private fun msg(role: String, content: String) = buildJsonObject { put("role", role); put("content", content) }

    private fun toolCalls(reply: AssistantMessage, round: Int) = buildJsonObject {
        put("role", "assistant")
        put("content", reply.content?.let { JsonPrimitive(it) } ?: JsonNull)
        putJsonArray("tool_calls") {
            reply.tool_calls.forEachIndexed { i, c ->
                add(buildJsonObject {
                    put("id", c.id.ifBlank { "call_${round}_$i" })
                    put("type", "function")
                    putJsonObject("function") { put("name", c.function.name); put("arguments", c.function.arguments) }
                })
            }
        }
    }

    /**
     * The recent conversation, whole turns only, small enough for one request
     * (the server takes ~120 KB). The current turn ([turnStart] on) always goes.
     */
    private fun window(turnStart: Int): List<JsonObject> {
        var from = 0
        fun size() = history.subList(from, history.size).sumOf { it.toString().length }
        while (from < turnStart && (history.size - from > 40 || size() > 60_000)) {
            from++
            while (from < turnStart && (history[from]["role"] as? JsonPrimitive)?.content != "user") from++
        }
        return history.subList(from, history.size).toList()
    }

    private fun system(st: LaundryState, tools: AssistantTools): JsonObject {
        val today = LocalDate.now()
        val iso = today.toString()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val lastMonth = today.minusMonths(1)
        val text = buildString {
            appendLine("You are the MyLaundry assistant inside the laundry shop app of \"${st.shop.name}\". You help the shop owner run the shop.")
            appendLine("Today is ${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, ${AppDate.plain(iso)} ${today.year} ($iso), time ${AppDate.nowText()}.")
            appendLine("Dates: yesterday = ${today.minusDays(1)}; this week = $monday to $iso; this month = ${today.withDayOfMonth(1)} to $iso; last month = ${lastMonth.withDayOfMonth(1)} to ${lastMonth.withDayOfMonth(lastMonth.lengthOfMonth())}.")
            appendLine()
            appendLine("How to answer:")
            appendLine("- Reply in the owner's language and script: Hindi in Devanagari, Hinglish in English letters, English, or any Indian language. Short and simple: 1-4 lines. No tables, no headings, no markdown; a few \"• \" lines are fine.")
            appendLine("- Money as ₹ with Indian commas (₹1,25,000). Baaki = money a customer still owes (their khata). Advance = they paid extra.")
            appendLine("- ALWAYS use the tools for shop data. Never guess customers, orders, prices or numbers.")
            appendLine("- Find a customer with find_customers first. If several match, ask which one (name + last 4 phone digits). If none, ask whether it's a new customer.")
            appendLine("- New order: create_order with items from the rate card below (service_id + item). If the owner didn't say the service and the item is in more than one, ask. A new customer needs a name and a 10-digit mobile. The app shows the order with a Save order button — say \"Check and tap Save order\". Never say it is saved.")
            appendLine("- Bills, status updates, reminders, any WhatsApp message: send_bill / send_status_update / send_khata_reminder / whatsapp_message. They show a WhatsApp button the owner taps — never say a message was sent.")
            appendLine("- \"Last order\" of a customer = their newest order (find_orders with customer_id).")
            appendLine("- Order statuses: to_pick_up (home pickup not done), received (at the shop, being washed), ready, delivered, cancelled.")
            appendLine("- mark_order can mark picked up or ready. Delivering (with payment), cancelling, editing or deleting an order is done by opening the order in the app — tell the owner that.")
            appendLine("- Only this shop's laundry work. For anything else, say politely you can only help with the shop.")
            appendLine()
            appendLine("Rate card (service_id: name — items):")
            appendLine(tools.rateCard(3500))
            appendLine()
            appendLine("Shop now: ${st.customers.size} customers, ${st.orders.count { LaundryMath.isOpen(it) }} open orders.")
            if (done.isNotEmpty()) {
                appendLine()
                appendLine("Done by the owner in this chat:")
                done.takeLast(10).forEach { appendLine("- $it") }
            }
        }
        return msg("system", text.trim())
    }

    private fun clean(s: String?): String = s.orEmpty()
        .replace("**", "")
        .lines().joinToString("\n") { it.trimEnd().replace(Regex("^#{1,6}\\s*"), "").replace(Regex("^[*-]\\s+"), "• ") }
        .trim()

    private fun errorText(e: Exception): String = when {
        e is AssistantException && e.code == "limit" -> "You've asked a lot today. The assistant will be back tomorrow."
        e is AssistantException && e.code == "off" -> "The assistant isn't available right now."
        e is AssistantException -> "The assistant didn't answer. Please try again."
        e is NotLoggedInException -> "Please log in again."
        else -> "No internet? Check it and try again."
    }

    private fun reason(e: Exception): String = when (e) {
        is AssistantException -> e.code
        is NotLoggedInException -> "login"
        else -> "network"
    }

    companion object {
        /** Model calls per question: answer, or tools then answer (a new order may take 3). */
        private const val MAX_ROUNDS = 5
        private const val MAX_VOICE_MS = 25_000L

        fun cardName(c: AssistantCard) = when (c) {
            is AssistantCard.NewOrder -> "new_order"
            is AssistantCard.SendBill -> "send_bill"
            is AssistantCard.StatusUpdates -> "status_update"
            is AssistantCard.Reminder -> "reminder"
            is AssistantCard.WhatsAppText -> "whatsapp_message"
            is AssistantCard.MarkOrder -> "mark_order"
        }
    }
}
