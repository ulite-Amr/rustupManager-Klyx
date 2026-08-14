package com.uliteamr.rustupmanager.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.uliteamr.rustupmanager.icons.Add
import com.uliteamr.rustupmanager.icons.Check
import com.uliteamr.rustupmanager.icons.Delete
import com.uliteamr.rustupmanager.icons.Download
import com.uliteamr.rustupmanager.icons.Info
import com.uliteamr.rustupmanager.icons.Refresh
import com.uliteamr.rustupmanager.icons.Server
import com.uliteamr.rustupmanager.icons.Terminal
import com.uliteamr.rustupmanager.icons.Warning
import com.uliteamr.rustupmanager.rustup.GithubRelease
import com.uliteamr.rustupmanager.rustup.LspChannel
import com.uliteamr.rustupmanager.rustup.LspSource
import com.uliteamr.rustupmanager.rustup.LspState
import com.uliteamr.rustupmanager.rustup.ManagedLspVersion
import com.uliteamr.rustupmanager.rustup.RustupController
import com.uliteamr.rustupmanager.rustup.RustupState
import com.uliteamr.rustupmanager.rustup.Toolchain
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    rustup: RustupController,
    onOpenLsp: () -> Unit,
    onOpenTerminal: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RustupState>(RustupState.Checking) }
    var busy by remember { mutableStateOf(false) }
    val logLines = remember { mutableStateListOf<String>() }

    suspend fun refresh() {
        state = rustup.loadState()
    }

    LaunchedEffect(Unit) { refresh() }

    fun runAction(label: String, action: suspend ((String) -> Unit) -> Boolean) {
        scope.launch {
            busy = true
            logLines.clear()
            logLines.add("$ $label")
            try {
                val ok = action { line -> logLines.add(line) }
                logLines.add(if (ok) "done" else "failed (see log above)")
            } catch (e: Exception) {
                logLines.add("error: ${e.message ?: "unexpected failure"}")
            } finally {
                busy = false
                refresh()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Rust Toolchain",
            onBack = onBack,
            trailing = {
                IconButton(
                    onClick = { scope.launch { refresh() } },
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                ) {
                    Icon(Refresh, contentDescription = "Refresh")
                }
            },
        )

        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                val fadeSpec = motion.fastSpatialSpec<Float>()
                val slideSpec = motion.defaultSpatialSpec<IntOffset>()
                (fadeIn(fadeSpec) + slideInVertically(slideSpec) { it / 12 }).togetherWith(
                    fadeOut(fadeSpec) + slideOutVertically(slideSpec) { -it / 12 },
                )
            },
            label = "rustup-state",
        ) { current ->
            when (current) {
                RustupState.Checking -> CheckingBody()
                RustupState.Installing -> CheckingBody()
                RustupState.EnvironmentMissing -> EnvironmentMissingBody(
                    onOpenTerminal = onOpenTerminal,
                    onRetry = { scope.launch { refresh() } },
                )
                RustupState.NotInstalled -> NotInstalledBody(
                    busy = busy,
                    logLines = logLines,
                    onInstall = { runAction("rustup-init") { onLine -> rustup.bootstrapInstall(onLine) } },
                    onReset = { runAction("reset rustup state") { onLine -> rustup.resetInstall(onLine) } },
                )
                is RustupState.Error -> ErrorBody(current.message) { scope.launch { refresh() } }
                is RustupState.Ready -> ReadyBody(
                    state = current,
                    busy = busy,
                    logLines = logLines,
                    onOpenLsp = onOpenLsp,
                    onSetDefault = { name -> runAction("rustup default $name") { onLine -> rustup.setDefaultToolchain(name, onLine) } },
                    onUninstallToolchain = { name -> runAction("rustup toolchain uninstall $name") { onLine -> rustup.uninstallToolchain(name, onLine) } },
                    onInstallToolchain = { name -> runAction("rustup toolchain install $name") { onLine -> rustup.installToolchain(name, onLine) } },
                    onUpdateToolchain = { name -> runAction("rustup update $name") { onLine -> rustup.updateToolchain(name, onLine) } },
                    onToggleComponent = { component, enable ->
                        val label = if (enable) "rustup component add $component" else "rustup component remove $component"
                        runAction(label) { onLine ->
                            if (enable) rustup.addComponent(component, onLine) else rustup.removeComponent(component, onLine)
                        }
                    },
                    onRemoveTarget = { target -> runAction("rustup target remove $target") { onLine -> rustup.removeTarget(target, onLine) } },
                    onAddTarget = { target -> runAction("rustup target add $target") { onLine -> rustup.addTarget(target, onLine) } },
                    onUpdateAll = { runAction("rustup update") { onLine -> rustup.updateAll(onLine) } },
                    onInstallRustupLsp = {
                        runAction("rustup component add rust-analyzer") { onLine -> rustup.installLspViaRustup(onLine) }
                    },
                    onRemoveRustupLsp = {
                        runAction("rustup component remove rust-analyzer") { onLine -> rustup.removeLspViaRustup(onLine) }
                    },
                    onInstallVersion = { tag ->
                        runAction("install rust-analyzer $tag") { onLine -> rustup.installLspViaGithub(tag, onLine) }
                    },
                    onUseVersion = { tag ->
                        runAction("activate rust-analyzer $tag") { onLine -> rustup.useManagedLsp(tag, onLine) }
                    },
                    onRemoveVersion = { tag ->
                        runAction("remove rust-analyzer $tag") { onLine -> rustup.removeManagedLsp(tag, onLine) }
                    },
                    onFetchLatest = { channel -> rustup.githubLatestTag(channel) },
                    onFetchReleases = { rustup.githubReleases() },
                )
            }
        }
    }
}

