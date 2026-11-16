// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RomanTransliteratorTest {
    private val engine = RomanTransliterator.load(File("src/main/assets/urdu/urdu_translit.bin").inputStream())

    private fun sentence(roman: String): String {
        var prev: String? = null
        return roman.split(" ").joinToString(" ") { w ->
            val best = engine.suggest(w, prev, prev == null, 1).first().text
            prev = best
            best
        }
    }

    @Test fun commonSentences() {
        assertEquals("آپ کیسے ہیں", sentence("aap kaise hain"))
        assertEquals("تم کہاں ہو", sentence("tum kahan ho"))
        assertEquals("میں گھر جا رہا ہوں", sentence("main ghar ja raha hun"))
        assertEquals("کیا ہے", sentence("kya hai"))
    }

    @Test fun completionsWhileTyping() {
        val c = engine.suggest("ka", null, true, 5)
        assertTrue(c.isNotEmpty())
        assertTrue(engine.suggest("kal", "میں", false, 5).any { it.text == "کل" })
    }

    @Test fun nextWordPrediction() {
        assertTrue(engine.nextWords("میں", false, 5).isNotEmpty())
    }

    @Test fun futureTenseVerbsAreDictionaryPhrases() {
        assertEquals("میں کل آؤں گا", sentence("mein kal aonga"))
        assertEquals("جاؤں گا", sentence("jaunga"))
        assertTrue(engine.suggest("aonga", null, true, 5).any { it.text == "آؤں گی" })
    }

    @Test fun englishLoanwordsGetUrduSpelling() {
        assertEquals("کمپیوٹر", sentence("computer"))
        assertEquals("میسج", sentence("message"))
        assertEquals("لیپ ٹاپ", sentence("laptop"))
        assertEquals("ڈاکٹر", sentence("doctor"))
    }

    @Test fun outOfDictionaryWordsAreOnlySoundSpellings() {
        // owner (2026-11-17): words the dictionary does not have are spelled by sound; such candidates are marked
        for (w in listOf("karunga", "mein", "kaise", "computer", "ka", "xyzqw")) {
            engine.suggest(w, null, true, 6).forEach { assertTrue(engine.isKnownWord(it.text) || it.phonetic, "$w -> ${it.text}") }
        }
        // a dictionary word is never displaced by a sound spelling
        for ((w, u) in listOf("karunga" to "کروں گا", "mein" to "میں", "computer" to "کمپیوٹر"))
            assertEquals(u, engine.suggest(w, null, true, 6).first().text)
    }
}
