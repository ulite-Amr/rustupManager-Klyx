package com.uliteamr.rustupmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.klyx.api.plugin.PluginSettings
import com.uliteamr.rustupmanager.icons.Server
import com.uliteamr.rustupmanager.lsp.LspStatus
import com.uliteamr.rustupmanager.lsp.RustAnalyzerSession
import com.uliteamr.rustupmanager.settings.SettingsKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LspScreen(settings: PluginSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val status = RustAnalyzerSession.status
    val isIndexing = RustAnalyzerSession.isIndexing
    val indexingProgress = RustAnalyzerSession.indexingProgress
    val logs = RustAnalyzerSession.logs

    var reverseCompletion by remember { mutableStateOf(settings.getBoolean(SettingsKeys.reverseCompletion, true)) }
    var indexingToast by remember { mutableStateOf(settings.getBoolean(SettingsKeys.indexingToast, true)) }
    var toolbarAutoHide by remember { mutableStateOf(settings.getBoolean(SettingsKeys.toolbarAutoHide, true)) }

    var errorsOnly by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    val visibleLogs = if (errorsOnly) logs.filter(::isErrorLine) else logs

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Language Server", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
        ) {
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { RustAnalyzerSession.stop() },
                    enabled = status is LspStatus.Running,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Stop")
                }
                Button(
                    onClick = { RustAnalyzerSession.clearLogs() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Clear logs")
                }
            }

            Text(
                "Klyx owns the language server's lifecycle: it calls this plugin to spawn rust-analyzer "
                    + "whenever a .rs file needs one. \"Stop\" kills the current process; Klyx will spawn a "
                    + "fresh one automatically the next time it needs the server (for example, re-opening or "
                    + "editing a Rust file).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
            )

            SectionLabel("Features")
            FeatureRow(
                title = "Completion order fix",
                description = "Reverses rust-analyzer's completion results so the most relevant entry appears on top",
                checked = reverseCompletion,
                enabled = true,
                onToggle = { enabled ->
                    reverseCompletion = enabled
                    scope.launch { settings.putBoolean(SettingsKeys.reverseCompletion, enabled) }
                },
            )
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
            FeatureRow(
                title = "Toolbar auto-hide",
                description = "Only shows the Rust Toolchain button while a .rs file is open",
                checked = toolbarAutoHide,
                enabled = true,
                onToggle = { enabled ->
                    toolbarAutoHide = enabled
                    scope.launch { settings.putBoolean(SettingsKeys.toolbarAutoHide, enabled) }
                },
            )
            FeatureRow(
                title = "Diagnostics",
                description = "Errors and warnings shown in the editor · managed by Klyx",
                checked = true,
                enabled = false,
            )
            FeatureRow(
                title = "Inlay hints",
                description = "Inline type hints in the editor · managed by Klyx (editor setting)",
                checked = true,
                enabled = false,
            )

            SectionLabel("Not available yet")
            FeatureRow(
                title = "Hover",
                description = "Symbol info on hover · not implemented in Klyx yet",
                checked = false,
                enabled = false,
            )
            FeatureRow(
                title = "Code actions",
                description = "Quick fixes and refactors · not implemented in Klyx yet",
                checked = false,
                enabled = false,
            )
            FeatureRow(
                title = "Go to definition",
                description = "Jump to a symbol's definition · not implemented in Klyx yet",
                checked = false,
                enabled = false,
            )
            FeatureRow(
                title = "Find references",
                description = "Locate all usages of a symbol · not implemented in Klyx yet",
                checked = false,
                enabled = false,
            )
            FeatureRow(
                title = "Rename symbol",
                description = "Workspace-wide rename · not implemented in Klyx yet",
                checked = false,
                enabled = false,
            )
            FeatureRow(
                title = "Signature help",
                description = "Parameter hints while typing a call · not implemented in Klyx yet",
                checked = false,
                enabled = false,
            )
        }

        SectionLabel("Logs")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(text = "All", selected = !errorsOnly, onClick = { errorsOnly = false })
            PillButton(text = "Errors", selected = errorsOnly, onClick = { errorsOnly = true })
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(visibleLogs.joinToString("\n")))
                    copied = true
                    scope.launch {
                        delay(2_000)
                        copied = false
                    }
                },
                enabled = visibleLogs.isNotEmpty(),
            ) {
                Text(if (copied) "Copied" else "Copy")
            }
        }
        LogPanel(
            lines = visibleLogs,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            minHeight = 120.dp,
        )
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
