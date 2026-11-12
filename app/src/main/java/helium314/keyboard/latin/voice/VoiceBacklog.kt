// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

/** Counts queued AND currently decoding audio. Capture stops at six phrases or 40 s of outstanding audio.
 * The last submitted phrase can cross the duration threshold by at most one 20 s segment.
 */
class VoiceBacklog {
    private var phrases = 0
    private var samples = 0L
    @Synchronized fun add(count: Int) { phrases++; samples += count }
    @Synchronized fun remove(count: Int) { phrases--; samples -= count }
    @get:Synchronized val isFull get() = phrases >= 6 || samples >= VoiceProtocol.SAMPLE_RATE * 40L
}
