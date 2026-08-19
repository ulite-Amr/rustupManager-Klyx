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

enum class LspSource { Rustup, Versions }

data class ManagedLspVersion(
    val tag: String,
    val installed: Boolean,
    val isActive: Boolean,
)

data class GithubRelease(
    val tag: String,
    val isNightly: Boolean,
)

/** A live download sample parsed from rustup/curl output: a fraction 0..1 (null while
 *  indeterminate) plus optional byte counts ([downloadedBytes] alone, or both when the
 *  total is known). */
data class DownloadSample(
    val fraction: Float? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
)

/** Live progress of a single operation. [fraction] is 0..1, or null while indeterminate. */
data class OpProgress(
    val label: String,
    val fraction: Float?,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
)

/** LSP install state. Exactly one source is active at a time: [activeVersion] (a managed
 *  GitHub release) when set, otherwise the rustup component when [rustupActive] is true. */
data class LspState(
    val installedViaRustup: Boolean,
    val versions: List<ManagedLspVersion>,
    val activeVersion: String?,
    val rustupActive: Boolean = false,
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
