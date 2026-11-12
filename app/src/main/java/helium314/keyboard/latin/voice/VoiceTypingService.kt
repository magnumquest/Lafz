// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import java.io.File

/** Microphone capture and serial inference run independently; control messages never wait for a model. */
class VoiceTypingService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var messenger: Messenger
    private val inference = java.util.concurrent.Executors.newSingleThreadExecutor()
    // Engine ownership belongs exclusively to the inference executor, including destruction.
    private var engine: SpeechEngine? = null
    private var engineDir: String? = null
    @Volatile private var session: Session? = null

    private class Session(val id: Int, val client: Messenger) {
        @Volatile var stop = false
        @Volatile var cancelled = false
        @Volatile var recorder: AudioRecord? = null
        var capture: Thread? = null
        val backlog = VoiceBacklog()
        val cancellation = SpeechCancellation()
        @Volatile var failure: String? = null
        val terminalSent = java.util.concurrent.atomic.AtomicBoolean()
    }

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("lafz-voice-control").also { it.start() }
        handler = Handler(thread.looper) { handle(it); true }
        messenger = Messenger(handler)
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        handler.post { cancelRecording(); inference.execute { closeEngine() } }
        return false
    }

    override fun onDestroy() {
        handler.post {
            cancelRecording()
            inference.execute { closeEngine() }
            inference.shutdown()
            thread.quitSafely()
        }
        super.onDestroy()
    }

    private fun handle(msg: Message) {
        when (msg.what) {
            VoiceProtocol.MSG_LOAD -> {
                val dir = msg.data.getString(VoiceProtocol.KEY_MODEL_DIR) ?: return
                if (session == null) inference.execute {
                    VoiceModels.installed(this).firstOrNull { it.dirName == dir }?.let {
                        try { ensureEngine(it) } catch (t: Throwable) { Log.e(TAG, "preload failed", t) }
                    }
                }
            }
            VoiceProtocol.MSG_UNLOAD -> { cancelRecording(); inference.execute { closeEngine() } }
            VoiceProtocol.MSG_START -> {
                val client = msg.replyTo ?: return
                val dir = msg.data.getString(VoiceProtocol.KEY_MODEL_DIR) ?: return
                start(dir, Session(msg.arg1, client))
            }
            VoiceProtocol.MSG_STOP -> session?.takeIf { it.id == msg.arg1 }?.stop = true
            VoiceProtocol.MSG_CANCEL -> if (session?.id == msg.arg1) cancelRecording()
        }
    }

    private fun reply(s: Session, what: Int, key: String? = null, value: String? = null) {
        if (s.cancelled) return
        val m = Message.obtain(null, what).apply { arg1 = s.id }
        if (key != null) m.data = Bundle().apply { putString(key, value) }
        try { s.client.send(m) } catch (e: Exception) { Log.w(TAG, "client is gone: ${e.message}") }
    }

    private fun fail(s: Session, error: Throwable) {
        synchronized(s) {
            if (s.cancelled || s.failure != null) return
            Log.e(TAG, "dictation failed", error)
            s.failure = "Dictation failed: ${error.message}"
            s.stop = true
        }
        abortInference(s)
        // A microphone error can originate on capture. Order the terminal error after
        // already published/decoding phrases so completed held text remains recoverable.
        inference.execute { complete(s) }
    }

    private fun complete(s: Session) {
        if (!s.terminalSent.compareAndSet(false, true)) return
        // Do not report IDLE while the failed/stopped capture still owns the microphone.
        s.capture?.join(2000)
        val failure = s.failure
        if (failure == null) reply(s, VoiceProtocol.MSG_RESULT, VoiceProtocol.KEY_TEXT, "")
        else reply(s, VoiceProtocol.MSG_ERROR, VoiceProtocol.KEY_ERROR, failure)
        handler.post { if (session === s) session = null }
    }

    private fun abortInference(s: Session) {
        try { s.cancellation.cancel() }
        catch (t: Exception) { Log.w(TAG, "could not interrupt inference", t) }
    }

    private fun start(modelDir: String, s: Session) {
        cancelRecording()
        session = s
        val info = VoiceModels.installed(this).firstOrNull { it.dirName == modelDir }
        if (info == null) { fail(s, IllegalStateException("model $modelDir not found")); return }
        // Queue model loading before any audio; keep it warm between phrases and mic taps.
        inference.execute {
            if (!s.cancelled && s.failure == null) try { ensureEngine(info) } catch (t: Throwable) { fail(s, t) }
        }
        val capture = Thread({
            var rec: AudioRecord? = null
            try {
                if (s.cancelled || s.failure != null) return@Thread
                val injected = injectedAudio()
                if (injected == null) {
                    val min = AudioRecord.getMinBufferSize(VoiceProtocol.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    require(min > 0) { "microphone format unavailable" }
                    rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, VoiceProtocol.SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 16384))
                    require(rec.state == AudioRecord.STATE_INITIALIZED) { "microphone unavailable" }
                    s.recorder = rec
                    rec.startRecording()
                }
                reply(s, VoiceProtocol.MSG_LISTENING)
                val segmenter = VoiceSegmenter()
                val frame = ShortArray(VoiceProtocol.SAMPLE_RATE / 50)
                var offset = 0
                var stopReason = VoiceProtocol.STOP_REQUESTED
                while (!s.stop) {
                    val n = if (injected != null) {
                        val count = minOf(frame.size, injected.size - offset)
                        if (count <= 0) break
                        injected.copyInto(frame, 0, offset, offset + count)
                        offset += count
                        Thread.sleep(20) // exercise the same capture/inference overlap in debug tests
                        count
                    } else rec!!.read(frame, 0, frame.size)
                    check(n > 0) { "microphone read failed ($n)" }
                    segmenter.add(frame, n)?.let { enqueue(s, it) }
                    if (s.backlog.isFull) { stopReason = VoiceProtocol.STOP_BACKLOG; break }
                }
                if (s.failure == null && !s.cancelled) segmenter.finish()?.let { enqueue(s, it) }
                if (!s.cancelled && s.failure == null) {
                    val ended = Message.obtain(null, VoiceProtocol.MSG_LIMIT).apply {
                        arg1 = s.id
                        data = Bundle().apply { putInt(VoiceProtocol.KEY_STOP_REASON, stopReason) }
                    }
                    try { s.client.send(ended) } catch (e: Exception) { Log.w(TAG, "client is gone", e) }
                    inference.execute { complete(s) }
                }
            } catch (t: Throwable) { if (!s.cancelled) fail(s, t) }
            finally {
                try { rec?.stop() } catch (_: Exception) {}
                rec?.release()
                s.recorder = null
            }
        }, "lafz-voice-record")
        s.capture = capture
        capture.start()
    }

    private fun enqueue(s: Session, pcm: ShortArray) {
        s.backlog.add(pcm.size)
        inference.execute {
            var started = 0L
            try {
                if (!s.cancelled && s.failure == null && !VoiceText.isSilent(pcm)) {
                    started = android.os.SystemClock.elapsedRealtime()
                    Log.i(TAG, "transcribing ${pcm.size * 1000L / VoiceProtocol.SAMPLE_RATE} ms audio")
                    val text = VoiceText.clean(checkNotNull(engine).transcribe(VoiceText.toFloat(pcm), s.cancellation))
                    Log.i(TAG, "phrase ${pcm.size * 1000L / VoiceProtocol.SAMPLE_RATE} ms audio, ${android.os.SystemClock.elapsedRealtime() - started} ms inference")
                    if (text.isNotEmpty()) reply(s, VoiceProtocol.MSG_PHRASE, VoiceProtocol.KEY_TEXT, text)
                }
            } catch (t: Throwable) {
                if (!s.cancelled) fail(s, t)
                else if (started != 0L) Log.i(TAG, "cancelled inference after ${android.os.SystemClock.elapsedRealtime() - started} ms")
            } finally { s.backlog.remove(pcm.size) }
        }
    }

    private fun ensureEngine(info: VoiceModelInfo) {
        if (engineDir == info.dirName && engine != null) return
        closeEngine()
        engine = SpeechEngine.create(this, info, VoiceModels.dir(this, info), VoiceModels.defaultThreads)
        engineDir = info.dirName
        Log.i(TAG, "loaded ${info.dirName}")
    }

    private fun cancelRecording() {
        val s = session ?: return
        s.cancelled = true
        s.stop = true
        // Abort the run without freeing the reusable engine from this control thread.
        abortInference(s)
        // Unblock a microphone read before joining.
        try { s.recorder?.stop() } catch (_: Exception) {}
        s.capture?.join(2000)
        session = null
    }

    private fun closeEngine() {
        if (engine != null) Log.i(TAG, "unloaded $engineDir")
        engine?.close(); engine = null; engineDir = null
    }

    /** debug builds: `files/voice_inject.pcm` (16 kHz mono 16 bit little endian) replaces the microphone, so the whole path can be tested on an emulator */
    private fun injectedAudio(): ShortArray? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        val f = File(filesDir, "voice_inject.pcm")
        if (!f.exists()) return null
        val b = f.readBytes()
        return ShortArray(b.size / 2) { ((b[2 * it].toInt() and 0xff) or (b[2 * it + 1].toInt() shl 8)).toShort() }
    }

    companion object {
        private const val TAG = "VoiceTypingService"
        fun intent(context: Context) = Intent(context, VoiceTypingService::class.java)
    }
}
