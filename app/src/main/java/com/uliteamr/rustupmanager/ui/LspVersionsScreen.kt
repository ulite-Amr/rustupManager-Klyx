package com.uliteamr.rustupmanager.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uliteamr.rustupmanager.icons.Refresh
import kotlinx.coroutines.launch

/**
 * Full release list opened from the dashboard's "Show all" button. Rows come straight
 * from the shared [LspManager] state, so this page shows the already-fetched releases
 * instantly and stays in sync with the dashboard.
 */
@Composable
fun LspVersionsScreen(lspManager: LspManager, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        lspManager.ensureReleases()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "All versions", onBack = onBack)

        val releases = lspManager.releases
        when {
            releases == null && !lspManager.fetchError -> Row(
                modifier = Modifier.padding(20.dp),
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
                modifier = Modifier.padding(20.dp),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${releases.size} releases",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { scope.launch { lspManager.fetchReleases() } }) {
                            Icon(Refresh, contentDescription = "Refresh")
                        }
                    }
                }
                items(releases, key = { it.tag }) { release ->
                    ReleaseVersionRow(lspManager = lspManager, release = release)
                }
            }
        }
    }
}