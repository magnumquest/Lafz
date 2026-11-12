// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** One recording's cancellation, independent of the lifetime of its reusable speech model. */
class SpeechCancellation {
    private val cancelled = AtomicBoolean()
    private val lock = Any()
    private var hook: (() -> Unit)? = null
    val isCancelled get() = cancelled.get()

    fun throwIfCancelled() {
        if (isCancelled) throw CancellationException("Dictation cancelled")
    }

    fun cancel() = synchronized(lock) {
        if (cancelled.compareAndSet(false, true)) hook?.invoke()
    }

    /** Serialize cancellation with hook removal, so a native run token cannot be freed during cancel(). */
    fun <T> withHook(onCancel: () -> Unit, block: () -> T): T {
        synchronized(lock) {
            throwIfCancelled()
            check(hook == null) { "Concurrent inference for one recording" }
            hook = onCancel
        }
        try {
            throwIfCancelled()
            return block()
        } finally {
            synchronized(lock) { hook = null }
        }
    }
}
