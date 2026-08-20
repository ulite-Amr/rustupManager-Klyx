package com.uliteamr.rustupmanager.rustup

import com.klyx.api.plugin.PluginSettings
import com.uliteamr.rustupmanager.settings.SettingsKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Persists the last known [RustupState.Ready] snapshot so the dashboard renders instantly
 * on open while the real rustup check runs in the background.
 *
 * Only successful Ready states are cached: a broken environment must never be masked, and a
 * stale cache is harmless because the background check swaps the page to the setup/error
 * screens as soon as it confirms a problem.
 */
object RustupStateCache {

    suspend fun save(settings: PluginSettings, state: RustupState.Ready) {
        settings.putString(SettingsKeys.stateCache, encode(state))
    }

    fun load(settings: PluginSettings): RustupState.Ready? {
        val raw = settings.getString(SettingsKeys.stateCache, "") ?: return null
        if (raw.isBlank()) return null
        return runCatching { decode(raw) }.getOrNull()
    }

    private fun encode(state: RustupState.Ready): String {
        val toolchains = JsonArray(
            state.toolchains.map {
                JsonObject(
                    buildMap {
                        put("name", JsonPrimitive(it.name))
                        put("default", JsonPrimitive(it.isDefault))
                        if (it.updateAvailable != null) put("update", JsonPrimitive(it.updateAvailable))
                    },
                )
            },
        )
        val versions = JsonArray(
            state.lsp.versions.map {
                JsonObject(
                    mapOf(
                        "tag" to JsonPrimitive(it.tag),
                        "active" to JsonPrimitive(it.isActive),
                    ),
                )
            },
        )
        val lsp = JsonObject(
            buildMap {
                put("viaRustup", JsonPrimitive(state.lsp.installedViaRustup))
                put("rustupActive", JsonPrimitive(state.lsp.rustupActive))
                put("versions", versions)
                if (state.lsp.activeVersion != null) put("active", JsonPrimitive(state.lsp.activeVersion))
            },
        )
        val components = JsonObject(
            mapOf(
                "clippy" to JsonPrimitive(state.components.clippy),
                "rustfmt" to JsonPrimitive(state.components.rustfmt),
                "rustSrc" to JsonPrimitive(state.components.rustSrc),
            ),
        )
        val root = JsonObject(
            buildMap {
                put("toolchains", toolchains)
                put("components", components)
                put("lsp", lsp)
                put("targets", JsonArray(state.activeTargets.map { JsonPrimitive(it) }))
            },
        )
        return Json.encodeToString(JsonElement.serializer(), root)
    }

    private fun decode(raw: String): RustupState.Ready {
        val root = Json.parseToJsonElement(raw) as? JsonObject ?: error("cache is not an object")
        val toolchains = (root["toolchains"] as? JsonArray)?.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            Toolchain(
                name = name,
                isDefault = obj.bool("default"),
                updateAvailable = (obj["update"] as? JsonPrimitive)?.contentOrNull,
            )
        } ?: emptyList()
        val componentsObj = root["components"] as? JsonObject
        val components = ComponentState(
            clippy = componentsObj?.bool("clippy") ?: false,
            rustfmt = componentsObj?.bool("rustfmt") ?: false,
            rustSrc = componentsObj?.bool("rustSrc") ?: false,
        )
        val lspObj = root["lsp"] as? JsonObject
        val versions = (lspObj?.get("versions") as? JsonArray)?.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val tag = (obj["tag"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            ManagedLspVersion(
                tag = tag,
                installed = true,
                isActive = obj.bool("active"),
            )
        } ?: emptyList()
        val lsp = LspState(
            installedViaRustup = lspObj?.bool("viaRustup") ?: false,
            versions = versions,
            activeVersion = (lspObj?.get("active") as? JsonPrimitive)?.contentOrNull,
            rustupActive = lspObj?.bool("rustupActive") ?: false,
        )
        val targets = (root["targets"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()
        return RustupState.Ready(
            toolchains = toolchains,
            components = components,
            lsp = lsp,
            activeTargets = targets,
        )
    }

    private fun JsonObject.bool(key: String): Boolean =
        (get(key) as? JsonPrimitive)?.contentOrNull == "true"
}