// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import helium314.keyboard.latin.utils.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * A speech recognition model that the owner copied to the phone (the app ships none: they are large). Each model lives in its own folder with a
 * `model.json` that says how to run it, see tools/voice-spike/README.md:
 * {"name": "Arabic (Omnilingual 300M)", "language": "ar", "type": "ctc_onnx", "model": "model.int8.onnx", "tokens": "tokens.txt",
 *  "blank_id": 1, "skip_ids": [0, 2, 3], "normalize": true, "sample_rate": 16000}
 */
data class VoiceModelInfo(
    val dirName: String,
    val name: String,
    val language: String,
    val type: String,
    val modelFile: String,
    val tokensFile: String,
    val blankId: Int,
    val skipIds: Set<Int>,
    val normalize: Boolean,
    val sampleRate: Int,
    val sizeBytes: Long,
)

object VoiceModels {
    const val DESCRIPTOR = "model.json"
    const val TYPE_CTC_ONNX = "ctc_onnx"
    private const val TAG = "VoiceModels"

    /** the speech runtime needs Android 7 */
    val isSupported get() = android.os.Build.VERSION.SDK_INT >= 24

    fun root(context: Context) = File(context.filesDir, "voice_models")

    fun dir(context: Context, info: VoiceModelInfo) = File(root(context), info.dirName)

    fun installed(context: Context): List<VoiceModelInfo> =
        root(context).listFiles()?.filter { it.isDirectory }?.mapNotNull { read(it) }?.sortedWith(compareBy({ it.language }, { it.name })) ?: emptyList()

    /** null if the folder has no valid descriptor or the files it names are missing */
    fun read(dir: File): VoiceModelInfo? = try {
        val o = Json.parseToJsonElement(File(dir, DESCRIPTOR).readText()).jsonObject
        val info = VoiceModelInfo(
            dirName = dir.name,
            name = o["name"]?.jsonPrimitive?.contentOrNull ?: dir.name,
            language = o["language"]!!.jsonPrimitive.content,
            type = o["type"]?.jsonPrimitive?.contentOrNull ?: TYPE_CTC_ONNX,
            modelFile = o["model"]!!.jsonPrimitive.content,
            tokensFile = o["tokens"]!!.jsonPrimitive.content,
            blankId = o["blank_id"]?.jsonPrimitive?.intOrNull ?: 0,
            skipIds = o["skip_ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }?.toSet() ?: emptySet(),
            normalize = o["normalize"]?.jsonPrimitive?.booleanOrNull ?: true,
            sampleRate = o["sample_rate"]?.jsonPrimitive?.intOrNull ?: 16000,
            sizeBytes = dir.listFiles()?.sumOf { it.length() } ?: 0L,
        )
        if (info.type == TYPE_CTC_ONNX && File(dir, info.modelFile).isFile && File(dir, info.tokensFile).isFile) info else null
    } catch (e: Exception) {
        Log.w(TAG, "invalid model folder ${dir.name}", e)
        null
    }

    fun delete(context: Context, info: VoiceModelInfo) { dir(context, info).deleteRecursively() }

    fun deleteAll(context: Context) { root(context).deleteRecursively() }

    class ImportResult(val imported: List<String>, val problems: List<String>)

    private class Doc(val id: String, val name: String, val isDir: Boolean)

    private fun children(context: Context, tree: Uri, parentId: String): List<Doc> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val out = ArrayList<Doc>()
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
            while (c.moveToNext())
                out.add(Doc(c.getString(0), c.getString(1) ?: "", c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR))
        }
        return out
    }

    private fun displayName(context: Context, tree: Uri, docId: String): String =
        context.contentResolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, docId),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "model"

    private fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifEmpty { "model" }

    /**
     * Copies the model folders found in [tree] (either one model folder, or a folder with one sub folder per model) into the app's own storage,
     * where the native runtime can read them by path. Already copied files of the same size are kept. Run on a background thread.
     */
    fun importFromTree(context: Context, tree: Uri, progress: (String) -> Unit): ImportResult {
        val imported = ArrayList<String>(); val problems = ArrayList<String>()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val top = children(context, tree, rootId)
        val candidates = if (top.any { !it.isDir && it.name == DESCRIPTOR }) listOf(Triple(displayName(context, tree, rootId), rootId, top))
        else top.filter { it.isDir }.map { Triple(it.name, it.id, children(context, tree, it.id)) }.filter { (_, _, files) -> files.any { it.name == DESCRIPTOR } }
        if (candidates.isEmpty()) return ImportResult(imported, listOf("no folder with a $DESCRIPTOR found"))
        for ((name, _, files) in candidates) {
            val target = File(root(context), safeName(name))
            try {
                target.mkdirs()
                for (f in files.filter { !it.isDir }) {
                    val dest = File(target, safeName(f.name))
                    val src = DocumentsContract.buildDocumentUriUsingTree(tree, f.id)
                    val size = context.contentResolver.query(src, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getLong(0) else -1L } ?: -1L
                    if (dest.isFile && size >= 0 && dest.length() == size) { progress("${target.name}/${f.name}: already here"); continue }
                    val part = File(target, dest.name + ".part")
                    var copied = 0L; var lastReport = 0L
                    context.contentResolver.openInputStream(src)!!.use { input ->
                        part.outputStream().use { output ->
                            val buf = ByteArray(1 shl 20)
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                output.write(buf, 0, n); copied += n
                                if (copied - lastReport >= 32L shl 20) { lastReport = copied; progress("${target.name}/${f.name}: ${copied shr 20} MB") }
                            }
                        }
                    }
                    dest.delete(); part.renameTo(dest)
                }
                if (read(target) != null) { imported.add(target.name); progress("${target.name}: ready") }
                else { problems.add("${target.name}: $DESCRIPTOR or the files it names are missing"); target.deleteRecursively() }
            } catch (e: Exception) {
                Log.w(TAG, "import of $name failed", e)
                problems.add("$name: ${e.message}"); target.deleteRecursively()
            }
        }
        return ImportResult(imported, problems)
    }
}
