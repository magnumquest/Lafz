// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

/** Messages between the keyboard (client) and [VoiceTypingService] (own process `:voice`, so a loaded model never counts against the keyboard). */
object VoiceProtocol {
    /** client -> service; data: [KEY_MODEL_DIR]. Loads that model (and frees any other one) so that it is ready when the user taps the mic key. */
    const val MSG_LOAD = 8
    /** client -> service. Frees the model (dictation was switched off). */
    const val MSG_UNLOAD = 9
    /** client -> service; data: [KEY_MODEL_DIR]. Starts the microphone; loads the model if [MSG_LOAD] has not done so yet. */
    const val MSG_START = 1
    /** client -> service. Stops the microphone and drains queued phrases; answered by [MSG_RESULT] or [MSG_ERROR]. */
    const val MSG_STOP = 2
    /** client -> service. Drops the recording without a result. */
    const val MSG_CANCEL = 3
    /** service -> client; data: [KEY_TEXT] (may be empty if nothing was heard) */
    const val MSG_RESULT = 4
    /** service -> client; data: [KEY_ERROR] */
    const val MSG_ERROR = 5
    /** service -> client: capture ended; [KEY_STOP_REASON] distinguishes a requested stop from backlog termination */
    const val MSG_LIMIT = 6
    /** service -> client: the microphone is open */
    const val MSG_LISTENING = 7
    /** A completed phrase; the microphone continues listening. arg1 identifies the session on every message. */
    const val MSG_PHRASE = 10

    const val KEY_STOP_REASON = "stop_reason"
    const val STOP_REQUESTED = 0
    const val STOP_BACKLOG = 1

    const val KEY_MODEL_DIR = "model_dir"
    const val KEY_TEXT = "text"
    const val KEY_ERROR = "error"

    const val SAMPLE_RATE = 16000
}
