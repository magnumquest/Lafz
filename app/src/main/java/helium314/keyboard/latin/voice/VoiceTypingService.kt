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

/**
 * Records from the microphone and runs the speech model, in its own process (`:voice`, see the manifest).
 * The model is loaded while the user is still talking and freed [VoiceProtocol.IDLE_UNLOAD_MS] after the last dictation or when the keyboard unbinds.
 * Nothing leaves the device; the service has no network access of any kind.
 */
class VoiceTypingService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var messenger: Messenger

    // everything below is only touched on `thread`
    private var engine: SpeechEngine? = null
    private var engineDir: String? = null
    private var client: Messenger? = null
    private var recorder: AudioRecord? = null
    private var recording: Thread? = null
    @Volatile private var stopRequested = false
    private val unload = Runnable { closeEngine() }

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("lafz-voice").also { it.start() }
        handler = Handler(thread.looper) { handle(it); true }
        messenger = Messenger(handler)
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        handler.post { cancelRecording(); closeEngine() }
        return false
    }

    override fun onDestroy() {
        thread.quitSafely()
        engine?.close()
        super.onDestroy()
    }

    private fun handle(msg: Message) {
        when (msg.what) {
            VoiceProtocol.MSG_START -> {
                client = msg.replyTo
                start(msg.data.getString(VoiceProtocol.KEY_MODEL_DIR) ?: return reply(VoiceProtocol.MSG_ERROR, VoiceProtocol.KEY_ERROR, "no model"))
            }
            VoiceProtocol.MSG_STOP -> { client = msg.replyTo ?: client; stopAndTranscribe() }
            VoiceProtocol.MSG_CANCEL -> cancelRecording()
        }
    }

    private fun reply(what: Int, key: String? = null, value: String? = null) {
        val m = Message.obtain(null, what)
        if (key != null) m.data = Bundle().apply { putString(key, value) }
        try { client?.send(m) } catch (e: Exception) { Log.w(TAG, "client is gone: ${e.message}") }
    }

    private fun start(modelDir: String) {
        handler.removeCallbacks(unload)
        cancelRecording()
        val info = VoiceModels.installed(this).firstOrNull { it.dirName == modelDir } ?: return reply(VoiceProtocol.MSG_ERROR, VoiceProtocol.KEY_ERROR, "model $modelDir not found")
        val pcm = ShortArray(VoiceProtocol.SAMPLE_RATE * VoiceProtocol.MAX_SECONDS)
        val injected = injectedAudio()
        if (injected == null) {
            val minBuffer = AudioRecord.getMinBufferSize(VoiceProtocol.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.MIC, VoiceProtocol.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, 16384) * 2)
            } catch (e: Exception) { return reply(VoiceProtocol.MSG_ERROR, VoiceProtocol.KEY_ERROR, "microphone: ${e.message}") }
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                return reply(VoiceProtocol.MSG_ERROR, VoiceProtocol.KEY_ERROR, "microphone not available (permission?)")
            }
            recorder = rec
        }
        stopRequested = false
        recorded = 0
        samples = pcm
        reply(VoiceProtocol.MSG_LISTENING)
        // the recording runs on its own thread so that loading the model (on `thread`) does not make us miss audio
        val rec = recorder
        recording = Thread({
            if (injected != null) { // debug builds only: audio from a file instead of the microphone
                val n = minOf(injected.size, pcm.size); System.arraycopy(injected, 0, pcm, 0, n); recorded = n
                while (!stopRequested) Thread.sleep(20)
            } else if (rec != null) {
                rec.startRecording()
                while (!stopRequested && recorded < pcm.size) {
                    val n = rec.read(pcm, recorded, minOf(4096, pcm.size - recorded))
                    if (n <= 0) break
                    recorded += n
                }
                try { rec.stop() } catch (_: Exception) {}
                if (!stopRequested) handler.post { reply(VoiceProtocol.MSG_LIMIT); stopAndTranscribe() }
            }
        }, "lafz-voice-record").also { it.start() }
        ensureEngine(info)
    }

    @Volatile private var recorded = 0
    private var samples: ShortArray? = null

    private fun ensureEngine(info: VoiceModelInfo) {
        if (engineDir == info.dirName && engine != null) return
        closeEngine()
        try {
            engine = SpeechEngine.create(this, info, VoiceModels.dir(this, info), VoiceModels.defaultThreads)
            engineDir = info.dirName
        } catch (t: Throwable) {
            Log.e(TAG, "cannot load ${info.name}", t)
            cancelRecording()
            reply(VoiceProtocol.MSG_ERROR, VoiceProtocol.KEY_ERROR, "cannot load ${info.name}: ${t.message}")
        }
    }

    private fun stopAndTranscribe() {
        val rec = recording
        val pcm = samples
        if (rec == null || pcm == null) return // nothing running (already stopped by the limit)
        stopRequested = true
        rec.join(2000)
        recording = null
        recorder?.release(); recorder = null
        samples = null
        val n = recorded
        val e = engine
        if (e == null) return // an error was already reported
        if (n < VoiceProtocol.SAMPLE_RATE / 4 || VoiceText.isSilent(pcm, n)) {
            reply(VoiceProtocol.MSG_RESULT, VoiceProtocol.KEY_TEXT, "")
        } else try {
            val text = VoiceText.clean(e.transcribe(VoiceText.toFloat(pcm, n)))
            reply(VoiceProtocol.MSG_RESULT, VoiceProtocol.KEY_TEXT, text)
        } catch (t: Throwable) {
            Log.e(TAG, "transcribe failed", t)
            reply(VoiceProtocol.MSG_ERROR, VoiceProtocol.KEY_ERROR, "transcribing failed: ${t.message}")
        }
        handler.removeCallbacks(unload)
        handler.postDelayed(unload, VoiceProtocol.IDLE_UNLOAD_MS)
    }

    private fun cancelRecording() {
        stopRequested = true
        recording?.join(2000); recording = null
        recorder?.release(); recorder = null
        samples = null
    }

    private fun closeEngine() {
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
