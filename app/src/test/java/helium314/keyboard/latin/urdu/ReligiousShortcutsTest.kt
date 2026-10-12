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

    @Test fun companionsAndProphets() {
        assertEquals("Umar Radiallahu Anhu", r.suggest("Umar", null, false, 3).first())
        assertEquals("Isa Alaihis Salaam", r.suggest("Isa", null, false, 3).first())
        assertEquals("Fatima Radiallahu Anha", r.suggest("Fatima", null, false, 3).first())
        assertEquals("عیسیٰ علیہ السلام", r.suggest("isa", null, true, 3).first())
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

    @Test fun afterNameOffersHonorific() {
        assertEquals("Radiallahu Anhu", r.afterWord("Umar", false, 3).first())
        assertEquals("رضی اللہ عنہا", r.afterWord("عائشہ", true, 3).first())
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
    }
}
