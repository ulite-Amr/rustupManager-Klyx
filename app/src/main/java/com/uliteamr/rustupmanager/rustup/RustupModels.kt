package com.uliteamr.rustupmanager.rustup

data class Toolchain(
    val name: String,
    val isDefault: Boolean,
)

data class ComponentState(
    val rustAnalyzer: Boolean,
    val clippy: Boolean,
    val rustfmt: Boolean,
    val rustSrc: Boolean,
)

sealed interface RustupState {
    data object Checking : RustupState
    data object NotInstalled : RustupState
    data object Installing : RustupState
    data class Ready(
        val toolchains: List<Toolchain>,
        val components: ComponentState,
        val activeTargets: List<String>,
    ) : RustupState
    data class Error(val message: String) : RustupState
}
