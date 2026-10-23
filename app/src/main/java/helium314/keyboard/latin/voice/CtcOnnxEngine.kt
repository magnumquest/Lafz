// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Speech recognition with a CTC model in ONNX format (waveform in, per-frame token scores out) and greedy decoding.
 * Nothing is loaded until an engine is created, and [close] gives all of the memory back.
 * The model folder is described by a [VoiceModelInfo] (see [VoiceModels]).
 */
class CtcOnnxEngine(private val info: VoiceModelInfo, dir: File, threads: Int) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val tokens: Array<String>

    init {
        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(threads.coerceIn(1, 8))
            options.setInterOpNumThreads(1)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            session = env.createSession(File(dir, info.modelFile).path, options)
        } finally {
            options.close()
        }
        inputName = session.inputNames.first()
        tokens = readTokens(File(dir, info.tokensFile))
    }

    /** [samples]: mono audio at the model's sample rate, values in -1..1. Long audio is processed in 30 s pieces. */
    fun transcribe(samples: FloatArray): String {
        val piece = 30 * info.sampleRate
        val parts = ArrayList<String>()
        var start = 0
        while (start < samples.size) {
            val end = minOf(samples.size, start + piece)
            if (end - start >= info.sampleRate / 10) // anything shorter than 0.1 s is noise
                parts.add(transcribeChunk(samples.copyOfRange(start, end)))
            start = end
        }
        return parts.filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun transcribeChunk(x: FloatArray): String {
        if (info.normalize) { // zero mean, unit variance: the models are trained on normalised audio
            var sum = 0.0
            for (v in x) sum += v
            val mean = sum / x.size
            var sq = 0.0
            for (v in x) { val d = v - mean; sq += d * d }
            val inv = 1.0 / (sqrt(sq / x.size) + 1e-7)
            for (i in x.indices) x[i] = ((x[i] - mean) * inv).toFloat()
        }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(x), longArrayOf(1, x.size.toLong())).use { input ->
            session.run(mapOf(inputName to input)).use { result ->
                val logits = result.get(0) as OnnxTensor
                val shape = logits.info.shape // [1, frames, vocabulary]
                val frames = shape[1].toInt()
                val vocab = shape[2].toInt()
                return greedy(logits.floatBuffer, frames, vocab)
            }
        }
    }

    private fun greedy(scores: FloatBuffer, frames: Int, vocab: Int): String {
        val sb = StringBuilder()
        var previous = -1
        for (t in 0 until frames) {
            val base = t * vocab
            var best = 0
            var bestScore = scores.get(base)
            for (v in 1 until vocab) {
                val s = scores.get(base + v)
                if (s > bestScore) { bestScore = s; best = v }
            }
            if (best != previous && best != info.blankId && best !in info.skipIds && best < tokens.size) sb.append(tokens[best])
            previous = best
        }
        return sb.toString().replace('▁', ' ').trim().replace(Regex(" +"), " ")
    }

    override fun close() { session.close() }

    companion object {
        /** lines of "token id"; the token itself may be a space, so the id is what follows the last space */
        fun readTokens(file: File): Array<String> {
            val map = HashMap<Int, String>()
            file.forEachLine(Charsets.UTF_8) { line ->
                val i = line.lastIndexOf(' ')
                if (i >= 0) line.substring(i + 1).toIntOrNull()?.let { map[it] = line.substring(0, i) }
            }
            return Array((map.keys.maxOrNull() ?: -1) + 1) { map[it] ?: "" }
        }
    }
}
