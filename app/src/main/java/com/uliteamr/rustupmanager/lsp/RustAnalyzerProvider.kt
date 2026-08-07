package com.uliteamr.rustupmanager.lsp

import com.klyx.api.lsp.LanguageServerProvider
import com.klyx.api.plugin.PluginSettings
import com.klyx.api.system.Stdin
import com.klyx.api.system.Stdio
import com.klyx.api.system.command
import com.klyx.lsp.server.LanguageClient
import com.klyx.lsp.server.LanguageServer
import com.uliteamr.rustupmanager.settings.SettingsKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Spawns rust-analyzer by its bare command name rather than an absolute path. Klyx resolves a
 * bare name against the rootfs's own PATH (falling through to a login-shell lookup), so this
 * works whether rust-analyzer was installed via `rustup component add` or `apt install` --
 * and avoids a guest-path translation bug that broke the absolute-path form.
 *
 * stdin/stdout/stderr are piped like Klyx's own reference implementation, and stderr is drained
 * by [RustAnalyzerSession] so rust-analyzer's log pipe can never fill up and stall the server
 * while its output stays visible in the LSP dashboard.
 */
class RustAnalyzerProvider(
    private val scope: CoroutineScope,
    private val settings: PluginSettings,
) : LanguageServerProvider {

    override suspend fun startServer(client: LanguageClient): LanguageServer = withContext(Dispatchers.IO) {
        try {
            val handle = command("rust-analyzer")
                // RA_LOG=info makes rust-analyzer print "indexing: N/M" progress on stderr so
                // the plugin can surface an indexing indicator and toast. The host drops the
                // $/progress notification, so stderr is the only reliable signal.
                .env("RA_LOG", "info")
                .stdin(Stdin.Pipe)
                .stdout(Stdio.Capture)
                .stderr(Stdio.Capture)
                .spawn()
            RustAnalyzerSession.attach(handle, scope, drainStderr = true)

            val server = LanguageServer(
                client = client,
                stdout = handle.stdout,
                stdin = handle.stdin,
            )

            // rust-analyzer returns completion items ordered best-last, and Klyx preserves the
            // server's order verbatim, so the popup ends up reversed. Flip it back unless the
            // user disabled the workaround.
            if (settings.getBoolean(SettingsKeys.reverseCompletion, true)) {
                ReversingLanguageServer(server)
            } else {
                server
            }
        } catch (e: Exception) {
            // Klyx's host-side LspManager swallows exceptions from startServer via runCatching,
            // so without this the failure would be completely invisible. Log it ourselves.
            RustAnalyzerSession.log("!!! startServer failed: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }
}
