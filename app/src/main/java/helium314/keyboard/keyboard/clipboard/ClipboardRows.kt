// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.clipboard

import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.FoundCode
import helium314.keyboard.latin.utils.SmartCodeExtractor

/** Presentation rows only: extracted details never duplicate the saved clipboard history. */
internal sealed class ClipboardRow {
    data class Heading(val title: Int) : ClipboardRow()
    data class Detail(val code: FoundCode) : ClipboardRow()
    data class Clip(val entry: ClipboardHistoryEntry) : ClipboardRow()
}

internal fun clipboardRows(entries: List<ClipboardHistoryEntry>, smart: Boolean, pinnedFirst: Boolean): List<ClipboardRow> {
    if (!smart) return entries.map { ClipboardRow.Clip(it) }
    val pinnedValues = entries.filter { it.isPinned && it.filename == null }.mapNotNull { it.text }.toSet()
    // Bound analysis to recent clips; opening a large, unlimited history must remain responsive.
    val details = entries.asSequence().filter { it.filename == null }.take(100)
        .flatMap { SmartCodeExtractor.find(it.text.orEmpty()).asSequence() }
        .filter { it.value !in pinnedValues }.distinctBy { it.value }.take(60).toList()
    return buildList {
        fun group(pinned: Boolean) {
            val clips = entries.filter { it.isPinned == pinned }
            if (clips.isEmpty()) return
            add(ClipboardRow.Heading(if (pinned) R.string.clipboard_pinned else R.string.clipboard_recent))
            clips.forEach { add(ClipboardRow.Clip(it)) }
        }
        if (pinnedFirst) group(true)
        if (details.isNotEmpty()) {
            add(ClipboardRow.Heading(R.string.clipboard_detected_details))
            details.forEach { add(ClipboardRow.Detail(it)) }
        }
        group(false)
        if (!pinnedFirst) group(true)
    }
}
