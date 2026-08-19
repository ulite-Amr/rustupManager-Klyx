package com.uliteamr.rustupmanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.klyx.api.plugin.PluginSettings
import com.uliteamr.rustupmanager.rustup.DownloadSample
import com.uliteamr.rustupmanager.rustup.GithubRelease
import com.uliteamr.rustupmanager.rustup.LspState
import com.uliteamr.rustupmanager.rustup.OpProgress
import com.uliteamr.rustupmanager.rustup.RustupController
import com.uliteamr.rustupmanager.settings.SettingsKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Shared rust-analyzer install state so the dashboard's "versions" tab and the LSP
 * cards stay in sync. Mutating [lsp] or [releases] recomposes only the cards that read them.
 *
 * The release list is cached in settings: [ensureReleases] renders the cache instantly and
 * then re-checks GitHub in the background, so new releases appear in the list without a
 * manual refresh and a failed check never blanks an already-loaded list.
 */
class LspManager(
    private val rustup: RustupController,
    private val settingsProvider: () -> PluginSettings,
) {

    private val settings get() = settingsProvider()

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

    /** Shows the cached list immediately (when one exists), then re-checks GitHub in the
     *  background. Newer releases replace the list as soon as they arrive. */
    suspend fun ensureReleases() {
        if (releases == null) {
            loadCachedReleases()?.let { releases = it }
        }
        fetchReleases()
    }

    /** Fetches the release list from GitHub. A successful fetch replaces the list and updates
     *  the cache; a failed fetch keeps whatever is already shown (cache or last fetch) and only
     *  surfaces an error when there is nothing at all to show. */
    suspend fun fetchReleases() {
        val list = rustup.githubReleases()
        if (list.isNotEmpty()) {
            releases = list
            fetchError = false
            settings.putString(SettingsKeys.releasesCache, encodeReleases(list))
        } else {
            fetchError = releases == null
        }
    }

    private fun loadCachedReleases(): List<GithubRelease>? {
        val raw = settings.getString(SettingsKeys.releasesCache, "") ?: return null
        if (raw.isBlank()) return null
        return runCatching {
            (Json.parseToJsonElement(raw) as? JsonArray)?.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val tag = (obj["tag"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                GithubRelease(tag = tag, isNightly = (obj["nightly"] as? JsonPrimitive)?.contentOrNull == "true")
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
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

private fun encodeReleases(releases: List<GithubRelease>): String {
    val array = JsonArray(
        releases.map {
            JsonObject(
                mapOf(
                    "tag" to JsonPrimitive(it.tag),
                    "nightly" to JsonPrimitive(it.isNightly),
                )
            )
        }
    )
    return Json.encodeToString(JsonElement.serializer(), array)
}
