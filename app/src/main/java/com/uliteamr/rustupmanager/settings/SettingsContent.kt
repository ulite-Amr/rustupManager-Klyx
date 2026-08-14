package com.uliteamr.rustupmanager.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.klyx.api.plugin.PluginSettings
import com.uliteamr.rustupmanager.icons.Clock
import com.uliteamr.rustupmanager.icons.Layers
import com.uliteamr.rustupmanager.icons.Refresh
import com.uliteamr.rustupmanager.ui.AppSwitch
import com.uliteamr.rustupmanager.ui.SegmentedChoice
import com.uliteamr.rustupmanager.ui.SettingsCard
import kotlinx.coroutines.launch

private val TOOLCHAINS = listOf("stable", "beta", "nightly")
private val INTERVALS = listOf(1 to "Daily", 7 to "Weekly", 30 to "Monthly")

@Composable
fun PluginSettings.RustupSettingsContent() {
    val scope = rememberCoroutineScope()

    var autoCheck by remember { mutableStateOf(getBoolean(SettingsKeys.autoCheckUpdates, true)) }
    var intervalDays by remember { mutableStateOf(getInt(SettingsKeys.checkIntervalDays, 7)) }
    var defaultChannel by remember { mutableStateOf(getString(SettingsKeys.defaultChannel, "stable") ?: "stable") }

    Column {
        SettingsCard(
            icon = Layers,
            title = "Toolchain channel",
            description = "Used when installing a new toolchain from the dashboard",
            content = {
                SegmentedChoice(
                    options = TOOLCHAINS,
                    selected = defaultChannel,
                    onSelect = { channel ->
                        defaultChannel = channel
                        scope.launch { putString(SettingsKeys.defaultChannel, channel) }
                    },
                )
            },
        )

        SettingsCard(
            icon = Refresh,
            title = "Check for updates automatically",
            description = "Runs \"rustup update\" in the background on the interval below",
            trailing = {
                AppSwitch(
                    checked = autoCheck,
                    onCheckedChange = { enabled ->
                        autoCheck = enabled
                        scope.launch { putBoolean(SettingsKeys.autoCheckUpdates, enabled) }
                    },
                )
            },
        )

        if (autoCheck) {
            SettingsCard(
                icon = Clock,
                title = "Check interval",
                content = {
                    SegmentedChoice(
                        options = INTERVALS.map { it.second },
                        selected = INTERVALS.first { it.first == intervalDays }.second,
                        onSelect = { label ->
                            val days = INTERVALS.first { it.second == label }.first
                            intervalDays = days
                            scope.launch { putInt(SettingsKeys.checkIntervalDays, days) }
                        },
                    )
                },
            )
        }
    }
}
