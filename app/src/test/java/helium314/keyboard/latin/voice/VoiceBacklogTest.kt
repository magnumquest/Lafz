// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceBacklogTest {
    @Test fun longPhrasesHitTheAudioDurationLimitBeforeThePhraseLimit() {
        val backlog = VoiceBacklog()
        backlog.add(20 * VoiceProtocol.SAMPLE_RATE)
        assertFalse(backlog.isFull)
        backlog.add(20 * VoiceProtocol.SAMPLE_RATE)
        assertTrue(backlog.isFull)
        backlog.remove(20 * VoiceProtocol.SAMPLE_RATE)
        assertFalse(backlog.isFull)
    }

    @Test fun manyShortPhrasesHitThePhraseLimit() {
        val backlog = VoiceBacklog()
        repeat(5) { backlog.add(VoiceProtocol.SAMPLE_RATE) }
        assertFalse(backlog.isFull)
        backlog.add(VoiceProtocol.SAMPLE_RATE)
        assertTrue(backlog.isFull)
        backlog.remove(VoiceProtocol.SAMPLE_RATE)
        assertFalse(backlog.isFull)
    }
}
