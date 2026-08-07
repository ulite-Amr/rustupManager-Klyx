package com.uliteamr.rustupmanager.lsp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.klyx.api.system.ProcessHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val INDEXING_WATCHDOG_MS = 8_000L

sealed interface LspStatus {
    data object NotStarted : LspStatus
    data class Running(val pid: Int) : LspStatus
    data class Exited(val code: Int) : LspStatus
}

/**
 * Tracks the rust-analyzer process spawned by [RustAnalyzerProvider] so the LSP dashboard
 * screen can show live status/logs even though the process itself is started and owned by
 * the host whenever it needs a language server for a .rs file.
 *
 * Indexing is detected by scanning the server's stdout for LSP `$/progress` notifications
 * (token `rustAnalyzer/cachePriming`, title "Indexing") as the host reads it. Klyx advertises
 * `workDoneProgress` but drops the notifications, so this wrapper is the only way to see the
 * `N/M` progress. A watchdog clears the flag if no progress line arrives for a while, covering
 * the case where the final `end` event is never received.
 */
object RustAnalyzerSession {

    var status by mutableStateOf<LspStatus>(LspStatus.NotStarted)
        private set

    var isIndexing by mutableStateOf(false)
        private set

    var indexingProgress by mutableStateOf<String?>(null)
        private set

    val logs: SnapshotStateList<String> = mutableStateListOf()

    private val indexingRegex = Regex("indexing[:\\s]+(\\d+)/(\\d+)")
    private val progressValueRegex = Regex("(\\d+)\\s*/\\s*(\\d+)")

    private var current: ProcessHandle? = null
    private var watchdogScope: CoroutineScope? = null
    private var watchdogJob: Job? = null

    /**
     * Wraps the server's stdout so the plugin can see `$/progress` frames while the host
     * continues to read the stream unchanged. Every byte is passed through untouched.
     */
    fun wrapStdout(source: InputStream): InputStream =
        ProgressScanInputStream(source) { token, kind, message -> onProgressEvent(token, kind, message) }

    fun attach(handle: ProcessHandle, scope: CoroutineScope, drainStderr: Boolean = false) {
        current = handle
        watchdogScope = scope
        status = LspStatus.Running(handle.pid)
        appendLog("--- started (pid ${handle.pid}) ---")

        if (!drainStderr) return

        // Only relevant when stderr is captured (Stdio.Capture) instead of inherited. Draining
        // it is required in that case: rust-analyzer logs to stderr, and if nothing reads that
        // pipe it fills up and the process blocks on its next write, freezing the server.
        scope.launch(Dispatchers.IO) {
            try {
                handle.stderr.bufferedReader().forEachLine { line -> onLine(line) }
            } catch (_: Exception) {
                // Stream closed because the process died or was killed; fall through to status update.
            } finally {
                stopIndexing()
                val code = exitCodeOf(handle)
                status = LspStatus.Exited(code)
                appendLog("--- exited (code $code) ---")
            }
        }
    }

    fun stop() {
        current?.kill()
    }

    fun log(line: String) {
        appendLog(line)
    }

    fun clearLogs() {
        logs.clear()
    }

    fun stopIndexing() {
        watchdogJob?.cancel()
        watchdogJob = null
        isIndexing = false
        indexingProgress = null
    }

    private fun onLine(line: String) {
        appendLog(line)
        val match = indexingRegex.find(line) ?: return
        val currentCount = match.groupValues[1].toIntOrNull() ?: return
        val total = match.groupValues[2].toIntOrNull() ?: return
        if (currentCount >= total) {
            stopIndexing()
        } else {
            isIndexing = true
            indexingProgress = match.groupValues[1] + "/" + match.groupValues[2]
            resetWatchdog()
        }
    }

    /** Handles a `$/progress` notification for the indexing token. */
    private fun onProgressEvent(token: String, kind: String, message: String?) {
        if (kind == "end") {
            stopIndexing()
            return
        }
        val progress = progressValueRegex.find(message.orEmpty())?.let { match ->
            val n = match.groupValues[1].toIntOrNull()
            val total = match.groupValues[2].toIntOrNull()
            if (n != null && total != null) n to total else null
        }
        when (kind) {
            "begin" -> {
                isIndexing = true
                indexingProgress = null
                resetWatchdog()
            }
            "report" -> if (progress != null) {
                isIndexing = true
                indexingProgress = "${progress.first}/${progress.second}"
                resetWatchdog()
            }
        }
    }

