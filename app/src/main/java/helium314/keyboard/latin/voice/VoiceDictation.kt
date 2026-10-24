// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.util.Locale

/**
 * The keyboard side of dictation (mic key): tap to start, tap again to stop; the text is handed to [Listener] when it is ready.
 * Everything runs in [VoiceTypingService]; this class only talks to it, and only while dictating (plus a short idle time).
 * Main thread only.
 */
class VoiceDictation(private val context: Context, private val listener: Listener) {
    interface Listener {
        fun onVoiceStateChanged(state: State)
        fun onVoiceText(text: String)
        /** [message] is shown to the user */
        fun onVoiceError(message: String)
    }

    enum class State { IDLE, LISTENING, TRANSCRIBING }

    var state = State.IDLE
        private set

    private val main = Handler(Looper.getMainLooper())
    private var service: Messenger? = null
    private var bound = false
    private var pendingStart: String? = null
    private val unbindLater = Runnable { unbind() }

    private val incoming = Messenger(Handler(Looper.getMainLooper()) { msg -> onMessage(msg); true })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = Messenger(binder)
            pendingStart?.let { pendingStart = null; send(VoiceProtocol.MSG_START, VoiceProtocol.KEY_MODEL_DIR, it) }
        }
        override fun onServiceDisconnected(name: ComponentName) { // the :voice process died (probably out of memory)
            service = null; bound = false
            if (state != State.IDLE) { setState(State.IDLE); listener.onVoiceError(context.getString(R.string.voice_dictation_failed)) }
        }
    }

    /** the mic key: starts listening, or stops and transcribes */
    fun toggle(locale: Locale) {
        when (state) {
            State.IDLE -> start(locale)
            State.LISTENING -> { setState(State.TRANSCRIBING); send(VoiceProtocol.MSG_STOP) }
            State.TRANSCRIBING -> {}
        }
    }

    fun cancel() {
        if (state == State.IDLE) return
        send(VoiceProtocol.MSG_CANCEL)
        pendingStart = null
        setState(State.IDLE)
    }

    fun destroy() {
        cancel()
        unbind()
    }

    private fun start(locale: Locale) {
        val language = VoiceText.modelLanguage(locale)
        val model = language?.let { l -> VoiceModels.installed(context).firstOrNull { it.language == l } }
        if (model == null) return listener.onVoiceError(context.getString(
            if (language == null) R.string.voice_dictation_language else R.string.voice_dictation_no_model, locale.displayLanguage))
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            context.startActivity(Intent(context, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        main.removeCallbacks(unbindLater)
        setState(State.LISTENING)
        if (service != null) send(VoiceProtocol.MSG_START, VoiceProtocol.KEY_MODEL_DIR, model.dirName)
        else {
            pendingStart = model.dirName
            if (!bound) bound = context.bindService(VoiceTypingService.intent(context), connection, Context.BIND_AUTO_CREATE)
            if (!bound) { pendingStart = null; setState(State.IDLE); listener.onVoiceError(context.getString(R.string.voice_dictation_failed)) }
        }
    }

    private fun send(what: Int, key: String? = null, value: String? = null) {
        val m = Message.obtain(null, what)
        m.replyTo = incoming
        if (key != null) m.data = android.os.Bundle().apply { putString(key, value) }
        try { service?.send(m) } catch (e: Exception) { Log.w(TAG, "cannot reach the voice service: ${e.message}") }
    }

    private fun onMessage(msg: Message) {
        when (msg.what) {
            VoiceProtocol.MSG_LIMIT -> setState(State.TRANSCRIBING)
            VoiceProtocol.MSG_RESULT -> {
                val wasCancelled = state == State.IDLE
                setState(State.IDLE)
                scheduleUnbind()
                if (!wasCancelled) msg.data.getString(VoiceProtocol.KEY_TEXT)?.takeIf { it.isNotEmpty() }?.let { listener.onVoiceText(it) }
            }
            VoiceProtocol.MSG_ERROR -> {
                setState(State.IDLE)
                scheduleUnbind()
                listener.onVoiceError(msg.data.getString(VoiceProtocol.KEY_ERROR) ?: context.getString(R.string.voice_dictation_failed))
            }
        }
    }

    private fun setState(s: State) {
        if (s == state) return
        state = s
        listener.onVoiceStateChanged(s)
    }

    private fun scheduleUnbind() {
        main.removeCallbacks(unbindLater)
        main.postDelayed(unbindLater, VoiceProtocol.IDLE_UNLOAD_MS)
    }

    private fun unbind() {
        if (!bound) return
        try { context.unbindService(connection) } catch (_: Exception) {}
        bound = false; service = null
    }

    companion object {
        private const val TAG = "VoiceDictation"

        /** the Settings switch; the mic key then dictates instead of handing over to another voice keyboard */
        fun isEnabled(context: Context) =
            VoiceModels.isSupported && context.prefs().getBoolean(Settings.PREF_VOICE_DICTATION, Defaults.PREF_VOICE_DICTATION)
    }
}
