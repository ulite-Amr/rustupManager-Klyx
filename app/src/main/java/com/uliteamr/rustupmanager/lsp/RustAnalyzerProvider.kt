package com.uliteamr.rustupmanager.lsp

import com.klyx.api.lsp.LanguageServerProvider
import com.klyx.api.system.command
import com.klyx.lsp.server.LanguageClient
import com.klyx.lsp.server.LanguageServer
import com.klyx.lsp.server.createLanguageServer
import com.uliteamr.rustupmanager.rustup.RustupController
import kotlinx.coroutines.CoroutineScope
import kotlinx.io.asSink
import kotlinx.io.asSource

class RustAnalyzerProvider(
    private val rustup: RustupController,
    private val scope: CoroutineScope,
) : LanguageServerProvider {

    override suspend fun startServer(client: LanguageClient): LanguageServer {
        val binary = rustup.rustAnalyzerPath()
            ?: error("rust-analyzer is not installed. Add the component from the dashboard first.")

        val handle = command(binary)
            .env("RA_LOG", "info")
            .spawn()
        RustAnalyzerSession.attach(handle, scope)

        return createLanguageServer(
            client = client,
            out = handle.stdout.asSource(),
            `in` = handle.stdin.asSink(),
        )
    }
}
