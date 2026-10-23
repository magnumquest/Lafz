// SPDX-License-Identifier: GPL-3.0-only
package app.lafz.whisper

import android.content.Context
import java.io.File

/**
 * whisper.cpp for speech recognition. The native libraries are only loaded when [available] is used the first time, i.e. when a Whisper model
 * is run; with voice typing off nothing from this module is touched.
 */
object WhisperLib {
    private var loaded: Boolean? = null
    private var error: String? = null

    /** true if the native libraries (arm64-v8a and x86_64 only) could be loaded */
    @Synchronized
    fun available(context: Context): Boolean {
        loaded?.let { return it }
        loaded = try {
            System.loadLibrary("lafz_whisper")
            loadBackends(context.applicationInfo.nativeLibraryDir)
            true
        } catch (t: Throwable) {
            error = t.message
            false
        }
        return loaded!!
    }

    fun unavailableReason() = error ?: "not supported on this device"

    fun systemInfo(): String = systemInfoNative()

    /** a loaded model; [close] gives its memory back */
    class Model(path: File) : AutoCloseable {
        private var ptr = initContext(path.path)
        init { require(ptr != 0L) { "could not load ${path.name}" } }

        /** [samples]: mono 16 kHz audio in -1..1. [beamSize] 1 is greedy and fastest. Audio longer than 30 s is split by whisper itself. */
        fun transcribe(samples: FloatArray, language: String, threads: Int, beamSize: Int = 5): String {
            check(ptr != 0L) { "closed" }
            return runWhisper(ptr, threads, beamSize, language, samples)?.trim() ?: throw IllegalStateException("whisper failed")
        }

        override fun close() { if (ptr != 0L) { freeContext(ptr); ptr = 0L } }
    }

    @JvmStatic private external fun loadBackends(dir: String)
    @JvmStatic private external fun initContext(modelPath: String): Long
    @JvmStatic private external fun freeContext(ptr: Long)
    @JvmStatic private external fun runWhisper(ptr: Long, threads: Int, beamSize: Int, language: String, audio: FloatArray): String?
    @JvmStatic private external fun systemInfoNative(): String
}
