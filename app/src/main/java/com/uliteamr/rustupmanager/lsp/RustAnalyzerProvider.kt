package com.uliteamr.rustupmanager.lsp

import com.klyx.api.lsp.LanguageServerProvider
import com.klyx.api.system.Stdin
import com.klyx.api.system.Stdio
import com.klyx.api.system.command
import com.klyx.lsp.server.LanguageClient
import com.klyx.lsp.server.LanguageServer
import com.klyx.lsp.server.createLanguageServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.io.asSink
import kotlinx.io.asSource

/**
 * Spawns rust-analyzer by its bare command name rather than an absolute path. Klyx resolves a
 * bare name against the rootfs's own PATH (falling through to a login-shell lookup), so this
 * works whether rust-analyzer was installed via `rustup component add` or `apt install` --
 * and avoids a guest-path translation bug that broke the absolute-path form.
 *
 * stderr is set to Inherit (not Capture) here to match a known-working reference
 * implementation exactly, instead of capturing it ourselves and draining it in a background
 * coroutine -- ruling out our own stderr-draining code as a source of the bridge issue.
 */
class RustAnalyzerProvider(
    private val scope: CoroutineScope,
) : LanguageServerProvider {

    override suspend fun startServer(client: LanguageClient): LanguageServer {
        try {
            val handle = command("rust-analyzer")
                .env("RA_LOG", "info")
                .stdin(Stdin.Pipe)
                .stdout(Stdio.Capture)
                .stderr(Stdio.Inherit)
                .spawn()
            RustAnalyzerSession.attach(handle, scope, drainStderr = false)

            return createLanguageServer(
                client = client,
                out = handle.stdout.asSource(),
                `in` = handle.stdin.asSink(),
            )
        } catch (e: Exception) {
            // Klyx's host-side LspManager swallows exceptions from startServer via runCatching,
            // so without this the failure would be completely invisible. Log it ourselves.
            RustAnalyzerSession.log("!!! startServer failed: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }
}
