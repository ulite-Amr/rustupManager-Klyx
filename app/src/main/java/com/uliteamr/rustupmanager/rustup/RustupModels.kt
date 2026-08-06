package com.uliteamr.rustupmanager.rustup

data class Toolchain(
    val name: String,
    val isDefault: Boolean,
    val updateAvailable: String? = null,
)

data class ComponentState(
    val clippy: Boolean,
    val rustfmt: Boolean,
    val rustSrc: Boolean,
)

enum class LspSource { Rustup, Apt }

data class LspState(
    val installedViaRustup: Boolean,
    val installedViaApt: Boolean,
)

sealed interface RustupState {
    data object Checking : RustupState
    data object NotInstalled : RustupState
    data object Installing : RustupState
    data class Ready(
        val toolchains: List<Toolchain>,
        val components: ComponentState,
        val lsp: LspState,
        val activeTargets: List<String>,
    ) : RustupState
    data class Error(val message: String) : RustupState
}
