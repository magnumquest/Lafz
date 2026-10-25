// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal

import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.ToolbarKey
import java.util.Locale

/** A consistent vector set for toolbar buttons; main keyboard icons keep their selected style. */
internal object ToolbarIcons {
    val resourceIds = ToolbarKey.entries.associate { key ->
        key.name.lowercase(Locale.US) to when (key) {
            ToolbarKey.VOICE -> R.drawable.ic_toolbar_voice
            ToolbarKey.CLIPBOARD -> R.drawable.ic_toolbar_clipboard
            ToolbarKey.NUMPAD -> R.drawable.ic_toolbar_numpad
            ToolbarKey.DPAD -> R.drawable.ic_toolbar_dpad
            ToolbarKey.UNDO -> R.drawable.ic_toolbar_undo
            ToolbarKey.REDO -> R.drawable.ic_toolbar_redo
            ToolbarKey.SETTINGS -> R.drawable.ic_toolbar_settings
            ToolbarKey.SELECT_ALL -> R.drawable.ic_toolbar_select_all
            ToolbarKey.SELECT_WORD -> R.drawable.ic_toolbar_select_word
            ToolbarKey.COPY -> R.drawable.ic_toolbar_copy
            ToolbarKey.CUT -> R.drawable.ic_toolbar_cut
            ToolbarKey.PASTE -> R.drawable.ic_toolbar_paste
            ToolbarKey.ONE_HANDED -> R.drawable.ic_toolbar_one_handed
            ToolbarKey.FLOATING -> R.drawable.ic_toolbar_floating
            ToolbarKey.SPLIT -> R.drawable.ic_toolbar_split
            ToolbarKey.INCOGNITO -> R.drawable.ic_toolbar_incognito
            ToolbarKey.AUTOCORRECT -> R.drawable.ic_toolbar_autocorrect
            ToolbarKey.CLEAR_CLIPBOARD -> R.drawable.ic_toolbar_clear_clipboard
            ToolbarKey.CLOSE_HISTORY -> R.drawable.ic_toolbar_close_history
            ToolbarKey.EMOJI -> R.drawable.ic_toolbar_emoji
            ToolbarKey.LEFT -> R.drawable.ic_toolbar_left
            ToolbarKey.RIGHT -> R.drawable.ic_toolbar_right
            ToolbarKey.UP -> R.drawable.ic_toolbar_up
            ToolbarKey.DOWN -> R.drawable.ic_toolbar_down
            ToolbarKey.WORD_LEFT -> R.drawable.ic_toolbar_word_left
            ToolbarKey.WORD_RIGHT -> R.drawable.ic_toolbar_word_right
            ToolbarKey.PAGE_UP -> R.drawable.ic_toolbar_page_up
            ToolbarKey.PAGE_DOWN -> R.drawable.ic_toolbar_page_down
            ToolbarKey.FULL_LEFT -> R.drawable.ic_toolbar_full_left
            ToolbarKey.FULL_RIGHT -> R.drawable.ic_toolbar_full_right
            ToolbarKey.PAGE_START -> R.drawable.ic_toolbar_page_start
            ToolbarKey.PAGE_END -> R.drawable.ic_toolbar_page_end
            ToolbarKey.BACKGROUND_GATHERING -> R.drawable.ic_toolbar_background_gathering
        }
    } + (KeyboardIconsSet.NAME_TOOLBAR_KEY to R.drawable.ic_toolbar_expand)
}
