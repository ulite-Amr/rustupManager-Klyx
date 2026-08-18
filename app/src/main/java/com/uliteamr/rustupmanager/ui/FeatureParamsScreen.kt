package com.uliteamr.rustupmanager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.klyx.api.plugin.PluginSettings
import com.uliteamr.rustupmanager.icons.Wrench
import com.uliteamr.rustupmanager.settings.SettingsKeys
import kotlinx.coroutines.launch

/**
 * Feature Parameters and Initialize: user-facing toggles for the parameters this
 * plugin sends to rust-analyzer through the SDK's `initializationOptions()` hook.
 * Every feature is enabled by default and can be switched off individually.
 */
@Composable
fun FeatureParamsScreen(settings: PluginSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var bindingModeHints by remember { mutableStateOf(settings.getBoolean(SettingsKeys.bindingModeHints, true)) }
    var macroDiagnostics by remember { mutableStateOf(settings.getBoolean(SettingsKeys.macroDiagnostics, true)) }
    var checkOnSave by remember { mutableStateOf(settings.getBoolean(SettingsKeys.checkOnSave, true)) }
    var currentTargetOnly by remember { mutableStateOf(settings.getBoolean(SettingsKeys.currentTargetOnly, true)) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Feature Parameters and Initialize", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
        ) {
            Text(
                "These parameters are sent to rust-analyzer in the initialize request through "
                    + "Klyx's initializationOptions hook. Every feature is enabled by default; "
                    + "changes apply the next time a server starts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
            )

            SettingsCard(
                icon = Wrench,
                title = "Diagnostics",
                description = "Report macro-expansion errors and warnings (experimental diagnostics)",
                trailing = {
                    AppSwitch(
                        checked = macroDiagnostics,
                        onCheckedChange = { enabled ->
                            macroDiagnostics = enabled
                            scope.launch { settings.putBoolean(SettingsKeys.macroDiagnostics, enabled) }
                        },
                    )
                },
            )
            SettingsCard(
                icon = Wrench,
                title = "Check on save",
                description = "Run cargo check when a file is saved so all diagnostics (not just semantic) appear",
                trailing = {
                    AppSwitch(
                        checked = checkOnSave,
                        onCheckedChange = { enabled ->
                            checkOnSave = enabled
                            scope.launch { settings.putBoolean(SettingsKeys.checkOnSave, enabled) }
                        },
                    )
                },
            )
            SettingsCard(
                icon = Wrench,
                title = "Current target only",
                description = "Only check the current target instead of all targets, keeping checks lighter",
                trailing = {
                    AppSwitch(
                        checked = currentTargetOnly,
                        onCheckedChange = { enabled ->
                            currentTargetOnly = enabled
                            scope.launch { settings.putBoolean(SettingsKeys.currentTargetOnly, enabled) }
                        },
                    )
                },
            )
            SettingsCard(
                icon = Wrench,
                title = "Binding-mode hints",
                description = "Show binding-mode inlay hints (mut/ref prefixes) in the editor",
                trailing = {
                    AppSwitch(
                        checked = bindingModeHints,
                        onCheckedChange = { enabled ->
                            bindingModeHints = enabled
                            scope.launch { settings.putBoolean(SettingsKeys.bindingModeHints, enabled) }
                        },
                    )
                },
            )
        }
    }
}
