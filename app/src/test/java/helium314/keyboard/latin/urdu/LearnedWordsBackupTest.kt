// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu

import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.App
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LearnedWordsBackupTest {
    private val context = ApplicationProvider.getApplicationContext<App>()

    private fun wait(cond: () -> Boolean) { val end = System.currentTimeMillis() + 3000; while (!cond() && System.currentTimeMillis() < end) Thread.sleep(20) }

    @Test fun exportThenImportOnAnotherPhoneKeepsOldAndAddsNew() {
        File(context.filesDir, "learned_ur-Latn.bin").delete()
        RomanScript.URDU.reloadLearned(context)
        RomanScript.URDU.personal!!.learn("kl", "کل", "میں", 2f)
        val zip = ByteArrayOutputStream().also { RomanScript.exportLearned(context, it) }.toByteArray()
        // "new phone": a different word was learned there already
        RomanScript.URDU.personal!!.clear()
        RomanScript.URDU.personal!!.learn("x", "ایکس", null, 2f)
        val n = RomanScript.importLearned(context, ByteArrayInputStream(zip))
        assertTrue(n >= 1)
        val pm = RomanScript.URDU.personal!!
        assertTrue(pm.bonus("میں", "کل", "kl") > 0, "imported word")
        assertTrue(pm.bonus(null, "ایکس", "x") > 0, "word that was already here")
        // saved in the background, and survives a reload from disk
        wait { File(context.filesDir, "learned_ur-Latn.bin").exists() }
        RomanScript.saveAllLearned()
        wait { File(context.filesDir, "learned_ur-Latn.bin").length() > 0 }
        RomanScript.URDU.reloadLearned(context)
        assertTrue(RomanScript.URDU.personal!!.bonus("میں", "کل", "kl") > 0)
    }

    @Test fun garbageFileImportsNothingAndDoesNotCrash() {
        val before = RomanScript.ARABIC.personal?.wordCount() ?: 0
        val n = RomanScript.importLearned(context, ByteArrayInputStream("not a zip".toByteArray()))
        assertEquals(0, n)
        assertEquals(before, RomanScript.ARABIC.personal?.wordCount() ?: 0)
    }
}
