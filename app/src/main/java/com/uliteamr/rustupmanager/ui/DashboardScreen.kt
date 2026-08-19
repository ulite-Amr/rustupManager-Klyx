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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.klyx.api.service.Logger
import com.klyx.api.service.error
import com.klyx.api.service.info
import com.klyx.api.service.rememberLogger
import com.klyx.api.service.warn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.vector.ImageVector
import com.uliteamr.rustupmanager.icons.Add
import com.uliteamr.rustupmanager.icons.Check
import com.uliteamr.rustupmanager.icons.Delete
import com.uliteamr.rustupmanager.icons.Download
import com.uliteamr.rustupmanager.icons.Info
import com.uliteamr.rustupmanager.icons.Moon
import com.uliteamr.rustupmanager.icons.Refresh
import com.uliteamr.rustupmanager.icons.Server
import com.uliteamr.rustupmanager.icons.Star
import com.uliteamr.rustupmanager.icons.Terminal
import com.uliteamr.rustupmanager.icons.Warning
import com.uliteamr.rustupmanager.rustup.DownloadSample
import com.uliteamr.rustupmanager.rustup.GithubRelease
import com.uliteamr.rustupmanager.rustup.LspSource
import com.uliteamr.rustupmanager.rustup.LspState
import com.uliteamr.rustupmanager.rustup.ManagedLspVersion
import com.uliteamr.rustupmanager.rustup.OpProgress
import com.uliteamr.rustupmanager.rustup.RustupController
import com.uliteamr.rustupmanager.rustup.RustupState
import com.uliteamr.rustupmanager.rustup.Toolchain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val LOG_TAG = "Rustup"

/** Runs an operation, publishing live [OpProgress] into [progress] (its owning card recomposes
 *  only), logging output to the SDK, then runs [onDone] (a section-scoped refresh). */
private fun runOp(
    scope: CoroutineScope,
    logger: Logger,
    progress: MutableState<OpProgress?>,
    label: String,
    onDone: suspend () -> Unit,
    action: suspend (onLine: (String) -> Unit, onProgress: (DownloadSample) -> Unit) -> Boolean,
) {
    scope.launch {
        progress.value = OpProgress(label, null)
        logger.info(LOG_TAG, "$ $label")
        try {
            val ok = action(
                { line -> logger.info(LOG_TAG, line) },
                { sample -> progress.value = OpProgress(label, sample.fraction, sample.downloadedBytes, sample.totalBytes) },
            )
            if (ok) logger.info(LOG_TAG, "done") else logger.warn(LOG_TAG, "failed (see log above)")
        } catch (e: Exception) {
            logger.error(LOG_TAG, e.message ?: "unexpected failure", e)
        } finally {
            progress.value = null
            onDone()
        }
    }
}

