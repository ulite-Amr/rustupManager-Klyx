package com.uliteamr.rustupmanager.rustup

import com.klyx.api.system.CommandResult
import com.klyx.api.system.ProcessEvent
import com.klyx.api.system.command
import kotlinx.coroutines.flow.Flow

private const val RUSTUP = "/root/.cargo/bin/rustup"
private const val BASH = "/bin/bash"
private const val RUSTUP_INIT_URL = "https://sh.rustup.rs"

private val KNOWN_COMPONENTS = listOf("rust-analyzer", "clippy", "rustfmt", "rust-src")

class RustupController {

    suspend fun isInstalled(): Boolean = try {
        command(RUSTUP, "--version").output().exitCode == 0
    } catch (_: Exception) {
        false
    }

    suspend fun bootstrapInstall(): Flow<ProcessEvent> {
        val script = "curl --proto '=https' --tlsv1.2 -sSf $RUSTUP_INIT_URL | " +
            "sh -s -- -y --default-toolchain stable --profile default"
        return command(BASH, "-lc", script).stream()
    }

    suspend fun listToolchains(): List<Toolchain> {
        val result = command(RUSTUP, "toolchain", "list").output()
        if (result.exitCode != 0) return emptyList()
        return result.stdoutLines
            .filter { it.isNotBlank() && !it.contains("no installed toolchains") }
            .map { line ->
                val isDefault = line.contains("(default)")
                Toolchain(
                    name = line.substringBefore("(default)").trim(),
                    isDefault = isDefault,
                )
            }
    }

    suspend fun installToolchain(name: String): CommandResult =
        command(RUSTUP, "toolchain", "install", name).output()

    suspend fun uninstallToolchain(name: String): CommandResult =
        command(RUSTUP, "toolchain", "uninstall", name).output()

    suspend fun setDefaultToolchain(name: String): CommandResult =
        command(RUSTUP, "default", name).output()

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

    suspend fun addComponent(component: String): CommandResult =
        command(RUSTUP, "component", "add", component).output()

    suspend fun removeComponent(component: String): CommandResult =
        command(RUSTUP, "component", "remove", component).output()

    suspend fun activeTargets(): List<String> {
        val result = command(RUSTUP, "target", "list", "--installed").output()
        if (result.exitCode != 0) return emptyList()
        return result.stdoutLines.filter { it.isNotBlank() }
    }

    suspend fun addTarget(target: String): CommandResult =
        command(RUSTUP, "target", "add", target).output()

    suspend fun removeTarget(target: String): CommandResult =
        command(RUSTUP, "target", "remove", target).output()

    suspend fun updateAll(): CommandResult =
        command(RUSTUP, "update").output()

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

    companion object {
        val availableComponents: List<String> get() = KNOWN_COMPONENTS
    }
}
