// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu

import android.content.Context
import helium314.keyboard.latin.utils.Log
import java.util.Locale
import kotlin.concurrent.thread

/** Holds the (lazily loaded, fully offline) Roman -> Urdu engine and tells whether a locale uses it. */
object UrduTransliteration {
    private const val MODEL_ASSET = "urdu/urdu_translit.bin"
    const val LANGUAGE_TAG = "ur-Latn"

    @Volatile private var engine: UrduTransliterator? = null
    @Volatile private var loading = false

    @JvmStatic
    fun isActive(locale: Locale?): Boolean = locale != null && locale.toLanguageTag() == LANGUAGE_TAG

    /** returns the engine if loaded, otherwise starts loading it in the background and returns null */
    @JvmStatic
    fun get(context: Context?): UrduTransliterator? {
        engine?.let { return it }
        if (context == null || loading) return null
        loading = true
        val appContext = context.applicationContext
        thread(name = "urdu-model-load") {
            try {
                engine = appContext.assets.open(MODEL_ASSET).use { UrduTransliterator.load(it) }
            } catch (e: Exception) {
                Log.e("UrduTransliteration", "could not load $MODEL_ASSET", e)
            } finally {
                loading = false
            }
        }
        return null
    }
}
