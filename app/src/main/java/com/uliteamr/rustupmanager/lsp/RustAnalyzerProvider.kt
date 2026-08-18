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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
     * Initialization options sent in the `initialize` request. The original goal
     * behind the initializationOptions SDK feature: rust-analyzer's defaults only
     * surface semantic diagnostics, so macro-expansion errors and cargo-check
     * results never appear in the editor. These options enable the full set:
     *
     * - diagnostics.experimental.enable - macro-expansion diagnostics
     *   (the main reason this feature exists)
     * - checkOnSave.enable - run cargo check on save, so the complete
     *   diagnostic set (not just semantic) is reported
     * - check.allTargets / checkOnSave.allTargets - only the current target is
     *   checked, keeping indexing and check runs lighter on-device
     * - inlayHints.bindingModeHints.enable - binding-mode hints (off by
     *   default); kept enabled as the visible proof that the options reached
     *   rust-analyzer
     *
     * Built with JsonObject/JsonPrimitive constructors on purpose: the host's
     * release APK is R8-minified and prunes JsonObjectBuilder/JsonElementBuildersKt
     * (nothing in the host references them), so the buildJsonObject/put API would
     * fail at runtime with NoClassDefFoundError.
     */
    override fun initializationOptions(): LSPAny = JsonObject(
        mapOf(
            "check" to JsonObject(mapOf("allTargets" to JsonPrimitive(false))),
            "diagnostics" to JsonObject(
                mapOf(
                    "enable" to JsonPrimitive(true),
                    "experimental" to JsonObject(mapOf("enable" to JsonPrimitive(true)))
                )
            ),
            "checkOnSave" to JsonObject(
                mapOf(
                    "enable" to JsonPrimitive(true),
                    "allTargets" to JsonPrimitive(false)
                )
            ),
            "inlayHints" to JsonObject(
                mapOf(
                    "bindingModeHints" to JsonObject(mapOf("enable" to JsonPrimitive(true)))
                )
            )
        )
    )

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
