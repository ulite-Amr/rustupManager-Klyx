package com.uliteamr.rustupmanager.lsp

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.readByteArray

private const val PREVIEW_LIMIT = 400

/** Wraps a RawSource and logs every chunk read from it (server -> client direction). */
class LoggingRawSource(
    private val delegate: RawSource,
    private val tag: String,
    private val onData: (String) -> Unit,
) : RawSource {
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        val before = sink.size
        val read = delegate.readAtMostTo(sink, byteCount)
        if (read > 0) {
            val preview = sink.peek().apply { skip(before) }.readByteArray()
            onData("$tag ${preview.decodeToString().take(PREVIEW_LIMIT)}")
        }
        return read
    }

    override fun close() = delegate.close()
}

/** Wraps a RawSink and logs every chunk written to it (client -> server direction). */
class LoggingRawSink(
    private val delegate: RawSink,
    private val tag: String,
    private val onData: (String) -> Unit,
) : RawSink {
    override fun write(source: Buffer, byteCount: Long) {
        val preview = source.peek().readByteArray(byteCount.coerceAtMost(source.size).toInt())
        onData("$tag ${preview.decodeToString().take(PREVIEW_LIMIT)}")
        delegate.write(source, byteCount)
    }

    override fun flush() = delegate.flush()
    override fun close() = delegate.close()
}
