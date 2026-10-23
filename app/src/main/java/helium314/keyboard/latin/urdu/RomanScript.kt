// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu

import android.content.Context
import helium314.keyboard.latin.utils.Log
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Roman -> native script typing languages ("abc -> اردو", "abc -> عربي"): which locales use it, and the lazily loaded,
 * fully offline engines (one model file per language, see tools/urdu and tools/arabic).
 */
enum class RomanScript(val languageTag: String, private val modelAsset: String, val honorificScript: Int,
                       /** punctuation typed on the Latin keyboard that becomes the native one: ? , always, . only if [fullStop] is set */
                       val questionMark: Char, val comma: Char, val fullStop: Char?) {
    URDU("ur-Latn", "urdu/urdu_translit.bin", ReligiousShortcuts.URDU, '؟', '،', '۔'),
    ARABIC("ar-Latn", "arabic/arabic_translit.bin", ReligiousShortcuts.ARABIC, '؟', '،', null);

    @Volatile private var engine: RomanTransliterator? = null
    @Volatile private var loading = false

    /** returns the engine if loaded, otherwise starts loading it in the background and returns null */
    fun engine(context: Context?): RomanTransliterator? {
        engine?.let { return it }
        if (context == null || loading) return null
        loading = true
        val appContext = context.applicationContext
        thread(name = "translit-model-load-$languageTag") {
            try {
                engine = appContext.assets.open(modelAsset).use { RomanTransliterator.load(it) }
            } catch (e: Exception) {
                Log.e("RomanScript", "could not load $modelAsset", e)
            } finally {
                loading = false
            }
        }
        return null
    }

    /** Like [engine] but waits (up to [timeoutMs]) for the model to finish loading; only for background threads. */
    fun engineBlocking(context: Context, timeoutMs: Long = 15000): RomanTransliterator? {
        engine(context)
        val end = System.currentTimeMillis() + timeoutMs
        while (engine == null && loading && System.currentTimeMillis() < end) Thread.sleep(20)
        return engine
    }

    companion object {
        @JvmStatic
        fun of(locale: Locale?): RomanScript? {
            if (locale == null) return null
            val tag = locale.toLanguageTag()
            return values().firstOrNull { it.languageTag == tag }
        }

        /** true for any locale that uses Roman -> native script transliteration */
        @JvmStatic
        fun isActive(locale: Locale?): Boolean = of(locale) != null

        @JvmStatic
        fun isUrdu(locale: Locale?): Boolean = of(locale) == URDU

        /** output script for [ReligiousShortcuts] for text in the (native) script of this locale's language, or LATIN */
        @JvmStatic
        fun honorificScript(locale: Locale?, textIsArabicBlock: Boolean): Int {
            of(locale)?.let { return it.honorificScript }
            if (!textIsArabicBlock) return ReligiousShortcuts.LATIN
            return if (locale?.language == "ar") ReligiousShortcuts.ARABIC else ReligiousShortcuts.URDU
        }
    }
}
