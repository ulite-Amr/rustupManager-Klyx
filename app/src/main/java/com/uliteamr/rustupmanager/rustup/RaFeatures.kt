package com.uliteamr.rustupmanager.rustup

import com.klyx.api.plugin.PluginSettings
import com.uliteamr.rustupmanager.settings.SettingsKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One toggleable option inside a rust-analyzer feature section. [path] locates it in the
 *  initialization-options JSON object; [defaultValue] is what rust-analyzer assumes when the
 *  path is absent. */
data class RaSubFeature(
    val title: String,
    val description: String,
    val path: List<String>,
    val defaultValue: Boolean = true,
)

/** A group of rust-analyzer options sharing one area. [key] is the top-level JSON key (and the
 *  stable lazy-item key). Sections with a native `enable` flag ([masterEnable] = true) drive
 *  their master switch from it; the others use section presence — off removes the section and
 *  rust-analyzer's defaults apply. */
data class RaFeatureSection(
    val title: String,
    val description: String,
    val key: String,
    val masterEnable: Boolean,
    val subFeatures: List<RaSubFeature>,
)

/** The curated rust-analyzer feature catalog rendered in the LSP screen. It is only a lens
 *  over the stored initialization-options object (SettingsKeys.rawInitOptions), which is sent
 *  to rust-analyzer verbatim. Sub-features never repeat the section's own master toggle. */
val RA_FEATURES: List<RaFeatureSection> = listOf(
    RaFeatureSection(
        title = "Check on save",
        description = "Run cargo check when a file is saved so all diagnostics (not just semantic) appear",
        key = "checkOnSave",
        masterEnable = true,
        subFeatures = listOf(
            RaSubFeature("Check all targets", "Check every target instead of only the current one", listOf("checkOnSave", "allTargets")),
        ),
    ),
    RaFeatureSection(
        title = "Check",
        description = "Cargo check options (legacy — check on save covers most of these)",
        key = "check",
        masterEnable = false,
        subFeatures = listOf(
            RaSubFeature("Check all targets", "Check every target instead of only the current one", listOf("check", "allTargets")),
            RaSubFeature("No default features", "Don't check the crate's default features", listOf("check", "noDefaultFeatures"), defaultValue = false),
        ),
    ),
    RaFeatureSection(
        title = "Diagnostics",
        description = "Errors and warnings shown in the editor",
        key = "diagnostics",
        masterEnable = true,
        subFeatures = listOf(
            RaSubFeature("Macro-expansion diagnostics", "Report errors and warnings from macro expansion (experimental)", listOf("diagnostics", "experimental", "enable"), defaultValue = false),
            RaSubFeature("Style lints", "Report clippy-style lints on top of rustc's", listOf("diagnostics", "styleLints", "enable"), defaultValue = false),
        ),
    ),
    RaFeatureSection(
        title = "Inlay hints",
        description = "Inline annotations in the editor; the switch turns every hint type on or off",
        key = "inlayHints",
        masterEnable = false,
        subFeatures = listOf(
            RaSubFeature("Binding-mode hints", "Show mut/ref prefixes on bindings", listOf("inlayHints", "bindingModeHints", "enable")),
            RaSubFeature("Chaining hints", "Show chained-call hint lines", listOf("inlayHints", "chainingHints", "enable")),
            RaSubFeature("Closing brace hints", "Show the matching expression of a closing brace", listOf("inlayHints", "closingBraceHints", "enable")),
            RaSubFeature("Discriminant hints", "Show the discriminant of enum values", listOf("inlayHints", "discriminantHints", "enable")),
            RaSubFeature("Implicit drops hints", "Show where implicit drops happen", listOf("inlayHints", "implicitDropsHints", "enable")),
            RaSubFeature("Lifetime elision hints", "Show elided lifetimes", listOf("inlayHints", "lifetimeElisionHints", "enable")),
            RaSubFeature("Parameter hints", "Show parameter names at call sites", listOf("inlayHints", "parameterHints", "enable")),
            RaSubFeature("Reborrow hints", "Show when a borrow is reborrowed", listOf("inlayHints", "reborrowHints", "enable")),
            RaSubFeature("Type hints", "Show inferred types of bindings", listOf("inlayHints", "typeHints", "enable")),
        ),
    ),
    RaFeatureSection(
        title = "Completion",
        description = "Code completion suggestions",
        key = "completion",
        masterEnable = false,
        subFeatures = listOf(
            RaSubFeature("Auto-insert self", "Add self/super in method bodies", listOf("completion", "autoself", "enable"), defaultValue = false),
            RaSubFeature("Callable snippets", "Expand function names with (…) call snippets", listOf("completion", "callable", "snippets")),
            RaSubFeature("Postfix snippets", "Postfix snippets such as expr.if", listOf("completion", "postfix", "enable")),
            RaSubFeature("Snippets", "Completion snippets such as match and if let", listOf("completion", "snippets")),
        ),
    ),
    RaFeatureSection(
        title = "Hover",
        description = "Information shown when hovering a symbol",
        key = "hover",
        masterEnable = false,
        subFeatures = listOf(
            RaSubFeature("Documentation", "Show doc comments and signature", listOf("hover", "documentation", "enable")),
            RaSubFeature("Links", "Show links to documentation", listOf("hover", "links", "enable")),
            RaSubFeature("Memory layout", "Show the memory layout of types", listOf("hover", "memoryLayout", "enable")),
        ),
    ),
    RaFeatureSection(
        title = "Cargo",
        description = "Cargo invocation options",
        key = "cargo",
        masterEnable = false,
        subFeatures = listOf(
            RaSubFeature("Auto-reload", "Reload the project when Cargo.toml changes", listOf("cargo", "autoreload")),
            RaSubFeature("No default features", "Don't pass --features default to cargo", listOf("cargo", "noDefaultFeatures"), defaultValue = false),
            RaSubFeature("Unset tests", "Don't pass --tests when checking", listOf("cargo", "unsetTest"), defaultValue = false),
        ),
    ),
    RaFeatureSection(
        title = "Build scripts",
        description = "Run build scripts and load their output",
        key = "cargo.buildScripts",
        masterEnable = true,
        subFeatures = emptyList(),
    ),
    RaFeatureSection(
        title = "Proc macros",
        description = "Support for procedural macros",
        key = "procMacro",
        masterEnable = true,
        subFeatures = listOf(
            RaSubFeature("Attribute macros", "Expand attribute macros", listOf("procMacro", "attributes", "enable"), defaultValue = false),
        ),
    ),
    RaFeatureSection(
        title = "Typing",
        description = "Typing aids while editing",
        key = "typing",
        masterEnable = false,
        subFeatures = listOf(
            RaSubFeature("Auto-closing angle brackets", "Auto-close < and >", listOf("typing", "autoClosingAngleBrackets")),
            RaSubFeature("Auto-closing comments", "Auto-close /* and */", listOf("typing", "autoClosingComments")),
        ),
    ),
    RaFeatureSection(
        title = "Notifications",
        description = "Editor notifications",
        key = "notification",
        masterEnable = false,
        subFeatures = listOf(
            RaSubFeature("Missing Cargo.toml", "Notify when the project has no Cargo.toml", listOf("notification", "cargoTomlNotFound")),
        ),
    ),
)

