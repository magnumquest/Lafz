// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gesture

import android.content.Context
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.utils.Log
import org.futo.ml.inference.SwipeDecoder
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Swipe recognition with the FUTO Swipe models (https://swipe.futo.tech, models under the FUTO Model
 * Weights License 1.0, library GPL-3.0-or-later). Runs fully offline; the models live in assets/futo_swipe.
 *
 * English uses encoder + English decoder + context language model. Roman Urdu/Arabic use the
 * layout-agnostic encoder alone, matched against the English words plus the language's Roman spellings.
 * Everything is called from the suggestion worker thread; the engines are not thread safe, hence the lock.
 */
object FutoSwipe {
    private const val TAG = "FutoSwipe"
    private const val ASSET_DIR = "futo_swipe"
    private const val LETTERS = "abcdefghijklmnopqrstuvwxyz"
    private const val RETRY_DELAY_MS = 3000L
    private const val MAX_RUNTIME_ERRORS = 3
    private val wordRegex = Regex("[a-z']+")
    /** Models and word lists are copied/rebuilt after every app update. */
    private val assetVersion get() = helium314.keyboard.latin.BuildConfig.VERSION_CODE.toString()

    /** Dictionary weights for [English words, language spellings]; tuned on 50 Roman Urdu swipes (40/50 vs 38/50 unweighted). */
    private val ROMAN_WEIGHTS = floatArrayOf(0.5f, 1.0f)

    // Heavy work (copying models, building word lists, loading the native engines) only ever runs on this thread,
    // never on the keyboard's suggestion thread while a swipe is being decoded.
    private val loader = Executors.newSingleThreadExecutor { Thread(it, "FutoSwipeLoader") }
    // Two locks, so threads that only ask "is it loaded?" (keyboard start, settings) never wait for a decode:
    // [stateLock] is only ever held briefly; [lock] is held by recognize() while decoding and by the loader when publishing.
    private val stateLock = Any()
    @Volatile var failed = false
        private set
    @Volatile private var readyLanguages: Set<String> = emptySet() // copy on write, guarded by stateLock for writers
    private val loading = HashSet<String>()      // stateLock
    private val retryAt = HashMap<String, Long>() // stateLock
    private val lock = Any() // guards the engines, word lists and layout state below
    private var runtimeErrors = 0
    private var dir: File? = null
    private var english: SwipeDecoder? = null
    private var roman: SwipeDecoder? = null
    private val tries = HashMap<String, Long>()
    private var englishLayout = 0L
    private var romanLayout = 0L
    private var romanKey: String? = null
    private var apostrophes: Map<String, Apostrophe> = emptyMap()

    /** Words the decoder may return, with the context needed to rank them. */
    class Request(val language: String, val previousWord: String?)

    fun isReady(language: String) = language in readyLanguages

    /** One line about the engine for the settings screen and the diagnostics report. */
    fun status(): String = synchronized(stateLock) {
        "FUTO Swipe: " + if (failed) "FAILED (using the built-in matcher)" else "ready=$readyLanguages loading=$loading"
    }

