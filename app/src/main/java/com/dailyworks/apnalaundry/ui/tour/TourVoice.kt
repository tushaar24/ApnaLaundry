package com.dailyworks.apnalaundry.ui.tour

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.dailyworks.apnalaundry.core.AppLanguage
import com.dailyworks.apnalaundry.core.I18n
import com.dailyworks.apnalaundry.data.Prefs
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
 * its own words, so the next card (or a sheet's tip) isn't cut off. A card's
 * words play again [REPEAT_AFTER_MS] after they finish, for as long as it
 * shows (owner, 2026-10-25).
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
    private var lastSaid: Pair<String, String>? = null // owner, words — what a repeat says again
    private var voiceFor: AppLanguage? = null // the app language the engine's voice was picked for
    private var hasVoice = false // the phone has a voice for [voiceFor]
    private var stopJob: Job? = null
    private var repeatJob: Job? = null

    /**
     * Starts the engine, picks the voice and loads it ahead of the first words, so
     * they don't wait 1–3 s for the engine on a budget phone. [forAsk]: for Ask's
     * replies, which the guide's mute doesn't stop (owner, 2026-10-25: Ask's first
     * answer was slow to speak). Safe to call often; a language change loads the
     * new voice.
     */
    fun warmUp(forAsk: Boolean = false) {
        if (!forAsk && muted.value) return
        val engine = tts ?: create()
        if (ready) preload(engine)
    }

    private var preloadedFor: AppLanguage? = null

    /**
     * Engines load a language's voice on its first words. A short line written to a
     * file — never played — loads it now instead. Skipped with words already waiting
     * (they load it) or the phone has no voice for the language.
     */
    private fun preload(engine: TextToSpeech) {
        val lang = I18n.language
        if (preloadedFor == lang || pending != null || !pickVoice(engine)) return
        preloadedFor = lang
        runCatching {
            engine.synthesizeToFile(com.dailyworks.apnalaundry.core.tr("OK"), android.os.Bundle(), java.io.File(app.cacheDir, "tts_warm.wav"), WARM_UP)
        }
    }

    /** Owners whose words play once, never repeated (Ask's replies). */
    private val once = mutableSetOf<String>()

    /**
     * Ask: reads an answer out loud once, in the app's language — no repeat,
     * and the guide's mute doesn't apply (Ask has its own switch; the caller
     * checks it).
     */
    fun read(text: String, owner: String) {
        once += owner
        speaking = owner
        stopJob?.cancel()
        repeatJob?.cancel()
        scope.launch {
            val words = spoken(text)
            if (words.isBlank() || speaking != owner) return@launch
            lastSaid = owner to words
            val engine = tts ?: create()
            if (ready) speak(engine, words, owner) else pending = words to owner
        }
    }

    /** Says [text] (replacing anything being said), unless muted. */
    fun say(text: String, owner: String) {
        // Claimed now, so a stop(owner) before the words start still cancels them.
        speaking = owner
        stopJob?.cancel()
        repeatJob?.cancel()
        scope.launch {
            if (prefs.tourMuted.first() || speaking != owner) return@launch
            val words = spoken(text)
            if (words.isBlank()) return@launch
            lastSaid = owner to words
            val engine = tts ?: create()
            if (ready) speak(engine, words, owner) else pending = words to owner
        }
    }

    /** Speaks in the app's language; silent when the phone has no voice for it. QUEUE_FLUSH cuts off any other card's words. */
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

    /**
     * Stops [owner]'s words; another card's words keep going. Not straight away:
     * the next card usually starts talking in the same moment, and on many phones
     * a stop() landing just before its speak() swallowed the new words too (the
     * "skipped fast, next card silent" bug). The next card's QUEUE_FLUSH replaces
     * these words anyway; the stop is only for a card closing with no next one.
     */
    fun stop(owner: String) {
        if (speaking != owner) return
        speaking = null
        pending = null
        repeatJob?.cancel()
        stopJob?.cancel()
        stopJob = scope.launch {
            delay(STOP_GRACE_MS)
            if (speaking == null) tts?.stop()
        }
    }

    /** Everything, now (muted, the app went to the background). */
    fun stopAll() {
        stopJob?.cancel()
        repeatJob?.cancel()
        speaking = null
        pending = null
        tts?.stop()
    }

    /** [then] runs once the choice is saved — so a [say] in it already sees the sound on. */
    fun setMuted(on: Boolean, then: () -> Unit = {}) {
        if (on) stopAll()
        scope.launch { prefs.setTourMuted(on); then() }
    }

    /** The card's words finished: say them again in a moment, while that card still shows. */
    private fun repeatLater(owner: String) {
        repeatJob?.cancel()
        repeatJob = scope.launch {
            delay(REPEAT_AFTER_MS)
            val (who, words) = lastSaid ?: return@launch
            if (who != owner || speaking != owner || prefs.tourMuted.first()) return@launch
            tts?.takeIf { ready }?.let { speak(it, words, owner) }
        }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        // Called off the main thread. Only words that played to the end repeat —
        // words cut off by another card end in onStop, not here.
        override fun onDone(utteranceId: String?) {
            val id = utteranceId ?: return
            scope.launch(Dispatchers.Main) { if (speaking == id && id !in once) repeatLater(id) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = Unit
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

    /** A little slower than normal talk, the voice picked now (not with the first words); then any waiting words. */
    private fun setUp(engine: TextToSpeech) {
        engine.setSpeechRate(0.9f)
        engine.setOnUtteranceProgressListener(progress)
        pickVoice(engine)
        ready = true
        if (pending == null) preload(engine)
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
        /** Pause before a card's words play again. */
        const val REPEAT_AFTER_MS = 5_000L
        const val STOP_GRACE_MS = 300L
        /** The [preload] line's id — not an owner, so it never repeats or counts as speaking. */
        const val WARM_UP = "warm_up"
    }
}
