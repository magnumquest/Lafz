// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import app.lafz.whisper.WhisperLib
import java.io.File
import java.text.Normalizer

/**
 * The speed and accuracy test in Settings. For every imported model it transcribes the test clips of the model's language (the folder "tests" that
 * was imported with the models, else the one bundled clip) and reports load time, memory, real-time factor, word and character error rate.
 * The full test repeats this for several settings (Whisper: beam search vs greedy, 4 vs all threads) so the best default can be chosen from the results.
 * Everything that is logged is also kept in [report] so it can be saved to a file.
 */
object VoiceBenchmark {
    class Clip(val name: String, val audio: FloatArray, val reference: String)

    private class Setting(val label: String, val beamSize: Int, val threads: Int)

    fun clips(context: Context, language: String): List<Clip> {
        val dir = VoiceModels.testsDir(context)
        val imported = dir.listFiles { f -> f.name.startsWith(language + "_") && f.name.endsWith(".wav") }?.sortedBy { it.name }
            ?.mapNotNull { wav ->
                val txt = File(dir, wav.name.removeSuffix(".wav") + ".txt")
                if (txt.isFile) Clip(wav.name, VoicePerfTest.readWav(wav.readBytes()), txt.readText().trim()) else null
            } ?: emptyList()
        if (imported.isNotEmpty()) return imported
        return (context.assets.list("voice_samples") ?: emptyArray()).filter { it.startsWith(language + "_") && it.endsWith(".wav") }.sorted().map {
            Clip(it, VoicePerfTest.readWav(context.assets.open("voice_samples/$it").readBytes()),
                context.assets.open("voice_samples/${it.removeSuffix(".wav")}.txt").bufferedReader().readText().trim())
        }
    }

    fun deviceInfo(context: Context): String {
        val cpu = try {
            File("/proc/cpuinfo").readLines().filter { it.startsWith("Features") || it.startsWith("CPU part") || it.startsWith("Hardware") || it.startsWith("model name") }
                .distinct().joinToString("; ")
        } catch (e: Exception) { "?" }
        val mem = try { File("/proc/meminfo").readLines().first { it.startsWith("MemTotal") }.filter { it.isDigit() }.toLong() / 1024 } catch (e: Exception) { 0L }
        val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
        return "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.SUPPORTED_ABIS.joinToString()}, $soc, " +
            "${Runtime.getRuntime().availableProcessors()} cores, $mem MB RAM\n  cpu: $cpu"
    }

