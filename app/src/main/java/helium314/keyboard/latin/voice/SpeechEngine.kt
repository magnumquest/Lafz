// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import app.lafz.whisper.WhisperLib
import java.io.File

/** A loaded speech recognition model. [close] gives all of its memory back. */
interface SpeechEngine : AutoCloseable {
    /** [samples]: mono audio at the model's sample rate, values in -1..1. [partial]: a quick, tentative result for the words
     *  heard so far while the user is still speaking (may be less accurate; replaced by a normal run at the end of the phrase) */
    fun transcribe(samples: FloatArray, cancellation: SpeechCancellation = SpeechCancellation(), partial: Boolean = false): String

    companion object {
        /** loads the model described by [info]; throws if it cannot run on this device */
        fun create(context: Context, info: VoiceModelInfo, dir: File, threads: Int, beamSize: Int = info.beamSize): SpeechEngine = when (info.type) {
            VoiceModels.TYPE_CTC_ONNX -> CtcOnnxEngine(info, dir, threads)
            VoiceModels.TYPE_WHISPER_CPP -> WhisperEngine(context, info, dir, threads, beamSize)
            else -> throw IllegalArgumentException("unknown model type ${info.type}")
        }
    }
}

/** Whisper through whisper.cpp (GGML model file, e.g. q5_1 quantised). The native libraries are loaded the first time one is created. */
class WhisperEngine(context: Context, private val info: VoiceModelInfo, dir: File, private val threads: Int, private val beamSize: Int) : SpeechEngine {
    private val model: WhisperLib.Model

    init {
        if (!WhisperLib.available(context)) throw UnsupportedOperationException("Whisper is not available: ${WhisperLib.unavailableReason()}")
        model = WhisperLib.Model(File(dir, info.modelFile))
    }

    override fun transcribe(samples: FloatArray, cancellation: SpeechCancellation, partial: Boolean): String {
        cancellation.throwIfCancelled()
        // whisper pads every input to 30 s (1500 encoder frames, 50 per second), so a short phrase costs as much as a long one.
        // partial: greedy, window just over the audio (at least 256 frames, shorter windows hallucinate).
        // final: audio + 2 s, at least 768 frames (15 s); screened 2026-11-12 on 8 English + 8 Urdu clips: English error rate
        // unchanged, Urdu 9.1 -> 8.5 % CER in total (a few single clips slightly worse), several times faster for short phrases
        val frames = samples.size * 50L / 16000
        val audioCtx = if (partial) ((frames + 32 + 63) / 64 * 64).toInt().coerceIn(256, 1500)
            else ((frames + 100 + 63) / 64 * 64).toInt().coerceIn(768, 1500)
        return WhisperLib.Cancellation().use { token ->
            cancellation.withHook(token::cancel) {
                try { model.transcribe(samples, info.language, threads, if (partial) 1 else beamSize, token, audioCtx, partial) }
                catch (t: Exception) { cancellation.throwIfCancelled(); throw t }
            }
        }
    }

    override fun close() = model.close()
}
