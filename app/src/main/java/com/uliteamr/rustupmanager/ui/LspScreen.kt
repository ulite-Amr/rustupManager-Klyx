package com.uliteamr.rustupmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uliteamr.rustupmanager.icons.Server
import com.uliteamr.rustupmanager.lsp.LspStatus
import com.uliteamr.rustupmanager.lsp.RustAnalyzerSession

@Composable
fun LspScreen(onBack: () -> Unit) {
    val status = RustAnalyzerSession.status
    val logs = RustAnalyzerSession.logs

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Language Server", onBack = onBack)

        SettingsCard(
            icon = Server,
            title = "rust-analyzer",
            description = statusText(status),
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
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        SectionLabel("Logs")
        LogPanel(
            lines = logs,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f, fill = false),
            minHeight = 200.dp,
        )
    }
}

private fun statusText(status: LspStatus): String = when (status) {
    LspStatus.NotStarted -> "Not started yet. It starts automatically when you open a .rs file."
    is LspStatus.Running -> "Running (pid ${status.pid})"
    is LspStatus.Exited -> "Exited (code ${status.code})"
}
