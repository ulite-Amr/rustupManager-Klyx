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
 * Indexing is detected from rust-analyzer's stderr "indexing: N/M" progress lines (the host
 * drops the $/progress notification, so stderr is the only reliable signal). A watchdog
 * clears the flag if no progress line arrives for a while, covering the case where the
 * final "N/N" line is never printed.
 */
object RustAnalyzerSession {

    var status by mutableStateOf<LspStatus>(LspStatus.NotStarted)
        private set

    var isIndexing by mutableStateOf(false)
        private set

    var indexingProgress by mutableStateOf<String?>(null)
        private set

    val logs: SnapshotStateList<String> = mutableStateListOf()

    private val indexingRegex = Regex("indexing\\s+(\\d+)/(\\d+)")

    private var current: ProcessHandle? = null
    private var watchdogScope: CoroutineScope? = null
    private var watchdogJob: Job? = null

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
