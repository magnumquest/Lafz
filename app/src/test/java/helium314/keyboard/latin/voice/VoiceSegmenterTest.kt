// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceSegmenterTest {
    private val quiet = ShortArray(320)
    private val speech = ShortArray(320) { if (it % 2 == 0) 1800 else -1800 }

    @Test fun phrasesArriveBeforeTheSessionEnds() {
        val s = VoiceSegmenter()
        repeat(10) { assertNull(s.add(quiet, quiet.size)) }
        repeat(50) { assertNull(s.add(speech, speech.size)) }
        repeat(32) { assertNull(s.add(quiet, quiet.size)) }
        val first = assertNotNull(s.add(quiet, quiet.size))
        assertEquals((10 + 50 + 33) * 320, first.size)
        assertFalse(s.timedOut)
        repeat(50) { assertNull(s.add(speech, speech.size)) }
        assertEquals(16000, assertNotNull(s.finish()).size)
        assertNull(s.finish()) // no duplicated tail after a manual stop
    }

    @Test fun quietSpeechStillReachesTheModel() {
        val s = VoiceSegmenter()
        val soft = ShortArray(320) { if (it % 8 == 0) 160 else 20 }
        repeat(50) { s.add(soft, soft.size) }
        assertNotNull(s.finish())
    }

    @Test fun silenceAndClicksDoNotReachTheModel() {
        val s = VoiceSegmenter()
        repeat(399) { assertNull(s.add(quiet, quiet.size)) }
        assertFalse(s.timedOut)
        assertNull(s.add(quiet, quiet.size))
        assertTrue(s.timedOut)
        assertNull(s.finish())
        s.add(speech, speech.size) // a 20 ms click is not a phrase
        assertNull(s.finish())
    }

    @Test fun shortHesitationsKeepWordsTogether() {
        val s = VoiceSegmenter()
        repeat(30) { assertNull(s.add(speech, speech.size)) }
        repeat(20) { assertNull(s.add(quiet, quiet.size)) }
        repeat(30) { assertNull(s.add(speech, speech.size)) }
        assertEquals(80 * 320, assertNotNull(s.finish()).size)
    }

    @Test fun continuousSpeechHasBoundedMemoryAndNoMissingSamples() {
        val s = VoiceSegmenter()
        var total = 0
        repeat(2500) { s.add(speech, speech.size)?.let { total += it.size } }
        total += s.finish()?.size ?: 0
        assertEquals(2500 * speech.size, total)
    }

    @Test fun partialFramesAtTheHardBoundaryPreserveEverySample() {
        val s = VoiceSegmenter()
        val expected = ShortArray(VoiceProtocol.SAMPLE_RATE * 41 + 160) { (1800 + it % 200).toShort() }
        val actual = ArrayList<Short>()
        var offset = 0
        while (offset < expected.size) {
            val count = minOf(if (offset == 0) 160 else 320, expected.size - offset)
            val frame = expected.copyOfRange(offset, offset + count)
            s.add(frame, count)?.let { actual.addAll(it.toList()) }
            offset += count
        }
        s.finish()?.let { actual.addAll(it.toList()) }
        assertEquals(expected.toList(), actual)
    }

    @Test fun shortTailOfContinuousSpeechSurvivesTheHardSplit() {
        val s = VoiceSegmenter()
        repeat(1000) { s.add(speech, speech.size) }
        s.add(speech, 160)
        assertEquals(160, assertNotNull(s.finish()).size)
    }

    @Test fun quietBeforeSpeechIsBoundedAndSpeechResetsTimeout() {
        val s = VoiceSegmenter()
        repeat(300) { s.add(quiet, quiet.size) }
        repeat(25) { s.add(speech, speech.size) }
        assertFalse(s.timedOut)
        assertEquals(35 * 320, assertNotNull(s.finish()).size)
    }
}
