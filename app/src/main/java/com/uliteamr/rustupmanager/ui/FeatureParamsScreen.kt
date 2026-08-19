package com.uliteamr.rustupmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.klyx.api.plugin.PluginSettings
import com.uliteamr.rustupmanager.icons.Add
import com.uliteamr.rustupmanager.icons.Check
import com.uliteamr.rustupmanager.icons.Delete
import com.uliteamr.rustupmanager.icons.Info
import com.uliteamr.rustupmanager.icons.Layers
import com.uliteamr.rustupmanager.icons.Warning
import com.uliteamr.rustupmanager.settings.SettingsKeys
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private data class CustomFeature(val name: String, val isBoolean: Boolean, val value: String)

/**
 * Feature Parameters and Initialize: user-facing toggles for the parameters this
 * plugin sends to rust-analyzer through the SDK's `initializationOptions()` hook.
 * Every feature is enabled by default and can be switched off individually.
 * Custom features are merged after the toggles, and the raw JSON object — when
 * present — is used verbatim and wins over everything else.
 */
@Composable
fun FeatureParamsScreen(settings: PluginSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var bindingModeHints by remember { mutableStateOf(settings.getBoolean(SettingsKeys.bindingModeHints, true)) }
    var macroDiagnostics by remember { mutableStateOf(settings.getBoolean(SettingsKeys.macroDiagnostics, true)) }
    var checkOnSave by remember { mutableStateOf(settings.getBoolean(SettingsKeys.checkOnSave, true)) }
    var currentTargetOnly by remember { mutableStateOf(settings.getBoolean(SettingsKeys.currentTargetOnly, true)) }

    var features by remember { mutableStateOf(decodeCustom(settings.getString(SettingsKeys.customInitOptions, "") ?: "")) }
    var rawJson by remember { mutableStateOf(settings.getString(SettingsKeys.rawInitOptions, "") ?: "") }
    var jsonError by remember { mutableStateOf<String?>(null) }

    fun saveFeatures(next: List<CustomFeature>) {
        features = next
        scope.launch { settings.putString(SettingsKeys.customInitOptions, encodeCustom(next)) }
    }

    fun saveRawJson(text: String) {
        rawJson = text
        jsonError = if (text.isBlank()) {
            null
        } else {
            runCatching {
                val parsed = Json.parseToJsonElement(text)
                if (parsed is JsonObject) null else "Must be a JSON object, not a ${parsed::class.simpleName}."
            }.getOrElse { "Invalid JSON: ${it.message}" }
        }
        if (jsonError == null) scope.launch { settings.putString(SettingsKeys.rawInitOptions, text.trim()) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Feature Parameters and Initialize", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
        ) {
            Text(
                "These parameters are sent to rust-analyzer in the initialize request through "
                    + "Klyx's initializationOptions hook. Every feature is enabled by default; "
                    + "changes apply the next time a server starts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
            )

            SettingsCard(
                icon = Warning,
                title = "Diagnostics",
                description = "Report macro-expansion errors and warnings (experimental diagnostics)",
                trailing = {
                    AppSwitch(
                        checked = macroDiagnostics,
                        onCheckedChange = { enabled ->
                            macroDiagnostics = enabled
                            scope.launch { settings.putBoolean(SettingsKeys.macroDiagnostics, enabled) }
                        },
                    )
                },
            )
            SettingsCard(
                icon = Check,
                title = "Check on save",
                description = "Run cargo check when a file is saved so all diagnostics (not just semantic) appear",
                trailing = {
                    AppSwitch(
                        checked = checkOnSave,
                        onCheckedChange = { enabled ->
                            checkOnSave = enabled
                            scope.launch { settings.putBoolean(SettingsKeys.checkOnSave, enabled) }
                        },
                    )
                },
            )
            SettingsCard(
                icon = Layers,
                title = "Current target only",
                description = "Only check the current target instead of all targets, keeping checks lighter",
                trailing = {
                    AppSwitch(
                        checked = currentTargetOnly,
                        onCheckedChange = { enabled ->
                            currentTargetOnly = enabled
                            scope.launch { settings.putBoolean(SettingsKeys.currentTargetOnly, enabled) }
                        },
                    )
                },
            )
            SettingsCard(
                icon = Info,
                title = "Binding-mode hints",
                description = "Show binding-mode inlay hints (mut/ref prefixes) in the editor",
                trailing = {
                    AppSwitch(
                        checked = bindingModeHints,
                        onCheckedChange = { enabled ->
                            bindingModeHints = enabled
                            scope.launch { settings.putBoolean(SettingsKeys.bindingModeHints, enabled) }
                        },
                    )
                },
            )

            SectionLabel("Custom features")
            SettingsCard(
                title = "Extra initialization options",
                description = "Add your own option keys. booleans are sent as true/false, strings as-is; "
                    + "custom features override the toggles above on name collision",
            ) {
                if (features.isEmpty()) {
                    Text(
                        "No custom features yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                features.forEachIndexed { index, feature ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppTextField(
                            value = feature.name,
                            onValueChange = { name -> saveFeatures(features.mapIndexed { i, f -> if (i == index) f.copy(name = name) else f }) },
                            placeholder = "key",
                            modifier = Modifier.weight(1.2f),
                        )
                        SegmentedChoice(
                            options = listOf("bool", "text"),
                            selected = if (feature.isBoolean) "bool" else "text",
                            onSelect = { kind -> saveFeatures(features.mapIndexed { i, f -> if (i == index) f.copy(isBoolean = kind == "bool", value = if (kind == "bool") "true" else f.value) else f }) },
                        )
                        if (feature.isBoolean) {
                            AppSwitch(
                                checked = feature.value != "false",
                                onCheckedChange = { on ->
                                    saveFeatures(features.mapIndexed { i, f -> if (i == index) f.copy(value = on.toString()) else f })
                                },
                            )
                        } else {
                            AppTextField(
                                value = feature.value,
                                onValueChange = { value -> saveFeatures(features.mapIndexed { i, f -> if (i == index) f.copy(value = value) else f }) },
                                placeholder = "value",
                                modifier = Modifier.weight(1.5f),
                            )
                        }
                        IconButton(
                            onClick = { saveFeatures(features.filterIndexed { i, _ -> i != index }) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Delete, contentDescription = "Remove feature", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                OutlinedButton(
                    onClick = { saveFeatures(features + CustomFeature("", true, "true")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add feature")
                }
            }

            SectionLabel("Raw init options JSON")
            SettingsCard(
                title = "Verbatim options object",
                description = "Overrides every option above. Sent to rust-analyzer exactly as written "
                    + "when it is a valid JSON object",
            ) {
                AppTextField(
                    value = rawJson,
                    onValueChange = ::saveRawJson,
                    placeholder = "{\"check\":{\"allTargets\":false}}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    multiline = true,
                    monospace = true,
                )
                if (jsonError != null) {
                    Text(
                        jsonError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

private fun encodeCustom(features: List<CustomFeature>): String {
    val array = JsonArray(
        features.map {
            JsonObject(
                mapOf(
                    "name" to JsonPrimitive(it.name),
                    "type" to JsonPrimitive(if (it.isBoolean) "boolean" else "string"),
                    "value" to JsonPrimitive(it.value),
                )
            )
        }
    )
    return Json.encodeToString(JsonElement.serializer(), array)
}

private fun decodeCustom(raw: String): List<CustomFeature> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        (Json.parseToJsonElement(raw) as? JsonArray)?.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val type = (obj["type"] as? JsonPrimitive)?.contentOrNull ?: "boolean"
            val value = (obj["value"] as? JsonPrimitive)?.contentOrNull ?: ""
            CustomFeature(name = name, isBoolean = type != "string", value = value)
        } ?: emptyList()
    }.getOrElse { emptyList() }
}
