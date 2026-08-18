package com.uliteamr.rustupmanager.settings

/**
 * Shared keys for the plugin's persisted settings. Keep them in one place so the
 * settings screen, the LSP dashboard, the provider and the plugin entry point all
 * read and write the same values.
 */
object SettingsKeys {
    /** Whether rust-analyzer completion items are reversed back to relevance order. */
    const val reverseCompletion = "lsp.reverseCompletion"

    /** Whether a toast is shown when rust-analyzer starts/stops indexing. */
    const val indexingToast = "lsp.indexingToast"

    /** Whether the dashboard toolbar button only shows while a .rs file is open. */
    const val toolbarAutoHide = "toolbar.autoHide"

    /** initializationOptions: show binding-mode hints (mut/ref prefixes) in the editor. */
    const val bindingModeHints = "initOptions.bindingModeHints"

    /** initializationOptions: report macro-expansion diagnostics (experimental diagnostics). */
    const val macroDiagnostics = "initOptions.macroDiagnostics"

    /** initializationOptions: run cargo check on save so all diagnostics (not just semantic) appear. */
    const val checkOnSave = "initOptions.checkOnSave"

    /** initializationOptions: only check the current target instead of all targets. */
    const val currentTargetOnly = "initOptions.currentTargetOnly"

    const val autoCheckUpdates = "autoCheckUpdates"
    const val checkIntervalDays = "checkIntervalDays"
    const val defaultChannel = "defaultChannel"
    const val lastUpdateCheck = "lastUpdateCheck"
}