    /**
     * Loads everything needed to decode swipes for [language] ("en", "urdu", "arabic") in the background; a no-op once
     * loaded or while loading. [englishWords] and [spellings] are only called on the loader thread.
     */
    fun preload(context: Context, language: String, englishWords: () -> Map<String, Int>, spellings: () -> Map<String, Int>) {
        val app = context.applicationContext
        if (failed || language in readyLanguages) return // the common case on every app switch: no lock at all
        synchronized(stateLock) {
            if (failed || language in readyLanguages || language in loading) return
            if (System.currentTimeMillis() < (retryAt[language] ?: 0L)) return
            loading.add(language)
        }
        SwipeDiagnostics.init(app)
        loader.execute {
            val start = System.nanoTime()
            try {
                val files = prepare(app)
                val enTrie = synchronized(lock) { tries["en"] } ?: loadTrie(files, "en", englishWords)
                if (synchronized(lock) { apostrophes.isEmpty() }) {
                    val forms = readApostrophes(File(files, "tries/en.combined"))
                    synchronized(lock) { apostrophes = forms }
                }
                val languageTrie = if (language == "en") 0L
                    else synchronized(lock) { tries[language] } ?: loadTrie(files, language, spellings)
                val engine = if (language == "en") synchronized(lock) { english } ?: englishEngine(files)
                    else synchronized(lock) { roman } ?: romanEngine(files)
                synchronized(lock) {
                    tries["en"] = enTrie
                    if (language != "en") tries[language] = languageTrie
                    if (language == "en") english = engine else roman = engine
                }
                synchronized(stateLock) { readyLanguages = readyLanguages + language }
                SwipeDiagnostics.log("loaded $language in ${(System.nanoTime() - start) / 1000000} ms, ${status()}")
            } catch (e: NoWordsException) {
                SwipeDiagnostics.log("not loaded yet ($language): ${e.message}, retrying later")
                synchronized(stateLock) { retryAt[language] = System.currentTimeMillis() + RETRY_DELAY_MS }
            } catch (e: Throwable) {
                SwipeDiagnostics.logError("loading $language failed, falling back to the built-in matcher", e)
                synchronized(stateLock) { failed = true }
            } finally {
                synchronized(stateLock) { loading.remove(language) }
            }
        }
    }

    /**
     * Swipe typing was switched off: free the native engines, word lists and decoder state so the plain keyboard carries
     * nothing from swipe. Runs on the loader thread (after any load still in progress). A cheap no-op when nothing is loaded,
     * which is the normal case on every keyboard start.
     */
    fun release() {
        synchronized(stateLock) {
            if (readyLanguages.isEmpty() && loading.isEmpty()) return
        }
        loader.execute {
            synchronized(lock) {
                try {
                    english?.close()
                    roman?.close()
                    tries.values.forEach { SwipeDecoder.nativeFreeTrie(it) }
                } catch (e: Throwable) {
                    SwipeDiagnostics.logError("releasing the swipe engines failed", e)
                }
                english = null
                roman = null
                tries.clear()
                englishLayout = 0L
                romanLayout = 0L
                romanKey = null
                apostrophes = emptyMap()
                synchronized(stateLock) { readyLanguages = emptySet() }
            }
            SwipeDiagnostics.log("swipe typing off: engines released")
        }
    }

    /** Best candidates for the swipe, or null when this engine cannot be used for it right now (not loaded, failed). */
    fun recognize(keyboard: Keyboard, pointers: InputPointers, request: Request, limit: Int): List<String>? {
        val waitStart = System.nanoTime()
        synchronized(lock) {
            val start = System.nanoTime()
            if (failed || request.language !in readyLanguages || pointers.pointerSize < 2) return null
            if (helium314.keyboard.latin.BuildConfig.DEBUG) { // test aid: pretend to be a slow phone
                dir?.let { File(it.parentFile, "swipe_delay_ms") }?.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
                    ?.let { Thread.sleep(it) }
            }
            try {
                val layout = Layout.of(keyboard) ?: return null
                val isEnglish = request.language == "en"
                val engine = (if (isEnglish) english else roman) ?: return null
                val trieKey = request.language
                val needsMode = if (isEnglish) englishLayout != layout.signature
                    else romanLayout != layout.signature || romanKey != trieKey
                if (needsMode) {
                    val enTrie = tries["en"] ?: return null
                    val dictionaries = if (isEnglish) longArrayOf(enTrie) else longArrayOf(enTrie, tries[trieKey] ?: return null)
                    if (!engine.setMode(LETTERS, layout.cx, layout.cy, dictionaries)) {
                        SwipeDiagnostics.log("setMode failed for $trieKey")
                        return null
                    }
                    if (isEnglish) englishLayout = layout.signature else {
                        val previous = romanKey
                        romanLayout = layout.signature
                        romanKey = trieKey
                        // the engine no longer references the other language's word list, free its memory
                        if (previous != null && previous != trieKey) tries.remove(previous)?.let {
                            SwipeDecoder.nativeFreeTrie(it)
                            synchronized(stateLock) { readyLanguages = readyLanguages - previous }
                        }
                    }
                }
                engine.setContext(listOfNotNull(request.previousWord))
                val (x, y, t) = layout.normalize(pointers)
                val weights = if (isEnglish) null else ROMAN_WEIGHTS
                val decoded = engine.recognize(x, y, t, topK = limit, trieWeights = weights).map { it.word }
                val words = if (isEnglish) decoded.flatMap { spellings(it) }.distinct() else decoded
                runtimeErrors = 0
                val runMs = (System.nanoTime() - start) / 1000000
                val waitMs = (start - waitStart) / 1000000
                if (runMs > 120 || waitMs > 120) SwipeDiagnostics.log("slow recognize ${request.language}: wait=${waitMs}ms run=${runMs}ms points=${pointers.pointerSize}")
                return words
            } catch (e: Throwable) {
                SwipeDiagnostics.logError("recognize failed (${++runtimeErrors} in a row)", e)
                if (runtimeErrors >= MAX_RUNTIME_ERRORS) synchronized(stateLock) { failed = true }
                return null
            }
        }
    }

