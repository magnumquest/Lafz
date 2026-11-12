// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.view.MotionEvent
import helium314.keyboard.keyboard.KeyboardActionListener.VoiceAction
import helium314.keyboard.keyboard.VoiceKeyGesture
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class VoiceKeyGestureTest {
    private val actions = mutableListOf<VoiceAction>()
    private val gesture = VoiceKeyGesture(48f) { actions.add(it) }

    private fun event(action: Int, x: Float = 100f, y: Float = 200f, mic: Boolean = false): Boolean {
        val e = MotionEvent.obtain(0, 0, action, x, y, 0)
        return try { gesture.onTouch(e, mic) } finally { e.recycle() }
    }

    @Test fun holdAndReleaseDoNotRequireClickOrLongPress() {
        assertTrue(event(MotionEvent.ACTION_DOWN, mic = true))
        assertEquals(listOf(VoiceAction.PRESS), actions)
        assertTrue(event(MotionEvent.ACTION_UP))
        assertEquals(listOf(VoiceAction.PRESS, VoiceAction.RELEASE), actions)
    }

    @Test fun upwardSwipeLocksOnceAndConsumesNeighbouringKeys() {
        event(MotionEvent.ACTION_DOWN, mic = true)
        assertTrue(event(MotionEvent.ACTION_MOVE, y = 140f))
        assertTrue(event(MotionEvent.ACTION_MOVE, y = 80f))
        event(MotionEvent.ACTION_UP, y = 80f)
        assertEquals(listOf(VoiceAction.PRESS, VoiceAction.LOCK, VoiceAction.RELEASE), actions)
    }

    @Test fun driftSidewaysDoesNotLockAndReleaseOutsideStillFinishes() {
        event(MotionEvent.ACTION_DOWN, mic = true)
        event(MotionEvent.ACTION_MOVE, x = 220f, y = 150f)
        event(MotionEvent.ACTION_UP, x = 220f, y = 150f)
        assertEquals(listOf(VoiceAction.PRESS, VoiceAction.RELEASE), actions)
    }

    @Test fun cancelledTouchCannotFinishOrInsertText() {
        event(MotionEvent.ACTION_DOWN, mic = true)
        event(MotionEvent.ACTION_CANCEL)
        assertFalse(event(MotionEvent.ACTION_UP))
        assertEquals(listOf(VoiceAction.PRESS, VoiceAction.CANCEL), actions)
    }

    @Test fun liftingTheMicFingerWithAnotherFingerDownFinishesOnlyOnce() {
        event(MotionEvent.ACTION_DOWN, mic = true)
        val properties = Array(2) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coordinates = Array(2) { MotionEvent.PointerCoords().apply { x = 100f; y = 200f } }
        fun multi(action: Int) {
            val e = MotionEvent.obtain(0, 1, action, 2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0)
            try { assertTrue(gesture.onTouch(e, false)) } finally { e.recycle() }
        }
        multi(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT))
        assertEquals(listOf(VoiceAction.PRESS), actions)
        multi(MotionEvent.ACTION_POINTER_UP) // owning pointer 0 lifts first
        assertEquals(listOf(VoiceAction.PRESS, VoiceAction.RELEASE), actions)
        assertTrue(event(MotionEvent.ACTION_MOVE)) // consume the remaining finger's stream
        assertTrue(event(MotionEvent.ACTION_UP))
        assertEquals(listOf(VoiceAction.PRESS, VoiceAction.RELEASE), actions)
    }

    @Test fun slidingIntoMicFromAnotherKeyDoesNotStartRecording() {
        assertFalse(event(MotionEvent.ACTION_DOWN))
        assertFalse(event(MotionEvent.ACTION_MOVE, mic = true))
        assertFalse(event(MotionEvent.ACTION_UP, mic = true))
        assertEquals(emptyList(), actions)
    }
}
