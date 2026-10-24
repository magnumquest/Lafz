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
 * Everything runs in [VoiceTypingService]; this class only talks to it. The service is only bound while dictation is switched on
 * ([sync]), and then holds the model of the keyboard's current language. Main thread only.
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

    private var service: Messenger? = null
    private var bound = false
    private var pendingStart: String? = null
    /** the model the service was last asked to load (null: none, dictation is off or has no model for this language) */
    private var wantedModel: String? = null

    private val incoming = Messenger(Handler(Looper.getMainLooper()) { msg -> onMessage(msg); true })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = Messenger(binder)
            val start = pendingStart; pendingStart = null
            if (start != null) send(VoiceProtocol.MSG_START, VoiceProtocol.KEY_MODEL_DIR, start)
            else wantedModel?.let { send(VoiceProtocol.MSG_LOAD, VoiceProtocol.KEY_MODEL_DIR, it) }
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
        wantedModel = null
        unbind()
    }

    /**
     * Makes the loaded model the one for [locale], if dictation is switched on: loads it (the old one is freed first), or frees everything if dictation is off
     * or there is no model for the language. Call when the keyboard starts, the language changes or the setting changes. Cheap if nothing changed.
     */
    fun sync(locale: Locale) {
        val wanted = if (isEnabled(context)) modelFor(locale)?.dirName else null
        if (wanted == wantedModel) return
        wantedModel = wanted
        if (wanted == null) {
            if (state != State.IDLE) cancel()
            if (service != null) send(VoiceProtocol.MSG_UNLOAD)
            unbind()
            return
        }
        if (state != State.IDLE) return // a recording is running; it uses the model it started with, the next sync/toggle follows up
        if (service != null) send(VoiceProtocol.MSG_LOAD, VoiceProtocol.KEY_MODEL_DIR, wanted)
        else if (!bound) bound = context.bindService(VoiceTypingService.intent(context), connection, Context.BIND_AUTO_CREATE)
    }

    private fun modelFor(locale: Locale): VoiceModelInfo? =
        VoiceText.modelLanguage(locale)?.let { l -> VoiceModels.installed(context).firstOrNull { it.language == l } }

    private fun start(locale: Locale) {
        val language = VoiceText.modelLanguage(locale)
        val model = modelFor(locale)
        if (model == null) return listener.onVoiceError(context.getString(
            if (language == null) R.string.voice_dictation_language else R.string.voice_dictation_no_model, locale.displayLanguage))
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            context.startActivity(Intent(context, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        wantedModel = model.dirName
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
                if (!wasCancelled) msg.data.getString(VoiceProtocol.KEY_TEXT)?.takeIf { it.isNotEmpty() }?.let { listener.onVoiceText(it) }
            }
            VoiceProtocol.MSG_ERROR -> {
                setState(State.IDLE)
                listener.onVoiceError(msg.data.getString(VoiceProtocol.KEY_ERROR) ?: context.getString(R.string.voice_dictation_failed))
            }
        }
    }

    private fun setState(s: State) {
        if (s == state) return
        state = s
        listener.onVoiceStateChanged(s)
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
