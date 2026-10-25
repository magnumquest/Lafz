// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.media

import android.content.ClipDescription
import android.content.Intent
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import java.io.File
import java.util.UUID

class MediaFileProvider : FileProvider()

/** Short-lived handoff owned by this process; never send through a stale InputConnection. */
object MediaInsertion {
    data class Target(val packageName: String?, val fieldId: Int, val inputType: Int) {
        fun matches(editor: EditorInfo) = packageName == editor.packageName && fieldId == editor.fieldId && inputType == editor.inputType
    }
    private data class Session(val id: String, val target: Target, val mimeTypes: Array<String>, val started: Long)
    private data class Pending(val session: Session, val file: File, val mime: String, val created: Long)
    private var session: Session? = null
    private var pending: Pending? = null
    @JvmStatic fun hasPending() = pending != null

    @JvmStatic fun open(ime: LatinIME, kind: String) {
        if (!MediaPrefs.enabled(ime)) return
        val editor = ime.currentInputEditorInfo ?: return
        val id = UUID.randomUUID().toString()
        session = Session(id, Target(editor.packageName, editor.fieldId, editor.inputType),
            EditorInfoCompat.getContentMimeTypes(editor), SystemClock.elapsedRealtime())
        pending = null
        ime.startActivity(Intent(ime, MediaBrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("kind", kind).putExtra("session", id))
    }

    fun canInsert(id: String?, mime: String): Boolean = session?.let {
        it.id == id && SystemClock.elapsedRealtime() - it.started < 15 * 60_000 &&
            ClipDescription("media", arrayOf(mime)).let { description -> it.mimeTypes.any(description::hasMimeType) }
    } == true

    fun prepare(id: String?, file: File, mime: String): Boolean {
        if (!canInsert(id, mime)) return false
        pending = Pending(session!!, file, mime, SystemClock.elapsedRealtime())
        return true
    }

    @JvmStatic fun editorChanged(editor: EditorInfo?) {
        val active = session ?: return
        if (editor == null || editor.packageName == BuildConfig.APPLICATION_ID) return
        if (!active.target.matches(editor)) { session = null; pending = null }
    }

    @JvmStatic fun deliver(ime: LatinIME) {
        val item = pending ?: return
        val editor = ime.currentInputEditorInfo ?: return
        // Search belongs to our own activity. Wait for the original editor to resume.
        if (editor.packageName == ime.packageName) return
        pending = null
        session = null
        if (!MediaPrefs.enabled(ime) ||
            SystemClock.elapsedRealtime() - item.created > 15_000 || !item.session.target.matches(editor)) {
            Toast.makeText(ime, R.string.media_insert_cancelled, Toast.LENGTH_LONG).show()
            return
        }
        val accepted = runCatching {
            val connection = ime.currentInputConnection ?: return@runCatching false
            val uri = FileProvider.getUriForFile(ime, ime.packageName + ".media", item.file)
            InputConnectionCompat.commitContent(connection, editor,
                InputContentInfoCompat(uri, ClipDescription("Lafz media", arrayOf(item.mime)), null),
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
        }.getOrDefault(false)
        if (!accepted) Toast.makeText(ime, R.string.media_insert_failed, Toast.LENGTH_LONG).show()
    }
}
