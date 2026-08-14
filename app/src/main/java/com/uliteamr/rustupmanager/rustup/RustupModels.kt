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

enum class LspSource { Rustup, Latest, Versions }

enum class LspChannel { Stable, Nightly }

data class ManagedLspVersion(
    val tag: String,
    val installed: Boolean,
    val isActive: Boolean,
)

data class GithubRelease(
    val tag: String,
    val isNightly: Boolean,
)

data class LspState(
    val installedViaRustup: Boolean,
    val versions: List<ManagedLspVersion>,
    val activeVersion: String?,
)

sealed interface RustupState {
    data object Checking : RustupState
    data object EnvironmentMissing : RustupState
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
