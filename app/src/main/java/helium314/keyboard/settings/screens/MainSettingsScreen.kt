// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.screens.gesturedata.END_DATE_EPOCH_MILLIS
import helium314.keyboard.settings.screens.gesturedata.TWO_WEEKS_IN_MILLIS

@Composable
fun MainSettingsScreen(
    onClickAbout: () -> Unit,
    onClickTextCorrection: () -> Unit,
    onClickPreferences: () -> Unit,
    onClickToolbar: () -> Unit,
    onClickGestureTyping: () -> Unit,
    onClickVoiceTyping: () -> Unit,
    onClickMedia: () -> Unit,
    onClickDataGathering: () -> Unit,
    onClickAdvanced: () -> Unit,
    onClickAppearance: () -> Unit,
    onClickLanguage: () -> Unit,
    onClickLayouts: () -> Unit,
    onClickDictionaries: () -> Unit,
    onClickBack: () -> Unit,
) {
    SearchSettingsScreen(onClickBack, "Lafz", emptyList(), home = true) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 840.dp).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                PreferenceCategory(stringResource(R.string.lafz_section_personalize))
                SettingsGroup {
                    HomeRow(R.string.language_and_layouts_title, R.drawable.ic_settings_languages,
                        SubtypeSettings.getEnabledSubtypes(true).joinToString(", ") { it.displayName() }, onClickLanguage)
                    HomeRow(R.string.settings_screen_appearance, R.drawable.ic_settings_appearance,
                        stringResource(R.string.lafz_appearance_description), onClickAppearance)
                    HomeRow(R.string.media_title, R.drawable.ic_toolbar_emoji,
                        stringResource(R.string.media_description), onClickMedia)
                    HomeRow(R.string.settings_screen_toolbar, R.drawable.ic_settings_toolbar,
                        stringResource(R.string.lafz_toolbar_description), onClickToolbar)
                }
                PreferenceCategory(stringResource(R.string.lafz_section_typing))
                SettingsGroup {
                    HomeRow(R.string.settings_screen_correction, R.drawable.ic_settings_correction,
                        stringResource(R.string.lafz_correction_description), onClickTextCorrection)
                    HomeRow(R.string.settings_screen_preferences, R.drawable.ic_settings_preferences,
                        stringResource(R.string.lafz_preferences_description), onClickPreferences)
                    HomeRow(R.string.settings_screen_gesture, R.drawable.ic_settings_gesture,
                        stringResource(R.string.lafz_gesture_description), onClickGestureTyping)
                    HomeRow(R.string.voice_typing_title, R.drawable.sym_keyboard_voice_rounded,
                        stringResource(R.string.voice_typing_description), onClickVoiceTyping)
                    HomeRow(R.string.dictionary_settings_category, R.drawable.ic_dictionary,
                        stringResource(R.string.lafz_dictionary_description), onClickDictionaries)
                }
                PreferenceCategory(stringResource(R.string.lafz_try_keyboard))
                SettingsGroup {
                    var text by rememberSaveable { mutableStateOf("") }
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.lafz_try_description), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = text, onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.lafz_try_hint)) },
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = true, imeAction = ImeAction.Default),
                            minLines = 2, maxLines = 5,
                        )
                    }
                }
                PreferenceCategory(stringResource(R.string.lafz_section_more))
                SettingsGroup {
                    HomeRow(R.string.settings_screen_secondary_layouts, R.drawable.ic_settings_layout,
                        stringResource(R.string.lafz_layouts_description), onClickLayouts)
                    HomeRow(R.string.settings_screen_advanced, R.drawable.ic_settings_advanced,
                        stringResource(R.string.lafz_advanced_description), onClickAdvanced)
                    if (JniUtils.sHaveGestureLib && System.currentTimeMillis() < END_DATE_EPOCH_MILLIS + TWO_WEEKS_IN_MILLIS)
                        HomeRow(R.string.gesture_data_screen, R.drawable.ic_settings_gesture, null, onClickDataGathering)
                    HomeRow(R.string.settings_screen_about, R.drawable.ic_settings_about,
                        stringResource(R.string.lafz_about_description), onClickAbout)
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(content = content)
    }
}

@Composable
private fun HomeRow(title: Int, icon: Int, description: String?, onClick: () -> Unit) {
    Preference(name = stringResource(title), description = description, icon = icon, onClick = onClick) {
        NextScreenIcon()
    }
}
