// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import kotlin.math.sqrt

/** Pause-based endpointing for the existing offline (non-streaming) models. Feed 20 ms frames.
 * Keeps a little pre-roll and trailing silence so consonants at phrase boundaries survive.
 */
class VoiceSegmenter(private val sampleRate: Int = VoiceProtocol.SAMPLE_RATE) {
    private val buffer = ShortArray(sampleRate * 20)
    private var size = 0
    private var speech = 0
    private var quiet = 0
    private var idle = 0
    private var noiseFloor = 8.0
    private var continuation = false
    val timedOut get() = idle >= sampleRate * 8

    fun add(frame: ShortArray, count: Int): ShortArray? {
        require(count in 1..minOf(frame.size, sampleRate / 50))
        var energy = 0.0
        for (i in 0 until count) energy += frame[i].toDouble() * frame[i]
        val rms = sqrt(energy / count)
        val voiced = rms > maxOf(20.0, noiseFloor * 3.0)
        if (!voiced && speech == 0) noiseFloor = (noiseFloor * 0.98 + rms * 0.02).coerceAtMost(600.0)
        var offset = 0
        var result: ShortArray? = null
        while (offset < count) {
            val part = minOf(count - offset, buffer.size - size)
            if (voiced) { speech += part; quiet = 0; idle = 0 }
            else { quiet += part; idle += part }
            frame.copyInto(buffer, size, offset, offset + part)
            size += part
            offset += part
            if (speech == 0) {
                // Keep 200 ms before the first voiced frame without accumulating silence.
                val keep = minOf(size, sampleRate / 5)
                buffer.copyInto(buffer, 0, size - keep, size)
                size = keep
                if (quiet >= sampleRate * 65 / 100) continuation = false
            } else if (size == buffer.size) {
                result = finish()
                // A short final tail of continuous speech belongs to the phrase just split.
                continuation = voiced
            }
        }
        if (result != null) return result
        if ((quiet >= sampleRate * 65 / 100 && size >= sampleRate)
            || (size >= sampleRate * 12 && quiet >= sampleRate * 24 / 100)) return finish()
        return null
    }

    fun finish(): ShortArray? {
        val result = if (speech >= sampleRate * 12 / 100 || (continuation && speech > 0)) buffer.copyOf(size) else null
        size = 0; speech = 0; quiet = 0; continuation = false
        return result
    }
}
