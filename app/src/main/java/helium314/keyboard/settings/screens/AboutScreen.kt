// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SpannableStringUtils
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsContainer
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.updates.GithubRelease
import helium314.keyboard.settings.updates.GithubReleaseUpdater
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.previewDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import androidx.core.content.edit
import helium314.keyboard.latin.utils.IntentUtils
import java.util.Locale

@Composable
fun AboutScreen(
    onClickBack: () -> Unit,
) {
    val items = listOf(
        R.string.lafz_section_project,
        SettingsWithoutKey.APP,
        SettingsWithoutKey.VERSION,
        SettingsWithoutKey.CHECK_FOR_UPDATES,
        SettingsWithoutKey.LICENSE,
        SettingsWithoutKey.FUTO_SWIPE,
        R.string.lafz_section_help,
        SettingsWithoutKey.HIDDEN_FEATURES,
        SettingsWithoutKey.GITHUB,
        SettingsWithoutKey.SAVE_LOG,
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_about),
        settings = items
    )
}

fun createAboutSettings(context: Context) = listOf(
    Setting(context, SettingsWithoutKey.FUTO_SWIPE, R.string.lafz_futo_swipe_title, R.string.lafz_futo_swipe_summary) {
        Preference(name = it.title, description = it.description, onClick = { }, icon = R.drawable.ic_settings_gesture)
    },
    Setting(context, SettingsWithoutKey.APP, R.string.english_ime_name, R.string.app_slogan) {
        Preference(
            name = it.title,
            description = it.description,
            onClick = { },
            icon = R.mipmap.ic_launcher_round
        )
    },
    Setting(context, SettingsWithoutKey.VERSION, R.string.version) {
        var count by rememberSaveable { mutableIntStateOf(0) }
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        Preference(
            name = it.title,
            description = stringResource(R.string.version_text, BuildConfig.VERSION_NAME),
            onClick = {
                if (prefs.getBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, Defaults.PREF_SHOW_DEBUG_SETTINGS) || BuildConfig.DEBUG)
                    return@Preference
                count++
                if (count < 5) return@Preference
                prefs.edit { putBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, true) }
                Toast.makeText(ctx, R.string.prefs_debug_settings_enabled, Toast.LENGTH_LONG).show()
            },
            icon = R.drawable.ic_settings_about_version
        )
    },
    Setting(context, SettingsWithoutKey.CHECK_FOR_UPDATES, R.string.check_for_updates, R.string.check_for_updates_summary) {
        UpdateSetting(it)
    },
    Setting(context, SettingsWithoutKey.LICENSE, R.string.license, R.string.gnu_gpl) {
        val ctx = LocalContext.current
        Preference(
            name = it.title,
            description = it.description,
            onClick = {
                val intent = Intent()
                intent.data = Links.LICENSE.toUri()
                intent.action = Intent.ACTION_VIEW
                ctx.startActivity(intent)
            },
            icon = R.drawable.ic_settings_about_license
        )
    },
    Setting(context, SettingsWithoutKey.HIDDEN_FEATURES, R.string.hidden_features_title, R.string.hidden_features_summary) {
        val ctx = LocalContext.current
        Preference(
            name = it.title,
            description = it.description,
            onClick = {
                // Compose dialogs are in a rather sad state. They don't understand HTML, and don't scroll without customization.
                // this should be re-done in compose, but... bah
                val link = ("<a href=\"https://developer.android.com/reference/android/content/Context#createDeviceProtectedStorageContext()\">"
                        + ctx.getString(R.string.hidden_features_text) + "</a>")
                val message = ctx.getString(R.string.hidden_features_message, link)
                val dialogMessage = SpannableStringUtils.fromHtml(message)
                val builder = AlertDialog.Builder(ctx)
                    .setIcon(R.drawable.ic_settings_about_hidden_features)
                    .setTitle(R.string.hidden_features_title)
                    .setMessage(dialogMessage)
                    .setPositiveButton(R.string.dialog_close, null)
                    .create()
                builder.show()
                (builder.findViewById<View>(android.R.id.message) as TextView).movementMethod = LinkMovementMethod.getInstance()
            },
            icon = R.drawable.ic_settings_about_hidden_features
        )
    },
    Setting(context, SettingsWithoutKey.GITHUB, R.string.about_github_link) {
        val ctx = LocalContext.current
        Preference(
            name = it.title,
            description = it.description,
            onClick = {
                val intent = Intent()
                intent.data = Links.GITHUB.toUri()
                intent.action = Intent.ACTION_VIEW
                ctx.startActivity(intent)
            },
            icon = R.drawable.ic_settings_about_github
        )
    },
    Setting(context, SettingsWithoutKey.SAVE_LOG, R.string.save_log) { setting ->
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                ctx.getActivity()?.contentResolver?.openOutputStream(uri)?.use { os ->
                    os.writer().use { writer ->
                        val logcat = Runtime.getRuntime().exec("logcat -d -b all *:W").inputStream.use { it.reader().readText() }
                        val internal = Log.getLog().joinToString("\n")
                        writer.write(logcat + "\n\n" + internal + "\n\n" + helium314.keyboard.latin.gesture.SwipeDiagnostics.report(ctx, helium314.keyboard.latin.gesture.FutoSwipe.status()))
                    }
                }
            }
        }
        Preference(
            name = setting.title,
            description = setting.description,
            onClick = {
                val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Calendar.getInstance().time)
                val intent = IntentUtils.getResolvableTypeIntent(ctx, "text/plain")
                    .setAction(Intent.ACTION_CREATE_DOCUMENT)
                    .putExtra(
                        Intent.EXTRA_TITLE,
                        ctx.getString(R.string.english_ime_name)
                            .replace(" ", "_") + "_log_$date.txt"
                    )
                launcher.launch(intent)
            },
            icon = R.drawable.ic_settings_about_log
        )
    },
)

