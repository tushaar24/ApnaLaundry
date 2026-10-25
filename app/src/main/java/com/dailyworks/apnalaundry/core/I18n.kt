package com.dailyworks.apnalaundry.core

import android.content.Context
import android.content.res.Configuration
import kotlinx.serialization.json.Json
import java.util.Locale

/** The app's languages: English + 10 Indian languages ([speech] = Sarvam's code for voice). */
enum class AppLanguage(val code: String, val native: String, val english: String, val speech: String, val script: String) {
    EN("en", "English", "English", "en-IN", "Latin"),
    HI("hi", "हिन्दी", "Hindi", "hi-IN", "Devanagari"),
    MR("mr", "मराठी", "Marathi", "mr-IN", "Devanagari"),
    GU("gu", "ગુજરાતી", "Gujarati", "gu-IN", "Gujarati"),
    BN("bn", "বাংলা", "Bengali", "bn-IN", "Bengali"),
    TA("ta", "தமிழ்", "Tamil", "ta-IN", "Tamil"),
    TE("te", "తెలుగు", "Telugu", "te-IN", "Telugu"),
    KN("kn", "ಕನ್ನಡ", "Kannada", "kn-IN", "Kannada"),
    ML("ml", "മലയാളം", "Malayalam", "ml-IN", "Malayalam"),
    PA("pa", "ਪੰਜਾਬੀ", "Punjabi", "pa-IN", "Gurmukhi"),
    OR("or", "ଓଡ଼ିଆ", "Odia", "od-IN", "Odia");

    val locale: Locale get() = Locale(code, "IN")

    companion object {
        fun of(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }
    }
}

/**
 * The language the app speaks. Screen text is written in English in the code
 * and wrapped in [tr]; each language's catalogue — assets/i18n/<code>.json,
 * English → translation, made by tools/i18n with Sarvam AI — supplies the
 * words, and a text it lacks shows in English. Placeholders are named, so
 * translators see what goes there: tr("Picked up · {name}", "name" to nm).
 *
 * Saved on the phone (not the account): it is the person holding it who reads
 * it, and the picker comes before login. Changing it recreates the screen.
 * Data stays as typed (customer, service and item names), and so does the bill
 * document, which matches the bill's web page.
 */
object I18n {
    private const val PREFS = "app_language"
    private const val KEY = "lang"

    @Volatile var language: AppLanguage = AppLanguage.EN
        private set

    @Volatile private var table: Map<String, String> = emptyMap()
    private val forceEnglish = ThreadLocal<Boolean>()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** App start: the saved language (English until one is picked). */
    fun init(context: Context) = use(context, AppLanguage.of(prefs(context).getString(KEY, null)) ?: AppLanguage.EN)

    /** A language was picked on this phone (the picker before login shows until then). */
    fun chosen(context: Context): Boolean = prefs(context).contains(KEY)

    /** Saves and switches; the caller recreates the screen. */
    fun set(context: Context, lang: AppLanguage) {
        prefs(context).edit().putString(KEY, lang.code).commit()
        use(context, lang)
    }

    private fun use(context: Context, lang: AppLanguage) {
        table = if (lang == AppLanguage.EN) emptyMap() else load(context, lang.code)
        language = lang
    }

    private fun load(context: Context, code: String): Map<String, String> = runCatching {
        context.assets.open("i18n/$code.json").bufferedReader().use { Json.decodeFromString<Map<String, String>>(it.readText()) }
    }.getOrDefault(emptyMap())

    fun tr(src: String, args: Array<out Pair<String, Any?>>): String {
        val t = if (forceEnglish.get() == true) src else table[src] ?: src
        if (args.isEmpty()) return t
        var out = t
        for ((k, v) in args) out = out.replace("{$k}", v?.toString().orEmpty())
        return out
    }

    /** [block]'s text in English whatever the app's language (the bill document). */
    fun <T> english(block: () -> T): T {
        val prev = forceEnglish.get()
        forceEnglish.set(true)
        try {
            return block()
        } finally {
            forceEnglish.set(prev)
        }
    }

    /** The activity's context in the chosen language — Android's own pickers and words follow it. */
    fun wrap(base: Context): Context {
        if (language == AppLanguage.EN) return base
        val config = Configuration(base.resources.configuration).apply { setLocale(language.locale) }
        return base.createConfigurationContext(config)
    }
}

/**
 * Screen text in the app's language. [src] is the English text, a plain
 * literal (tools/i18n collects every tr("…")); dynamic parts are named
 * placeholders: tr("{n} orders", "n" to 3).
 */
fun tr(src: String, vararg args: Pair<String, Any?>): String = I18n.tr(src, args)

/** Marks an English text shown later through tr(variable), so tools/i18n collects it. */
fun i18n(src: String): String = src
