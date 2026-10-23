// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

interface OnKeyEventListener {

    fun onKeyDown(clipId: Long)

    fun onKeyUp(clipId: Long)

    /** a value picked out of an entry (a code or number), pasted instead of the whole entry */
    fun onValue(text: String)

}