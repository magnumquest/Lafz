// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gesture

import android.content.Context
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.utils.Log
import org.futo.ml.inference.SwipeDecoder
import java.io.File
import java.util.Locale

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
    private const val ASSET_VERSION = "1"
    private const val LETTERS = "abcdefghijklmnopqrstuvwxyz"
    private val wordRegex = Regex("[a-z']+")

    private val lock = Any()
    private var failed = false
    private var dir: File? = null
    private var english: SwipeDecoder? = null
    private var roman: SwipeDecoder? = null
    private val tries = HashMap<String, Long>()
    private var englishLayout = 0L
    private var romanLayout = 0L
    private var romanKey: String? = null

    /** Words the decoder may return, with the context needed to rank them. */
    class Request(val language: String, val spellings: () -> Map<String, Int>, val previousWord: String?)

    /** True once the native library could not be loaded or the models are missing; callers should fall back. */
    val unavailable get() = failed

    /** Best candidates for the swipe, or null when this engine cannot be used. */
    fun recognize(context: Context, keyboard: Keyboard, pointers: InputPointers, request: Request,
                  englishWords: () -> Map<String, Int>, limit: Int): List<String>? = synchronized(lock) {
        if (failed || pointers.pointerSize < 2) return null
        try {
            val layout = Layout.of(keyboard) ?: return null
            val files = prepare(context)
            val isEnglish = request.language == "en"
            val engine = if (isEnglish) englishEngine(files) else romanEngine(files)
            val enTrie = trie(files, "en", englishWords)
            val tries = if (isEnglish) longArrayOf(enTrie)
                else longArrayOf(enTrie, trie(files, request.language, request.spellings))
            val signature = layout.signature
            val trieKey = if (isEnglish) "en" else request.language
            val needsMode = if (isEnglish) englishLayout != signature else romanLayout != signature || romanKey != trieKey
            if (needsMode) {
                if (!engine.setMode(LETTERS, layout.cx, layout.cy, tries)) {
                    Log.e(TAG, "setMode failed")
                    return null
                }
                if (isEnglish) englishLayout = signature else { romanLayout = signature; romanKey = trieKey }
            }
            engine.setContext(listOfNotNull(request.previousWord))
            val (x, y, t) = layout.normalize(pointers)
            engine.recognize(x, y, t, topK = limit).map { it.word }
        } catch (e: Throwable) {
            Log.e(TAG, "FUTO swipe unavailable", e)
            failed = true
            null
        }
    }

    private fun prepare(context: Context): File {
        dir?.let { return it }
        val target = File(context.filesDir, ASSET_DIR)
        val stamp = File(target, ".version")
        if (!stamp.isFile || stamp.readText() != ASSET_VERSION) {
            target.deleteRecursively()
            copyAssets(context, ASSET_DIR, target)
            File(target, "tries").mkdirs()
            stamp.writeText(ASSET_VERSION)
        }
        dir = target
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

    private fun englishEngine(files: File): SwipeDecoder = english ?: SwipeDecoder(
        File(files, "honorable_sturgeon/model_fp32.pte").path,
        File(files, "magic_macaw/model_fp32.pte").path,
        lmModelPath = File(files, "hungry_jellyfish/context_lm.pte").path,
        lmVocabPath = File(files, "hungry_jellyfish/vocab.txt").path,
    ).also { english = it }

    private fun romanEngine(files: File): SwipeDecoder = roman ?: SwipeDecoder(
        File(files, "honorable_sturgeon/model_fp32.pte").path
    ).also { roman = it }

    /** Loads (building the word list file on first use) the dictionary for [key]. */
    private fun trie(files: File, key: String, words: () -> Map<String, Int>): Long = tries.getOrPut(key) {
        val file = File(files, "tries/$key.combined")
        if (!file.isFile || file.length() == 0L) {
            val tmp = File(file.path + ".tmp")
            tmp.bufferedWriter().use { out ->
                for ((word, frequency) in words()) {
                    val w = word.lowercase(Locale.ROOT)
                    if (w.length !in 2..24 || !wordRegex.matches(w)) continue
                    out.write("word=$w,f=${frequency.coerceIn(1, 255)}\n")
                }
            }
            tmp.renameTo(file)
        }
        val handle = SwipeDecoder.nativeLoadTrie(file.path)
        check(handle != 0L) { "could not load dictionary $key" }
        handle
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
