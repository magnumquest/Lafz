// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu

import android.content.Context
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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

    /** what the owner's typing taught the keyboard; null until loaded (learning is skipped, never waited for, before that) */
    @Volatile var personal: PersonalModel? = null
        private set
    private var saveTask: ScheduledFuture<*>? = null
    @Volatile private var appContext: Context? = null

    private fun learnedFile(context: Context) = File(context.filesDir, "learned_$languageTag.bin")

    private fun loadPersonal(context: Context) {
        val f = learnedFile(context)
        personal = try {
            if (f.exists()) f.inputStream().buffered().use { PersonalModel.load(it) } else PersonalModel()
        } catch (e: Exception) {
            Log.e("RomanScript", "could not load learned words", e)
            PersonalModel()
        }
    }

    fun learn(roman: String, word: String, prev: String?, weight: Float) {
        val pm = personal ?: return
        pm.learn(roman, word, prev, weight)
        scheduleSave()
    }

    fun unlearn(roman: String, word: String, prev: String?, weight: Float) {
        val pm = personal ?: return
        pm.unlearn(roman, word, prev, weight)
        scheduleSave()
    }

    /** saves 10 s after the last change, on a background thread */
    private fun scheduleSave() {
        synchronized(SAVER) {
            saveTask?.cancel(false)
            saveTask = SAVER.schedule({ saveNow() }, 10, TimeUnit.SECONDS)
        }
    }

    fun saveNow() {
        val pm = personal ?: return
        val context = appContext ?: return
        if (!pm.isDirty) return
        try {
            val f = learnedFile(context)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.outputStream().buffered().use { pm.save(it) }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        } catch (e: Exception) {
            Log.e("RomanScript", "could not save learned words", e)
        }
    }

    /** reads the learned words from disk again, e.g. after a backup was restored (anything not saved yet is dropped) */
    fun reloadLearned(context: Context) {
        appContext = context.applicationContext
        synchronized(SAVER) { saveTask?.cancel(false) }
        loadPersonal(context.applicationContext)
    }

    /** writes this language's learned words as a zip entry, see [exportLearned] */
    private fun writeLearnedTo(zip: ZipOutputStream, context: Context) {
        appContext = context.applicationContext
        if (personal == null) loadPersonal(context.applicationContext)
        zip.putNextEntry(ZipEntry("learned_$languageTag.bin"))
        personal?.save(zip)
        zip.closeEntry()
    }

    /** forgets everything learned in this language, also on disk */
    fun clearLearned(context: Context) {
        appContext = context.applicationContext
        if (personal == null) loadPersonal(context.applicationContext)
        personal?.clear()
        SAVER.execute { saveNow() }
    }

    /** returns the engine if loaded, otherwise starts loading it in the background and returns null */
    fun engine(context: Context?): RomanTransliterator? {
        engine?.let { return it }
        if (context == null || loading) return null
        loading = true
        val appContext = context.applicationContext
        this.appContext = appContext
        thread(name = "translit-model-load-$languageTag") {
            try {
                if (personal == null) loadPersonal(appContext)
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
        private val SAVER = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "lafz-learn-save").apply { isDaemon = true } }

        /** all learned words in one zip (one entry per language); run on a background thread */
        @JvmStatic
        fun exportLearned(context: Context, out: java.io.OutputStream) {
            ZipOutputStream(out).use { zip -> values().forEach { it.writeLearnedTo(zip, context) } }
        }

        /** merges a zip written by [exportLearned] into what is learned here (counts are added); returns the number of words found, run on a background thread */
        @JvmStatic
        fun importLearned(context: Context, input: java.io.InputStream): Int {
            var found = 0
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val script = values().firstOrNull { "learned_${it.languageTag}.bin" == entry!!.name }
                    if (script != null) {
                        script.appContext = context.applicationContext
                        if (script.personal == null) script.loadPersonal(context.applicationContext)
                        val incoming = PersonalModel.load(java.io.BufferedInputStream(zip))
                        found += incoming.wordCount()
                        script.personal?.merge(incoming)
                        script.scheduleSave()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            return found
        }

        /** after a restore: the files on disk are the truth */
        @JvmStatic
        fun reloadAllLearned(context: Context) { values().forEach { it.reloadLearned(context) } }

        /** writes pending learned words now (keyboard hidden) */
        @JvmStatic
        fun saveAllLearned() { values().forEach { s -> SAVER.execute { s.saveNow() } } }

        @JvmStatic
        fun clearAllLearned(context: Context) { values().forEach { it.clearLearned(context) } }

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
