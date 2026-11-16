// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.InputStyle
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.Suggest
import helium314.keyboard.latin.suggestions.SuggestedWords
import java.io.File

/**
 * Debug builds only: measures suggestion quality with the real engine on a device / emulator.
 * Push `files/sugg_eval_in.tsv` (lines: typed TAB expected TAB previous word, empty = no context) and open the keyboard;
 * each line is typed as taps on the key centres of the current layout and the result is written to
 * `files/sugg_eval_out.tsv`: typed, expected, auto-correct flag, the strip words in order (typed word first).
 * The input file is deleted when the run starts. Scoring: tools/eval/score_suggestions.py.
 */
object SuggestionEval {
    @Volatile private var running = false

    /** [executor] must run the work on the thread that computes suggestions while typing: the native dictionary
     *  session is not safe for concurrent use (a second thread made a lookup spin forever) */
    fun maybeRun(context: Context, suggest: Suggest, executor: java.util.concurrent.Executor) {
        if (!BuildConfig.DEBUG || running) return
        val input = File(context.filesDir, "sugg_eval_in.tsv")
        if (!input.exists()) return
        running = true
        val lines = input.readLines()
        input.delete()
        executor.execute {
            try {
                run(context, suggest, lines)
            } catch (e: Exception) {
                Log.e("SuggestionEval", "failed", e)
            } finally {
                running = false
            }
        }
    }

    private fun run(context: Context, suggest: Suggest, lines: List<String>) {
        Thread.sleep(1500) // the input view starts loading the field's settings (auto-correction) after this hook
        var keyboard = KeyboardSwitcher.getInstance().keyboard
        for (i in 0 until 50) { if (keyboard != null) break; Thread.sleep(100); keyboard = KeyboardSwitcher.getInstance().keyboard }
        keyboard ?: return
        val out = File(context.filesDir, "sugg_eval_out.tmp")
        val start = System.nanoTime()
        val worker = Thread.currentThread()
        val lineStart = java.util.concurrent.atomic.AtomicLong(System.nanoTime())
        val watchdog = Thread {
            try {
                while (true) {
                    Thread.sleep(2000)
                    if (System.nanoTime() - lineStart.get() > 5_000_000_000L)
                        Log.w("SuggestionEval", "slow line, worker at:\n" + worker.stackTrace.joinToString("\n"))
                }
            } catch (_: InterruptedException) { }
        }.apply { isDaemon = true; start() }
        out.bufferedWriter().use { w ->
            for (line in lines) {
                lineStart.set(System.nanoTime())
                val p = line.split('\t')
                if (p.size < 2 || p[0].isEmpty()) continue
                val prev = p.getOrNull(2).orEmpty()
                val ngram = if (prev.isEmpty()) NgramContext.EMPTY_PREV_WORDS_INFO
                    else NgramContext(NgramContext.WordInfo(prev))
                val composer = WordComposer()
                val codePoints = StringUtils.toCodePointArray(p[0])
                composer.setComposingWord(codePoints, keyboard.getCoordinates(codePoints))
                val sv = Settings.getValues()
                val words: SuggestedWords = suggest.getSuggestedWords(composer, null, ngram, keyboard,
                    sv.mSettingsValuesForSuggestion, sv.mAutoCorrectEnabled, InputStyle.TYPING, 0)
                val shown = (0 until words.size).take(8).joinToString("|") { words.getWord(it).replace('|', '/') }
                w.write("${p[0]}\t${p[1]}\t${if (words.willAutoCorrect) 1 else 0}\t$shown\n")
            }
        }
        watchdog.interrupt()
        out.renameTo(File(context.filesDir, "sugg_eval_out.tsv"))
        Log.i("SuggestionEval", "autocorrect=${Settings.getValues().mAutoCorrectEnabled} locale=${Settings.getValues().mLocale}")
        Log.i("SuggestionEval", "done: ${lines.size} lines in ${(System.nanoTime() - start) / 1_000_000} ms")
    }
}
