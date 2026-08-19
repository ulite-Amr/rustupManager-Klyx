package com.uliteamr.rustupmanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uliteamr.rustupmanager.rustup.DownloadSample
import com.uliteamr.rustupmanager.rustup.GithubRelease
import com.uliteamr.rustupmanager.rustup.LspState
import com.uliteamr.rustupmanager.rustup.OpProgress
import com.uliteamr.rustupmanager.rustup.RustupController

/**
 * Shared rust-analyzer install state so the dashboard's "versions" tab and the full versions
 * screen stay in sync. Mutating [lsp] or [releases] recomposes only the LSP cards that read them.
 */
class LspManager(private val rustup: RustupController) {

    var lsp by mutableStateOf(LspState(installedViaRustup = false, versions = emptyList(), activeVersion = null))
        private set

    var releases by mutableStateOf<List<GithubRelease>?>(null)
        private set

    var fetchError by mutableStateOf(false)
        private set

    val tracker = OpTracker()

    suspend fun refresh() {
        lsp = rustup.lspState()
    }

    suspend fun fetchReleases() {
        releases = null
        fetchError = false
        val list = rustup.githubReleases()
        releases = list
        fetchError = list.isEmpty()
    }

    fun isBusy(tag: String): Boolean =
        tracker.state("lsp:install:$tag").value != null ||
            tracker.state("lsp:use:$tag").value != null ||
            tracker.state("lsp:remove:$tag").value != null

    fun installProgress(tag: String): OpProgress? = tracker.state("lsp:install:$tag").value

    fun rustupBusy(): Boolean =
        tracker.state("lsp:rustup:install").value != null ||
            tracker.state("lsp:rustup:use").value != null ||
            tracker.state("lsp:rustup:remove").value != null

    suspend fun installViaRustup(onLine: (String) -> Unit): Boolean =
        runOp("lsp:rustup:install", "Install rust-analyzer via rustup", onLine) { l, p -> rustup.installLspViaRustup(l, p) }

    suspend fun useViaRustup(onLine: (String) -> Unit): Boolean =
        runOp("lsp:rustup:use", "Activate the rustup component rust-analyzer", onLine) { l, _ -> rustup.useViaRustup(l) }

    suspend fun removeViaRustup(onLine: (String) -> Unit): Boolean =
        runOp("lsp:rustup:remove", "Remove rust-analyzer via rustup", onLine) { l, _ -> rustup.removeLspViaRustup(l) }

    suspend fun install(tag: String, onLine: (String) -> Unit): Boolean =
        runOp("lsp:install:$tag", "Install rust-analyzer $tag", onLine) { l, p -> rustup.installLspViaGithub(tag, l, p) }

    suspend fun use(tag: String, onLine: (String) -> Unit): Boolean =
        runOp("lsp:use:$tag", "Activate rust-analyzer $tag", onLine) { l, _ -> rustup.useManagedLsp(tag, l) }

    suspend fun remove(tag: String, onLine: (String) -> Unit): Boolean =
        runOp("lsp:remove:$tag", "Remove rust-analyzer $tag", onLine) { l, _ -> rustup.removeManagedLsp(tag, l) }

    private suspend fun runOp(
        key: String,
        label: String,
        onLine: (String) -> Unit,
        block: suspend (onLine: (String) -> Unit, onProgress: (DownloadSample) -> Unit) -> Boolean,
    ): Boolean {
        tracker.set(key, OpProgress(label, null))
        val ok = block(onLine) { sample ->
            tracker.set(key, OpProgress(label, sample.fraction, sample.downloadedBytes, sample.totalBytes))
        }
        tracker.set(key, null)
        if (ok) refresh()
        return ok
    }
}
