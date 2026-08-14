package com.uliteamr.rustupmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.klyx.api.service.Logger
import com.klyx.api.service.error
import com.klyx.api.service.info
import com.klyx.api.service.rememberLogger
import com.klyx.api.service.warn
import com.uliteamr.rustupmanager.icons.Check
import com.uliteamr.rustupmanager.icons.Delete
import com.uliteamr.rustupmanager.icons.Download
import com.uliteamr.rustupmanager.icons.Refresh
import com.uliteamr.rustupmanager.rustup.GithubRelease
import kotlinx.coroutines.launch

private const val LOG_TAG = "Rustup"

/** Full rust-analyzer release browser, opened from the dashboard's "Show all" action. */
@Composable
fun LspVersionsScreen(lspManager: LspManager, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val logger: Logger = rememberLogger()
    val releases = lspManager.releases
    val fetching = releases == null && !lspManager.fetchError
    val fetchError = lspManager.fetchError

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "rust-analyzer versions", onBack = onBack)

        LazyColumn(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "All releases — install any version and switch with Use.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        scope.launch {
                            lspManager.fetchReleases()
                            lspManager.refresh()
                        }
                    }) { Icon(Refresh, contentDescription = "Refresh") }
                }
            }
            when {
                fetching -> item {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InlineSpinner(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading releases...")
                    }
                }
                fetchError || releases.isNullOrEmpty() -> item {
                    Text(
                        "Couldn't reach GitHub — check your connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                else -> items(releases!!) { release ->
                    ReleaseVersionRow(lspManager = lspManager, release = release)
                }
            }
        }
    }
}

/** A single release row: state line (In use / Installed / Not installed), a live progress bar
 *  while installing, and Install/Use/Remove actions. Shared by the dashboard and this screen. */
@Composable
fun ReleaseVersionRow(lspManager: LspManager, release: GithubRelease) {
    val scope = rememberCoroutineScope()
    val logger: Logger = rememberLogger()
    val managed = lspManager.lsp.versions.firstOrNull { it.tag == release.tag }
    val busy = lspManager.isBusy(release.tag)
    val progress = lspManager.installProgress(release.tag)

    val title = if (release.isNightly) "nightly (rolling)" else release.tag
    val description = when {
        managed?.isActive == true -> "In use"
        managed != null -> "Installed"
        else -> "Not installed"
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    onClick = { scope.launch { log(lspManager.remove(release.tag)) { logger.info(LOG_TAG, it) } } },
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
                managed != null -> {
                    FilledTonalButton(
                        onClick = { scope.launch { log(lspManager.use(release.tag)) { logger.info(LOG_TAG, it) } } },
                        enabled = !busy,
                    ) { Text("Use") }
                    OutlinedButton(
                        onClick = { scope.launch { log(lspManager.remove(release.tag)) { logger.info(LOG_TAG, it) } } },
                        enabled = !busy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Remove") }
                }
                else -> Button(
                    onClick = { scope.launch { log(lspManager.install(release.tag)) { logger.info(LOG_TAG, it) } } },
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

private suspend fun log(result: Boolean, onLine: (String) -> Unit) {
    if (!result) onLine("operation failed (see log above)")
}
