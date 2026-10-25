// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.clipboard

import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.CodeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClipboardRowsTest {
    private fun clip(id: Long, text: String, pinned: Boolean = false) =
        ClipboardHistoryEntry(id, id, pinned, text, null, null)

    @Test fun detailsAreEnumeratedAndOriginalTextRemainsAvailable() {
        val copied = clip(1, "IBAN PK36SCBL0000001123456702 Phone 03001234567 Account 0123456789012")
        val rows = clipboardRows(listOf(copied), true, true)
        assertEquals(listOf(CodeKind.IBAN, CodeKind.PHONE, CodeKind.ACCOUNT),
            rows.filterIsInstance<ClipboardRow.Detail>().map { it.code.kind })
        assertEquals(listOf(copied), rows.filterIsInstance<ClipboardRow.Clip>().map { it.entry })
        assertEquals(listOf(R.string.clipboard_detected_details, R.string.clipboard_recent),
            rows.filterIsInstance<ClipboardRow.Heading>().map { it.title })
    }

    @Test fun pinningADetailRemovesItsDuplicateFromDiscoveredDetails() {
        val rows = clipboardRows(listOf(clip(2, "03001234567", true), clip(1, "Phone 03001234567")), true, true)
        assertTrue(rows.none { it is ClipboardRow.Detail })
        assertEquals(listOf(R.string.clipboard_pinned, R.string.clipboard_recent),
            rows.filterIsInstance<ClipboardRow.Heading>().map { it.title })
    }

    @Test fun smartClipboardOffRetainsOriginalCardsAndOrder() {
        val entries = listOf(clip(1, "hello"), clip(2, "Phone 03001234567"))
        assertEquals(entries.map { ClipboardRow.Clip(it) }, clipboardRows(entries, false, false))
        assertTrue(clipboardRows(emptyList(), true, true).isEmpty())
    }

    @Test fun pinnedFirstPreferenceAppliesBeforeDetectedDetails() {
        val entries = listOf(clip(2, "Keep this", true), clip(1, "Phone 03001234567"))
        val headings = clipboardRows(entries, true, true).filterIsInstance<ClipboardRow.Heading>()
        assertEquals(listOf(R.string.clipboard_pinned, R.string.clipboard_detected_details, R.string.clipboard_recent),
            headings.map { it.title })
    }

    @Test fun detectedValuesAreDeduplicatedAcrossClips() {
        val rows = clipboardRows(listOf(clip(1, "Call 03001234567"), clip(2, "Phone 03001234567")), true, false)
        assertEquals(1, rows.filterIsInstance<ClipboardRow.Detail>().size)
        assertEquals(2, rows.filterIsInstance<ClipboardRow.Clip>().size)
    }
}
