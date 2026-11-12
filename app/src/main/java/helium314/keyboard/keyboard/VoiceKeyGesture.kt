// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.view.MotionEvent
import kotlin.math.abs

/** Owns a touch that starts on the mic, so sliding up cannot type neighbouring letters. */
class VoiceKeyGesture(private val lockDistance: Float, private val listener: (KeyboardActionListener.VoiceAction) -> Unit) {
    private var consuming = false
    private var pointer = -1
    private var startX = 0f
    private var startY = 0f
    private var locked = false

    fun onTouch(event: MotionEvent, startsOnMic: Boolean): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (consuming) cancel()
            if (!startsOnMic) return false
            consuming = true
            pointer = event.getPointerId(0)
            startX = event.x; startY = event.y; locked = false
            listener(KeyboardActionListener.VoiceAction.PRESS)
            return true
        }
        if (!consuming) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointer)
                if (pointer != -1 && index < 0) cancel()
                else if (index >= 0 && !locked && startY - event.getY(index) >= lockDistance
                    && startY - event.getY(index) > abs(event.getX(index) - startX)) {
                    locked = true
                    listener(KeyboardActionListener.VoiceAction.LOCK)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointer) release()
            MotionEvent.ACTION_UP -> { release(); consuming = false }
            MotionEvent.ACTION_CANCEL -> cancel()
        }
        return true
    }

    private fun release() {
        if (pointer == -1) return
        pointer = -1
        listener(KeyboardActionListener.VoiceAction.RELEASE)
    }

    fun cancel() {
        if (consuming && pointer != -1) listener(KeyboardActionListener.VoiceAction.CANCEL)
        consuming = false; pointer = -1
    }
}
