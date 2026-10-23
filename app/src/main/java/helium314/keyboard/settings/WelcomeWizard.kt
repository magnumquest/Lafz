// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import helium314.keyboard.latin.utils.previewDark
import kotlinx.coroutines.delay

@Composable
fun WelcomeWizard(close: () -> Unit, finish: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    fun currentStep() = when {
        !UncachedInputMethodManagerUtils.isThisImeEnabled(context, inputMethodManager) -> 1
        !UncachedInputMethodManagerUtils.isThisImeCurrent(context, inputMethodManager) -> 2
        else -> 3
    }
    var step by rememberSaveable { mutableIntStateOf(currentStep()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        step = currentStep()
    }
    LaunchedEffect(step) {
        if (step == 2) {
            while (step == 2 && !UncachedInputMethodManagerUtils.isThisImeCurrent(context, inputMethodManager)) {
                delay(250)
            }
            if (step == 2) step = 3
        }
    }
    val appName = stringResource(context.applicationInfo.labelRes)
    val colors = MaterialTheme.colorScheme

    Surface(color = colors.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                // Brand block: the Urdu wordmark is the one expressive element on the screen.
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = RoundedCornerShape(36.dp), color = colors.primaryContainer) {
                        Text("لفظ", style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                            color = colors.onPrimaryContainer, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 40.dp, vertical = 20.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(if (step == 3) stringResource(R.string.setup_step3_title)
                        else stringResource(R.string.setup_welcome_title, appName),
                        style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                        color = colors.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.lafz_setup_intro), style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("English", "اردو", "عربي").forEach {
                            Surface(shape = RoundedCornerShape(50), color = colors.secondaryContainer) {
                                Text(it, style = MaterialTheme.typography.labelLarge, color = colors.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                            }
                        }
                    }
                }

                Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        SetupRow(1, step, R.drawable.ic_setup_key, stringResource(R.string.setup_step1_title, appName),
                            stringResource(R.string.setup_step1_instruction, appName))
                        SetupRow(2, step, R.drawable.ic_setup_select, stringResource(R.string.setup_step2_title, appName),
                            stringResource(R.string.setup_step2_instruction, appName))
                        SetupRow(3, step, R.drawable.sym_keyboard_language_switch, stringResource(R.string.lafz_setup_ready),
                            stringResource(R.string.setup_step3_instruction, appName))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        when (step) {
                            1 -> launcher.launch(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addCategory(Intent.CATEGORY_DEFAULT))
                            2 -> inputMethodManager.showInputMethodPicker()
                            else -> close()
                        }
                    }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(when (step) {
                            1 -> stringResource(R.string.setup_step1_action)
                            2 -> stringResource(R.string.setup_step2_action)
                            else -> stringResource(R.string.setup_step3_action)
                        }, style = MaterialTheme.typography.titleMedium)
                    }
                    if (step >= 2) {
                        TextButton(onClick = if (step == 2) close else finish, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Text(stringResource(if (step == 2) R.string.setup_step3_action else R.string.setup_finish_action))
                        }
                    }
                }
            }
        }
    }
}

/** One checklist entry: done (check), current (filled icon, instruction shown) or upcoming (dimmed). */
@Composable
private fun SetupRow(index: Int, step: Int, icon: Int, title: String, instruction: String) {
    val colors = MaterialTheme.colorScheme
    val done = index < step
    val current = index == step
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = CircleShape, modifier = Modifier.size(44.dp),
            color = when { current -> colors.primary; done -> colors.primaryContainer; else -> colors.surfaceContainerHighest }) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(if (done) R.drawable.ic_setup_check else icon), contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = when { current -> colors.onPrimary; done -> colors.onPrimaryContainer; else -> colors.onSurfaceVariant })
            }
        }
        Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (current || done) colors.onSurface else colors.onSurfaceVariant)
            if (current) Text(instruction, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Preview
@Composable
private fun PreviewWelcomeWizard() {
    Theme(previewDark) { WelcomeWizard(close = {}, finish = {}) }
}
