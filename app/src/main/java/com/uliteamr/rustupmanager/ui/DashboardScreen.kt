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
import com.uliteamr.rustupmanager.rustup.LspSource
import com.uliteamr.rustupmanager.rustup.LspState
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
                    onInstallLsp = { source ->
                        val label = if (source == LspSource.Rustup) "rustup component add rust-analyzer" else "apt-get install rust-analyzer"
                        runAction(label) { onLine ->
                            if (source == LspSource.Rustup) rustup.installLspViaRustup(onLine) else rustup.installLspViaApt(onLine)
                        }
                    },
                    onRemoveLsp = { source ->
                        val label = if (source == LspSource.Rustup) "rustup component remove rust-analyzer" else "apt-get remove rust-analyzer"
                        runAction(label) { onLine ->
                            if (source == LspSource.Rustup) rustup.removeLspViaRustup(onLine) else rustup.removeLspViaApt(onLine)
                        }
                    },
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
    onInstallLsp: (LspSource) -> Unit,
    onRemoveLsp: (LspSource) -> Unit,
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
                onInstall = { onInstallLsp(lspSource) },
                onRemove = { onRemoveLsp(lspSource) },
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
    onInstall: () -> Unit,
    onRemove: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val installedForSelected = when (selected) {
        LspSource.Rustup -> lsp.installedViaRustup
        LspSource.Apt -> lsp.installedViaApt
    }
    SettingsCard(
        icon = Server,
        title = "Language server",
        description = statusLine(lsp),
        trailing = { TextButton(onClick = onOpenLogs) { Text("Logs") } },
        content = {
            Column {
                SegmentedChoice(
                    options = listOf("rustup", "apt"),
                    selected = selected.name.lowercase(),
                    enabled = !busy,
                    onSelect = { onSelect(if (it == "apt") LspSource.Apt else LspSource.Rustup) },
                )
                Row(modifier = Modifier.padding(top = 12.dp)) {
                    if (installedForSelected) {
                        OutlinedButton(
                            onClick = onRemove,
                            enabled = !busy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Remove (${selected.name.lowercase()})")
                        }
                    } else {
                        Button(onClick = onInstall, enabled = !busy) {
                            Icon(Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Install via ${selected.name.lowercase()}")
                        }
                    }
                }
            }
        },
    )
}

private fun statusLine(lsp: LspState): String = when {
    lsp.installedViaRustup && lsp.installedViaApt -> "Installed via both rustup and apt"
    lsp.installedViaRustup -> "Installed via rustup component"
    lsp.installedViaApt -> "Installed via apt"
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
