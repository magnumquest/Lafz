// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReligiousShortcutsTest {
    private val r = ReligiousShortcuts.load(File("src/main/assets/honorifics/honorifics.tsv").inputStream())

    @Test fun prophetMuhammad() {
        assertEquals(listOf("Rasulullah ﷺ", "Rasulullah Sallallahu Alaihi Wasallam"), r.suggest("Rasul", null, false, 3))
        assertEquals("Rasulullah ﷺ", r.suggest("rasoolullah", null, false, 3).first())
        assertEquals("رسول اللہ ﷺ", r.suggest("rasul", null, true, 3).first())
        assertEquals("رسول اللہ ﷺ", r.suggest("رسول", null, true, 3).first())
    }

    @Test fun namesStayNames() {
        // owner: "Umar" is usually a person; the honorific is offered only when it is typed ("Umar rad")
        for (name in listOf("Umar", "Isa", "Fatima", "Muhammad", "Mustafa", "Ali", "Shoaib", "Shuaib", "Hasnain"))
            assertTrue(r.suggest(name, null, false, 3).isEmpty(), name)
        assertTrue(r.suggest("isa", null, true, 3).isEmpty())
        assertTrue(r.suggest("shou", null, false, 3).isEmpty()) // folded to "sho": must not reach Shuaib
        // the Prophet ﷺ written as Nabi / Huzoor still expands
        assertEquals("Nabi Kareem ﷺ", r.suggest("nabi", null, false, 3).first())
        assertEquals("Huzoor ﷺ", r.suggest("Huzoor", null, false, 3).first())
    }

    @Test fun honorificSuffixTypedAfterName() {
        assertEquals("Radiallahu Anhu", r.suggest("rad", "Umar", false, 3).first())
        assertEquals("Radiallahu Anha", r.suggest("rad", "Aisha", false, 3).first())
        assertEquals("Radiallahu Anhuma", r.suggest("radi", "Hasnain", false, 3).first())
        assertEquals("Sallallahu Alaihi Wasallam", r.suggest("sal", "Nabi", false, 3).first())
        assertEquals("Sallallahu Alaihi Wasallam", r.suggest("sall", "Muhammad", false, 3).first())
        assertEquals("Alaihis Salaam", r.suggest("alai", "Isa", false, 3).first())
        assertEquals("Rahimahullah", r.suggest("rah", "Bukhari", false, 3).first())
        assertEquals("رضی اللہ عنہا", r.suggest("rad", "Aisha", ReligiousShortcuts.URDU, 3).first())
        assertTrue(r.isStrongMatch("rad", "Umar"))
        assertTrue(!r.isStrongMatch("rad", "the"))  // "rad" alone is an English word: offered, but not in a main slot
        assertTrue(!r.isStrongMatch("Umar", null))
        assertTrue(r.isStrongMatch("alhamdu", null))
        assertTrue(r.isStrongMatch("nabi", null))
    }

    @Test fun abbreviationsDependOnPreviousName() {
        assertEquals("Radiallahu Anhu", r.suggest("ra", "Umar", false, 4).first())
        assertEquals("Radiallahu Anha", r.suggest("ra", "Aisha", false, 4).first())
        assertEquals("Alaihis Salaam", r.suggest("as", "Isa", false, 4).first())
        assertEquals("ﷺ", r.suggest("saw", "Muhammad", false, 4).first())
        // ordinary English words must not trigger anything
        assertTrue(r.suggest("as", "the", false, 4).isEmpty())
        assertTrue(r.suggest("saw", "I", false, 4).isEmpty())
        assertTrue(r.suggest("all", null, false, 4).isEmpty())
    }

    @Test fun afterNameOffersHonorificOnlyForTheProphet() {
        assertEquals("ﷺ", r.afterWord("Nabi", false, 3).first())
        assertTrue(r.afterWord("Umar", false, 3).isEmpty())
        assertTrue(r.afterWord("Muhammad", false, 3).isEmpty())
        assertTrue(r.afterWord("عائشہ", true, 3).isEmpty())
        assertTrue(r.afterWord("the", false, 3).isEmpty())
    }

    @Test fun spellingVariants() {
        assertEquals("Sallallahu Alaihi Wasallam", r.suggest("Salallahualaihiwasallam", null, false, 3).first())
        assertEquals("Radiallahu Anhu", r.suggest("RadiAllahuanh", null, false, 3).first())
    }

    @Test fun commonPhrasesAndTerms() {
        assertEquals("InshaAllah", r.suggest("inshallah", null, false, 3).first())
        assertEquals("ان شاء اللہ", r.suggest("inshallah", null, true, 3).first())
        assertEquals("JazakAllahu Khairan", r.suggest("jazakallah", null, false, 3).first())
        assertEquals("Namaz", r.suggest("namaz", null, false, 3).first())
        assertEquals("Alhamdulillah", r.suggest("Alhamdu", null, false, 3).first())
        assertEquals("Alhamdulillah", r.suggest("Alham", null, false, 3).first())
    }
}
