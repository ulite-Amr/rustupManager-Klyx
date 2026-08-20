package com.uliteamr.rustupmanager.settings

/**
 * Shared keys for the plugin's persisted settings. Keep them in one place so the
 * settings screen, the LSP dashboard, the provider and the plugin entry point all
 * read and write the same values.
 */
object SettingsKeys {
    /** Whether a toast is shown when rust-analyzer starts/stops indexing. */
    const val indexingToast = "lsp.indexingToast"

    /** initializationOptions: show binding-mode hints (mut/ref prefixes) in the editor. */
    const val bindingModeHints = "initOptions.bindingModeHints"

    /** initializationOptions: report macro-expansion diagnostics (experimental diagnostics). */
    const val macroDiagnostics = "initOptions.macroDiagnostics"

    /** initializationOptions: run cargo check on save so all diagnostics (not just semantic) appear. */
    const val checkOnSave = "initOptions.checkOnSave"

    /** initializationOptions: only check the current target instead of all targets. */
    const val currentTargetOnly = "initOptions.currentTargetOnly"

    /** initializationOptions: user-defined extra features, JSON array of {name, type, value}. */
    const val customInitOptions = "initOptions.custom"

    /** initializationOptions: raw JSON object overriding every other option; blank when unused. */
    const val rawInitOptions = "initOptions.raw"

    /** Persisted GitHub release list (JSON array of {tag, nightly}) so the versions list
     *  renders instantly from cache while a background check looks for newer releases. */
    const val releasesCache = "lsp.releasesCache"

    /** Last known dashboard snapshot (toolchains/components/targets/lsp) so the dashboard
     *  renders instantly from cache while a background check re-validates it. */
    const val stateCache = "state.cache"

    const val autoCheckUpdates = "autoCheckUpdates"
    const val checkIntervalDays = "checkIntervalDays"
    const val defaultChannel = "defaultChannel"
    const val lastUpdateCheck = "lastUpdateCheck"
}
