package com.uliteamr.rustupmanager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.uliteamr.rustupmanager.icons.Wrench
import com.uliteamr.rustupmanager.lsp.LspStatus
import com.uliteamr.rustupmanager.lsp.RustAnalyzerSession
import com.uliteamr.rustupmanager.settings.SettingsKeys
import kotlinx.coroutines.launch

@Composable
fun LspScreen(
    settings: PluginSettings,
    onOpenFeatureParams: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val status = RustAnalyzerSession.status
    val isIndexing = RustAnalyzerSession.isIndexing
    val indexingProgress = RustAnalyzerSession.indexingProgress

    var reverseCompletion by remember { mutableStateOf(settings.getBoolean(SettingsKeys.reverseCompletion, false)) }
    var indexingToast by remember { mutableStateOf(settings.getBoolean(SettingsKeys.indexingToast, true)) }
    var toolbarAutoHide by remember { mutableStateOf(settings.getBoolean(SettingsKeys.toolbarAutoHide, false)) }

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

            SettingsCard(
                icon = Wrench,
                title = "Feature Parameters and Initialize",
                description = "Parameters sent to rust-analyzer on the initialize request — every feature on by default, toggleable",
                trailing = { TextButton(onClick = onOpenFeatureParams) { Text("Configure") } },
            )

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
                description = "Reverses rust-analyzer's completion results. Off by default: rust-analyzer already returns the most relevant entry on top",
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
