package com.uliteamr.rustupmanager

import com.klyx.api.NavDestination
import com.klyx.api.Navigator
import com.klyx.api.lsp.LanguageServerRegistration
import com.klyx.api.lsp.LanguageServerRegistry
import com.klyx.api.plugin.Author
import com.klyx.api.plugin.KlyxPlugin
import com.klyx.api.plugin.PluginManifest
import com.klyx.api.plugin.PluginSettings
import com.klyx.api.plugin.PluginSettingsRegistration
import com.klyx.api.plugin.PluginSettingsRegistry
import com.klyx.api.plugin.plugin
import com.klyx.api.plugin.pluginScope
import com.klyx.api.plugin.runtime
import com.klyx.api.ui.ScreenId
import com.klyx.api.ui.ScreenRegistry
import com.klyx.api.ui.ToolbarAction
import com.klyx.api.ui.ToolbarCategory
import com.klyx.api.ui.ToolbarIcon
import com.klyx.api.ui.ToolbarRegistry
import com.uliteamr.rustupmanager.icons.Wrench
import com.uliteamr.rustupmanager.lsp.RustAnalyzerProvider
import com.uliteamr.rustupmanager.rustup.RustupController
import com.uliteamr.rustupmanager.settings.RustupSettingsContent
import com.uliteamr.rustupmanager.ui.DashboardScreen
import com.uliteamr.rustupmanager.ui.LspScreen
import kotlinx.coroutines.delay
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
    private val settings: PluginSettings by runtime()

    private val rustup = RustupController()
    private var lspRegistration: LanguageServerRegistration? = null
    private var settingsRegistration: PluginSettingsRegistration? = null

    override suspend fun onLoad() {
        screens[DASHBOARD_SCREEN] = {
            DashboardScreen(
                rustup = rustup,
                onOpenLsp = { navigator.navigateTo(NavDestination.Custom(LSP_SCREEN)) },
                onBack = { navigator.navigateBack() },
            )
        }

        screens[LSP_SCREEN] = {
            LspScreen(onBack = { navigator.navigateBack() })
        }

        toolbar.register(
            ToolbarAction(
                id = TOOLBAR_ACTION_ID,
                label = "Rust Toolchain",
                icon = ToolbarIcon(Wrench),
                category = ToolbarCategory("Rust"),
                priority = 100,
                onClick = { navigator.navigateTo(NavDestination.Custom(DASHBOARD_SCREEN)) },
            ),
        )

        lspRegistration = languageServers.register(LSP_PATTERN, RustAnalyzerProvider(rustup, pluginScope))

        settingsRegistration = settingsRegistry.register { RustupSettingsContent() }
    }

    override suspend fun onStart() {
        pluginScope.launch { autoUpdateLoop() }
    }

    override suspend fun onStop() {
        // No running resources to pause; the LSP process lifecycle is owned by the host.
    }

    override suspend fun onUnload() {
        screens.unregister(DASHBOARD_SCREEN)
        screens.unregister(LSP_SCREEN)
        toolbar.unregister(TOOLBAR_ACTION_ID)
        lspRegistration?.unregister()
        settingsRegistration?.unregister()
    }

    private suspend fun autoUpdateLoop() {
        while (true) {
            checkForUpdateIfDue()
            delay(UPDATE_CHECK_LOOP_DELAY_HOURS * 60 * 60 * 1000L)
        }
    }

    private suspend fun checkForUpdateIfDue() {
        if (!settings.getBoolean("autoCheckUpdates", true)) return
        if (!rustup.isInstalled()) return

        val intervalMs = settings.getInt("checkIntervalDays", 7) * 24 * 60 * 60 * 1000L
        val last = settings.getLong("lastUpdateCheck", 0L)
        val now = System.currentTimeMillis()
        if (now - last < intervalMs) return

        rustup.updateAll(onLine = {})
        settings.putLong("lastUpdateCheck", now)
    }
}
