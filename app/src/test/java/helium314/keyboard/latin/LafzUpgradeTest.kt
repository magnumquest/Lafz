// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class LafzUpgradeTest {
    private val context = ApplicationProvider.getApplicationContext<App>()

    @Test fun `disabled swipe delete survives upgrade from Lafz 0 2`() {
        val prefs = context.prefs()
        prefs.edit { clear(); putInt(Settings.PREF_VERSION_CODE, 4202); putBoolean("delete_swipe", false) }
        checkVersionUpgrade(context)
        assertEquals(0, prefs.getInt(Settings.PREF_DELETE_SWIPE, -1))
        assertFalse(prefs.contains("delete_swipe"))
        assertEquals(BuildConfig.VERSION_CODE, prefs.getInt(Settings.PREF_VERSION_CODE, 0))
    }

    @Test fun `existing new swipe mode is preserved during migration`() {
        val prefs = context.prefs()
        prefs.edit {
            clear()
            putInt(Settings.PREF_VERSION_CODE, 4202)
            putBoolean("delete_swipe", true)
            putInt(Settings.PREF_DELETE_SWIPE, 2)
        }
        checkVersionUpgrade(context)
        assertEquals(2, prefs.getInt(Settings.PREF_DELETE_SWIPE, -1))
        assertFalse(prefs.contains("delete_swipe"))
    }
}
