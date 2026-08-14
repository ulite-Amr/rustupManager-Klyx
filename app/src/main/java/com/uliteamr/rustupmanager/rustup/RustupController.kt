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

    suspend fun bootstrapInstall(onLine: (String) -> Unit): Boolean {
        val script = "curl --proto '=https' --tlsv1.2 -sSf $RUSTUP_INIT_URL | " +
            "sh -s -- -y --default-toolchain stable --profile default"
        return runStreaming(BASH, arrayOf("-lc", script), onLine)
    }

    suspend fun resetInstall(onLine: (String) -> Unit): Boolean =
        runStreaming(BASH, arrayOf("-lc", "rm -rf /root/.rustup /root/.cargo"), onLine)

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

    suspend fun installToolchain(name: String, onLine: (String) -> Unit): Boolean =
        runRustup("toolchain", "install", name, onLine = onLine)

    suspend fun uninstallToolchain(name: String, onLine: (String) -> Unit): Boolean =
        runRustup("toolchain", "uninstall", name, onLine = onLine)

    suspend fun setDefaultToolchain(name: String, onLine: (String) -> Unit): Boolean =
        runRustup("default", name, onLine = onLine)

    suspend fun updateToolchain(name: String, onLine: (String) -> Unit): Boolean =
        runRustup("update", name, onLine = onLine)

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

    suspend fun addComponent(component: String, onLine: (String) -> Unit): Boolean =
        runRustup("component", "add", component, onLine = onLine)

    suspend fun removeComponent(component: String, onLine: (String) -> Unit): Boolean =
        runRustup("component", "remove", component, onLine = onLine)

    suspend fun activeTargets(): List<String> {
        val result = command(RUSTUP, "target", "list", "--installed").output()
        if (result.exitCode != 0) return emptyList()
        return result.stdoutLines.filter { it.isNotBlank() }
    }

    suspend fun addTarget(target: String, onLine: (String) -> Unit): Boolean =
        runRustup("target", "add", target, onLine = onLine)

    suspend fun removeTarget(target: String, onLine: (String) -> Unit): Boolean =
        runRustup("target", "remove", target, onLine = onLine)

    suspend fun updateAll(onLine: (String) -> Unit): Boolean =
        runRustup("update", onLine = onLine)

    // --- rust-analyzer: source-independent by design ---
    // Klyx resolves a bare command name against the rootfs's own PATH (including whatever
    // rustup adds to .bashrc), so spawning "rust-analyzer" works the same whether it was
    // installed via `rustup component add` or `apt install`. See RustAnalyzerProvider.

    suspend fun lspState(): LspState {
        val viaRustup = command(RUSTUP, "component", "list", "--installed").output()
            .let { it.exitCode == 0 && it.stdoutLines.any { line -> line.startsWith("rust-analyzer") } }
        val viaApt = command(BASH, "-lc", "dpkg -s rust-analyzer").output().exitCode == 0
        return LspState(installedViaRustup = viaRustup, installedViaApt = viaApt)
    }

    suspend fun installLspViaRustup(onLine: (String) -> Unit): Boolean =
        runRustup("component", "add", "rust-analyzer", onLine = onLine)

    suspend fun removeLspViaRustup(onLine: (String) -> Unit): Boolean =
        runRustup("component", "remove", "rust-analyzer", onLine = onLine)

    suspend fun installLspViaApt(onLine: (String) -> Unit): Boolean =
        runStreaming(BASH, arrayOf("-lc", "apt-get update && apt-get install -y rust-analyzer"), onLine)

    suspend fun removeLspViaApt(onLine: (String) -> Unit): Boolean =
        runStreaming(BASH, arrayOf("-lc", "apt-get remove -y rust-analyzer"), onLine)

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

    private suspend fun runRustup(vararg args: String, onLine: (String) -> Unit): Boolean =
        runStreaming(RUSTUP, arrayOf(*args), onLine)

    private suspend fun runStreaming(program: String, args: Array<String>, onLine: (String) -> Unit): Boolean {
        var success = false
        var exitCode = -1
        val stderrTail = mutableListOf<String>()
        try {
            command(program, *args).stream().collect { event ->
                when (event) {
                    is ProcessEvent.Stdout -> emitLines(event.text, onLine)
                    is ProcessEvent.Stderr -> {
                        emitLines(event.text, onLine)
                        val trimmed = event.text.trim()
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
