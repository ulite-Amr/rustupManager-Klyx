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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Top-level keys owned by the four toggle cards; everything else is a user parameter. */
private val KNOWN_KEYS = setOf("check", "diagnostics", "checkOnSave", "inlayHints")

/**
 * Feature Parameters and Initialize — a live settings.json-style editor.
 *
 * The single source of truth is one JSON object (stored in [SettingsKeys.rawInitOptions]).
 * The four toggle cards read and write their own paths of that object, user parameters are
 * rendered as editable rows straight from the object (booleans as switches, strings and
 * numbers as text fields, nested objects/arrays as recursive rows), and the raw JSON box at
 * the bottom shows the same object live. Every change is persisted immediately and is sent
 * to rust-analyzer verbatim on the next server start.
 */
@Composable
fun FeatureParamsScreen(settings: PluginSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var jsonObject by remember { mutableStateOf(seedInitOptions(settings)) }
    var jsonDraft by remember { mutableStateOf(encode(jsonObject)) }
    var jsonError by remember { mutableStateOf<String?>(null) }

    fun commit(next: JsonObject) {
        jsonObject = next
        jsonError = null
        scope.launch { settings.putString(SettingsKeys.rawInitOptions, encode(next)) }
    }

    fun applyDraft(text: String) {
        jsonDraft = text
        if (text.isBlank()) {
            jsonError = null
            return
        }
        runCatching {
            val parsed = Json.parseToJsonElement(text)
            if (parsed is JsonObject) {
                commit(parsed)
                return
            }
            jsonError = "Must be a JSON object, not ${parsed::class.simpleName}."
        }.onFailure { jsonError = "Invalid JSON: ${it.message}" }
    }

    // The JSON box mirrors the structured view: any switch/parameter change re-serializes it.
    LaunchedEffect(jsonObject) { jsonDraft = encode(jsonObject) }

    // First run with no stored object: persist the seeded one so the provider sends it verbatim.
    LaunchedEffect(Unit) {
        val raw = settings.getString(SettingsKeys.rawInitOptions, "") ?: ""
        if (raw.isBlank()) scope.launch { settings.putString(SettingsKeys.rawInitOptions, encode(jsonObject)) }
    }

    fun boolAt(default: Boolean, vararg path: String): Boolean {
        var cur: JsonElement? = jsonObject
        for (key in path) {
            cur = (cur as? JsonObject)?.get(key) ?: return default
        }
        return (cur as? JsonPrimitive)?.contentOrNull == "true"
    }

    val currentTargetOnly = !boolAt(true, "check", "allTargets")

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Feature Parameters and Initialize", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
        ) {
            Text(
                "This object is sent to rust-analyzer in the initialize request. The switches and "
                    + "parameters below edit it live — the raw JSON at the bottom is the same object, "
                    + "and every change is saved immediately.",
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
                        checked = boolAt(true, "diagnostics", "experimental", "enable"),
                        onCheckedChange = { on ->
                            if (on) {
                                commit(
                                    jsonObject.setPath(
                                        JsonObject(
                                            mapOf(
                                                "enable" to JsonPrimitive(true),
                                                "experimental" to JsonObject(mapOf("enable" to JsonPrimitive(true)))
                                            )
                                        ),
                                        "diagnostics",
                                    )
                                )
                            } else {
                                commit(jsonObject.removeKey("diagnostics"))
                            }
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
                        checked = boolAt(true, "checkOnSave", "enable"),
                        onCheckedChange = { on ->
                            if (on) {
                                commit(
                                    jsonObject.setPath(
                                        JsonObject(
                                            mapOf(
                                                "enable" to JsonPrimitive(true),
                                                "allTargets" to JsonPrimitive(!currentTargetOnly)
                                            )
                                        ),
                                        "checkOnSave",
                                    )
                                )
                            } else {
                                commit(jsonObject.removeKey("checkOnSave"))
                            }
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
                        onCheckedChange = { on -> commit(jsonObject.setPath(JsonPrimitive(!on), "check", "allTargets")) },
                    )
                },
            )
            SettingsCard(
                icon = Info,
                title = "Binding-mode hints",
                description = "Show binding-mode inlay hints (mut/ref prefixes) in the editor",
                trailing = {
                    AppSwitch(
                        checked = boolAt(true, "inlayHints", "bindingModeHints", "enable"),
                        onCheckedChange = { on ->
                            if (on) {
                                commit(
                                    jsonObject.setPath(
                                        JsonObject(mapOf("bindingModeHints" to JsonObject(mapOf("enable" to JsonPrimitive(true))))),
                                        "inlayHints",
                                    )
                                )
                            } else {
                                commit(jsonObject.removeKey("inlayHints"))
                            }
                        },
                    )
                },
            )

            SectionLabel("Custom parameters")
            SettingsCard(
                title = "Your own initialization options",
                description = "Anything outside the four toggles above. Each entry is edited live, "
                    + "deletable, and written straight into the JSON object below",
            ) {
                val customKeys = jsonObject.keys.filter { it !in KNOWN_KEYS }
                if (customKeys.isEmpty()) {
                    Text(
                        "No custom parameters yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                customKeys.forEach { key ->
                    val element = jsonObject[key] ?: return@forEach
                    JsonKeyEditor(
                        key = key,
                        element = element,
                        depth = 0,
                        onUpdate = { new -> commit(jsonObject.setPath(new, key)) },
                        onRemove = { commit(jsonObject.removeKey(key)) },
                    )
                }

                var newKey by rememberSaveable { mutableStateOf("") }
                var newType by rememberSaveable { mutableStateOf("bool") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        placeholder = "new key",
                        modifier = Modifier.weight(1.1f),
                    )
                    SegmentedChoice(
                        options = listOf("bool", "text", "num", "object"),
                        selected = newType,
                        onSelect = { newType = it },
                        modifier = Modifier.weight(1.5f),
                    )
                }
                OutlinedButton(
                    onClick = {
                        val key = newKey.trim()
                        if (key.isNotBlank() && key !in KNOWN_KEYS) {
                            commit(jsonObject.setPath(defaultFor(newType), key))
                            newKey = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add parameter")
                }
            }

            SectionLabel("Raw init options JSON")
            SettingsCard(
                title = "Verbatim options object",
                description = "The live object — edit it directly here. It is sent to rust-analyzer "
                    + "exactly as shown; invalid edits are rejected and the last valid object is kept",
            ) {
                AppTextField(
                    value = jsonDraft,
                    onValueChange = ::applyDraft,
                    placeholder = "{\"check\":{\"allTargets\":false}}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
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

/** One row for a user parameter, rendered from its JSON type. [depth] indents nested objects. */
@Composable
private fun JsonKeyEditor(
    key: String,
    element: JsonElement,
    depth: Int,
    onUpdate: (JsonElement) -> Unit,
    onRemove: () -> Unit,
) {
    val indent = (depth * 14).dp
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(start = indent)
        .padding(vertical = 2.dp)

    when (element) {
        is JsonPrimitive -> {
            val content = element.contentOrNull
            if (content != null && content.toBooleanStrictOrNull() != null && !element.isString) {
                Row(
                    modifier = rowModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(key, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    AppSwitch(
                        checked = content == "true",
                        onCheckedChange = { onUpdate(JsonPrimitive(it)) },
                    )
                    RemoveButton(onRemove)
                }
            } else if (content != null && element.isString) {
                Row(
                    modifier = rowModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(key, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    AppTextField(
                        value = content,
                        onValueChange = { onUpdate(JsonPrimitive(it)) },
                        placeholder = "value",
                        modifier = Modifier.weight(1.4f),
                    )
                    RemoveButton(onRemove)
                }
            } else if (content != null && content.toDoubleOrNull() != null) {
                var draft by remember { mutableStateOf(content) }
                var lastCommitted by remember { mutableStateOf(content) }
                LaunchedEffect(content) {
                    if (content != lastCommitted) draft = content
                }
                Row(
                    modifier = rowModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(key, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    AppTextField(
                        value = draft,
                        onValueChange = { text ->
                            draft = text
                            text.toDoubleOrNull()?.let { d ->
                                val out = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
                                lastCommitted = out
                                onUpdate(JsonPrimitive(d))
                            }
                        },
                        placeholder = "number",
                        modifier = Modifier.weight(1.4f),
                    )
                    RemoveButton(onRemove)
                }
            } else {
                Row(
                    modifier = rowModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(key, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    AppTextField(
                        value = content ?: "null",
                        onValueChange = { onUpdate(JsonPrimitive(it)) },
                        placeholder = "value",
                        modifier = Modifier.weight(1.4f),
                    )
                    RemoveButton(onRemove)
                }
            }
        }
        is JsonObject -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = rowModifier,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$key {${element.size}}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    RemoveButton(onRemove)
                }
                element.forEach { (childKey, child) ->
                    JsonKeyEditor(
                        key = childKey,
                        element = child,
                        depth = depth + 1,
                        onUpdate = { new -> onUpdate(element.setPath(new, childKey)) },
                        onRemove = { onUpdate(element.removeKey(childKey)) },
                    )
                }
            }
        }
        is JsonArray -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = rowModifier,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$key [${element.size}]",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    RemoveButton(onRemove)
                }
                element.forEachIndexed { index, child ->
                    JsonKeyEditor(
                        key = "[$index]",
                        element = child,
                        depth = depth + 1,
                        onUpdate = { new -> onUpdate(element.replaceAt(index, new)) },
                        onRemove = { onUpdate(element.removeAt(index)) },
                    )
                }
            }
        }
        JsonNull -> {
            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(key, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                AppTextField(
                    value = "null",
                    onValueChange = { onUpdate(JsonPrimitive(it)) },
                    placeholder = "null",
                    modifier = Modifier.weight(1.4f),
                )
                RemoveButton(onRemove)
            }
        }
    }
}

@Composable
private fun RemoveButton(onRemove: () -> Unit) {
    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
        Icon(Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp))
    }
}

private fun defaultFor(type: String): JsonElement = when (type) {
    "text" -> JsonPrimitive("")
    "num" -> JsonPrimitive(0)
    "object" -> JsonObject(emptyMap())
    else -> JsonPrimitive(true)
}

private fun encode(json: JsonObject): String = Json.encodeToString(JsonElement.serializer(), json)

/** Reads the stored object, or seeds one from the legacy per-toggle keys and custom features
 *  the first time, so existing installs keep their settings. */
private fun seedInitOptions(settings: PluginSettings): JsonObject {
    val raw = settings.getString(SettingsKeys.rawInitOptions, "") ?: ""
    if (raw.isNotBlank()) {
        return (runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject) ?: JsonObject(emptyMap())
    }

    val currentTargetOnly = settings.getBoolean(SettingsKeys.currentTargetOnly, true)
    val map = LinkedHashMap<String, JsonElement>()
    map["check"] = JsonObject(mapOf("allTargets" to JsonPrimitive(!currentTargetOnly)))
    if (settings.getBoolean(SettingsKeys.macroDiagnostics, true)) {
        map["diagnostics"] = JsonObject(
            mapOf(
                "enable" to JsonPrimitive(true),
                "experimental" to JsonObject(mapOf("enable" to JsonPrimitive(true)))
            )
        )
    }
    if (settings.getBoolean(SettingsKeys.checkOnSave, true)) {
        map["checkOnSave"] = JsonObject(
            mapOf(
                "enable" to JsonPrimitive(true),
                "allTargets" to JsonPrimitive(!currentTargetOnly)
            )
        )
    }
    if (settings.getBoolean(SettingsKeys.bindingModeHints, true)) {
        map["inlayHints"] = JsonObject(mapOf("bindingModeHints" to JsonObject(mapOf("enable" to JsonPrimitive(true)))))
    }

    val oldCustom = settings.getString(SettingsKeys.customInitOptions, "") ?: ""
    if (oldCustom.isNotBlank()) {
        runCatching {
            (Json.parseToJsonElement(oldCustom) as? JsonArray)?.forEach { el ->
                val obj = el as? JsonObject ?: return@forEach
                val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                if (name.isBlank()) return@forEach
                val type = (obj["type"] as? JsonPrimitive)?.contentOrNull ?: "boolean"
                val value = (obj["value"] as? JsonPrimitive)?.contentOrNull ?: ""
                map[name] = if (type != "boolean") JsonPrimitive(value) else JsonPrimitive(value != "false")
            }
        }
    }
    return JsonObject(map)
}

private fun JsonObject.setPath(value: JsonElement, key: String): JsonObject =
    JsonObject(mapOf(key to value) + filterKeys { it != key })

private fun JsonObject.setPath(value: JsonElement, key: String, vararg path: String): JsonObject {
    if (path.isEmpty()) return setPath(value, key)
    val child = get(key) as? JsonObject ?: JsonObject(emptyMap())
    return setPath(child.setPath(value, path[0], *path.drop(1).toTypedArray()), key)
}

private fun JsonObject.removeKey(key: String): JsonObject = JsonObject(filterKeys { it != key })

private fun JsonArray.replaceAt(index: Int, value: JsonElement): JsonArray =
    JsonArray(mapIndexed { i, el -> if (i == index) value else el })

private fun JsonArray.removeAt(index: Int): JsonArray =
    JsonArray(filterIndexed { i, _ -> i != index })