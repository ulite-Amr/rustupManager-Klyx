package com.uliteamr.rustupmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.uliteamr.rustupmanager.icons.Server
import com.uliteamr.rustupmanager.rustup.RustupController
import com.uliteamr.rustupmanager.rustup.RustupState
import com.uliteamr.rustupmanager.rustup.Toolchain
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    rustup: RustupController,
    onOpenLsp: () -> Unit,
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
            val ok = action { line -> logLines.add(line) }
            logLines.add(if (ok) "done" else "failed (see log above)")
            busy = false
            refresh()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Rust Toolchain", onBack = onBack)

        when (val current = state) {
            RustupState.Checking -> CheckingBody()
            RustupState.NotInstalled -> NotInstalledBody(
                busy = busy,
                logLines = logLines,
                onInstall = { runAction("rustup-init") { onLine -> rustup.bootstrapInstall(onLine) } },
                onReset = { runAction("reset rustup state") { onLine -> rustup.resetInstall(onLine) } },
            )
            is RustupState.Error -> ErrorBody(current.message) { scope.launch { refresh() } }
            RustupState.Installing -> CheckingBody()
            is RustupState.Ready -> ReadyBody(
                state = current,
                busy = busy,
                logLines = logLines,
                onOpenLsp = onOpenLsp,
                onSetDefault = { name -> runAction("rustup default $name") { onLine -> rustup.setDefaultToolchain(name, onLine) } },
                onUninstallToolchain = { name -> runAction("rustup toolchain uninstall $name") { onLine -> rustup.uninstallToolchain(name, onLine) } },
                onInstallToolchain = { name -> runAction("rustup toolchain install $name") { onLine -> rustup.installToolchain(name, onLine) } },
                onToggleComponent = { component, enable ->
                    val label = if (enable) "rustup component add $component" else "rustup component remove $component"
                    runAction(label) { onLine ->
                        if (enable) rustup.addComponent(component, onLine) else rustup.removeComponent(component, onLine)
                    }
                },
                onRemoveTarget = { target -> runAction("rustup target remove $target") { onLine -> rustup.removeTarget(target, onLine) } },
                onAddTarget = { target -> runAction("rustup target add $target") { onLine -> rustup.addTarget(target, onLine) } },
                onUpdateAll = { runAction("rustup update") { onLine -> rustup.updateAll(onLine) } },
            )
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
        InlineSpinner()
        Text("Checking rustup...", modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ErrorBody(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(message, modifier = Modifier.padding(top = 4.dp))
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
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
            title = "rustup is not installed",
            description = "This installs rustup and a default stable toolchain inside Klyx's built-in Linux environment.",
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onInstall, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(if (busy) "Working..." else "Install rustup")
            }
            OutlinedButton(onClick = onReset, enabled = !busy) {
                Text("Reset & retry")
            }
        }
        Text(
            "If a previous attempt failed partway, \"Reset & retry\" clears the leftover rustup state "
                + "first \u2014 a stale settings file is a common cause of install failures.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, top = 6.dp, bottom = 4.dp),
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
    onToggleComponent: (String, Boolean) -> Unit,
    onRemoveTarget: (String) -> Unit,
    onAddTarget: (String) -> Unit,
    onUpdateAll: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsCard(
                icon = Server,
                title = "Language server",
                description = "Status, live logs, and controls for rust-analyzer",
                trailing = { TextButton(onClick = onOpenLsp) { Text("Open") } },
            )
        }

        item { SectionLabel("Toolchains") }
        items(state.toolchains) { toolchain ->
            ToolchainRow(
                toolchain = toolchain,
                enabled = !busy,
                onSetDefault = { onSetDefault(toolchain.name) },
                onUninstall = { onUninstallToolchain(toolchain.name) },
            )
        }
        item { InstallToolchainRow(enabled = !busy, onInstall = onInstallToolchain) }

        item { SectionLabel("Components") }
        item {
            SettingsCard {
                Column {
                    ComponentRow("rust-analyzer", state.components.rustAnalyzer, !busy, onToggleComponent)
                    ComponentRow("clippy", state.components.clippy, !busy, onToggleComponent)
                    ComponentRow("rustfmt", state.components.rustfmt, !busy, onToggleComponent)
                    ComponentRow("rust-src", state.components.rustSrc, !busy, onToggleComponent)
                }
            }
        }

        item { SectionLabel("Targets") }
        items(state.activeTargets) { target ->
            TargetRow(target = target, enabled = !busy, onRemove = { onRemoveTarget(target) })
        }
        item { AddTargetRow(enabled = !busy, onAdd = onAddTarget) }

        item {
            OutlinedButton(
                onClick = onUpdateAll,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
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
private fun ToolchainRow(
    toolchain: Toolchain,
    enabled: Boolean,
    onSetDefault: () -> Unit,
    onUninstall: () -> Unit,
) {
    SettingsCard(
        title = toolchain.name,
        description = if (toolchain.isDefault) "default" else null,
        content = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!toolchain.isDefault) {
                    OutlinedButton(onClick = onSetDefault, enabled = enabled) { Text("Set default") }
                }
                OutlinedButton(
                    onClick = onUninstall,
                    enabled = enabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("stable, beta, nightly, 1.80.0...") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { if (name.isNotBlank()) { onInstall(name.trim()); name = "" } },
                    enabled = enabled && name.isNotBlank(),
                ) { Text("Install") }
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
        Text(id, style = MaterialTheme.typography.bodyMedium)
        if (installed) {
            OutlinedButton(
                onClick = { onToggle(id, false) },
                enabled = enabled,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Remove") }
        } else {
            Button(onClick = { onToggle(id, true) }, enabled = enabled) { Text("Install") }
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
            ) { Text("Remove") }
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
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("e.g. wasm32-unknown-unknown") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { if (target.isNotBlank()) { onAdd(target.trim()); target = "" } },
                    enabled = enabled && target.isNotBlank(),
                ) { Text("Add") }
            }
        },
    )
}