@Composable
fun DashboardScreen(
    rustup: RustupController,
    lspManager: LspManager,
    onOpenLsp: () -> Unit,
    onOpenVersions: () -> Unit,
    onOpenTerminal: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val logger: Logger = rememberLogger()
    var state by remember { mutableStateOf<RustupState>(RustupState.Checking) }

    suspend fun refresh() {
        state = rustup.loadState()
    }

    LaunchedEffect(Unit) { refresh() }

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
                    scope = scope,
                    logger = logger,
                    rustup = rustup,
                    onRefresh = { refresh() },
                )
                is RustupState.Error -> ErrorBody(current.message) { scope.launch { refresh() } }
                is RustupState.Ready -> ReadyBody(
                    state = current,
                    rustup = rustup,
                    lspManager = lspManager,
                    scope = scope,
                    logger = logger,
                    onOpenLsp = onOpenLsp,
                    onOpenVersions = onOpenVersions,
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
    scope: CoroutineScope,
    logger: Logger,
    rustup: RustupController,
    onRefresh: suspend () -> Unit,
) {
    val installOp = remember { mutableStateOf<OpProgress?>(null) }
    val resetOp = remember { mutableStateOf<OpProgress?>(null) }

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
            Button(
                onClick = { runOp(scope, logger, installOp, "rustup-init", onDone = onRefresh) { l, p -> rustup.bootstrapInstall(l, p) } },
                enabled = installOp.value == null && resetOp.value == null,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (installOp.value != null) "Installing..." else "Install rustup")
            }
            OutlinedButton(
                onClick = { runOp(scope, logger, resetOp, "reset rustup state", onDone = onRefresh) { l, _ -> rustup.resetInstall(l) } },
                enabled = installOp.value == null && resetOp.value == null,
            ) {
                Icon(Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset & retry")
            }
        }
        if (installOp.value != null) {
            OpProgressBar(
                progress = installOp.value,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (resetOp.value != null) {
            OpProgressBar(
                progress = resetOp.value,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Text(
            "If a previous attempt failed partway, \"Reset & retry\" clears the leftover rustup state "
                + "first \u2014 a stale settings file is a common cause of install failures.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ReadyBody(
    state: RustupState.Ready,
    rustup: RustupController,
    lspManager: LspManager,
    scope: CoroutineScope,
    logger: Logger,
    onOpenLsp: () -> Unit,
    onOpenVersions: () -> Unit,
) {
    var toolchains by remember { mutableStateOf(state.toolchains) }
    var components by remember { mutableStateOf(state.components) }
    var targets by remember { mutableStateOf(state.activeTargets) }
    var lspSource by remember { mutableStateOf(LspSource.Rustup) }
    val updateAllOp = remember { mutableStateOf<OpProgress?>(null) }

    suspend fun reloadToolchains() { toolchains = rustup.listToolchains() }
    suspend fun reloadComponents() { components = rustup.componentState() }
    suspend fun reloadTargets() { targets = rustup.activeTargets() }

    LaunchedEffect(Unit) { lspManager.refresh() }

    // Fetching lives here, not inside the card: the card sits in a LazyColumn item that is
    // disposed when scrolled off-screen, so an effect inside it would re-fetch (and blank the
    // list) every time the user scrolls back up.
    LaunchedEffect(lspSource) {
        if (lspSource == LspSource.Versions) lspManager.ensureReleases()
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { SectionLabel("Language server") }
        item {
            LspSourceCard(
                lspManager = lspManager,
                selected = lspSource,
                onSelect = { lspSource = it },
                onOpenLsp = onOpenLsp,
                onShowAll = onOpenVersions,
            )
        }

        item { SectionLabel("Toolchains") }
        if (toolchains.isEmpty()) {
            item {
                SettingsCard(
                    icon = Info,
                    title = "No toolchains installed",
                    description = "Install one below — try stable, beta or nightly.",
                )
            }
        } else {
            items(toolchains, key = { it.name }) { toolchain ->
                ToolchainRow(
                    toolchain = toolchain,
                    rustup = rustup,
                    scope = scope,
                    logger = logger,
                    onDone = { reloadToolchains() },
                )
            }
        }
        item(key = "install-toolchain") {
            InstallToolchainRow(
                rustup = rustup,
                scope = scope,
                logger = logger,
                onDone = { reloadToolchains() },
            )
        }

        item { SectionLabel("Components") }
        item {
            SettingsCard {
                Column {
                    ComponentRow("clippy", components.clippy, rustup, scope, logger, { reloadComponents() })
                    ComponentRow("rustfmt", components.rustfmt, rustup, scope, logger, { reloadComponents() })
                    ComponentRow("rust-src", components.rustSrc, rustup, scope, logger, { reloadComponents() })
                }
            }
        }

        item { SectionLabel("Targets") }
        if (targets.isEmpty()) {
            item {
                SettingsCard(
                    icon = Info,
                    title = "No extra targets",
                    description = "Your toolchain compiles for the host platform only.",
                )
            }
        } else {
            items(targets, key = { it }) { target ->
                TargetRow(
                    target = target,
                    rustup = rustup,
                    scope = scope,
                    logger = logger,
                    onDone = { reloadTargets() },
                )
            }
        }
        item(key = "add-target") {
            AddTargetRow(
                rustup = rustup,
                scope = scope,
                logger = logger,
                onDone = { reloadTargets() },
            )
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                OutlinedButton(
                    onClick = { runOp(scope, logger, updateAllOp, "rustup update", onDone = { reloadToolchains() }) { l, p -> rustup.updateAll(l, p) } },
                    enabled = updateAllOp.value == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Update all toolchains")
                }
                if (updateAllOp.value != null) {
                    OpProgressBar(
                        progress = updateAllOp.value,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LspSourceCard(
    lspManager: LspManager,
    selected: LspSource,
    onSelect: (LspSource) -> Unit,
    onOpenLsp: () -> Unit,
    onShowAll: () -> Unit,
) {
    SettingsCard(
        icon = Server,
        title = "Language server",
        description = statusLine(lspManager.lsp),
        trailing = { TextButton(onClick = onOpenLsp) { Text("Manage") } },
        content = {
            Column {
                SegmentedChoice(
                    options = listOf("rustup", "versions"),
                    selected = selected.name.lowercase(),
                    onSelect = { onSelect(LspSource.valueOf(it.replaceFirstChar { c -> c.uppercase() })) },
                )
                when (selected) {
                    LspSource.Rustup -> RustupLspTab(lspManager = lspManager)
                    LspSource.Versions -> VersionsLspTab(lspManager = lspManager, onShowAll = onShowAll)
                }
            }
        },
    )
}

@Composable
private fun RustupLspTab(lspManager: LspManager) {
    val scope = rememberCoroutineScope()
    val installed = lspManager.lsp.installedViaRustup
    val rustupActive = lspManager.lsp.rustupActive
    val busy = lspManager.rustupBusy()
    val progress = lspManager.tracker.state("lsp:rustup:install").value

    Column(modifier = Modifier.padding(top = 12.dp)) {
        if (installed) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (rustupActive) {
                    Icon(
                        Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "In use",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    FilledTonalButton(
                        onClick = { scope.launch { lspManager.useViaRustup { } } },
                        enabled = !busy,
                    ) {
                        Icon(Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Use")
                    }
                    Spacer(Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = { scope.launch { lspManager.removeViaRustup { } } },
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Remove (rustup)")
                }
            }
        } else {
            Button(onClick = { scope.launch { lspManager.installViaRustup { } } }, enabled = !busy) {
                Icon(Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Install via rustup")
            }
        }
        if (busy) {
            OpProgressBar(
                progress = progress,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun VersionsLspTab(lspManager: LspManager, onShowAll: () -> Unit) {
    val scope = rememberCoroutineScope()
    val releases = lspManager.releases
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Pick a version from rust-analyzer's releases — latest stable and nightly are pinned on top",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { lspManager.fetchReleases() } }) { Icon(Refresh, contentDescription = "Refresh") }
        }
        when {
            releases == null && !lspManager.fetchError -> Row(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineSpinner(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Loading releases...")
            }
            lspManager.fetchError || releases == null -> Text(
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
            else -> {
                val stable = releases.firstOrNull { !it.isNightly }
                val nightly = releases.firstOrNull { it.isNightly }
                val others = releases.filter { it != stable && it != nightly }
                // Resolved once per lsp-state change and looked up by tag below, instead of every
                // row scanning the full versions list on every recomposition.
                val versionsByTag = remember(lspManager.lsp.versions) {
                    lspManager.lsp.versions.associateBy { it.tag }
                }

                if (stable != null) {
                    ReleaseVersionRow(
                        lspManager = lspManager,
                        release = stable,
                        managed = versionsByTag[stable.tag],
                        title = "Latest stable",
                        subtitle = stable.tag,
                        icon = Star,
                    )
                }
                if (nightly != null) {
                    ReleaseVersionRow(
                        lspManager = lspManager,
                        release = nightly,
                        managed = versionsByTag[nightly.tag],
                        title = "nightly (rolling)",
                        subtitle = nightly.tag,
                        icon = Moon,
                    )
                }
                if (stable != null || nightly != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                }

                others.take(8).forEach { release ->
                    ReleaseVersionRow(
                        lspManager = lspManager,
                        release = release,
                        managed = versionsByTag[release.tag],
                    )
                }
                if (others.size > 8) {
                    OutlinedButton(
                        onClick = onShowAll,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text("Show all ${others.size} releases")
                    }
                }
            }
        }
    }
}

private fun statusLine(lsp: LspState): String = when {
    lsp.activeVersion != null -> "Using rust-analyzer ${lsp.activeVersion} (GitHub release)"
    lsp.rustupActive -> "Using rustup component rust-analyzer"
    lsp.installedViaRustup -> "Installed via rustup — activate it with Use"
    else -> "Not installed yet"
}

@Composable
private fun ToolchainRow(
    toolchain: Toolchain,
    rustup: RustupController,
    scope: CoroutineScope,
    logger: Logger,
    onDone: suspend () -> Unit,
) {
    val op = remember { mutableStateOf<OpProgress?>(null) }
    val enabled = op.value == null

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
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (toolchain.updateAvailable != null) {
                        FilledTonalButton(
                            onClick = { runOp(scope, logger, op, "rustup update ${toolchain.name}", onDone) { l, p -> rustup.updateToolchain(toolchain.name, l, p) } },
                            enabled = enabled,
                        ) {
                            Icon(Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Update")
                        }
                    }
                    if (!toolchain.isDefault) {
                        OutlinedButton(
                            onClick = { runOp(scope, logger, op, "rustup default ${toolchain.name}", onDone) { l, _ -> rustup.setDefaultToolchain(toolchain.name, l) } },
                            enabled = enabled,
                        ) {
                            Icon(Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Set default")
                        }
                    }
                    OutlinedButton(
                        onClick = { runOp(scope, logger, op, "rustup toolchain uninstall ${toolchain.name}", onDone) { l, _ -> rustup.uninstallToolchain(toolchain.name, l) } },
                        enabled = enabled,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Remove")
                    }
                }
                if (op.value != null) {
                    OpProgressBar(
                        progress = op.value,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun InstallToolchainRow(
    rustup: RustupController,
    scope: CoroutineScope,
    logger: Logger,
    onDone: suspend () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val op = remember { mutableStateOf<OpProgress?>(null) }
    SettingsCard(
        title = "Install a toolchain",
        content = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "stable, beta, nightly, 1.80.0...",
                        enabled = op.value == null,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val target = name.trim()
                            name = ""
                            runOp(scope, logger, op, "rustup toolchain install $target", onDone) { l, p -> rustup.installToolchain(target, l, p) }
                        },
                        enabled = op.value == null && name.isNotBlank(),
                    ) {
                        Icon(Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Install")
                    }
                }
                if (op.value != null) {
                    OpProgressBar(
                        progress = op.value,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun ComponentRow(
    id: String,
    installed: Boolean,
    rustup: RustupController,
    scope: CoroutineScope,
    logger: Logger,
    onDone: suspend () -> Unit,
) {
    val op = remember { mutableStateOf<OpProgress?>(null) }
    val enabled = op.value == null

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    onClick = { runOp(scope, logger, op, "rustup component remove $id", onDone) { l, _ -> rustup.removeComponent(id, l) } },
                    enabled = enabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remove")
                }
            } else {
                Button(
                    onClick = { runOp(scope, logger, op, "rustup component add $id", onDone) { l, p -> rustup.addComponent(id, l, p) } },
                    enabled = enabled,
                ) {
                    Icon(Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Install")
                }
            }
        }
        if (op.value != null) {
            OpProgressBar(
                progress = op.value,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TargetRow(
    target: String,
    rustup: RustupController,
    scope: CoroutineScope,
    logger: Logger,
    onDone: suspend () -> Unit,
) {
    val op = remember { mutableStateOf<OpProgress?>(null) }
    SettingsCard(
        title = target,
        trailing = {
            OutlinedButton(
                onClick = { runOp(scope, logger, op, "rustup target remove $target", onDone) { l, _ -> rustup.removeTarget(target, l) } },
                enabled = op.value == null,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Remove")
            }
        },
        content = {
            if (op.value != null) {
                OpProgressBar(
                    progress = op.value,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
    )
}

@Composable
private fun AddTargetRow(
    rustup: RustupController,
    scope: CoroutineScope,
    logger: Logger,
    onDone: suspend () -> Unit,
) {
    var target by rememberSaveable { mutableStateOf("") }
    val op = remember { mutableStateOf<OpProgress?>(null) }
    SettingsCard(
        title = "Add a target",
        content = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTextField(
                        value = target,
                        onValueChange = { target = it },
                        placeholder = "e.g. wasm32-unknown-unknown",
                        enabled = op.value == null,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val name = target.trim()
                            target = ""
                            runOp(scope, logger, op, "rustup target add $name", onDone) { l, p -> rustup.addTarget(name, l, p) }
                        },
                        enabled = op.value == null && target.isNotBlank(),
                    ) {
                        Icon(Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add")
                    }
                }
                if (op.value != null) {
                    OpProgressBar(
                        progress = op.value,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
    )
}

/** A single release row: state line (In use / Installed / Not installed), a live progress bar
 *  while installing, and Install/Use/Remove actions. [title] overrides the default label
 *  (used to pin "Latest stable" and "nightly" on top of the versions list). [managed] is
 *  resolved once by the caller (a tag -> Toolchain map built from [LspManager.lsp]) instead of
 *  every row scanning the full versions list on every recomposition. [icon] marks the pinned
 *  latest-stable (star) and nightly (moon) rows; plain releases pass null. */
@Composable
fun ReleaseVersionRow(
    lspManager: LspManager,
    release: GithubRelease,
    managed: ManagedLspVersion?,
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    horizontalPadding: Dp = 16.dp,
) {
    val scope = rememberCoroutineScope()
    val logger: Logger = rememberLogger()
    val busy = lspManager.isBusy(release.tag)
    val progress = lspManager.installProgress(release.tag)

    val displayTitle = title ?: if (release.isNightly) "nightly (rolling)" else release.tag
    val stateText = when {
        managed?.isActive == true -> "In use"
        managed != null -> "Installed"
        else -> "Not installed"
    }
    val description = buildString {
        if (subtitle != null) {
            append(subtitle).append(" \u00b7 ")
        }
        append(stateText)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(displayTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
                    onClick = { scope.launch { runManaged(lspManager, logger, release.tag) { tag, onLine -> lspManager.remove(tag, onLine) } } },
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
                managed != null -> {
                    FilledTonalButton(
                        onClick = { scope.launch { runManaged(lspManager, logger, release.tag) { tag, onLine -> lspManager.use(tag, onLine) } } },
                        enabled = !busy,
                    ) { Text("Use") }
                    OutlinedButton(
                        onClick = { scope.launch { runManaged(lspManager, logger, release.tag) { tag, onLine -> lspManager.remove(tag, onLine) } } },
                        enabled = !busy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Remove") }
                }
                else -> Button(
                    onClick = { scope.launch { runManaged(lspManager, logger, release.tag) { tag, onLine -> lspManager.install(tag, onLine) } } },
                    enabled = !busy,
                ) {
                    Icon(Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Install")
                }
            }
        }
        if (busy) {
            OpProgressBar(
                progress = progress,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

private suspend fun runManaged(
    lspManager: LspManager,
    logger: Logger,
    tag: String,
    action: suspend (tag: String, onLine: (String) -> Unit) -> Boolean,
) {
    val ok = action(tag) { logger.info(LOG_TAG, it) }
    if (!ok) logger.info(LOG_TAG, "operation failed (see log above)")
}