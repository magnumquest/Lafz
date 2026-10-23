// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.urdu.RomanScript
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.IntentUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.InfoDialog
import helium314.keyboard.settings.filePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** saves the words learned in Roman Urdu / Arabic to a file the owner chooses */
@Composable
fun ExportLearnedWordsPreference(setting: Setting) {
    val ctx = LocalContext.current
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = filePicker { uri ->
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            try {
                RomanScript.saveAllLearned()
                ctx.getActivity()?.contentResolver?.openOutputStream(uri)?.use { RomanScript.exportLearned(ctx, it) }
                ctx.getActivity()?.runOnUiThread { Toast.makeText(ctx, R.string.learned_words_exported, Toast.LENGTH_LONG).show() }
            } catch (t: Throwable) {
                Log.w("LearnedWords", "export failed", t)
                error = t.message ?: "?"
            }
        }
    }
    Preference(name = setting.title, description = setting.description, onClick = {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        launcher.launch(IntentUtils.getResolvableTypeIntent(ctx, "application/zip")
            .setAction(Intent.ACTION_CREATE_DOCUMENT)
            .putExtra(Intent.EXTRA_TITLE, "Lafz_learned_words_$date.zip"))
    })
    error?.let { InfoDialog(stringResource(R.string.learned_words_error, it)) { error = null } }
}

/** adds the words from such a file to what is learned here, existing words are kept (counts are added) */
@Composable
fun ImportLearnedWordsPreference(setting: Setting) {
    val ctx = LocalContext.current
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = filePicker { uri ->
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            try {
                val n = ctx.getActivity()?.contentResolver?.openInputStream(uri)?.use { RomanScript.importLearned(ctx, it) } ?: 0
                ctx.getActivity()?.runOnUiThread { Toast.makeText(ctx, ctx.getString(R.string.learned_words_imported, n), Toast.LENGTH_LONG).show() }
            } catch (t: Throwable) {
                Log.w("LearnedWords", "import failed", t)
                error = t.message ?: "?"
            }
        }
    }
    Preference(name = setting.title, description = setting.description, onClick = {
        launcher.launch(IntentUtils.getResolvableTypeIntent(ctx, "application/zip").setAction(Intent.ACTION_OPEN_DOCUMENT))
    })
    error?.let { InfoDialog(stringResource(R.string.learned_words_error, it)) { error = null } }
}
