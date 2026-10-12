// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UrduTransliteratorTest {
    private val engine = UrduTransliterator.load(File("src/main/assets/urdu/urdu_translit.bin").inputStream())

    private fun sentence(roman: String): String {
        var prev: String? = null
        return roman.split(" ").joinToString(" ") { w ->
            val best = engine.suggest(w, prev, prev == null, 1).first().urdu
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
        assertTrue(engine.suggest("kal", "میں", false, 5).any { it.urdu == "کل" })
    }

    @Test fun nextWordPrediction() {
        assertTrue(engine.nextWords("میں", false, 5).isNotEmpty())
    }

    @Test fun futureTenseVerbsAreDictionaryPhrases() {
        assertEquals("میں کل آؤں گا", sentence("mein kal aonga"))
        assertEquals("جاؤں گا", sentence("jaunga"))
        assertTrue(engine.suggest("aonga", null, true, 5).any { it.urdu == "آؤں گی" })
    }

    @Test fun englishLoanwordsGetUrduSpelling() {
        assertEquals("کمپیوٹر", sentence("computer"))
        assertEquals("میسج", sentence("message"))
        assertEquals("لیپ ٹاپ", sentence("laptop"))
        assertEquals("ڈاکٹر", sentence("doctor"))
    }

    @Test fun neverSuggestsOutOfDictionaryWords() {
        assertTrue(engine.suggest("xyzqw", null, true, 5).isEmpty())
        for (w in listOf("karunga", "mein", "kaise", "computer", "ka")) {
            engine.suggest(w, null, true, 6).forEach { assertTrue(engine.isKnownWord(it.urdu), "$w -> ${it.urdu}") }
        }
    }
}
