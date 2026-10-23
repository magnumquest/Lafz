// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import java.io.File

/** Helpers for [VoiceBenchmark]: memory reading, WAV parsing and a simple error rate. */
object VoicePerfTest {
    fun residentMb(): Long = try {
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
