// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalModelTest {
    @Test fun bonusGrowsAndSaturates() {
        val pm = PersonalModel()
        assertEquals(0.0, pm.bonus("a", "b", "x"))
        pm.learn("x", "b", "a", 1f)
        val one = pm.bonus("a", "b", "x")
        repeat(50) { pm.learn("x", "b", "a", 1f) }
        val many = pm.bonus("a", "b", "x")
        assertTrue(one > 0 && many > one)
        assertTrue(many <= PersonalModel.MAX_SPELLING_BONUS + PersonalModel.MAX_PAIR_BONUS + PersonalModel.MAX_WORD_BONUS + 1e-9)
    }

    @Test fun unlearnTakesBack() {
        val pm = PersonalModel()
        pm.learn("kl", "کل", "میں", 2f)
        pm.unlearn("kl", "کل", "میں", 2f)
        assertEquals(0.0, pm.bonus("میں", "کل", "kl"))
        assertTrue(pm.isEmpty())
    }

    @Test fun injectsOnlyRepeatedSpellings() {
        val pm = PersonalModel()
        pm.learn("Ahmed", "Ahmed", null, 1f)
        assertTrue(pm.injected("ahmed", 3).isEmpty())
        pm.learn("ahmed", "Ahmed", null, 1f)
        assertEquals(listOf("Ahmed"), pm.injected("ahmed", 3))
    }

    @Test fun nextWordsByCount() {
        val pm = PersonalModel()
        pm.learn("a", "x", "p", 1f); pm.learn("b", "y", "p", 1f); pm.learn("b", "y", "p", 1f)
        assertEquals(listOf("y", "x"), pm.nextWords("p", 5))
    }

    @Test fun saveLoadRoundTripAndDamagedFile() {
        val pm = PersonalModel()
        pm.learn("kl", "کل", "میں", 2f)
        val bytes = ByteArrayOutputStream().also { pm.save(it) }.toByteArray()
        val back = PersonalModel.load(ByteArrayInputStream(bytes))
        assertEquals(pm.bonus("میں", "کل", "kl"), back.bonus("میں", "کل", "kl"))
        assertTrue(PersonalModel.load(ByteArrayInputStream(bytes.copyOf(20))).isEmpty())
        assertTrue(PersonalModel.load(ByteArrayInputStream(ByteArray(0))).isEmpty())
    }

    @Test fun capsAreEnforcedOnSave() {
        val pm = PersonalModel()
        for (i in 0 until PersonalModel.MAX_WORDS + 500) pm.learn("", "w$i", null, 1f)
        ByteArrayOutputStream().also { pm.save(it) }
        assertTrue(pm.wordCount() <= PersonalModel.MAX_WORDS + 5)
    }

    private val engine = RomanTransliterator.load(File("src/main/assets/urdu/urdu_translit.bin").inputStream())

    @Test fun learnedSpellingReordersAndNeverBreaksWithoutModel() {
        val cands = engine.suggest("kal", null, true, 6)
        val base = cands.map { it.text }
        engine.personal = null
        assertEquals(base, engine.suggest("kal", null, true, 6).map { it.text })
        // a habit for the exact spelling can promote a close runner-up; plain frequency of a word alone is capped well below a clear lead
        val second = cands.drop(1).first { !it.completion }.text   // a completion is never put first by design
        val pm = PersonalModel()
        repeat(4) { pm.learn("kal", second, null, 2f) }
        engine.personal = pm
        try {
            assertEquals(second, engine.suggest("kal", null, true, 6).first().text)
            val pm2 = PersonalModel(); repeat(50) { pm2.learn("", second, null, 2f) }
            engine.personal = pm2
            assertEquals(base[0], engine.suggest("kal", null, true, 6).first().text)   // lead is 2.7, word bonus <= 1.2
        } finally { engine.personal = null }
    }

    @Test fun nameKeptInRomanIsOffered() {
        val pm = PersonalModel()
        repeat(2) { pm.learn("zxqv", "Zxqv", null, 2f) }
        engine.personal = pm
        try {
            assertEquals("Zxqv", engine.suggest("zxqv", null, true, 6).first().text)
            assertTrue(engine.nextWords("میں", false, 5).isNotEmpty())
        } finally { engine.personal = null }
    }

    @Test fun onePickPutsTheWordFirstAndTheLatestPickWins() {
        val cands = engine.suggest("data", null, false, 6).map { it.text }
        val other = cands[1]
        val pm = PersonalModel()
        pm.learn("data", other, null, 2f)          // one manual pick
        engine.personal = pm
        try {
            assertEquals(other, engine.suggest("data", null, false, 6).first().text)
            pm.learn("data", cands[0], null, 2f)   // changed mind: picked the first one again
            assertEquals(cands[0], engine.suggest("data", null, false, 6).first().text)
        } finally { engine.personal = null }
    }

    @Test fun unknownWordIsSpelledBySound() {
        // no dictionary word: the sound spelling comes first, with the hard t / d (ٹ ڈ) version next to it
        val c = engine.suggest("pompom", null, false, 6)
        assertEquals("پمپوم", c.first().text)
        assertTrue(c.first().phonetic)
        // listed loanwords are dictionary words even where the letter channel cannot align the spelling
        assertEquals("واٹس ایپ", engine.suggest("whatsapp", null, false, 6).first().text)
        assertEquals("ٹک ٹاک", engine.suggest("tiktok", null, false, 6).first().text)
        // a dictionary word keeps its place; the sound spelling is only offered behind it
        val d = engine.suggest("download", null, false, 6)
        assertEquals("ڈاؤن لوڈ", d.first().text)
        // a word spelled by sound and picked is offered first next time
        val pm = PersonalModel()
        pm.learn("faargive", "فارگیو", null, 2f)
        engine.personal = pm
        try { assertEquals("فارگیو", engine.suggest("faargive", null, false, 6).first().text) } finally { engine.personal = null }
    }

    @Test fun lettersForAKey() {
        assertEquals(listOf("ت", "ٹ", "ط"), engine.lettersFor("t").take(3))
        assertEquals("خ", engine.lettersFor("kh").first())
        assertTrue(engine.lettersFor("z").containsAll(listOf("ز", "ذ", "ض", "ظ")))
    }

    @Test fun hardTAndDVariantForUnknownWords() {
        val c = engine.suggest("dodnot", null, false, 6).map { it.text }
        assertTrue(c.any { it.contains('ڈ') || it.contains('ٹ') }, c.toString())
    }

    @Test fun learnedNextWordComesFirst() {
        val pm = PersonalModel()
        pm.learn("x", "کتاب", "میں", 2f)
        engine.personal = pm
        try { assertEquals("کتاب", engine.nextWords("میں", false, 5).first()) } finally { engine.personal = null }
    }

    @Test fun lookupCostIsNegligible() {
        val pm = PersonalModel()
        for (i in 0 until 5000) pm.learn("r$i", "w$i", "p${i % 50}", 1f)
        engine.personal = pm
        try {
            repeat(200) { engine.suggest("kal", "میں", false, 6) }
            val t0 = System.nanoTime(); repeat(500) { engine.suggest("kal", "میں", false, 6) }; val with = System.nanoTime() - t0
            engine.personal = null
            val t1 = System.nanoTime(); repeat(500) { engine.suggest("kal", "میں", false, 6) }; val without = System.nanoTime() - t1
            println("suggest with personal ${with / 500 / 1000} us, without ${without / 500 / 1000} us")
            assertTrue(with < without * 1.5 + 200_000L * 500)
        } finally { engine.personal = null }
    }
}

class PersonalModelMergeTest {
    @Test fun mergeAddsCountsAndKeepsExisting() {
        val a = PersonalModel(); val b = PersonalModel()
        a.learn("kl", "کل", "میں", 1f); a.learn("x", "ایکس", null, 1f)
        b.learn("kl", "کل", "میں", 1f); b.learn("y", "وائی", null, 2f)
        val before = a.bonus("میں", "کل", "kl")
        a.merge(b)
        assertTrue(a.bonus("میں", "کل", "kl") > before)          // counts added
        assertTrue(a.bonus(null, "ایکس", "x") > 0 || a.bonus(null, "ایکس", null) > 0) // own word kept
        assertTrue(a.bonus(null, "وائی", null) > 0)               // imported word present
        a.merge(a)                                                 // self merge is a no-op, no deadlock
    }

    @Test fun exportedBytesLoadBack() {
        val a = PersonalModel(); a.learn("kl", "کل", null, 2f)
        val out = ByteArrayOutputStream(); a.save(out)
        val b = PersonalModel(); b.merge(PersonalModel.load(ByteArrayInputStream(out.toByteArray())))
        assertEquals(a.bonus(null, "کل", "kl"), b.bonus(null, "کل", "kl"))
    }
}
