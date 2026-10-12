// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu

import android.content.Context
import helium314.keyboard.latin.utils.Log

/** Lazily loaded religious honorific shortcuts (assets/honorifics/honorifics.tsv), see [ReligiousShortcuts]. */
object Honorifics {
    private const val ASSET = "honorifics/honorifics.tsv"
    @Volatile private var shortcuts: ReligiousShortcuts? = null
    @Volatile private var failed = false

    @JvmStatic
    fun get(context: Context?): ReligiousShortcuts? {
        shortcuts?.let { return it }
        if (context == null || failed) return null
        synchronized(this) { // small file (~20 KB), loaded once on first use
            shortcuts?.let { return it }
            try {
                shortcuts = context.applicationContext.assets.open(ASSET).use { ReligiousShortcuts.load(it) }
            } catch (e: Exception) {
                failed = true
                Log.e("Honorifics", "could not load $ASSET", e)
            }
        }
        return shortcuts
    }
}
