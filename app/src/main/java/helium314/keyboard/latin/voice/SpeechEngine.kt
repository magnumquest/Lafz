// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import app.lafz.whisper.WhisperLib
import java.io.File

/** A loaded speech recognition model. [close] gives all of its memory back. */
interface SpeechEngine : AutoCloseable {
    /** [samples]: mono audio at the model's sample rate, values in -1..1 */
    fun transcribe(samples: FloatArray): String

    companion object {
        /** loads the model described by [info]; throws if it cannot run on this device */
        fun create(context: Context, info: VoiceModelInfo, dir: File, threads: Int): SpeechEngine = when (info.type) {
            VoiceModels.TYPE_CTC_ONNX -> CtcOnnxEngine(info, dir, threads)
            VoiceModels.TYPE_WHISPER_CPP -> WhisperEngine(context, info, dir, threads)
            else -> throw IllegalArgumentException("unknown model type ${info.type}")
        }
    }
}

/** Whisper through whisper.cpp (GGML model file, e.g. q5_1 quantised). The native libraries are loaded the first time one is created. */
class WhisperEngine(context: Context, private val info: VoiceModelInfo, dir: File, private val threads: Int) : SpeechEngine {
    private val model: WhisperLib.Model

    init {
        if (!WhisperLib.available(context)) throw UnsupportedOperationException("Whisper is not available: ${WhisperLib.unavailableReason()}")
        model = WhisperLib.Model(File(dir, info.modelFile))
    }

    override fun transcribe(samples: FloatArray): String = model.transcribe(samples, info.language, threads, info.beamSize)

    override fun close() = model.close()
}
