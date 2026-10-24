// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.voice.VoiceModels
import helium314.keyboard.latin.voice.VoiceBenchmark
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.latin.settings.Defaults

/**
 * Voice typing models: copy them to the phone, point Lafz at the folder, import them into the app's storage and test how fast they run.
 * The models are large and are not part of the app; nothing is loaded unless a test or (later) the mic key runs.
 */
@Composable
fun VoiceTypingScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    var models by remember { mutableStateOf(VoiceModels.installed(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    if (!VoiceModels.isSupported) {
        SearchSettingsScreen(onClickBack, stringResource(R.string.voice_typing_title), emptyList()) {
            Text(stringResource(R.string.voice_needs_android_7), modifier = Modifier.padding(16.dp))
        }
        return
    }
    fun append(line: String) { log = (log + line + "\n").takeLast(12000) }

    fun importFrom(uri: Uri) {
        if (busy) return
        busy = true; log = ""
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            try {
                val r = VoiceModels.importFromTree(ctx, uri) { append(it) }
                r.problems.forEach { append("problem: $it") }
                append(if (r.imported.isEmpty()) "nothing imported" else "imported: ${r.imported.joinToString()}")
            } catch (t: Throwable) { append("import failed: ${t.message}") }
            models = VoiceModels.installed(ctx)
            busy = false
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (_: Exception) { try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {} }
        prefs.edit { putString(Settings.PREF_VOICE_MODELS_FOLDER, uri.toString()) }
        importFrom(uri)
    }

    SearchSettingsScreen(onClickBack, stringResource(R.string.voice_typing_title), emptyList()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.voice_typing_intro), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            PreferenceCategory(stringResource(R.string.voice_dictation_category))
            SwitchPreference(name = stringResource(R.string.voice_dictation_title), key = Settings.PREF_VOICE_DICTATION, default = Defaults.PREF_VOICE_DICTATION,
                description = stringResource(R.string.voice_dictation_summary), onCheckedChange = { on ->
                    if (on && ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                })
            PreferenceCategory(stringResource(R.string.voice_models_category))
            Preference(name = stringResource(R.string.voice_choose_folder_title), description = stringResource(R.string.voice_choose_folder_summary), onClick = {
                if (!busy) folderPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
            })
            val saved = prefs.getString(Settings.PREF_VOICE_MODELS_FOLDER, null)
            if (saved != null)
                Preference(name = stringResource(R.string.voice_reimport_title), description = stringResource(R.string.voice_reimport_summary),
                    onClick = { importFrom(Uri.parse(saved)) })
            if (models.isEmpty())
                Text(stringResource(R.string.voice_no_models), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            for (m in models)
                Preference(name = m.name, description = "${m.language} · ${m.sizeBytes shr 20} MB", onClick = {})
            PreferenceCategory(stringResource(R.string.voice_test_category))
            fun runTest(full: Boolean) {
                if (busy || models.isEmpty()) return
                busy = true; log = ""
                ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
                    val report = StringBuilder()
                    fun both(line: String) { report.append(line).append('\n'); append(line) }
                    try { VoiceBenchmark.run(ctx, models, full) { both(it) } }
                    catch (t: Throwable) { both("test failed: ${t.message}") }
                    val tree = prefs.getString(Settings.PREF_VOICE_MODELS_FOLDER, null)?.let { Uri.parse(it) }
                    for (where in VoiceBenchmark.save(ctx, report.toString(), tree)) append("saved: $where")
                    busy = false
                }
            }
            Preference(name = stringResource(R.string.voice_test_title), description = stringResource(R.string.voice_test_summary), onClick = { runTest(false) })
            Preference(name = stringResource(R.string.voice_test_full_title), description = stringResource(R.string.voice_test_full_summary), onClick = { runTest(true) })
            if (models.isNotEmpty())
                Preference(name = stringResource(R.string.voice_remove_title), description = stringResource(R.string.voice_remove_summary), onClick = {
                    if (!busy) { VoiceModels.deleteAll(ctx); models = VoiceModels.installed(ctx); log = "" }
                })
            if (busy) Text(stringResource(R.string.voice_working), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            if (log.isNotEmpty())
                SelectionContainer {
                    Text(log, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                }
        }
    }
}