@Composable
private fun UpdateSetting(setting: Setting) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var release by remember { mutableStateOf<GithubRelease?>(null) }
    var stagedApk by remember { mutableStateOf<java.io.File?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun installStagedUpdate() {
        val apk = stagedApk ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            status = context.getString(R.string.update_allow_installs)
            context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")))
            return
        }
        try {
            GithubReleaseUpdater.install(context, apk)
        } catch (e: Exception) {
            status = e.message ?: context.getString(R.string.update_failed)
        }
    }

    fun checkForUpdate() {
        if (checking || downloading) return
        scope.launch {
            checking = true
            status = context.getString(R.string.checking_for_updates)
            try {
                val found = GithubReleaseUpdater.findUpdate()
                if (found == null) {
                    status = context.getString(R.string.up_to_date, BuildConfig.VERSION_NAME)
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                } else {
                    release = found
                    showDialog = true
                    status = context.getString(R.string.update_available, found.versionName)
                }
            } catch (e: Exception) {
                status = e.message ?: context.getString(R.string.update_failed)
                Toast.makeText(context, status, Toast.LENGTH_LONG).show()
            } finally {
                checking = false
            }
        }
    }

    fun downloadUpdate() {
        val latest = release ?: return
        if (downloading) return
        scope.launch {
            downloading = true
            progress = 0
            status = context.getString(R.string.downloading_update, 0)
            try {
                stagedApk = GithubReleaseUpdater.download(context, latest) { percent ->
                    progress = percent
                    status = context.getString(R.string.downloading_update, percent)
                }
                status = context.getString(R.string.update_downloaded, latest.versionName)
            } catch (e: Exception) {
                status = e.message ?: context.getString(R.string.update_failed)
                Toast.makeText(context, status, Toast.LENGTH_LONG).show()
            } finally {
                downloading = false
            }
        }
    }

    Preference(
        name = setting.title,
        description = status ?: setting.description,
        onClick = { if (stagedApk != null) installStagedUpdate() else checkForUpdate() },
        icon = R.drawable.ic_settings_about_version,
    )

    val latest = release
    if (showDialog && latest != null) {
        val sizeMb = String.format(Locale.ROOT, "%.1f MB", latest.sizeBytes / (1024f * 1024f))
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!downloading) showDialog = false },
            title = { Text(stringResource(R.string.update_available, latest.versionName)) },
            text = {
                Text(buildString {
                    append(context.getString(R.string.update_download_size, sizeMb))
                    val notes = latest.notes.take(1200)
                    if (notes.isNotBlank()) append("\n\n").append(notes)
                    if (downloading) append("\n\n").append(context.getString(R.string.downloading_update, progress))
                    if (stagedApk != null) append("\n\n").append(context.getString(R.string.update_allow_installs))
                })
            },
            confirmButton = {
                TextButton(
                    enabled = !downloading,
                    onClick = { if (stagedApk != null) installStagedUpdate() else downloadUpdate() },
                ) {
                    Text(when {
                        downloading -> context.getString(R.string.downloading_update, progress)
                        stagedApk != null -> context.getString(R.string.install_update)
                        else -> context.getString(R.string.download_update)
                    })
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }, enabled = !downloading) {
                    Text(stringResource(R.string.dialog_close))
                }
            },
        )
    }
}

@Preview
@Composable
private fun Preview() {
    SettingsActivity.settingsContainer = SettingsContainer(LocalContext.current)
    Theme(previewDark) {
        Surface {
            AboutScreen {  }
        }
    }
}