    /** [full]: all clips and several settings per model; otherwise 3 clips with the model's own settings */
    fun run(context: Context, models: List<VoiceModelInfo>, full: Boolean, log: (String) -> Unit) {
        log("Lafz voice test ${if (full) "(full)" else "(quick)"} ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        log(deviceInfo(context))
        val cores = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        for (info in models) {
            val clips = clips(context, info.language).let { if (full) it else it.take(3) }
            log("")
            log("== ${info.name} (${info.language}, ${info.type}, ${info.sizeBytes shr 20} MB), ${clips.size} clips, ${"%.0f".format(clips.sumOf { it.audio.size }.toDouble() / info.sampleRate)} s of audio")
            if (clips.isEmpty()) { log("  no test clips for '${info.language}'"); continue }
            val whisper = info.type == VoiceModels.TYPE_WHISPER_CPP
            val four = minOf(4, cores)
            val settings = if (!full) listOf(Setting("beam ${info.beamSize}, $four threads".takeIf { whisper } ?: "$four threads", info.beamSize, four))
            else if (whisper) listOfNotNull(
                Setting("beam ${info.beamSize}, $four threads", info.beamSize, four),
                Setting("greedy, $four threads", 1, four),
                Setting("greedy, $cores threads", 1, cores).takeIf { cores != four },
            ) else listOfNotNull(Setting("$four threads", 0, four), Setting("$cores threads", 0, cores).takeIf { cores != four })
            for (s in settings) runSetting(context, info, clips, s, log)
        }
        log("")
        log("done")
    }

    private fun runSetting(context: Context, info: VoiceModelInfo, clips: List<Clip>, s: Setting, log: (String) -> Unit) {
        log("-- ${s.label}")
        val rssBefore = VoicePerfTest.residentMb()
        val t0 = System.nanoTime()
        val engine = try { SpeechEngine.create(context, info, VoiceModels.dir(context, info), s.threads, s.beamSize) }
        catch (t: Throwable) { log("  could not load: ${t.message}"); return }
        engine.use {
            if (info.type == VoiceModels.TYPE_WHISPER_CPP) log("  whisper.cpp: ${WhisperLib.systemInfo().trim()}")
            log("  loaded in ${(System.nanoTime() - t0) / 1_000_000} ms, memory ${VoicePerfTest.residentMb() - rssBefore} MB more than before")
            var audioSeconds = 0.0; var wallSeconds = 0.0
            var wordErrors = 0; var wordTotal = 0; var charErrors = 0; var charTotal = 0
            var peak = 0L
            for ((i, clip) in clips.withIndex()) {
                val seconds = clip.audio.size.toDouble() / info.sampleRate
                val t = System.nanoTime()
                val text = try { engine.transcribe(clip.audio.copyOf()) } catch (e: Throwable) { log("  ${clip.name} failed: ${e.message}"); return }
                val took = (System.nanoTime() - t) / 1e9
                peak = maxOf(peak, VoicePerfTest.residentMb() - rssBefore)
                audioSeconds += seconds; wallSeconds += took
                val ref = normalize(clip.reference, info.language).split(' ').filter { it.isNotEmpty() }
                val hyp = normalize(text, info.language).split(' ').filter { it.isNotEmpty() }
                val we = editDistance(ref, hyp); wordErrors += we; wordTotal += ref.size
                val refC = ref.joinToString(" "); val hypC = hyp.joinToString(" ")
                val ce = editDistance(refC.toList(), hypC.toList()); charErrors += ce; charTotal += refC.length
                log("  ${clip.name}${if (i == 0) " (first run, includes warm-up)" else ""}: ${"%.1f".format(seconds)} s audio in ${"%.2f".format(took)} s (x${"%.2f".format(took / seconds)}), words wrong $we/${ref.size}")
                log("    ref:  ${clip.reference}")
                log("    heard: $text")
            }
            log("  RESULT ${info.name} | ${s.label}: x${"%.2f".format(wallSeconds / audioSeconds)} real time (${"%.1f".format(wallSeconds)} s for ${"%.0f".format(audioSeconds)} s of audio), " +
                "WER ${"%.1f".format(100.0 * wordErrors / maxOf(1, wordTotal))} %, CER ${"%.1f".format(100.0 * charErrors / maxOf(1, charTotal))} %, peak memory +$peak MB")
        }
        log("  memory after unloading: ${VoicePerfTest.residentMb() - rssBefore} MB more than before")
    }

    // same normalisation as the desktop evaluation (tools/voice-spike/evalcommon.py) so the numbers are comparable
    private val urduMap = mapOf('ي' to 'ی', 'ى' to 'ی', 'ك' to 'ک', 'ه' to 'ہ', 'ة' to 'ہ', 'ۃ' to 'ہ', 'ھ' to 'ہ', 'ۂ' to 'ہ', 'أ' to 'ا', 'إ' to 'ا', 'آ' to 'ا',
        'ٱ' to 'ا', 'ؤ' to 'و', 'ئ' to 'ی', 'ے' to 'ی', 'ې' to 'ی', 'ۓ' to 'ی')
    private val arabicMap = mapOf('ى' to 'ي', 'ة' to 'ه', 'أ' to 'ا', 'إ' to 'ا', 'آ' to 'ا', 'ٱ' to 'ا')
    private val diacritics = Regex("[\\u064B-\\u0670\\u06D6-\\u06ED\\u0640]")
    private val punctuation = Regex("[^\\p{L}\\p{N}_\\s]")
    private val digits = Regex("[0-9\\u0660-\\u0669\\u06F0-\\u06F9]")

    fun normalize(text: String, language: String): String {
        var s = Normalizer.normalize(text, Normalizer.Form.NFKC).replace(diacritics, "")
        if (language == "en") s = s.lowercase()
        val map = if (language == "ur") urduMap else if (language == "ar") arabicMap else null
        if (map != null) s = s.map { map[it] ?: it }.joinToString("")
        return s.replace(punctuation, " ").replace(digits, "").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun <T> editDistance(a: List<T>, b: List<T>): Int {
        var prev = IntArray(b.size + 1) { it }
        for (i in 1..a.size) {
            val cur = IntArray(b.size + 1); cur[0] = i
            for (j in 1..b.size) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.size]
    }

    /**
     * Writes [text] as lafz_voice_results_<time>.txt into the app's own files folder (Android/data/<app>/files, visible over USB) and, if the
     * user picked a folder with write access, also into that folder. Returns where it was saved.
     */
    fun save(context: Context, text: String, tree: Uri?): List<String> {
        val name = "lafz_voice_results_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + ".txt"
        val saved = ArrayList<String>()
        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            File(dir, name).writeText(text, Charsets.UTF_8); saved.add("${dir.path}/$name")
        } catch (e: Exception) { saved.add("could not save in the app folder: ${e.message}") }
        if (tree != null) try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val doc = DocumentsContract.createDocument(context.contentResolver, parent, "text/plain", name)
            if (doc != null) {
                context.contentResolver.openOutputStream(doc)!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                saved.add("your folder: $name")
            }
        } catch (e: Exception) { saved.add("could not save in your folder (no write access?): ${e.message}") }
        return saved
    }
}
