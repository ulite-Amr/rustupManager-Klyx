package com.uliteamr.rustupmanager.lsp

import com.klyx.api.lsp.LanguageServerProvider
import com.klyx.api.plugin.PluginSettings
import com.klyx.api.system.Stdin
import com.klyx.api.system.Stdio
import com.klyx.api.system.command
import com.klyx.lsp.LogMessageParams
import com.klyx.lsp.MessageType
import com.klyx.lsp.server.LanguageClient
import com.klyx.lsp.server.LanguageServer
import com.klyx.lsp.types.LSPAny
import com.uliteamr.rustupmanager.settings.SettingsKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Spawns rust-analyzer by its bare command name rather than an absolute path. Klyx resolves a
 * bare name against the rootfs's own PATH (falling through to a login-shell lookup), so this
 * works whether rust-analyzer came from a `rustup component add` or a prebuilt GitHub release
 * symlinked into ~/.local/bin -- and avoids a guest-path translation bug that broke the
 * absolute-path form.
 *
 * stdin/stdout/stderr are piped like Klyx's own reference implementation, and stderr is drained
 * by [RustAnalyzerSession] so rust-analyzer's log pipe can never fill up and stall the server
 * while its output is forwarded to Klyx's LSP log.
 */
class RustAnalyzerProvider(
    private val scope: CoroutineScope,
    private val settings: PluginSettings,
) : LanguageServerProvider {

    /**
     * Test overrides sent in the `initialize` request: binding-mode hints
     * (off by default) are enabled so their presence in the editor proves the
     * options actually reached rust-analyzer, and only the current target is
     * checked to keep indexing lighter on-device.
     */
    override fun initializationOptions(): LSPAny = buildJsonObject {
        put("check", buildJsonObject { put("allTargets", false) })
        put("inlayHints", buildJsonObject {
            put("bindingModeHints", buildJsonObject { put("enable", true) })
        })
    }

    override suspend fun startServer(client: LanguageClient): LanguageServer = withContext(Dispatchers.IO) {
        try {
            val handle = command("rust-analyzer")
                // RA_LOG=info keeps useful detail in the LSP log. Indexing progress
                // itself comes from the $/progress notifications we scan off stdout (see
                // RustAnalyzerSession.wrapStdout); the host drops those notifications, and
                // stderr's "indexing: N/M" lines are kept as a fallback for older builds.
                .env("RA_LOG", "info")
                .stdin(Stdin.Pipe)
                .stdout(Stdio.Capture)
                .stderr(Stdio.Capture)
                .spawn()
            RustAnalyzerSession.attach(handle, scope, client, drainStderr = true)

            val server = LanguageServer(
                client = client,
                stdout = RustAnalyzerSession.wrapStdout(handle.stdout),
                stdin = handle.stdin,
            )

            // rust-analyzer already returns completion items ordered by relevance (best first),
            // and Klyx preserves the server's order verbatim. The reversal workaround is off by
            // default; it exists only so users who preferred the old behavior can re-enable it.
            if (settings.getBoolean(SettingsKeys.reverseCompletion, false)) {
                ReversingLanguageServer(server)
            } else {
                server
            }
        } catch (e: Exception) {
            // Klyx's host-side LspManager swallows exceptions from startServer via runCatching,
            // so without this the failure would be completely invisible. Forward it to Klyx's LSP log.
            client.logMessage(
                LogMessageParams(MessageType.Error, "!!! startServer failed: ${e::class.simpleName}: ${e.message}")
            )
            throw e
        }
    }
}