    private class NoWordsException(message: String) : Exception(message)

    /** The decoder's word list is letters only; this remembers the apostrophe spelling of a word ("dont" -> "don't"). */
    private class Apostrophe(val form: String, val frequency: Int, val plainFrequency: Int)

    private fun readApostrophes(file: File): Map<String, Apostrophe> {
        fun parse(line: String): Pair<String, Int>? {
            if (!line.startsWith("word=")) return null
            val comma = line.indexOf(',')
            val f = line.indexOf(",f=")
            if (comma < 0 || f < 0) return null
            return line.substring(5, comma) to (line.substring(f + 3).toIntOrNull() ?: return null)
        }
        val forms = HashMap<String, Pair<String, Int>>()
        file.forEachLine { line ->
            val (word, frequency) = parse(line) ?: return@forEachLine
            if (!word.contains('\'')) return@forEachLine
            val alpha = word.replace("'", "")
            if (alpha.length >= 2 && frequency > (forms[alpha]?.second ?: 0)) forms[alpha] = word to frequency
        }
        val plain = HashMap<String, Int>()
        file.forEachLine { line ->
            val (word, frequency) = parse(line) ?: return@forEachLine
            if (!word.contains('\'') && forms.containsKey(word)) plain[word] = frequency
        }
        return forms.mapValues { Apostrophe(it.value.first, it.value.second, plain[it.key] ?: 0) }
    }

    /** Real spellings for a decoded English word, best first: "dont" -> "don't", "its" -> "its", "it's", "i" -> "I". */
    private fun spellings(word: String): List<String> {
        if (word == "i") return listOf("I")
        val a = apostrophes[word] ?: return listOf(word)
        val form = if (a.form.startsWith("i'")) "I" + a.form.substring(1) else a.form
        return when {
            a.plainFrequency == 0 -> listOf(form)
            a.frequency >= a.plainFrequency -> listOf(form, word)
            else -> listOf(word, form)
        }
    }

    private fun prepare(context: Context): File {
        synchronized(lock) { dir }?.let { return it }
        val target = File(context.filesDir, ASSET_DIR)
        val stamp = File(target, ".version")
        if (!stamp.isFile || stamp.readText() != assetVersion) {
            target.deleteRecursively()
            copyAssets(context, ASSET_DIR, target)
            File(target, "tries").mkdirs()
            stamp.writeText(assetVersion)
        }
        synchronized(lock) { dir = target }
        return target
    }

