// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu

import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneticFallbackTest {

    @Test fun urduBasics() {
        assertEquals("کی", PhoneticFallback.toUrdu("ki"))
        assertEquals("کیا", PhoneticFallback.toUrdu("kiya"))
        assertEquals("شب", PhoneticFallback.toUrdu("shab"))
        assertEquals("کتاب", PhoneticFallback.toUrdu("kitab"))
        assertEquals("یار", PhoneticFallback.toUrdu("yaar"))
    }

    @Test fun arabicBasics() {
        assertEquals("مع", PhoneticFallback.toArabic("ma'a"))
        assertEquals("كتاب", PhoneticFallback.toArabic("kitaab"))
    }

    @Test fun arabiziDigitsAreLettersNextToLetters() {
        assertEquals("مع", PhoneticFallback.toArabic("ma3a")) // same as ma'a
        assertEquals("علم", PhoneticFallback.toArabic("3ilm")) // a vowel after a leading digit is not word-initial
        assertEquals("حب", PhoneticFallback.toArabic("7ubb"))
        assertEquals("غبر", PhoneticFallback.toArabic("8abar"))
    }

    @Test fun loneDigitsStayDigits() {
        assertEquals("7", PhoneticFallback.toArabic("7"))
        assertEquals("2 3", PhoneticFallback.toArabic("2 3"))
        assertEquals("7", PhoneticFallback.toUrdu("7"))
    }

    @Test fun emptyAndNonLetters() {
        assertEquals("", PhoneticFallback.toUrdu(""))
        assertEquals("", PhoneticFallback.toArabic(""))
        assertEquals("3", PhoneticFallback.toUrdu("3"))
        assertEquals("3", PhoneticFallback.toArabic("3"))
    }
}