/** Reads a boolean option from the stored object; the key's absence falls back to the
 *  rust-analyzer default, so switches always reflect what the server actually gets. */
fun boolAt(root: JsonObject, default: Boolean, vararg path: String): Boolean {
    var cur: JsonElement? = root
    for (key in path) {
        cur = (cur as? JsonObject)?.get(key) ?: return default
    }
    return (cur as? JsonPrimitive)?.contentOrNull == "true"
}

/** Sets a nested path, creating intermediate objects on demand. */
fun setPath(root: JsonObject, value: JsonElement, path: List<String>): JsonObject {
    val key = path.first()
    if (path.size == 1) {
        return JsonObject(mapOf(key to value) + root.filterKeys { it != key })
    }
    val child = root[key] as? JsonObject ?: JsonObject(emptyMap())
    return JsonObject(mapOf(key to setPath(child, value, path.drop(1))) + root.filterKeys { it != key })
}

/** Encodes the options object for storage. */
fun encodeOptions(json: JsonObject): String = Json.encodeToString(JsonElement.serializer(), json)

/** Master switch state: the native `enable` flag where the section has one, otherwise derived
 *  from the sub-features — it is on only while every sub-switch is on. */
fun masterChecked(section: RaFeatureSection, root: JsonObject): Boolean =
    if (section.masterEnable) {
        boolAt(root, true, section.key, "enable")
    } else {
        section.subFeatures.all { boolAt(root, it.defaultValue, *it.path.toTypedArray()) }
    }

/** Applies a master-switch flip: native-enable sections write their flag explicitly; the
 *  others are all-or-nothing — off switches every sub-feature off, on switches them all on. */
fun setMaster(section: RaFeatureSection, root: JsonObject, on: Boolean): JsonObject {
    if (section.masterEnable) {
        return setPath(root, JsonPrimitive(on), listOf(section.key, "enable"))
    }
    return section.subFeatures.fold(root) { acc, sub ->
        setPath(acc, JsonPrimitive(on), sub.path)
    }
}

/** Loads the options object for the UI, seeding it once from the legacy per-toggle keys (the
 *  same fallback the provider uses) when nothing is stored yet, so UI and server agree. */
suspend fun loadInitOptions(settings: PluginSettings): JsonObject {
    val raw = settings.getString(SettingsKeys.rawInitOptions, "") ?: ""
    if (raw.isNotBlank()) {
        return (runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject) ?: JsonObject(emptyMap())
    }
    val seed = seedFromLegacyKeys(settings)
    if (seed.isNotEmpty()) {
        settings.putString(SettingsKeys.rawInitOptions, encodeOptions(seed))
    }
    return seed
}

/** Seeds the options object from the pre-1.2 per-toggle keys and the legacy custom-features
 *  list, so existing installs keep their settings without ever opening the editor. */
private fun seedFromLegacyKeys(settings: PluginSettings): JsonObject {
    val currentTargetOnly = settings.getBoolean(SettingsKeys.currentTargetOnly, true)
    val map = LinkedHashMap<String, JsonElement>()
    map["check"] = JsonObject(mapOf("allTargets" to JsonPrimitive(!currentTargetOnly)))
    if (settings.getBoolean(SettingsKeys.macroDiagnostics, true)) {
        map["diagnostics"] = JsonObject(
            mapOf(
                "enable" to JsonPrimitive(true),
                "experimental" to JsonObject(mapOf("enable" to JsonPrimitive(true))),
            ),
        )
    }
    if (settings.getBoolean(SettingsKeys.checkOnSave, true)) {
        map["checkOnSave"] = JsonObject(
            mapOf(
                "enable" to JsonPrimitive(true),
                "allTargets" to JsonPrimitive(!currentTargetOnly),
            ),
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
