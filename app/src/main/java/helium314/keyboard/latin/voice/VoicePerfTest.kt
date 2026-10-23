// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import java.io.File

/** The speed test in Settings: loads a model, transcribes the bundled sample clip of its language twice, reports time, memory and error rate. */
object VoicePerfTest {
    fun run(context: Context, info: VoiceModelInfo, log: (String) -> Unit) {
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        log("${info.name} (${info.language}, ${info.sizeBytes shr 20} MB, $threads threads)")
        val samples = (context.assets.list("voice_samples") ?: emptyArray()).filter { it.startsWith(info.language + "_") && it.endsWith(".wav") }
        if (samples.isEmpty()) { log("  no sample clip for '${info.language}' in the app"); return }
        val rssBefore = residentMb()
        val t0 = System.nanoTime()
        val engine = try { SpeechEngine.create(context, info, VoiceModels.dir(context, info), threads) } catch (t: Throwable) { log("  could not load: ${t.message}"); return }
        engine.use {
            log("  loaded in ${ms(t0)} ms, memory ${residentMb() - rssBefore} MB more than before")
            for (name in samples) {
                val audio = readWav(context.assets.open("voice_samples/$name").readBytes())
                val seconds = audio.size.toDouble() / info.sampleRate
                val reference = context.assets.open("voice_samples/${name.removeSuffix(".wav")}.txt").bufferedReader().readText()
                for (run in 1..2) {
                    val t = System.nanoTime()
                    val text = try { engine.transcribe(audio.copyOf()) } catch (e: Throwable) { log("  failed: ${e.message}"); return }
                    val took = (System.nanoTime() - t) / 1e9
                    log("  ${name} ${"%.1f".format(seconds)} s of audio, run $run: ${"%.2f".format(took)} s (x${"%.2f".format(took / seconds)} real time), " +
                        "peak memory ${residentMb() - rssBefore} MB, error ${"%.0f".format(100 * characterErrorRate(reference, text))} %")
                    if (run == 2) log("  heard: $text")
                }
            }
        }
        log("  memory after unloading: ${residentMb() - rssBefore} MB more than before")
    }

    private fun ms(since: Long) = (System.nanoTime() - since) / 1_000_000

    private fun residentMb(): Long = try {
        File("/proc/self/status").readLines().firstOrNull { it.startsWith("VmRSS:") }?.filter { it.isDigit() }?.toLong()?.div(1024) ?: 0L
    } catch (e: Exception) { 0L }

    /** 16-bit PCM mono WAV to floats in -1..1 */
    fun readWav(bytes: ByteArray): FloatArray {
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = (bytes[pos + 4].toInt() and 255) or ((bytes[pos + 5].toInt() and 255) shl 8) or ((bytes[pos + 6].toInt() and 255) shl 16) or ((bytes[pos + 7].toInt() and 255) shl 24)
            if (id == "data") {
                val n = minOf(size, bytes.size - pos - 8) / 2
                return FloatArray(n) { i -> ((bytes[pos + 8 + 2 * i].toInt() and 255) or (bytes[pos + 9 + 2 * i].toInt() shl 8)).toShort() / 32768f }
            }
            pos += 8 + size + (size and 1)
        }
        return FloatArray(0)
    }

    private fun clean(s: String) = s.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.replace(Regex(" +"), " ").trim()

    /** character error rate: edit distance over the length of the reference, punctuation and case ignored (approximate, not the benchmark normalisation) */
    fun characterErrorRate(reference: String, hypothesis: String): Double {
        val a = clean(reference); val b = clean(hypothesis)
        if (a.isEmpty()) return if (b.isEmpty()) 0.0 else 1.0
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length].toDouble() / a.length
    }
}