    private fun resetWatchdog() {
        val scope = watchdogScope ?: return
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(INDEXING_WATCHDOG_MS)
            watchdogJob = null
            isIndexing = false
            indexingProgress = null
        }
    }

    /** Polls briefly for the process to fully exit so we can report its real exit code. */
    private fun exitCodeOf(handle: ProcessHandle): Int {
        val deadline = System.currentTimeMillis() + 5_000
        while (handle.isRunning && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        return try {
            if (!handle.isRunning) handle.exitCode else -1
        } catch (_: Exception) {
            -1
        }
    }

    private fun appendLog(line: String) {
        if (logs.size > 500) logs.removeAt(0)
        logs.add(line)
    }
}

/**
 * Pass-through [InputStream] that additionally scans LSP JSON-RPC frames for `$/progress`
 * notifications while the host reads the stream. Frames are delimited by
 * `Content-Length: N\r\n\r\n<json>`, exactly as the host's own frame reader expects, so the
 * bytes handed to the host are unchanged.
 */
private class ProgressScanInputStream(
    private val source: InputStream,
    private val onProgress: (token: String, kind: String, message: String?) -> Unit,
) : InputStream() {

    private val buffer = ByteArrayOutputStream()
    private var bodyRemaining = -1

    override fun read(): Int {
        val b = source.read()
        if (b >= 0) feed(byteArrayOf(b.toByte()), 0, 1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = source.read(b, off, len)
        if (n > 0) feed(b, off, n)
        return n
    }

    override fun available(): Int = source.available()
    override fun close() = source.close()
    override fun skip(n: Long): Long = source.skip(n)

    private fun feed(b: ByteArray, off: Int, len: Int) {
        buffer.write(b, off, len)
        scan()
    }

    private fun scan() {
        while (true) {
            if (bodyRemaining < 0) {
                val data = buffer.toByteArray()
                val headerEnd = indexOfHeaderEnd(data) ?: return
                val header = String(data, 0, headerEnd, Charsets.UTF_8)
                val length = contentLengthOf(header)
                buffer.reset()
                if (length == null) {
                    // Not a Content-Length framed stream; give up scanning to avoid unbounded buffering.
                    return
                }
                bodyRemaining = length
                buffer.write(data, headerEnd, data.size - headerEnd)
            }
            if (buffer.size() < bodyRemaining) return
            val data = buffer.toByteArray()
            val json = String(data, 0, bodyRemaining, Charsets.UTF_8)
            val leftover = data.size - bodyRemaining
            buffer.reset()
            if (leftover > 0) buffer.write(data, bodyRemaining, leftover)
            bodyRemaining = -1
            handleFrame(json)
        }
    }

    private fun indexOfHeaderEnd(data: ByteArray): Int? {
        val cr = '\r'.code.toByte()
        val lf = '\n'.code.toByte()
        var i = 0
        while (i <= data.size - 4) {
            if (data[i] == cr && data[i + 1] == lf && data[i + 2] == cr && data[i + 3] == lf) {
                return i + 4
            }
            i++
        }
        return null
    }

    private fun contentLengthOf(header: String): Int? =
        Regex("(?i)content-length\\s*:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toIntOrNull()

    private fun handleFrame(json: String) {
        if (!json.contains("\$/progress")) return
        val token = tokenRegex.find(json)?.groupValues?.get(1) ?: return
        if (!token.contains("indexing", ignoreCase = true) &&
            !token.contains("cachePriming", ignoreCase = true)
        ) {
            return
        }
        val kind = kindRegex.find(json)?.groupValues?.get(1) ?: return
        val message = messageRegex.find(json)?.groupValues?.get(1)
        onProgress(token, kind, message)
    }

    companion object {
        private val tokenRegex = Regex("\"token\"\\s*:\\s*\"([^\"]*)\"")
        private val kindRegex = Regex("\"kind\"\\s*:\\s*\"(begin|report|end)\"")
        private val messageRegex = Regex("\"message\"\\s*:\\s*\"([^\"]*)\"")
    }
}