@Composable
private fun CheckingBody() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        InlineSpinner(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
        Text("Checking rustup...", modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun EnvironmentMissingBody(
    onOpenTerminal: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ExpressiveIconChip(icon = Terminal)
        Text(
            "Klyx Linux environment is not installed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Rustup Manager runs inside Klyx's built-in Linux environment (PRoot). " +
                "Open the terminal to finish the setup, then come back here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(onClick = onOpenTerminal, modifier = Modifier.padding(top = 20.dp)) {
            Icon(Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open terminal")
        }
        TextButton(onClick = onRetry) { Text("Check again") }
    }
}

@Composable
private fun ErrorBody(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ExpressiveIconChip(
            icon = Warning,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            "Something went wrong",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) {
            Icon(Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun NotInstalledBody(
    busy: Boolean,
    logLines: List<String>,
    onInstall: () -> Unit,
    onReset: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsCard(
            icon = Download,
            title = "rustup is not installed",
            description = "This installs rustup and a default stable toolchain inside Klyx's built-in Linux environment.",
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onInstall, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Working..." else "Install rustup")
            }
            OutlinedButton(onClick = onReset, enabled = !busy) {
                Icon(Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset & retry")
            }
        }
        if (busy) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Text(
            "If a previous attempt failed partway, \"Reset & retry\" clears the leftover rustup state "
                + "first \u2014 a stale settings file is a common cause of install failures.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp),
        )
        SectionLabel("Log")
        LogPanel(lines = logLines, modifier = Modifier.padding(horizontal = 16.dp), minHeight = 160.dp)
    }
}

@Composable
private fun ReadyBody(
    state: RustupState.Ready,
    busy: Boolean,
    logLines: List<String>,
    onOpenLsp: () -> Unit,
    onSetDefault: (String) -> Unit,
    onUninstallToolchain: (String) -> Unit,
    onInstallToolchain: (String) -> Unit,
    onUpdateToolchain: (String) -> Unit,
    onToggleComponent: (String, Boolean) -> Unit,
    onRemoveTarget: (String) -> Unit,
    onAddTarget: (String) -> Unit,
    onUpdateAll: () -> Unit,
    onInstallRustupLsp: () -> Unit,
    onRemoveRustupLsp: () -> Unit,
    onInstallVersion: (String) -> Unit,
    onUseVersion: (String) -> Unit,
    onRemoveVersion: (String) -> Unit,
    onFetchLatest: suspend (LspChannel) -> String?,
    onFetchReleases: suspend () -> List<GithubRelease>,
) {
    var lspSource by remember { mutableStateOf(LspSource.Rustup) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        item {
            LspSourceCard(
                lsp = state.lsp,
                selected = lspSource,
                onSelect = { lspSource = it },
                busy = busy,
                onInstallRustup = onInstallRustupLsp,
                onRemoveRustup = onRemoveRustupLsp,
                onInstallVersion = onInstallVersion,
                onUseVersion = onUseVersion,
                onRemoveVersion = onRemoveVersion,
                onFetchLatest = onFetchLatest,
                onFetchReleases = onFetchReleases,
                onOpenLogs = onOpenLsp,
            )
        }

        item { SectionLabel("Toolchains") }
        if (state.toolchains.isEmpty()) {
            item {
                SettingsCard(
                    icon = Info,
                    title = "No toolchains installed",
                    description = "Install one below — try stable, beta or nightly.",
                )
            }
        } else {
            items(state.toolchains) { toolchain ->
                ToolchainRow(
                    toolchain = toolchain,
                    enabled = !busy,
                    onSetDefault = { onSetDefault(toolchain.name) },
                    onUninstall = { onUninstallToolchain(toolchain.name) },
                    onUpdate = { onUpdateToolchain(toolchain.name) },
                )
            }
        }
        item { InstallToolchainRow(enabled = !busy, onInstall = onInstallToolchain) }

        item { SectionLabel("Components") }
        item {
            SettingsCard {
                Column {
                    ComponentRow("clippy", state.components.clippy, !busy, onToggleComponent)
                    ComponentRow("rustfmt", state.components.rustfmt, !busy, onToggleComponent)
                    ComponentRow("rust-src", state.components.rustSrc, !busy, onToggleComponent)
                }
            }
        }

        item { SectionLabel("Targets") }
        if (state.activeTargets.isEmpty()) {
            item {
                SettingsCard(
                    icon = Info,
                    title = "No extra targets",
                    description = "Your toolchain compiles for the host platform only.",
                )
            }
        } else {
            items(state.activeTargets) { target ->
                TargetRow(target = target, enabled = !busy, onRemove = { onRemoveTarget(target) })
            }
        }
        item { AddTargetRow(enabled = !busy, onAdd = onAddTarget) }

        item {
            OutlinedButton(
                onClick = onUpdateAll,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Update all toolchains")
            }
        }

        item { SectionLabel("Activity") }
        item {
            LogPanel(
                lines = logLines,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                minHeight = 60.dp,
            )
        }
    }
}

@Composable
private fun LspSourceCard(
    lsp: LspState,
    selected: LspSource,
    onSelect: (LspSource) -> Unit,
    busy: Boolean,
    onInstallRustup: () -> Unit,
    onRemoveRustup: () -> Unit,
    onInstallVersion: (String) -> Unit,
    onUseVersion: (String) -> Unit,
    onRemoveVersion: (String) -> Unit,
    onFetchLatest: suspend (LspChannel) -> String?,
    onFetchReleases: suspend () -> List<GithubRelease>,
    onOpenLogs: () -> Unit,
) {
    var channel by remember { mutableStateOf(LspChannel.Stable) }
    var latestTag by remember { mutableStateOf<String?>(null) }
    var releases by remember { mutableStateOf<List<GithubRelease>?>(null) }
    var fetchError by remember { mutableStateOf(false) }
    var fetchTick by remember { mutableStateOf(0) }

    suspend fun refreshLatest() {
        fetchError = false
        latestTag = null
        val tag = onFetchLatest(channel)
        if (tag == null) fetchError = true else latestTag = tag
    }

    suspend fun refreshReleases() {
        fetchError = false
        releases = null
        val list = onFetchReleases()
        if (list.isEmpty()) fetchError = true else releases = list
    }

    LaunchedEffect(selected, channel, fetchTick) {
        when (selected) {
            LspSource.Rustup -> Unit
            LspSource.Latest -> refreshLatest()
            LspSource.Versions -> refreshReleases()
        }
    }

    SettingsCard(
        icon = Server,
        title = "Language server",
        description = statusLine(lsp),
        trailing = { TextButton(onClick = onOpenLogs) { Text("Logs") } },
        content = {
            Column {
                SegmentedChoice(
                    options = listOf("rustup", "latest", "versions"),
                    selected = selected.name.lowercase(),
                    enabled = !busy,
                    onSelect = { onSelect(LspSource.valueOf(it.replaceFirstChar { c -> c.uppercase() })) },
                )
                when (selected) {
                    LspSource.Rustup -> RustupLspTab(
                        installed = lsp.installedViaRustup,
                        busy = busy,
                        onInstall = onInstallRustup,
                        onRemove = onRemoveRustup,
                    )
                    LspSource.Latest -> LatestLspTab(
                        lsp = lsp,
                        channel = channel,
                        onChannelChange = { channel = it },
                        latestTag = latestTag,
                        fetching = latestTag == null && !fetchError,
                        fetchError = fetchError,
                        onRefresh = { fetchTick++ },
                        busy = busy,
                        onInstall = { latestTag?.let(onInstallVersion) },
                        onUse = { latestTag?.let(onUseVersion) },
                        onRemove = { latestTag?.let(onRemoveVersion) },
                    )
                    LspSource.Versions -> VersionsLspTab(
                        lsp = lsp,
                        releases = releases,
                        fetching = releases == null && !fetchError,
                        fetchError = fetchError,
                        onRefresh = { fetchTick++ },
                        busy = busy,
                        onInstall = onInstallVersion,
                        onUse = onUseVersion,
                        onRemove = onRemoveVersion,
                    )
                }
            }
        },
    )
}

@Composable
private fun RustupLspTab(
    installed: Boolean,
    busy: Boolean,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(modifier = Modifier.padding(top = 12.dp)) {
        if (installed) {
            OutlinedButton(
                onClick = onRemove,
                enabled = !busy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Remove (rustup)")
            }
        } else {
            Button(onClick = onInstall, enabled = !busy) {
                Icon(Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Install via rustup")
            }
        }
    }
}

@Composable
private fun LatestLspTab(
    lsp: LspState,
    channel: LspChannel,
    onChannelChange: (LspChannel) -> Unit,
    latestTag: String?,
    fetching: Boolean,
    fetchError: Boolean,
    onRefresh: () -> Unit,
    busy: Boolean,
    onInstall: () -> Unit,
    onUse: () -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentedChoice(
                options = listOf("Stable", "Nightly"),
                selected = channel.name,
                enabled = !busy,
                onSelect = { onChannelChange(if (it == "Nightly") LspChannel.Nightly else LspChannel.Stable) },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh, enabled = !busy) { Icon(Refresh, contentDescription = "Refresh") }
        }

        val tag = latestTag
        val managed = tag?.let { t -> lsp.versions.firstOrNull { it.tag == t } }
        when {
            fetching -> Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineSpinner(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Checking GitHub...")
            }
            fetchError || tag == null -> Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Couldn't reach GitHub — check your connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRefresh, enabled = !busy) { Text("Retry") }
            }
            else -> {
                val label = if (channel == LspChannel.Stable) "Latest stable" else "Nightly (rolling)"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$label: $tag",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (managed?.isActive == true) {
                        Icon(Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("In use", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        managed?.isActive == true -> OutlinedButton(
                            onClick = onRemove,
                            enabled = !busy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Remove")
                        }
                        managed != null -> {
                            FilledTonalButton(onClick = onUse, enabled = !busy) {
                                Icon(Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Use")
                            }
                            OutlinedButton(
                                onClick = onRemove,
                                enabled = !busy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Remove")
                            }
                        }
                        else -> Button(onClick = onInstall, enabled = !busy) {
                            Icon(Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (channel == LspChannel.Stable) "Install latest stable" else "Install nightly")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionsLspTab(
    lsp: LspState,
    releases: List<GithubRelease>?,
    fetching: Boolean,
    fetchError: Boolean,
    onRefresh: () -> Unit,
    busy: Boolean,
    onInstall: (String) -> Unit,
    onUse: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Pick a version from rust-analyzer's releases",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh, enabled = !busy) { Icon(Refresh, contentDescription = "Refresh") }
        }
        when {
            fetching -> Row(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineSpinner(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Loading releases...")
            }
            fetchError || releases == null -> Text(
                "Couldn't reach GitHub — check your connection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            releases.isEmpty() -> Text(
                "No releases found.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            else -> releases.forEach { release ->
                ReleaseVersionRow(
                    release = release,
                    managed = lsp.versions.firstOrNull { it.tag == release.tag },
                    busy = busy,
                    onInstall = { onInstall(release.tag) },
                    onUse = { onUse(release.tag) },
                    onRemove = { onRemove(release.tag) },
                )
            }
        }
    }
}

@Composable
private fun ReleaseVersionRow(
    release: GithubRelease,
    managed: ManagedLspVersion?,
    busy: Boolean,
    onInstall: () -> Unit,
    onUse: () -> Unit,
    onRemove: () -> Unit,
) {
    val title = if (release.isNightly) "nightly (rolling)" else release.tag
    val description = when {
        managed?.isActive == true -> "In use"
        managed != null -> "Installed"
        else -> "Not installed"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (managed?.isActive == true) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        when {
            managed?.isActive == true -> OutlinedButton(
                onClick = onRemove,
                enabled = !busy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Remove") }
            managed != null -> {
                FilledTonalButton(onClick = onUse, enabled = !busy) { Text("Use") }
                OutlinedButton(
                    onClick = onRemove,
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            }
            else -> Button(onClick = onInstall, enabled = !busy) {
                Icon(Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Install")
            }
        }
    }
}

private fun statusLine(lsp: LspState): String = when {
    lsp.activeVersion != null -> "Using rust-analyzer ${lsp.activeVersion} (GitHub release)"
    lsp.installedViaRustup -> "Using rustup component rust-analyzer"
    else -> "Not installed yet"
}

@Composable
private fun ToolchainRow(
    toolchain: Toolchain,
    enabled: Boolean,
    onSetDefault: () -> Unit,
    onUninstall: () -> Unit,
    onUpdate: () -> Unit,
) {
    val description = buildString {
        if (toolchain.isDefault) append("default")
        if (toolchain.updateAvailable != null) {
            if (isNotEmpty()) append(" \u00b7 ")
            append("update available: ").append(toolchain.updateAvailable)
        }
    }.ifEmpty { null }

    SettingsCard(
        title = toolchain.name,
        description = description,
        content = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (toolchain.updateAvailable != null) {
                    FilledTonalButton(onClick = onUpdate, enabled = enabled) {
                        Icon(Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Update")
                    }
                }
                if (!toolchain.isDefault) {
                    OutlinedButton(onClick = onSetDefault, enabled = enabled) {
                        Icon(Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Set default")
                    }
                }
                OutlinedButton(
                    onClick = onUninstall,
                    enabled = enabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Remove")
                }
            }
        },
    )
}

@Composable
private fun InstallToolchainRow(enabled: Boolean, onInstall: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    SettingsCard(
        title = "Install a toolchain",
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "stable, beta, nightly, 1.80.0...",
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { if (name.isNotBlank()) { onInstall(name.trim()); name = "" } },
                    enabled = enabled && name.isNotBlank(),
                ) {
                    Icon(Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Install")
                }
            }
        },
    )
}

@Composable
private fun ComponentRow(
    id: String,
    installed: Boolean,
    enabled: Boolean,
    onToggle: (String, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            id,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (installed) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (installed) {
            OutlinedButton(
                onClick = { onToggle(id, false) },
                enabled = enabled,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Remove")
            }
        } else {
            Button(onClick = { onToggle(id, true) }, enabled = enabled) {
                Icon(Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Install")
            }
        }
    }
}

@Composable
private fun TargetRow(target: String, enabled: Boolean, onRemove: () -> Unit) {
    SettingsCard(
        title = target,
        trailing = {
            OutlinedButton(
                onClick = onRemove,
                enabled = enabled,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Remove")
            }
        },
    )
}

@Composable
private fun AddTargetRow(enabled: Boolean, onAdd: (String) -> Unit) {
    var target by remember { mutableStateOf("") }
    SettingsCard(
        title = "Add a target",
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextField(
                    value = target,
                    onValueChange = { target = it },
                    placeholder = "e.g. wasm32-unknown-unknown",
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { if (target.isNotBlank()) { onAdd(target.trim()); target = "" } },
                    enabled = enabled && target.isNotBlank(),
                ) {
                    Icon(Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add")
                }
            }
        },
    )
}
