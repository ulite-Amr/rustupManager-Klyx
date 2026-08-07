package com.uliteamr.rustupmanager.lsp

import com.klyx.api.lsp.LanguageServerProvider
import com.klyx.api.system.Stdin
import com.klyx.api.system.Stdio
import com.klyx.api.system.command
import com.klyx.lsp.server.LanguageClient
import com.klyx.lsp.server.LanguageServer
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
) : LanguageServerProvider {

    override suspend fun startServer(client: LanguageClient): LanguageServer = withContext(Dispatchers.IO) {
        try {
            val handle = command("rust-analyzer")
                .stdin(Stdin.Pipe)
                .stdout(Stdio.Capture)
                .stderr(Stdio.Capture)
                .spawn()
            RustAnalyzerSession.attach(handle, scope, drainStderr = true)

            LanguageServer(
                client = client,
                stdout = handle.stdout,
                stdin = handle.stdin,
            )
        } catch (e: Exception) {
            // Klyx's host-side LspManager swallows exceptions from startServer via runCatching,
            // so without this the failure would be completely invisible. Log it ourselves.
            RustAnalyzerSession.log("!!! startServer failed: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }
}