    private fun copyAssets(context: Context, assetPath: String, to: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            to.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input -> to.outputStream().use { input.copyTo(it) } }
        } else {
            to.mkdirs()
            children.forEach { copyAssets(context, "$assetPath/$it", File(to, it)) }
        }
    }

    private fun englishEngine(files: File): SwipeDecoder = SwipeDecoder(
        File(files, "honorable_sturgeon/model_fp32.pte").path,
        File(files, "magic_macaw/model_fp32.pte").path,
        lmModelPath = File(files, "hungry_jellyfish/context_lm.pte").path,
        lmVocabPath = File(files, "hungry_jellyfish/vocab.txt").path,
    )

    private fun romanEngine(files: File): SwipeDecoder = SwipeDecoder(File(files, "honorable_sturgeon/model_fp32.pte").path)

    /** Loads (building the word list file on first use) the dictionary for [key]; only called on the loader thread. */
    private fun loadTrie(files: File, key: String, words: () -> Map<String, Int>): Long {
        val file = File(files, "tries/$key.combined")
        if (!file.isFile || file.length() == 0L) {
            val list = words()
            if (list.isEmpty()) throw NoWordsException("no word list for $key")
            val tmp = File(file.path + ".tmp")
            tmp.bufferedWriter().use { out ->
                for ((word, frequency) in list) {
                    val w = word.lowercase(Locale.ROOT)
                    if (w.length !in 2..24 || !wordRegex.matches(w)) continue
                    out.write("word=$w,f=${frequency.coerceIn(1, 255)}\n")
                }
            }
            tmp.renameTo(file)
        }
        val handle = SwipeDecoder.nativeLoadTrie(file.path)
        check(handle != 0L) { "could not load dictionary $key" }
        return handle
    }

    /** Letter key centres and the letter-key bounding box, which is what the models treat as the unit square. */
    private class Layout(val cx: FloatArray, val cy: FloatArray, val left: Float, val top: Float,
                         val width: Float, val height: Float) {
        val signature: Long = run {
            var hash = 1469598103934665603L
            for (value in floatArrayOf(left, top, width, height) + cx + cy) {
                hash = (hash xor value.toRawBits().toLong()) * 1099511628211L
            }
            if (hash == 0L) 1L else hash
        }

        fun normalize(pointers: InputPointers): Triple<FloatArray, FloatArray, FloatArray> {
            val size = pointers.pointerSize
            val xs = pointers.xCoordinates
            val ys = pointers.yCoordinates
            val times = pointers.times
            val x = FloatArray(size) { (xs[it] - left) / width }
            val y = FloatArray(size) { (ys[it] - top) / height }
            val t = FloatArray(size)
            val start = times[0]
            for (i in 0 until size) t[i] = (times[i] - start).toFloat()
            // timestamps are required to increase; synthesise 60 Hz ones if the input has none
            if (t[size - 1] <= 0f) for (i in 0 until size) t[i] = i * 16f
            return Triple(x, y, t)
        }

        companion object {
            fun of(keyboard: Keyboard): Layout? {
                val cx = FloatArray(26)
                val cy = FloatArray(26)
                val seen = BooleanArray(26)
                var left = Float.MAX_VALUE
                var top = Float.MAX_VALUE
                var right = 0f
                var bottom = 0f
                for (key in keyboard.sortedKeys) {
                    val code = Character.toLowerCase(key.code)
                    if (code < 'a'.code || code > 'z'.code) continue
                    val index = code - 'a'.code
                    if (seen[index]) continue
                    seen[index] = true
                    cx[index] = key.x + key.width / 2f
                    cy[index] = key.y + key.height / 2f
                    left = minOf(left, key.x.toFloat())
                    top = minOf(top, key.y.toFloat())
                    right = maxOf(right, (key.x + key.width).toFloat())
                    bottom = maxOf(bottom, (key.y + key.height).toFloat())
                }
                if (seen.any { !it } || right <= left || bottom <= top) return null
                val width = right - left
                val height = bottom - top
                for (i in 0 until 26) {
                    cx[i] = (cx[i] - left) / width
                    cy[i] = (cy[i] - top) / height
                }
                return Layout(cx, cy, left, top, width, height)
            }
        }
    }
}
