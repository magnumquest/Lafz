// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class VoiceDictationTest {
    private val requests = mutableListOf<Message>()
    private val text = mutableListOf<String>()
    private val notices = mutableListOf<String>()
    private val errors = mutableListOf<String>()
    private lateinit var connection: ServiceConnection
    private lateinit var dictation: VoiceDictation
    private lateinit var context: Context
    private val remote = Messenger(Handler(Looper.getMainLooper()) {
        requests.add(Message.obtain(it)); true
    })

    @Before fun setup() {
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun checkSelfPermission(permission: String) = PackageManager.PERMISSION_GRANTED
            override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
                connection = conn
                return true
            }
            override fun unbindService(conn: ServiceConnection) {}
        }
        val dir = File(VoiceModels.root(context), "test_en").apply { mkdirs() }
        File(dir, "m.bin").writeBytes(byteArrayOf(1))
        File(dir, "model.json").writeText("""{"language":"en","type":"whisper_cpp","model":"m.bin"}""")
        dictation = VoiceDictation(context, object : VoiceDictation.Listener {
            override fun onVoiceStateChanged(state: VoiceDictation.State) {}
            override fun onVoiceText(value: String) { text.add(value) }
            override fun onVoiceError(message: String) { errors.add(message) }
            override fun onVoiceNotice(message: String) { notices.add(message) }
        })
    }

    @After fun cleanup() {
        dictation.destroy()
        File(VoiceModels.root(context), "test_en").deleteRecursively()
    }

    private fun connect() {
        connection.onServiceConnected(ComponentName(context, VoiceTypingService::class.java), remote.binder)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun reply(start: Message, what: Int, value: String = "", reason: Int = VoiceProtocol.STOP_REQUESTED) {
        start.replyTo.send(Message.obtain(null, what).apply {
            arg1 = start.arg1
            data = Bundle().apply { putString(VoiceProtocol.KEY_TEXT, value); putInt(VoiceProtocol.KEY_STOP_REASON, reason) }
        })
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun phrasesInsertWhileListeningAndDuringFinalDrain() {
        dictation.toggle(Locale.US)
        connect()
        val start = requests.last()
        reply(start, VoiceProtocol.MSG_PHRASE, "First sentence.")
        assertEquals(VoiceDictation.State.LOCKED, dictation.state)
        dictation.toggle(Locale.US)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(VoiceProtocol.MSG_STOP, requests.last().what)
        reply(start, VoiceProtocol.MSG_PHRASE, "Second sentence.")
        assertEquals(VoiceDictation.State.TRANSCRIBING, dictation.state)
        reply(start, VoiceProtocol.MSG_RESULT)
        assertEquals(VoiceDictation.State.IDLE, dictation.state)
        assertEquals(listOf("First sentence.", "Second sentence."), text)
    }

    @Test fun cancelledSessionCannotWriteIntoRestartedSession() {
        dictation.toggle(Locale.US)
        connect()
        val old = requests.last()
        dictation.cancel()
        dictation.toggle(Locale.US)
        shadowOf(Looper.getMainLooper()).idle()
        val current = requests.last()
        reply(old, VoiceProtocol.MSG_PHRASE, "Stale text")
        reply(old, VoiceProtocol.MSG_ERROR)
        reply(old, VoiceProtocol.MSG_RESULT)
        assertEquals(VoiceDictation.State.LOCKED, dictation.state)
        assertEquals(emptyList(), text)
        assertEquals(emptyList(), errors)
        reply(current, VoiceProtocol.MSG_PHRASE, "Current text")
        assertEquals(listOf("Current text"), text)
    }

    @Test fun releaseInsertsHeldPhrasesAndFinishesWithoutAnotherTap() {
        dictation.press(Locale.US)
        connect()
        val start = requests.last()
        reply(start, VoiceProtocol.MSG_PHRASE, "First sentence.")
        reply(start, VoiceProtocol.MSG_PHRASE, "Second sentence.")
        assertEquals(emptyList(), text)
        dictation.release()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(VoiceProtocol.MSG_STOP, requests.last().what)
        assertEquals(VoiceDictation.State.TRANSCRIBING, dictation.state)
        assertEquals(listOf("First sentence. Second sentence."), text)
        reply(start, VoiceProtocol.MSG_PHRASE, "Final words.")
        reply(start, VoiceProtocol.MSG_RESULT)
        assertEquals(listOf("First sentence. Second sentence.", "Final words."), text)
        assertEquals(VoiceDictation.State.IDLE, dictation.state)
    }

    @Test fun swipeLocksAndLateMicrophoneReadyDoesNotUnlock() {
        dictation.press(Locale.US)
        connect()
        val start = requests.last()
        reply(start, VoiceProtocol.MSG_PHRASE, "Held text")
        dictation.lock()
        assertEquals(listOf("Held text"), text)
        dictation.release()
        reply(start, VoiceProtocol.MSG_LISTENING)
        assertEquals(VoiceDictation.State.LOCKED, dictation.state)
        reply(start, VoiceProtocol.MSG_PHRASE, "More text")
        assertEquals(listOf("Held text", "More text"), text)
        dictation.press(Locale.US)
        dictation.release()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(VoiceProtocol.MSG_STOP, requests.last().what)
        assertEquals(VoiceDictation.State.TRANSCRIBING, dictation.state)
    }

    @Test fun cancelledHoldDiscardsBufferedWords() {
        dictation.press(Locale.US)
        connect()
        val start = requests.last()
        reply(start, VoiceProtocol.MSG_PHRASE, "Do not insert")
        dictation.cancel()
        dictation.press(Locale.US)
        dictation.release()
        assertEquals(emptyList(), text)
        reply(start, VoiceProtocol.MSG_PHRASE, "Stale text")
        assertEquals(emptyList(), text)
    }

    @Test fun recordingErrorPreservesCompletedHeldText() {
        dictation.press(Locale.US)
        connect()
        val start = requests.last()
        reply(start, VoiceProtocol.MSG_PHRASE, "Completed sentence.")
        reply(start, VoiceProtocol.MSG_ERROR)
        assertEquals(listOf("Completed sentence."), text)
        assertEquals(1, errors.size)
        assertEquals(VoiceDictation.State.IDLE, dictation.state)
        reply(start, VoiceProtocol.MSG_ERROR)
        assertEquals(1, errors.size)
        assertEquals(1, text.size)
    }

    @Test fun voiceProcessDeathPreservesDecodedHeldWords() {
        dictation.press(Locale.US)
        connect()
        reply(requests.last(), VoiceProtocol.MSG_PHRASE, "Recovered sentence.")
        connection.onServiceDisconnected(ComponentName(context, VoiceTypingService::class.java))
        assertEquals(listOf("Recovered sentence."), text)
        assertEquals(1, errors.size)
        assertEquals(VoiceDictation.State.IDLE, dictation.state)
    }

    @Test fun backlogStopExplainsWhyCaptureEndedAndStillDrainsText() {
        dictation.press(Locale.US)
        connect()
        val start = requests.last()
        reply(start, VoiceProtocol.MSG_PHRASE, "Already decoded.")
        reply(start, VoiceProtocol.MSG_LIMIT, reason = VoiceProtocol.STOP_BACKLOG)
        assertEquals(VoiceDictation.State.TRANSCRIBING, dictation.state)
        assertEquals(1, notices.size)
        assertEquals(emptyList(), errors)
        assertEquals(listOf("Already decoded."), text)
        reply(start, VoiceProtocol.MSG_PHRASE, "Queued phrase.")
        reply(start, VoiceProtocol.MSG_RESULT)
        assertEquals(listOf("Already decoded.", "Queued phrase."), text)
        assertEquals(VoiceDictation.State.IDLE, dictation.state)
    }

    @Test fun requestedStopDoesNotShowAnOverloadNotice() {
        dictation.press(Locale.US)
        connect()
        reply(requests.last(), VoiceProtocol.MSG_LIMIT)
        assertEquals(emptyList(), notices)
    }

    @Test fun releasingBeforeConnectionDeliversStopAfterStart() {
        dictation.press(Locale.US)
        dictation.release()
        connect()
        assertEquals(listOf(VoiceProtocol.MSG_START, VoiceProtocol.MSG_STOP), requests.map { it.what })
    }

    @Test fun stoppingBeforeServiceConnectsStillStopsCapture() {
        dictation.toggle(Locale.US)
        dictation.toggle(Locale.US)
        connect()
        assertEquals(listOf(VoiceProtocol.MSG_START, VoiceProtocol.MSG_STOP), requests.map { it.what })
        assertEquals(requests.first().arg1, requests.last().arg1)
    }
}
