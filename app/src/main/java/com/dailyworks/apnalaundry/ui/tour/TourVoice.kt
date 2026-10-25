package com.dailyworks.apnalaundry.ui.tour

import android.content.Context
import android.speech.tts.TextToSpeech
import com.dailyworks.apnalaundry.core.AppLanguage
import com.dailyworks.apnalaundry.core.I18n
import com.dailyworks.apnalaundry.data.Prefs
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Reads the guide's cards out loud with the phone's own text-to-speech, in the
 * app's language: English with an Indian English voice when the phone has one
 * (else UK / US); another language with the phone's voice for it. Sound is on
 * by default; the owner's mute is remembered. No voice on the phone for the
 * language → the guide stays silent (an English voice can't read Hindi,
 * Tamil… script).
 *
 * Each card speaks as its [say] owner: a card leaving the screen stops only
 * its own words, so the next card (or a sheet's tip) isn't cut off.
 */
class TourVoice(context: Context, private val prefs: Prefs) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val muted: StateFlow<Boolean> = prefs.tourMuted.stateIn(scope, SharingStarted.Eagerly, false)

    private var tts: TextToSpeech? = null
    private var initOk = false // the engine answered SUCCESS (maybe before [tts] was set)
    private var ready = false
    private var pending: Pair<String, String>? = null // text, owner — said once the engine is up
    private var speaking: String? = null // owner of the words being said
    private var voiceFor: AppLanguage? = null // the app language the engine's voice was picked for
    private var hasVoice = false // the phone has a voice for [voiceFor]

    /** Says [text] (replacing anything being said), unless muted. */
    fun say(text: String, owner: String) {
        // Claimed now, so a stop(owner) before the words start still cancels them.
        speaking = owner
        scope.launch {
            if (prefs.tourMuted.first() || speaking != owner) return@launch
            val words = spoken(text)
            if (words.isBlank()) return@launch
            val engine = tts ?: create()
            if (ready) speak(engine, words, owner) else pending = words to owner
        }
    }

    /** Speaks in the app's language; silent when the phone has no voice for it. */
    private fun speak(engine: TextToSpeech, words: String, owner: String) {
        if (!pickVoice(engine)) return
        engine.speak(words, TextToSpeech.QUEUE_FLUSH, null, owner)
    }

    /**
     * The voice for the app's language, picked again after the language
     * changes: English → Indian English, else UK / US; another language → the
     * phone's voice for it (Hindi in India…), else none.
     */
    private fun pickVoice(engine: TextToSpeech): Boolean {
        val lang = I18n.language
        if (lang == voiceFor) return hasVoice
        voiceFor = lang
        val choices = if (lang == AppLanguage.EN) listOf(Locale.forLanguageTag("en-IN"), Locale.UK, Locale.US) else listOf(lang.locale)
        val locale = choices.firstOrNull { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
        hasVoice = locale != null
        if (locale != null) engine.language = locale
        return hasVoice
    }

    /** Stops [owner]'s words; another card's words keep going. */
    fun stop(owner: String) {
        if (speaking != owner) return
        stopAll()
    }

    fun stopAll() {
        speaking = null
        pending = null
        tts?.stop()
    }

    /** [then] runs once the choice is saved — so a [say] in it already sees the sound on. */
    fun setMuted(on: Boolean, then: () -> Unit = {}) {
        if (on) stopAll()
        scope.launch { prefs.setTourMuted(on); then() }
    }

    private fun create(): TextToSpeech {
        val engine = TextToSpeech(app) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            initOk = true
            tts?.let(::setUp) // else set up right after the constructor returns
        }
        tts = engine
        if (initOk && !ready) setUp(engine)
        return engine
    }

    /** A little slower than normal talk; then any waiting words (the voice is picked as they're said). */
    private fun setUp(engine: TextToSpeech) {
        engine.setSpeechRate(0.9f)
        ready = true
        pending?.let { (words, owner) -> if (speaking == owner) speak(engine, words, owner) }
        pending = null
    }

    /** The card's words as speech: no emoji, quotes or middle dots. */
    private fun spoken(text: String): String = buildString {
        text.codePoints().forEach { cp ->
            when {
                cp == '·'.code -> append(',')
                cp in QUOTES -> Unit
                cp < 0x2190 -> appendCodePoint(cp) // letters, digits, punctuation, ₹ — not ✓ 🎉
            }
        }
    }.trim()

    private companion object {
        val QUOTES = setOf('‘'.code, '’'.code, '“'.code, '”'.code)
    }
}
