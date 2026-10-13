// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArabicTransliteratorTest {
    private val engine = RomanTransliterator.load(File("src/main/assets/arabic/arabic_translit.bin").inputStream())

    private fun sentence(roman: String): String {
        var prev: String? = null
        return roman.split(" ").joinToString(" ") { w ->
            val best = engine.suggest(w, prev, prev == null, 1).first().text
            prev = best
            best
        }
    }

    @Test fun scriptureWords() {
        assertEquals("بسم الله الرحمن الرحيم", sentence("bismillah alrahman alrahim"))
        assertEquals("قل هو الله أحد", sentence("qul huwa allahu ahad"))
    }

    @Test fun arabiziDigitsAreLetters() {
        assertTrue(engine.suggest("3ala", null, true, 5).any { it.text == "على" })
        assertTrue(engine.suggest("7abibi", null, true, 5).any { it.text == "حبيبي" })
    }

    @Test fun everyCandidateIsADictionaryEntry() {
        for (w in listOf("rasul", "qalb", "salat", "zzzz"))
            engine.suggest(w, null, true, 6).forEach { assertTrue(engine.isKnownWord(it.text), "$w -> ${it.text}") }
    }
}
