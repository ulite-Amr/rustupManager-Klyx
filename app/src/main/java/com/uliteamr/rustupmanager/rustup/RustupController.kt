package com.uliteamr.rustupmanager.rustup

import com.klyx.api.system.ProcessEvent
import com.klyx.api.system.command
import kotlinx.coroutines.flow.collect

private const val RUSTUP = "/root/.cargo/bin/rustup"
private const val BASH = "/bin/bash"
private const val RUSTUP_INIT_URL = "https://sh.rustup.rs"

class RustupController {

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
        return result.stdoutLines
            .filter { it.isNotBlank() && !it.contains("no installed toolchains") }
            .map { line ->
                Toolchain(
                    name = line.substringBefore("(default)").trim(),
                    isDefault = line.contains("(default)"),
                )
            }
    }

    suspend fun installToolchain(name: String, onLine: (String) -> Unit): Boolean =
        runRustup("toolchain", "install", name, onLine = onLine)

    suspend fun uninstallToolchain(name: String, onLine: (String) -> Unit): Boolean =
        runRustup("toolchain", "uninstall", name, onLine = onLine)

    suspend fun setDefaultToolchain(name: String, onLine: (String) -> Unit): Boolean =
        runRustup("default", name, onLine = onLine)

    suspend fun componentState(): ComponentState {
        val result = command(RUSTUP, "component", "list", "--installed").output()
        val installed = if (result.exitCode == 0) result.stdoutLines else emptyList()
        fun has(component: String) = installed.any { it.startsWith(component) }
        return ComponentState(
            rustAnalyzer = has("rust-analyzer"),
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

    suspend fun rustAnalyzerPath(): String? {
        val result = command(RUSTUP, "which", "rust-analyzer").output()
        if (result.exitCode != 0) return null
        return result.stdoutText.trim().takeIf { it.isNotEmpty() }
    }

    suspend fun loadState(): RustupState {
        if (!isInstalled()) return RustupState.NotInstalled
        return try {
            RustupState.Ready(
                toolchains = listToolchains(),
                components = componentState(),
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
        command(program, *args).stream().collect { event ->
            when (event) {
                is ProcessEvent.Stdout -> emitLines(event.text, onLine)
                is ProcessEvent.Stderr -> emitLines(event.text, onLine)
                is ProcessEvent.ExitCode -> success = event.code == 0
            }
        }
        return success
    }

    private fun emitLines(chunk: String, onLine: (String) -> Unit) {
        chunk.split('\n').forEach { line ->
            val trimmed = line.trimEnd('\r')
            if (trimmed.isNotEmpty()) onLine(trimmed)
        }
    }
}
