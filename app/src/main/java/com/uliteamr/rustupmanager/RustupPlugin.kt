package com.uliteamr.rustupmanager

import com.klyx.api.NavDestination
import com.klyx.api.Navigator
import com.klyx.api.data.editor.WorkspaceTab
import com.klyx.api.event.editor.FileOpenedEvent
import com.klyx.api.event.eventBus
import com.klyx.api.lsp.LanguageServerRegistration
import com.klyx.api.lsp.LanguageServerRegistry
import com.klyx.api.plugin.Author
import com.klyx.api.plugin.KlyxPlugin
import com.klyx.api.plugin.PluginManifest
import com.klyx.api.plugin.PluginSettings
import com.klyx.api.plugin.PluginSettingsRegistration
import com.klyx.api.plugin.PluginSettingsRegistry
import com.klyx.api.plugin.plugin
import com.klyx.api.plugin.pluginContext
import com.klyx.api.plugin.pluginScope
import com.klyx.api.plugin.runtime
import com.klyx.api.plugin.showToast
import com.klyx.api.service.Tabs
import com.klyx.api.ui.ScreenId
import com.klyx.api.ui.ScreenRegistry
import com.klyx.api.ui.ToolbarAction
import com.klyx.api.ui.ToolbarCategory
import com.klyx.api.ui.ToolbarIcon
import com.klyx.api.ui.ToolbarRegistry
import com.klyx.core.event.EventSubscription
import com.uliteamr.rustupmanager.icons.Wrench
import com.uliteamr.rustupmanager.lsp.RustAnalyzerProvider
import com.uliteamr.rustupmanager.lsp.RustAnalyzerSession
import com.uliteamr.rustupmanager.rustup.RustupController
import com.uliteamr.rustupmanager.settings.RustupSettingsContent
import com.uliteamr.rustupmanager.settings.SettingsKeys
import com.uliteamr.rustupmanager.ui.DashboardScreen
import com.uliteamr.rustupmanager.ui.LspScreen
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private val DASHBOARD_SCREEN = ScreenId("com.uliteamr.rustupmanager.dashboard")
private val LSP_SCREEN = ScreenId("com.uliteamr.rustupmanager.lsp")
private const val TOOLBAR_ACTION_ID = "com.uliteamr.rustupmanager.open"
private const val LSP_PATTERN = "rs"
private const val UPDATE_CHECK_LOOP_DELAY_HOURS = 6L

@PluginManifest(
    id = "com.uliteamr.rustupmanager",
    name = "Rustup Manager",
    description = "Full Rust toolchain manager built on rustup: install and switch toolchains, manage components and targets, and get rust-analyzer wired up as a language server automatically.",
    icon = "icon.png",
    author = Author(name = "uliteamr"),
    license = "MIT",
)
class RustupPlugin : KlyxPlugin {

    private val screens: ScreenRegistry by plugin()
    private val toolbar: ToolbarRegistry by plugin()
    private val navigator: Navigator by plugin()
    private val languageServers: LanguageServerRegistry by plugin()
    private val settingsRegistry: PluginSettingsRegistry by plugin()
    private val tabs: Tabs by plugin()
    private val settings: PluginSettings by runtime()

    private val rustup = RustupController()
    private var lspRegistration: LanguageServerRegistration? = null
    private var settingsRegistration: PluginSettingsRegistration? = null
    private var fileOpenedSubscription: EventSubscription? = null
    private var toolbarActionRegistered = false

