package com.uliteamr.rustupmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.klyx.api.plugin.PluginSettings
import com.uliteamr.rustupmanager.icons.Close
import com.uliteamr.rustupmanager.icons.Server
import com.uliteamr.rustupmanager.lsp.LspStatus
import com.uliteamr.rustupmanager.lsp.RustAnalyzerSession
import com.uliteamr.rustupmanager.rustup.RA_FEATURES
import com.uliteamr.rustupmanager.rustup.encodeOptions
import com.uliteamr.rustupmanager.rustup.loadInitOptions
import com.uliteamr.rustupmanager.settings.SettingsKeys
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

@Composable
fun LspScreen(
    settings: PluginSettings,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val status = RustAnalyzerSession.status
    val isIndexing = RustAnalyzerSession.isIndexing
    val indexingProgress = RustAnalyzerSession.indexingProgress

    var indexingToast by remember { mutableStateOf(settings.getBoolean(SettingsKeys.indexingToast, true)) }
    var options by remember { mutableStateOf<JsonObject?>(null) }
    var expandedSections by remember { mutableStateOf(setOf<String>()) }

    fun commit(next: JsonObject) {
        options = next
        scope.launch { settings.putString(SettingsKeys.rawInitOptions, encodeOptions(next)) }
    }

    LaunchedEffect(Unit) {
        options = loadInitOptions(settings)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Language Server", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "status") {
                SettingsCard(
                    icon = Server,
                    title = "rust-analyzer",
                    description = statusText(status, isIndexing, indexingProgress),
                    trailing = if (isIndexing) {
                        { InlineSpinner() }
                    } else {
                        null
                    },
                )
            }

            item(key = "indexing") {
                FeatureRow(
                    title = "Indexing notifications",
                    description = "Shows a toast when rust-analyzer starts and finishes indexing",
                    checked = indexingToast,
                    enabled = true,
                    onToggle = { enabled ->
                        indexingToast = enabled
                        scope.launch { settings.putBoolean(SettingsKeys.indexingToast, enabled) }
                    },
                )
            }

            item(key = "stop") {
                OutlinedButton(
                    onClick = { RustAnalyzerSession.stop() },
                    enabled = status is LspStatus.Running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Stop")
                }
            }

            item(key = "lifecycle") {
                Text(
                    "Klyx owns the language server's lifecycle: it calls this plugin to spawn rust-analyzer "
                        + "whenever a .rs file needs one. \"Stop\" kills the current process; Klyx will spawn a "
                        + "fresh one automatically the next time it needs the server (for example, re-opening or "
                        + "editing a Rust file).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                )
            }

            val current = options
            if (current != null) {
                item(key = "features-label") { SectionLabel("rust-analyzer features") }
                item(key = "features-note") {
                    Text(
                    "Sent to rust-analyzer in the initialize request. A feature's switch is on while "
                        + "all its sub-options are on, and flipping it off turns every sub-option off "
                        + "with it. Tap next to a switch to tune the sub-options; every switch stores "
                        + "its exact value.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                    )
                }
                items(RA_FEATURES, key = { it.key }) { section ->
                    RaFeatureSectionCard(
                        section = section,
                        root = current,
                        expanded = section.key in expandedSections,
                        onToggleExpanded = {
                            expandedSections = if (section.key in expandedSections) {
                                expandedSections - section.key
                            } else {
                                expandedSections + section.key
                            }
                        },
                        onCommit = ::commit,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: ((Boolean) -> Unit)? = null,
) {
    SettingsCard(
        title = title,
        description = description,
        trailing = {
            AppSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onToggle ?: {},
            )
        },
    )
}

private fun statusText(status: LspStatus, isIndexing: Boolean, progress: String?): String = when (status) {
    LspStatus.NotStarted -> "Not started yet. It starts automatically when you open a .rs file."
    is LspStatus.Running -> buildString {
        append("Running (pid ").append(status.pid).append(')')
        if (isIndexing) {
            append(" · indexing")
            if (progress != null) append(' ').append(progress)
            append("…")
        }
    }
    is LspStatus.Exited -> "Exited (code ${status.code})"
}
