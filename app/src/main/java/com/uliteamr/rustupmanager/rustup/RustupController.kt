package com.uliteamr.rustupmanager.rustup

import com.klyx.api.data.fs.Paths
import com.klyx.api.system.ProcessEvent
import com.klyx.api.system.command
import com.klyx.api.terminal.rootFs
import com.klyx.api.terminal.versionFile
import kotlinx.coroutines.flow.collect

private const val RUSTUP = "/root/.cargo/bin/rustup"
private const val BASH = "/bin/bash"
private const val RUSTUP_INIT_URL = "https://sh.rustup.rs"
private const val MAX_STDERR_TAIL = 5

// Managed rust-analyzer installs live under ~/.local/share, with the active one symlinked to
// ~/.local/bin/rust-analyzer so Klyx's bare-name resolution (HOME_BIN_PATHS) picks it up.
private const val RA_SHARE_DIR = "/root/.local/share/rust-analyzer"
private const val RA_BIN_DIR = "/root/.local/bin"
private const val RA_BIN_LINK = "$RA_BIN_DIR/rust-analyzer"
private const val RA_API_BASE = "https://api.github.com/repos/rust-lang/rust-analyzer"
private const val RA_DL_BASE = "https://github.com/rust-lang/rust-analyzer/releases/download"