    override suspend fun onLoad() {
        screens[DASHBOARD_SCREEN] = {
            DashboardScreen(
                rustup = rustup,
                onOpenLsp = { navigator.navigateTo(NavDestination.Custom(LSP_SCREEN)) },
                onBack = { navigator.navigateBack() },
            )
        }

        screens[LSP_SCREEN] = {
            LspScreen(settings = settings, onBack = { navigator.navigateBack() })
        }

        lspRegistration = languageServers.register(LSP_PATTERN, RustAnalyzerProvider(pluginScope, settings))

        settingsRegistration = settingsRegistry.register { RustupSettingsContent() }

        // The toolbar action only shows while a .rs file is open. The plugin API has no
        // tab-close/switch event, so we reconcile against Tabs on every file-open event
        // (and on any settings change), and the onClick guards the stale-button case.
        syncToolbarVisibility()
        fileOpenedSubscription = pluginContext.eventBus.subscribe(FileOpenedEvent::class) {
            syncToolbarVisibility()
        }
        pluginScope.launch { settings.values.collect { syncToolbarVisibility() } }
        pluginScope.launch { observeIndexing() }
    }

    override suspend fun onStart() {
        pluginScope.launch { autoUpdateLoop() }
    }

    override suspend fun onStop() {
        // No running resources to pause; the LSP process lifecycle is owned by the host.
    }

    override suspend fun onUnload() {
        fileOpenedSubscription?.cancel()
        fileOpenedSubscription = null
        screens.unregister(DASHBOARD_SCREEN)
        screens.unregister(LSP_SCREEN)
        toolbar.unregister(TOOLBAR_ACTION_ID)
        toolbarActionRegistered = false
        lspRegistration?.unregister()
        settingsRegistration?.unregister()
    }

    /** Shows the toolbar action only while a .rs file is open, unless auto-hide is disabled. */
    private fun syncToolbarVisibility() {
        val autoHide = settings.getBoolean(SettingsKeys.toolbarAutoHide, false)
        val show = !autoHide || hasOpenRsFile()
        if (show && !toolbarActionRegistered) {
            toolbar.register(createToolbarAction())
            toolbarActionRegistered = true
        } else if (!show && toolbarActionRegistered) {
            toolbar.unregister(TOOLBAR_ACTION_ID)
            toolbarActionRegistered = false
        }
    }

    private fun createToolbarAction() = ToolbarAction(
        id = TOOLBAR_ACTION_ID,
        label = "Rust Toolchain",
        icon = ToolbarIcon(Wrench),
        category = ToolbarCategory("Rust"),
        priority = 100,
        onClick = {
            if (hasOpenRsFile()) {
                navigator.navigateTo(NavDestination.Custom(DASHBOARD_SCREEN))
            } else {
                showToast("Open a .rs file to manage rust-analyzer")
            }
        },
    )

    private fun hasOpenRsFile(): Boolean =
        tabs.opened.any { it is WorkspaceTab.TextFile && it.file.name.endsWith(".rs") }

    /** Toasts when rust-analyzer starts and finishes indexing. */
    private suspend fun observeIndexing() {
        snapshotFlow { RustAnalyzerSession.isIndexing }
            .drop(1)
            .distinctUntilChanged()
            .collect { indexing ->
                if (!settings.getBoolean(SettingsKeys.indexingToast, true)) return@collect
                if (indexing) {
                    showToast("rust-analyzer is indexing…")
                } else {
                    showToast("rust-analyzer finished indexing")
                }
            }
    }

    private suspend fun autoUpdateLoop() {
        while (true) {
            checkForUpdateIfDue()
            delay(UPDATE_CHECK_LOOP_DELAY_HOURS * 60 * 60 * 1000L)
        }
    }

    private suspend fun checkForUpdateIfDue() {
        if (!settings.getBoolean(SettingsKeys.autoCheckUpdates, true)) return
        if (!rustup.isInstalled()) return

        val intervalMs = settings.getInt(SettingsKeys.checkIntervalDays, 7) * 24 * 60 * 60 * 1000L
        val last = settings.getLong(SettingsKeys.lastUpdateCheck, 0L)
        val now = System.currentTimeMillis()
        if (now - last < intervalMs) return

        rustup.updateAll(onLine = {})
        settings.putLong(SettingsKeys.lastUpdateCheck, now)
    }
}
