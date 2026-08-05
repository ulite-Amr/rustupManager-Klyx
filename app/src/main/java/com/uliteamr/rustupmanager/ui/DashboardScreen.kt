package com.uliteamr.rustupmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.uliteamr.rustupmanager.icons.ArrowBack
import com.uliteamr.rustupmanager.icons.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.uliteamr.rustupmanager.rustup.ComponentState
import com.uliteamr.rustupmanager.rustup.RustupController
import com.uliteamr.rustupmanager.rustup.RustupState
import com.uliteamr.rustupmanager.rustup.Toolchain
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    rustup: RustupController,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<RustupState>(RustupState.Checking) }
    var busy by remember { mutableStateOf(false) }
    var installLog by remember { mutableStateOf("") }

    suspend fun refresh() {
        state = RustupState.Checking
        state = rustup.loadState()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rust Toolchain") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { refresh() } }) {
                        Icon(Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (busy) {
                LinearBusyIndicator()
            }

            when (val current = state) {
                RustupState.Checking -> LoadingBody()
                RustupState.NotInstalled -> NotInstalledBody(
                    installing = busy,
                    log = installLog,
                    onInstall = {
                        scope.launch {
                            busy = true
                            installLog = ""
                            rustup.bootstrapInstall().collect { event ->
                                installLog += eventText(event)
                            }
                            busy = false
                            refresh()
                        }
                    },
                )
                is RustupState.Error -> ErrorBody(current.message) {
                    scope.launch { refresh() }
                }
                RustupState.Installing -> LoadingBody()
                is RustupState.Ready -> ReadyBody(
                    state = current,
                    busy = busy,
                    onSetBusy = { busy = it },
                    rustup = rustup,
                    onRefresh = { scope.launch { refresh() } },
                )
            }
        }
    }
}

private fun eventText(event: com.klyx.api.system.ProcessEvent): String = when (event) {
    is com.klyx.api.system.ProcessEvent.Stdout -> event.text
    is com.klyx.api.system.ProcessEvent.Stderr -> event.text
    is com.klyx.api.system.ProcessEvent.ExitCode -> "\n[exit ${event.code}]\n"
}

@Composable
private fun LinearBusyIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun LoadingBody() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text("Checking rustup...", modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ErrorBody(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "Something went wrong",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(message, modifier = Modifier.padding(top = 4.dp))
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
            Text("Retry")
        }
    }
}

@Composable
private fun NotInstalledBody(
    installing: Boolean,
    log: String,
    onInstall: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("rustup is not installed", style = MaterialTheme.typography.titleMedium)
        Text(
            "This installs rustup and a default stable toolchain inside Klyx's built-in Linux environment.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Button(onClick = onInstall, enabled = !installing) {
            Text(if (installing) "Installing..." else "Install rustup")
        }
        if (log.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Text(
                    text = log,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun ReadyBody(
    state: RustupState.Ready,
    busy: Boolean,
    onSetBusy: (Boolean) -> Unit,
    rustup: RustupController,
    onRefresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    fun run(action: suspend () -> Unit) {
        scope.launch {
            onSetBusy(true)
            action()
            onSetBusy(false)
            onRefresh()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("Toolchains") }
        items(state.toolchains) { toolchain ->
            ToolchainRow(
                toolchain = toolchain,
                enabled = !busy,
                onSetDefault = { run { rustup.setDefaultToolchain(toolchain.name) } },
                onUninstall = { run { rustup.uninstallToolchain(toolchain.name) } },
            )
        }
        item {
            InstallToolchainRow(enabled = !busy) { name ->
                run { rustup.installToolchain(name) }
            }
        }

        item { SectionHeader("Components") }
        item {
            ComponentsCard(
                components = state.components,
                enabled = !busy,
                onToggle = { component, enable ->
                    run {
                        if (enable) rustup.addComponent(component) else rustup.removeComponent(component)
                    }
                },
            )
        }

        item { SectionHeader("Targets") }
        items(state.activeTargets) { target ->
            TargetRow(
                target = target,
                enabled = !busy,
                onRemove = { run { rustup.removeTarget(target) } },
            )
        }
        item {
            AddTargetRow(enabled = !busy) { target ->
                run { rustup.addTarget(target) }
            }
        }

        item {
            OutlinedButton(
                onClick = { run { rustup.updateAll() } },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Text("Update all toolchains")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ToolchainRow(
    toolchain: Toolchain,
    enabled: Boolean,
    onSetDefault: () -> Unit,
    onUninstall: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column {
                Text(toolchain.name, style = MaterialTheme.typography.bodyMedium)
                if (toolchain.isDefault) {
                    Text(
                        "default",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row {
                if (!toolchain.isDefault) {
                    TextButton(onClick = onSetDefault, enabled = enabled) { Text("Set default") }
                }
                TextButton(onClick = onUninstall, enabled = enabled) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun InstallToolchainRow(enabled: Boolean, onInstall: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
        ) {
            Text("Install")
        }
    }
}

@Composable
private fun ComponentsCard(
    components: ComponentState,
    enabled: Boolean,
    onToggle: (component: String, enable: Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            ComponentSwitch("rust-analyzer", components.rustAnalyzer, enabled, onToggle)
            ComponentSwitch("clippy", components.clippy, enabled, onToggle)
            ComponentSwitch("rustfmt", components.rustfmt, enabled, onToggle)
            ComponentSwitch("rust-src", components.rustSrc, enabled, onToggle)
        }
    }
}

@Composable
private fun ComponentSwitch(
    id: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (String, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(id, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = { onToggle(id, it) },
        )
    }
}

@Composable
private fun TargetRow(target: String, enabled: Boolean, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(target, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRemove, enabled = enabled) { Text("Remove") }
        }
    }
}

@Composable
private fun AddTargetRow(enabled: Boolean, onAdd: (String) -> Unit) {
    var target by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
        ) {
            Text("Add")
        }
    }
}
