// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechCancellationTest {
    @Test fun cancelledSessionNeverStartsNativeWork() {
        val cancellation = SpeechCancellation()
        cancellation.cancel()
        assertFailsWith<CancellationException> { cancellation.withHook({ error("unused") }) { error("must not run") } }
    }

    @Test fun cancelSignalsAnActiveRunAndCannotReachAClosedToken() {
        val cancellation = SpeechCancellation()
        val ready = CountDownLatch(1)
        val terminate = CountDownLatch(1)
        var calls = 0
        var cancelled = false
        val worker = thread {
            try {
                cancellation.withHook({ calls++; terminate.countDown() }) {
                    ready.countDown()
                    assertTrue(terminate.await(5, TimeUnit.SECONDS))
                    cancellation.throwIfCancelled()
                }
            } catch (_: CancellationException) { cancelled = true }
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        cancellation.cancel()
        worker.join(5000)
        assertFalse(worker.isAlive)
        assertTrue(cancelled)
        cancellation.cancel()
        assertEquals(1, calls)
    }

    @Test fun completedRunsUnregisterBeforeTheirNativeResourcesClose() {
        val cancellation = SpeechCancellation()
        var calls = 0
        assertEquals("text", cancellation.withHook({ calls++ }) { "text" })
        cancellation.cancel()
        assertEquals(0, calls)
    }
}
