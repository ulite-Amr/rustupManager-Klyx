package com.uliteamr.rustupmanager.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.klyx.api.plugin.PluginSettings
import kotlinx.coroutines.launch

private val TOOLCHAINS = listOf("stable", "beta", "nightly")
private val INTERVALS = listOf(1 to "Daily", 7 to "Weekly", 30 to "Monthly")

@Composable
fun PluginSettings.RustupSettingsContent() {
    val scope = rememberCoroutineScope()

    var autoCheck by remember { mutableStateOf(getBoolean("autoCheckUpdates", true)) }
    var intervalDays by remember { mutableStateOf(getInt("checkIntervalDays", 7)) }
    var defaultChannel by remember { mutableStateOf(getString("defaultChannel", "stable") ?: "stable") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text("Toolchain channel", style = MaterialTheme.typography.titleSmall)
            Text(
                "Used when installing a new toolchain from the dashboard",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TOOLCHAINS.forEach { channel ->
                    FilterChip(
                        selected = defaultChannel == channel,
                        onClick = {
                            defaultChannel = channel
                            scope.launch { putString("defaultChannel", channel) }
                        },
                        label = { Text(channel) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Check for updates automatically", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Runs \"rustup update\" in the background on the interval below",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = autoCheck,
                onCheckedChange = { enabled ->
                    autoCheck = enabled
                    scope.launch { putBoolean("autoCheckUpdates", enabled) }
                },
            )
        }

        if (autoCheck) {
            Column {
                Text("Check interval", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    INTERVALS.forEach { (days, label) ->
                        FilterChip(
                            selected = intervalDays == days,
                            onClick = {
                                intervalDays = days
                                scope.launch { putInt("checkIntervalDays", days) }
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
    }
}
