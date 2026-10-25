// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodService::class])
class ClipboardHistoryManagerTest {
    private val ime = Robolectric.setupService(LatinIME::class.java)
    private val manager = ime.clipboardHistoryManager
    private val clipboard = ime.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val parent = LinearLayout(ime)
    private val editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }

    @BeforeTest fun resetSettings() {
        ime.prefs().edit {
            putBoolean(Settings.PREF_SMART_CLIPBOARD, true)
            putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true)
            putBoolean(Settings.PREF_SUGGEST_CLIPBOARD_CONTENT, true)
            putBoolean(Settings.PREF_CLIPBOARD_SKIP_CODES, false)
        }
        Settings.getInstance().loadSettings(ime)
    }

    private fun copy(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("test", text))
        // Robolectric timestamps clips with uptime; Android uses wall-clock time.
        ReflectionHelpers.setField(clipboard.primaryClip!!.description, "mTimeStamp", System.currentTimeMillis())
        manager.onPrimaryClipChanged()
    }

    private fun suggestion() = manager.getClipboardSuggestionView(editor, parent)

    @Test fun dismissalResetsOnCopyEvenWithHistoryDisabled() {
        ime.prefs().edit { putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, false) }
        Settings.getInstance().loadSettings(ime)
        val originalSize = manager.getHistorySize()
        copy("Call 03001234567")
        assertNotNull(suggestion()).findViewById<View>(R.id.clipboard_suggestion_close).performClick()
        assertNull(suggestion())
        copy("Call +44 20 7946 0958")
        assertNotNull(suggestion())
        assertEquals(originalSize, manager.getHistorySize())
    }

    @Test fun sensitiveClipsAreMaskedAndNotSaved() {
        val originalSize = manager.getHistorySize()
        val clip = ClipData.newPlainText("secret", "Your OTP is 482916")
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        clipboard.setPrimaryClip(clip)
        // Robolectric timestamps clips with uptime; Android uses wall-clock time.
        ReflectionHelpers.setField(clipboard.primaryClip!!.description, "mTimeStamp", System.currentTimeMillis())
        manager.onPrimaryClipChanged()
        assertEquals(originalSize, manager.getHistorySize())
        val chips = assertNotNull(suggestion()).findViewById<LinearLayout>(R.id.clipboard_suggestion_chips)
        assertTrue((chips.getChildAt(0) as TextView).text.contains("••••"))
        assertTrue(!chips.getChildAt(0).contentDescription.contains("482916"))
    }

    @Test fun passwordFieldsMaskEvenWhenSourceMarksClipNonSensitive() {
        val clip = ClipData.newPlainText("test", "Call 03001234567")
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", false) }
        clipboard.setPrimaryClip(clip)
        // Robolectric timestamps clips with uptime; Android uses wall-clock time.
        ReflectionHelpers.setField(clipboard.primaryClip!!.description, "mTimeStamp", System.currentTimeMillis())
        manager.onPrimaryClipChanged()
        editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val chips = assertNotNull(suggestion()).findViewById<LinearLayout>(R.id.clipboard_suggestion_chips)
        assertTrue((chips.getChildAt(0) as TextView).text.contains("••••"))
    }

    @Test fun numericFieldsNeverOfferIbanChips() {
        copy("IBAN PK36SCBL0000001123456702 Phone 03001234567")
        editor.inputType = InputType.TYPE_CLASS_NUMBER
        val chips = assertNotNull(suggestion()).findViewById<LinearLayout>(R.id.clipboard_suggestion_chips)
        assertEquals(1, chips.childCount)
        assertTrue((chips.getChildAt(0) as TextView).text.contains("03001234567"))
    }

    @Test fun reopeningClipboardDoesNotResurrectADeletedClip() {
        copy("A temporary note")
        val index = (0 until manager.getHistorySize()).first { manager.getHistoryEntry(it)?.text == "A temporary note" }
        manager.removeEntry(index)
        manager.onStartInputView()
        manager.prepareClipboardHistory()
        assertTrue((0 until manager.getHistorySize()).none { manager.getHistoryEntry(it)?.text == "A temporary note" })
    }

    @Test fun pinningADetailSavesItIndependentlyAndSurvivesClearingRecentClips() {
        copy("Phone 03001234567")
        manager.pinText("03001234567")
        manager.pinText("03001234567")
        manager.clearHistory()
        val clips = (0 until manager.getHistorySize()).mapNotNull { manager.getHistoryEntry(it) }
        assertEquals(1, clips.count { it.text == "03001234567" })
        assertTrue(clips.first { it.text == "03001234567" }.isPinned)
        assertTrue(clips.none { it.text == "Phone 03001234567" })
    }

    @Test fun disablingSmartClipboardKeepsFullTextPasteSuggestion() {
        ime.prefs().edit { putBoolean(Settings.PREF_SMART_CLIPBOARD, false) }
        Settings.getInstance().loadSettings(ime)
        val text = "Phone 03001234567"
        copy(text)
        val view = assertNotNull(suggestion())
        assertEquals(text, view.findViewById<TextView>(R.id.clipboard_suggestion_text).text.toString())
        assertEquals(View.GONE, view.findViewById<View>(R.id.clipboard_suggestion_scroll).visibility)
    }
}
