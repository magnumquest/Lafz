// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.media.*
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.preferences.Preference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MediaSettingsScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.prefs()
    var enabled by remember { mutableStateOf(MediaPrefs.enabled(context)) }
    var mature by remember { mutableStateOf(prefs.getBoolean(MediaPrefs.MATURE, false)) }
    var apiKey by remember { mutableStateOf(prefs.getString(MediaPrefs.KEY, "").orEmpty()) }
    var useApi by remember { mutableStateOf(MediaPrefs.useApi(context)) }
    var customKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    SearchSettingsScreen(onClickBack, stringResource(R.string.media_title), emptyList()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.media_intro))
            MediaSwitch(stringResource(R.string.media_enable), enabled) {
                enabled = it; prefs.edit { putBoolean(MediaPrefs.ENABLED, it) }
            }
            OutlinedButton(onClick = { context.startActivity(Intent(context, MediaBrowserActivity::class.java)) }, enabled = enabled) {
                Text(stringResource(R.string.media_open))
            }
            Text(stringResource(R.string.media_privacy), style = MaterialTheme.typography.bodySmall)
            MediaSwitch(stringResource(R.string.media_use_api), useApi, enabled) {
                useApi = it; prefs.edit { putBoolean(MediaPrefs.USE_API, it) }
            }
            if (useApi) {
                Text(stringResource(if (BuildConfig.GIPHY_API_KEY.isNotEmpty()) R.string.media_bundled_key else R.string.media_no_bundled_key),
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { customKey = !customKey }) { Text(stringResource(R.string.media_custom_key)) }
            }
            if (useApi && customKey) {
                OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.media_api_key)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                Button(onClick = {
                    prefs.edit { if (apiKey.isBlank()) remove(MediaPrefs.KEY) else putString(MediaPrefs.KEY, apiKey.trim()) }
                    status = context.getString(R.string.media_key_saved)
                }) { Text(stringResource(R.string.media_save_key)) }
            }
            MediaSwitch(stringResource(R.string.media_mature), mature, enabled) {
                mature = it; prefs.edit { putBoolean(MediaPrefs.MATURE, it) }
            }
            Text(stringResource(R.string.media_provider_notes), style = MaterialTheme.typography.bodySmall)
            fun test(kind: MediaKind) {
                if (busy) return
                busy = true
                status = context.getString(R.string.media_testing)
                scope.launch {
                    try {
                        val repository = MediaRepository(context)
                        val results = repository.search(kind, "hello", 0)
                        val item = results.firstOrNull()
                        if (item != null) { repository.thumbnail(item); repository.download(item) }
                        status = context.getString(if (item == null) R.string.media_test_empty else R.string.media_test_ok)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { status = context.getString(R.string.media_connection_error, e.message.orEmpty()) }
                    finally { busy = false }
                }
            }
            OutlinedButton(onClick = { test(MediaKind.GIFS) }, enabled = enabled && !busy) {
                Text(stringResource(R.string.media_test_giphy))
            }
            OutlinedButton(onClick = {
                scope.launch {
                    busy = true
                    withContext(Dispatchers.IO) { File(context.cacheDir, "media").deleteRecursively() }
                    status = context.getString(R.string.media_cache_cleared)
                    busy = false
                }
            }, enabled = !busy) { Text(stringResource(R.string.media_clear_cache)) }
            Text(stringResource(R.string.media_cache_note), style = MaterialTheme.typography.bodySmall)
            if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.primary)
        }
    }
}

fun createMediaSettings(context: Context) = listOf(
    Setting(context, "screen_media", R.string.media_title, R.string.media_description) {
        Preference(name = it.title, description = it.description, onClick = { SettingsDestination.navigateTo(SettingsDestination.Media) })
    }
)

@Composable
private fun MediaSwitch(title: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked, onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
    }
}