private val TAG_NAME_RE = Regex(""""tag_name"\s*:\s*"([^"]+)"""")
private val PRERELEASE_RE = Regex(""""prerelease"\s*:\s*(true|false)""")

// " 89.5 MiB / 89.5 MiB (100.0 %)  54.6 KiB/s" (rustup) or curl's " 45.2 MiB 12.3 %" progress.
private val PERCENT_RE = Regex("""(\d{1,3}(?:\.\d+)?)\s*%""")
private val SIZE_RE = Regex("""(\d+(?:\.\d+)?)\s*(KiB|MiB|GiB)""")
private val SIZE_TOTAL_RE = Regex("""(\d+(?:\.\d+)?)\s*(KiB|MiB|GiB)\s*/\s*(\d+(?:\.\d+)?)\s*(KiB|MiB|GiB)""")

private fun parseBytes(value: String, unit: String): Long = when (unit) {
    "KiB" -> (value.toDouble() * 1024).toLong()
    "MiB" -> (value.toDouble() * 1024 * 1024).toLong()
    "GiB" -> (value.toDouble() * 1024 * 1024 * 1024).toLong()
    else -> 0L
}

class RustupController {

    /** True while Klyx's PRoot Linux environment is bootstrapped (rootfs + .bootstrap-version). */
    suspend fun linuxEnvironmentReady(): Boolean = try {
        Paths.rootFs.exists() && Paths.versionFile.exists()
    } catch (_: Throwable) {
        false
    }

    suspend fun isInstalled(): Boolean = try {
        command(RUSTUP, "--version").output().exitCode == 0
    } catch (_: Exception) {
        false
    }

    suspend fun bootstrapInstall(onLine: (String) -> Unit, onProgress: (DownloadSample) -> Unit = {}): Boolean {
        // --progress-bar keeps curl's live progress (percent + size) on stderr even while its
        // stdout is piped into the installer, so the setup page shows real download progress.
        val script = "curl -L --progress-bar --proto '=https' --tlsv1.2 -sSf $RUSTUP_INIT_URL | " +
            "sh -s -- -y --default-toolchain stable --profile default"
        return runStreaming(BASH, arrayOf("-lc", script), onLine, onProgress)
    }

    suspend fun resetInstall(onLine: (String) -> Unit, onProgress: (DownloadSample) -> Unit = {}): Boolean =
        runStreaming(BASH, arrayOf("-lc", "rm -rf /root/.rustup /root/.cargo"), onLine, onProgress)

    suspend fun listToolchains(): List<Toolchain> {
        val result = command(RUSTUP, "toolchain", "list").output()
        if (result.exitCode != 0) return emptyList()
        val updates = toolchainUpdates()
        return result.stdoutLines
            .filter { it.isNotBlank() && !it.contains("no installed toolchains") }
            .map { line ->
                val name = line.substringBefore("(default)").trim()
                Toolchain(
                    name = name,
                    isDefault = line.contains("(default)"),
                    updateAvailable = updates[name],
                )
            }
    }

    /** Parses `rustup check` into toolchain name -> "old -> new" version string (absent if up to date). */
    private suspend fun toolchainUpdates(): Map<String, String> {
        val result = command(RUSTUP, "check").output()
        if (result.exitCode != 0 && result.exitCode != 1) return emptyMap()
        val updates = mutableMapOf<String, String>()
        for (line in result.stdoutLines) {
            val marker = " - Update available"
            val idx = line.indexOf(marker)
            if (idx <= 0) continue
            val name = line.substring(0, idx).trim()
            if (name == "rustup") continue
            val versions = line.substringAfter(":", "").trim()
            updates[name] = versions.ifEmpty { "update available" }
        }
        return updates
    }

    suspend fun installToolchain(name: String, onLine: (String) -> Unit, onProgress: (DownloadSample) -> Unit = {}): Boolean =
        runRustup("toolchain", "install", name, onLine = onLine, onProgress = onProgress)

    suspend fun uninstallToolchain(name: String, onLine: (String) -> Unit): Boolean =
        runRustup("toolchain", "uninstall", name, onLine = onLine)

    suspend fun setDefaultToolchain(name: String, onLine: (String) -> Unit): Boolean =
        runRustup("default", name, onLine = onLine)

    suspend fun updateToolchain(name: String, onLine: (String) -> Unit, onProgress: (DownloadSample) -> Unit = {}): Boolean =
        runRustup("update", name, onLine = onLine, onProgress = onProgress)

    suspend fun componentState(): ComponentState {
        val result = command(RUSTUP, "component", "list", "--installed").output()
        val installed = if (result.exitCode == 0) result.stdoutLines else emptyList()
        fun has(component: String) = installed.any { it.startsWith(component) }
        return ComponentState(
            clippy = has("clippy"),
            rustfmt = has("rustfmt"),
            rustSrc = has("rust-src"),
        )
    }

    suspend fun addComponent(component: String, onLine: (String) -> Unit, onProgress: (DownloadSample) -> Unit = {}): Boolean =
        runRustup("component", "add", component, onLine = onLine, onProgress = onProgress)

    suspend fun removeComponent(component: String, onLine: (String) -> Unit): Boolean =
        runRustup("component", "remove", component, onLine = onLine)

    suspend fun activeTargets(): List<String> {
        val result = command(RUSTUP, "target", "list", "--installed").output()
        if (result.exitCode != 0) return emptyList()
        return result.stdoutLines.filter { it.isNotBlank() }
    }

    suspend fun addTarget(target: String, onLine: (String) -> Unit, onProgress: (DownloadSample) -> Unit = {}): Boolean =
        runRustup("target", "add", target, onLine = onLine, onProgress = onProgress)

    suspend fun removeTarget(target: String, onLine: (String) -> Unit): Boolean =
        runRustup("target", "remove", target, onLine = onLine)

    suspend fun updateAll(onLine: (String) -> Unit, onProgress: (DownloadSample) -> Unit = {}): Boolean =
        runRustup("update", onLine = onLine, onProgress = onProgress)

    // --- rust-analyzer: source-independent by design ---
    // Klyx resolves a bare command name against the rootfs's own PATH (including whatever
    // rustup adds to .bashrc), so spawning "rust-analyzer" works whether it came from a
    // rustup component or a prebuilt GitHub release. See RustAnalyzerProvider.

    suspend fun lspState(): LspState {
        val viaRustup = command(RUSTUP, "component", "list", "--installed").output()
            .let { it.exitCode == 0 && it.stdoutLines.any { line -> line.startsWith("rust-analyzer") } }
        val active = activeManagedTag()
        val installedTags = command(BASH, "-lc", "ls -1 $RA_SHARE_DIR 2>/dev/null").output().stdoutLines
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        val versions = installedTags.map { tag ->
            ManagedLspVersion(tag = tag, installed = true, isActive = tag == active)
        }
        // Exactly one source is active at a time: a managed tag when the symlink exists,
        // otherwise the rustup component when it is installed.
        return LspState(
            installedViaRustup = viaRustup,
            versions = versions,
            activeVersion = active,
            rustupActive = active == null && viaRustup,
        )
    }

    /** Makes the rustup component the active source by dropping the managed symlink, so the
     *  bare-name resolution falls back to the toolchain's rust-analyzer. */
    suspend fun useViaRustup(onLine: (String) -> Unit): Boolean =
        runStreaming(BASH, arrayOf("-lc", "rm -f $RA_BIN_LINK"), onLine)

    suspend fun installLspViaRustup(onLine: (String) -> Unit, onProgress: (DownloadSample) -> Unit = {}): Boolean =
        runRustup("component", "add", "rust-analyzer", onLine = onLine, onProgress = onProgress)

    suspend fun removeLspViaRustup(onLine: (String) -> Unit): Boolean =
        runRustup("component", "remove", "rust-analyzer", onLine = onLine)

    // --- rust-analyzer from GitHub releases ---

    /** Latest stable tag (e.g. "2026-08-10") or the rolling "nightly" tag. */
    suspend fun githubLatestTag(channel: LspChannel): String? {
        val url = if (channel == LspChannel.Stable) {
            "$RA_API_BASE/releases/latest"
        } else {
            "$RA_API_BASE/releases/tags/nightly"
        }
        val result = command(BASH, "-lc", "curl -fsSL --max-time 20 '$url'").output()
        if (result.exitCode != 0) return null
        for (line in result.stdoutLines) {
            TAG_NAME_RE.find(line)?.let { return it.groupValues[1] }
        }
        return null
    }

    /** Release tags (newest first) with their nightly flag, fetched from the GitHub API. */
    suspend fun githubReleases(limit: Int = 50): List<GithubRelease> {
        val result = command(BASH, "-lc", "curl -fsSL --max-time 20 '$RA_API_BASE/releases?per_page=$limit'").output()
        if (result.exitCode != 0) return emptyList()
        val releases = mutableListOf<GithubRelease>()
        var pendingTag: String? = null
        for (line in result.stdoutLines) {
            TAG_NAME_RE.find(line)?.let { pendingTag = it.groupValues[1] }
            PRERELEASE_RE.find(line)?.let { m ->
                val tag = pendingTag ?: return@let
                releases += GithubRelease(tag = tag, isNightly = m.groupValues[1] == "true")
                pendingTag = null
            }
        }
        return releases
    }

    /** Installs a specific release tag (from [githubReleases] or [githubLatestTag]) and activates it. */
    suspend fun installLspViaGithub(tag: String, onLine: (String) -> Unit, onProgress: (DownloadSample) -> Unit = {}): Boolean {
        val arch = githubArch()
        if (arch == null) {
            onLine("error: unsupported device architecture for rust-analyzer downloads")
            return false
        }
        val script = """
            set -e
            mkdir -p $RA_SHARE_DIR/$tag $RA_BIN_DIR
            echo "downloading rust-analyzer $tag ($arch) from github.com/rust-lang/rust-analyzer ..."
            curl -fL --progress-bar --max-time 90 -o /tmp/rust-analyzer-$tag.gz '$RA_DL_BASE/$tag/rust-analyzer-$arch-unknown-linux-gnu.gz'
            gunzip -c /tmp/rust-analyzer-$tag.gz > $RA_SHARE_DIR/$tag/rust-analyzer
            chmod +x $RA_SHARE_DIR/$tag/rust-analyzer
            ln -sfn ../share/rust-analyzer/$tag/rust-analyzer $RA_BIN_LINK
            rm -f /tmp/rust-analyzer-$tag.gz
            echo "rust-analyzer $tag installed and activated"
        """.trimIndent()
        return runStreaming(BASH, arrayOf("-lc", script), onLine, onProgress)
    }

    /** Points the active `rust-analyzer` (resolved as a bare name via ~/.local/bin) at [tag].
     *  Also removes the rustup component if present, so exactly one source is active and the
     *  tag can never be shadowed by the toolchain's binary on PATH. */
    suspend fun useManagedLsp(tag: String, onLine: (String) -> Unit): Boolean {
        val script = """
            ln -sfn ../share/rust-analyzer/$tag/rust-analyzer $RA_BIN_LINK
            if $RUSTUP component list --installed 2>/dev/null | grep -q '^rust-analyzer'; then
                echo "removing rustup component rust-analyzer so $tag stays the active source"
                $RUSTUP component remove rust-analyzer
            fi
        """.trimIndent()
        return runStreaming(BASH, arrayOf("-lc", script), onLine)
    }

    /** Removes a managed version; if it was active, also drops the active link. */
    suspend fun removeManagedLsp(tag: String, onLine: (String) -> Unit): Boolean {
        val script = if (activeManagedTag() == tag) {
            "rm -rf $RA_SHARE_DIR/$tag && rm -f $RA_BIN_LINK"
        } else {
            "rm -rf $RA_SHARE_DIR/$tag"
        }
        return runStreaming(BASH, arrayOf("-lc", script), onLine)
    }

    private suspend fun activeManagedTag(): String? =
        command(BASH, "-lc", "readlink -f $RA_BIN_LINK").output().stdoutLines
            .firstOrNull { it.startsWith("$RA_SHARE_DIR/") }
            ?.substringAfter("$RA_SHARE_DIR/")
            ?.substringBeforeLast("/")
            ?.takeIf { it.isNotBlank() }

    private suspend fun githubArch(): String? =
        when (command(BASH, "-lc", "uname -m").output().stdoutLines.firstOrNull()?.trim()) {
            "aarch64", "arm64" -> "aarch64"
            "x86_64", "amd64" -> "x86_64"
            "armv7l", "armv8l" -> "arm"
            else -> null
        }

    suspend fun loadState(): RustupState {
        if (!linuxEnvironmentReady()) return RustupState.EnvironmentMissing
        if (!isInstalled()) return RustupState.NotInstalled
        return try {
            RustupState.Ready(
                toolchains = listToolchains(),
                components = componentState(),
                lsp = lspState(),
                activeTargets = activeTargets(),
            )
        } catch (e: Exception) {
            RustupState.Error(e.message ?: "Unknown error while reading rustup state")
        }
    }

    private suspend fun runRustup(vararg args: String, onLine: (String) -> Unit, onProgress: (DownloadSample) -> Unit = {}): Boolean =
        runStreaming(RUSTUP, arrayOf(*args), onLine, onProgress)

    private suspend fun runStreaming(
        program: String,
        args: Array<String>,
        onLine: (String) -> Unit,
        onProgress: (DownloadSample) -> Unit = {},
    ): Boolean {
        var success = false
        var exitCode = -1
        val stderrTail = mutableListOf<String>()
        try {
            command(program, *args).stream().collect { event ->
                when (event) {
                    is ProcessEvent.Stdout -> {
                        emitLines(event.text, onLine)
                        emitProgress(event.text, onProgress)
                    }
                    is ProcessEvent.Stderr -> {
                        val text = event.text
                        if (text.contains('\r')) {
                            // curl's --progress-bar writes \r-updated progress; surface only the
                            // percentage instead of flooding the log with every redraw.
                            emitProgress(text, onProgress)
                        } else {
                            emitLines(text, onLine)
                            emitProgress(text, onProgress)
                        }
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty()) {
                            stderrTail.add(trimmed)
                            if (stderrTail.size > MAX_STDERR_TAIL) stderrTail.removeAt(0)
                        }
                    }
                    is ProcessEvent.ExitCode -> {
                        success = event.code == 0
                        exitCode = event.code
                    }
                }
            }
        } catch (e: Exception) {
            onLine("error: ${e.message ?: "command failed to start"}")
            return false
        }
        if (!success) {
            diagnosticHint(stderrTail, exitCode)?.let { onLine("hint: $it") }
        }
        return success
    }

    /** Parses the last progress sample in a chunk (rustup "(100.0 %)" with "89.5 MiB / 89.5 MiB"
     *  totals, or curl's "45.2 MiB 12.3 %") and forwards it. Only the final redraw of a \r-updated
     *  line matters, so each \r segment is parsed in turn and the last non-empty sample wins. */
    private fun emitProgress(chunk: String, onProgress: (DownloadSample) -> Unit) {
        var sample: DownloadSample? = null
        for (segment in chunk.split('\r')) {
            var fraction: Float? = null
            var downloaded: Long? = null
            var total: Long? = null
            PERCENT_RE.find(segment)?.let { m ->
                m.groupValues[1].toFloatOrNull()?.let { value ->
                    fraction = (value / 100f).coerceIn(0f, 1f)
                }
            }
            SIZE_TOTAL_RE.find(segment)?.let { m ->
                downloaded = parseBytes(m.groupValues[1], m.groupValues[2])
                total = parseBytes(m.groupValues[3], m.groupValues[4])
            } ?: SIZE_RE.find(segment)?.let { m ->
                downloaded = parseBytes(m.groupValues[1], m.groupValues[2])
            }
            if (fraction != null || downloaded != null) {
                sample = DownloadSample(
                    fraction = fraction,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                )
            }
        }
        sample?.let(onProgress)
    }

    /** Maps a failed command's stderr to a short, actionable hint (or the last stderr line). */
    private fun diagnosticHint(stderrTail: List<String>, exitCode: Int): String? {
        val joined = stderrTail.joinToString(" ").lowercase()
        val lastLine = stderrTail.lastOrNull()?.trim().orEmpty()
        return when {
            exitCode == 126 || exitCode == 127 ->
                "command not found inside the Klyx Linux environment"
            "curl: (6)" in joined || "curl: (7)" in joined || "could not resolve" in joined ||
                "failed to connect" in joined || "connection timed out" in joined ->
                "network problem while downloading — check your connection"
            "no such file or directory" in joined ->
                "a required file is missing inside the Linux environment"
            joined.isNotBlank() -> lastLine.take(160).ifBlank { null }
            else -> null
        }
    }

    private fun emitLines(chunk: String, onLine: (String) -> Unit) {
        chunk.split('\n').forEach { line ->
            val trimmed = line.trimEnd('\r')
            if (trimmed.isNotEmpty()) onLine(trimmed)
        }
    }
}
