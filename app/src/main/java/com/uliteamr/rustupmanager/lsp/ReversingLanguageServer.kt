package com.uliteamr.rustupmanager.lsp

import com.klyx.lsp.CompletionItem
import com.klyx.lsp.CompletionList
import com.klyx.lsp.CompletionParams
import com.klyx.lsp.server.LanguageServer
import com.klyx.lsp.server.TextDocumentService
import com.klyx.lsp.types.OneOf
import com.klyx.lsp.types.asLeft
import com.klyx.lsp.types.asRight
import com.klyx.lsp.types.fold

/**
 * Wraps a [LanguageServer] and reverses the order of the completion results returned by
 * `textDocument/completion`.
 *
 * Klyx preserves the exact order in which the server returns completion items, and rust-analyzer
 * responds with items sorted by score (most relevant last), which surfaces in the editor as a
 * reversed popup. This wrapper flips that order back so the most relevant entry is on top.
 */
class ReversingLanguageServer(
    private val delegate: LanguageServer,
    private val reverseCompletion: Boolean = true,
) : LanguageServer by delegate {

    override val textDocument: TextDocumentService =
        if (reverseCompletion) ReversingTextDocumentService(delegate.textDocument) else delegate.textDocument

    private class ReversingTextDocumentService(
        private val delegate: TextDocumentService,
    ) : TextDocumentService by delegate {

        override suspend fun completion(params: CompletionParams): OneOf<List<CompletionItem>, CompletionList>? {
            val result = delegate.completion(params) ?: return null
            return result.fold(
                leftFn = { it.reversed().asLeft() },
                rightFn = { it.copy(items = it.items.reversed()).asRight() },
            )
        }
    }
}
