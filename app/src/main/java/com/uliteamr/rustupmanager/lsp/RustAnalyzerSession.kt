package com.uliteamr.rustupmanager.lsp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.klyx.api.system.ProcessHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed interface LspStatus {
    data object NotStarted : LspStatus
    data class Running(val pid: Int) : LspStatus
    data class Exited(val code: Int) : LspStatus
}

/**
 * Tracks the rust-analyzer process spawned by [RustAnalyzerProvider] so the LSP dashboard
 * screen can show live status/logs even though the process itself is started and owned by
 * the host whenever it needs a language server for a .rs file.
 */
object RustAnalyzerSession {

    var status by mutableStateOf<LspStatus>(LspStatus.NotStarted)
        private set

    val logs: SnapshotStateList<String> = mutableStateListOf()

    private var current: ProcessHandle? = null

    fun attach(handle: ProcessHandle, scope: CoroutineScope) {
        current = handle
        status = LspStatus.Running(handle.pid)
        appendLog("--- started (pid ${handle.pid}) ---")

        // Continuously draining stderr is required here: rust-analyzer logs to stderr, and if
        // nothing reads that pipe it fills up and the process blocks on its next write, which
        // silently freezes the whole language server. This loop doubles as the live log feed.
        scope.launch(Dispatchers.IO) {
            try {
                handle.stderr.bufferedReader().forEachLine { line -> appendLog(line) }
            } catch (_: Exception) {
                // Stream closed because the process died or was killed; fall through to status update.
            } finally {
                val code = try {
                    if (!handle.isRunning) handle.exitCode else -1
                } catch (_: Exception) {
                    -1
                }
                status = LspStatus.Exited(code)
                appendLog("--- exited (code $code) ---")
            }
        }
    }

    fun stop() {
        current?.kill()
    }

    fun clearLogs() {
        logs.clear()
    }

    private fun appendLog(line: String) {
        if (logs.size > 500) logs.removeAt(0)
        logs.add(line)
    }
}
