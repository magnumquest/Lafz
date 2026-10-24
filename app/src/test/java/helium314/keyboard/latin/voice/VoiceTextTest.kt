// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceTextTest {
    @Test fun noiseMarkersAreNotText() {
        assertEquals("", VoiceText.clean(" [BLANK_AUDIO] "))
        assertEquals("", VoiceText.clean("(music)"))
        assertEquals("", VoiceText.clean("   "))
        assertEquals("Hello world.", VoiceText.clean("  Hello   world. "))
        assertEquals("کل آؤں گا", VoiceText.clean("کل آؤں گا"))
    }

    @Test fun modelLanguage() {
        assertEquals("ur", VoiceText.modelLanguage(Locale.forLanguageTag("ur-Latn")))
        assertEquals("ar", VoiceText.modelLanguage(Locale.forLanguageTag("ar-Latn")))
        assertEquals("en", VoiceText.modelLanguage(Locale.US))
        assertNull(VoiceText.modelLanguage(Locale.GERMAN))
    }

    @Test fun leadingSpace() {
        assertEquals("hi", VoiceText.withLeadingSpace("hi", 0))
        assertEquals("hi", VoiceText.withLeadingSpace("hi", ' '.code))
        assertEquals("hi", VoiceText.withLeadingSpace("hi", '\n'.code))
        assertEquals(" hi", VoiceText.withLeadingSpace("hi", 'a'.code))
        assertEquals(" hi", VoiceText.withLeadingSpace("hi", '.'.code))
    }

    @Test fun silence() {
        assertTrue(VoiceText.isSilent(ShortArray(100)))
        assertFalse(VoiceText.isSilent(ShortArray(100) { if (it == 5) 3000 else 0 }))
        assertEquals(0.5f, VoiceText.toFloat(shortArrayOf(16384))[0])
    }
}
