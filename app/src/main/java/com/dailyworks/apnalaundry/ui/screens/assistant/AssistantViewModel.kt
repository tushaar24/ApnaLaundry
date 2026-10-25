package com.dailyworks.apnalaundry.ui.screens.assistant

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyworks.apnalaundry.analytics.Analytics
import com.dailyworks.apnalaundry.core.AppDate
import com.dailyworks.apnalaundry.core.tr
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
import kotlinx.serialization.json.jsonObject
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
            // The nudge below and the reply it corrected: dropped from the history after the turn.
            var nudgeAt = -1
            try {
                var answer: String? = null
                var force = false
                val made = mutableMapOf<String, String>() // tool + args → result: one card per change per turn
                while (true) {
                    rounds++
                    val st = shop.filterNotNull().first()
                    val runner = AssistantTools(st)
                    val reply = api.chat(
                        listOf(system(st, runner)) + window(turnStart),
                        if (force) AssistantTools.SCHEMA_WITH_REPLY else AssistantTools.SCHEMA,
                        mustUseTool = force,
                    )
                    force = false
                    reply.tool_calls.firstOrNull { it.function.name == AssistantTools.REPLY }?.let { r ->
                        answer = AssistantTools.replyText(r.function.arguments)
                        return@let
                    }
                    if (answer != null) break
                    if (reply.tool_calls.isEmpty() || rounds >= MAX_ROUNDS) {
                        // "Tap Save" with no card on screen: the model skipped the tool (it copies its
                        // earlier replies). Once per question: send it back to do it — or just answer.
                        val said = clean(reply.content)
                        if (cards.isEmpty() && nudgeAt < 0 && rounds < MAX_ROUNDS && mentionsButton(said)) {
                            nudgeAt = history.size
                            history += msg("assistant", said)
                            history += msg("user", NO_CARD_NUDGE)
                            force = true
                            Analytics.assistantNudged()
                            continue
                        }
                        answer = reply.content
                        break
                    }
                    history += toolCalls(reply, rounds)
                    reply.tool_calls.forEachIndexed { i, call ->
                        tools += call.function.name
                        val sig = call.function.name + "|" + call.function.arguments
                        val content = made[sig] ?: run {
                            val out = runner.run(call.function.name, call.function.arguments)
                            out.card?.let { c -> cards += c; Analytics.assistantCardShown(cardName(c)) }
                            (if (out.card != null) cardOnScreen(out.json) else out.json).also { if (out.card != null) made[sig] = it }
                        }
                        history += buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", call.id.ifBlank { "call_${rounds}_$i" })
                            put("content", content)
                        }
                    }
                }
                if (nudgeAt >= 0) repeat(2) { history.removeAt(nudgeAt) }
                val text = clean(answer).ifBlank {
                    if (cards.isNotEmpty()) tr("Check it below and tap the button.") else tr("Sorry, I couldn't get that. Please ask again in simple words.")
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
            add(Who.ERROR, tr("Couldn't start the mic. Close any app using it and try again."))
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
            add(Who.ERROR, tr("Didn't hear anything. Tap the mic, speak, then tap Stop."))
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
                add(Who.ERROR, tr("Couldn't make out the words. Please say it again, a little slower."))
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
            appendLine("You are the MyLaundry assistant inside the laundry shop app of \"${st.shop.name}\". You are talking to the shop owner — call them \"you\" / \"aap\", never \"the owner\".")
            val phone = st.shop.billPhone.ifBlank { st.shop.phone }
            if (phone.isNotBlank() || st.shop.address.isNotBlank()) {
                appendLine("Shop contact for messages: " + listOfNotNull(phone.takeIf { it.isNotBlank() }?.let { "phone $it" }, st.shop.address.takeIf { it.isNotBlank() }?.let { "address $it" }).joinToString(", ") + ". Never write placeholders like [shop number].")
            }
            appendLine("Today is ${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, ${com.dailyworks.apnalaundry.core.I18n.english { AppDate.plain(iso) }} ${today.year} ($iso), time ${AppDate.nowText()}.")
            appendLine("Dates: yesterday = ${today.minusDays(1)}; this week = $monday to $iso; this month = ${today.withDayOfMonth(1)} to $iso; last month = ${lastMonth.withDayOfMonth(1)} to ${lastMonth.withDayOfMonth(lastMonth.lengthOfMonth())}.")
            appendLine()
            appendLine("How to answer:")
            val lang = com.dailyworks.apnalaundry.core.I18n.language
            if (lang == com.dailyworks.apnalaundry.core.AppLanguage.EN) {
                appendLine("- The app is set to English: always reply in simple English, whatever language the owner writes or speaks in.")
            } else {
                appendLine("- The app is set to ${lang.english}: ALWAYS reply ONLY in ${lang.english}, in ${lang.script} script — whatever language or letters the owner uses. Everyday words shop owners say in English (order, bill, cash, UPI, WhatsApp) may stay as they are, written in ${lang.script}.")
                appendLine("- But in tool arguments write customer names and items in English letters, as they are saved in the app (e.g. a name said as \"रमेश\" → \"Ramesh\", \"शर्ट\" → \"Shirt\").")
            }
            appendLine("- Short and simple: 1-4 lines. No tables, no headings, no markdown; a few \"• \" lines are fine.")
            appendLine("- Money as ₹ with Indian commas (₹1,25,000). Baaki = money a customer still owes (their khata). Advance = they paid extra.")
            appendLine("- ALWAYS use the tools for shop data. Never guess customers, orders, prices or numbers.")
            appendLine("- Customers: for any action just pass customer_name (and customer_phone if the owner said a number). The app checks whether they are already saved — by number, then name — and takes them automatically; for a new order with someone not saved, they become a new customer (needs a 10-digit number: ask if missing). If the app says several match, ask which one (name + last 4 digits). Use find_customers only to look someone up.")
            appendLine("- New order: once the customer and clothes are clear, call create_order straight away — don't ask \"shall I make it?\"; its card with the Save order button IS the confirmation. Items come from the rate card below (service_id + item); \"press\" = iron. If the owner didn't say the service and the item is in more than one, ask. Then say \"Check and tap Save order\". Never say it is saved.")
            appendLine("- Bills, status updates, reminders, any WhatsApp message: send_bill / send_status_update / send_khata_reminder / whatsapp_message. They show a WhatsApp button the owner taps — never say a message was sent or WhatsApp was opened.")
            appendLine("- Never mention tool names or ids (find_orders, c12…) — speak like a helper in the shop.")
            appendLine("- \"Last order\" of a customer = their newest order (find_orders with customer_id).")
            appendLine("- Order statuses: to_pick_up (home pickup not done), received (at the shop, being washed), ready, delivered, cancelled.")
            appendLine("- You can change anything in the shop:")
            appendLine("  • change_status: any status, forward or back (delivered → ready, cancelled → received…). For delivered you need what the customer paid now: if the owner didn't say, ask \"Kitna mila — cash ya UPI? Ya sab khata mein?\". \"Paid\" / \"pura paisa\" = paid_full.")
            appendLine("  • record_payment: \"paid\" / \"part paid\" / \"300 diye\" on an order, or a khata payment.")
            appendLine("  • edit_order: add or remove clothes, this order's rate for an item, kg, express, discount, dates, note. An order with no clothes yet must get them (edit_order) before it can be ready or delivered.")
            appendLine("  • edit_rate_card: change a price, add / remove an item, add a new service, per-kg rate, ready days, rename or delete a service.")
            appendLine("  • save_customer (new customer or change name / phone / address), add_old_baaki.")
            appendLine("  • Cancel = change_status to cancelled (the order stays, marked cancelled). delete_order only when they say delete / mita do / galti se bana — never for cancel.")
            appendLine("  Several changes at once = several tool calls.")
            appendLine("- Every new request needs its OWN tool call — a card from an earlier message doesn't do it. Never tell the owner to tap a button unless a tool in THIS reply put a card on screen (card_on_screen).")
            appendLine("- Never invent names, items or prices the owner didn't say (no \"New Service\", \"Item1\"). If something needed is missing, ask.")
            appendLine("- IMPORTANT: a change happens ONLY when the owner taps the button on its card. So never write \"ho gaya\", \"kar diya\", \"add ho gaya\", \"done\", \"saved\", \"marked\". Say what the card will do and ask them to tap. Wrong: \"Shirt ka rate ₹12 kar diya.\" Right: \"Shirt ka rate ₹12 karne ke liye 'Save to rate card' tap karo.\" Wrong: \"2 pant add ho gaye.\" Right: \"2 pant jodne ke liye 'Save changes' tap karo.\"")
            appendLine("- Never ask the owner for a customer_id, order id or service id — look them up yourself (find_customers, find_orders, the rate card). Ask only for things only they know: which customer when several match, amounts, cash or UPI.")
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

    /**
     * A card's tool result: the card is on screen and the model's part is done
     * (said plainly — "nothing happened yet" made it call the tool again), but
     * it isn't saved until the owner taps, so no "ho gaya".
     */
    private fun cardOnScreen(json: String): String = runCatching {
        JsonObject(
            kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject + mapOf(
                "card_on_screen" to JsonPrimitive(true),
                "say" to JsonPrimitive("The card with its button is on the owner's screen now — your part is done; don't call this tool again. In one line tell them to check the card and tap its button. It isn't saved until they tap, so don't say ho gaya / kar diya / done."),
            ),
        ).toString()
    }.getOrDefault(json)

    /** Does the reply tell the owner to tap something? (In every app language.) */
    private fun mentionsButton(text: String): Boolean {
        val t = text.lowercase()
        return LATIN_BUTTON.containsMatchIn(t) || BUTTON_WORDS.any { t.contains(it) }
    }

    private fun clean(s: String?): String = s.orEmpty()
        .replace("**", "")
        .lines().joinToString("\n") { it.trimEnd().replace(Regex("^#{1,6}\\s*"), "").replace(Regex("^[*-]\\s+"), "• ") }
        .trim()

    private fun errorText(e: Exception): String = when {
        e is AssistantException && e.code == "limit" -> tr("You've asked a lot today. The assistant will be back tomorrow.")
        e is AssistantException && e.code == "off" -> tr("The assistant isn't available right now.")
        e is AssistantException -> tr("The assistant didn't answer. Please try again.")
        e is NotLoggedInException -> tr("Please log in again.")
        else -> tr("No internet? Check it and try again.")
    }

    private fun reason(e: Exception): String = when (e) {
        is AssistantException -> e.code
        is NotLoggedInException -> "login"
        else -> "network"
    }

    companion object {
        /** Model calls per question: answer, or tools then answer (a new order may take 3). */
        private const val MAX_ROUNDS = 6

        /**
         * "Tap" / "button" in the app's languages — a reply with these but no card is
         * checked once. Whole words in English letters ("Tapan" isn't "tap"); not
         * "press", which also means ironing ("3 shirt press").
         */
        private val LATIN_BUTTON = Regex("\\b(tap|tapping|button|buttons)\\b")
        private val BUTTON_WORDS = listOf(
            "टैप", "बटन", "दबा", // Hindi, Marathi
            "ટેપ", "બટન", "દબા", // Gujarati
            "ট্যাপ", "বাটন", "চাপ", // Bengali
            "தட்டு", "டேப்", "பட்டன்", "பொத்தான்", // Tamil
            "ట్యాప్", "బటన్", "నొక్క", // Telugu
            "ಟ್ಯಾಪ್", "ಬಟನ್", "ಒತ್ತ", // Kannada
            "ടാപ്പ്", "ബട്ടൺ", "അമർത്ത", // Malayalam
            "ਟੈਪ", "ਬਟਨ", "ਦਬਾ", // Punjabi
            "ଟ୍ୟାପ୍", "ବଟନ୍", "ଦବା", // Odia
        )

        /** Sent (once) when a reply points at a button but no tool made a card. */
        private const val NO_CARD_NUDGE =
            "[App] No card is on the screen — you did not call any tool, so there is no button. " +
                "If I asked you to make a change or send something, call its tool now. " +
                "If I only asked a question or how to do something, use reply to answer."
        private const val MAX_VOICE_MS = 25_000L

        fun cardName(c: AssistantCard) = when (c) {
            is AssistantCard.NewOrder -> "new_order"
            is AssistantCard.SendBill -> "send_bill"
            is AssistantCard.StatusUpdates -> "status_update"
            is AssistantCard.Reminder -> "reminder"
            is AssistantCard.WhatsAppText -> "whatsapp_message"
            is AssistantCard.StatusChange -> "change_status"
            is AssistantCard.Payment -> "payment"
            is AssistantCard.EditOrder -> "edit_order"
            is AssistantCard.RateChange -> "rate_card"
            is AssistantCard.SaveCustomer -> "customer"
            is AssistantCard.OldBaaki -> "old_baaki"
            is AssistantCard.DeleteOrder -> "delete_order"
        }
    }
}
