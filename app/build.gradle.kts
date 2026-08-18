plugins {
    alias(libs.plugins.klyx)
}

klyx {
    outputDirectory = rootProject.file("output")
}

dependencies {
    // The host app provides kotlinx-serialization at runtime (same as klyx-api).
    // Bundling it here would load a second copy of JsonElement/JsonObject and
    // crash LSP startup with a ClassCastException once LSPAny (= JsonElement)
    // crosses the plugin/host boundary.
    compileOnly(libs.kotlinx.serialization.json)
}

android {
    namespace = "com.uliteamr.rustupmanager"

    compileSdk {
        version = release(37)
    }

    buildTypes {
        release {
            optimization {
                // The plugin class is loaded reflectively via entryClass in plugin.json,
                // so R8 must not strip or rename it.
                enable = false
            }
        }
    }
}

// Workaround for a klyx-gradle-plugin bug: at configuration time it reads
// build/klyx/generated/plugin.json to resolve the bundle icon, and treats a
// missing file as an absent provider, which crashes task creation with
// "Cannot query the value of this provider because it has no value available".
// Seed the descriptor from the committed root plugin.json so configuration
// succeeds; the klyx compiler plugin overwrites it with the real descriptor
// during compileReleaseKotlin before the bundle is assembled.
val generatedDescriptorDir = layout.buildDirectory.dir("klyx/generated").get().asFile
val generatedDescriptor = generatedDescriptorDir.resolve("plugin.json")
if (!generatedDescriptor.exists()) {
    val rootDescriptor = rootProject.file("plugin.json")
    if (rootDescriptor.isFile) {
        generatedDescriptorDir.mkdirs()
        rootDescriptor.copyTo(generatedDescriptor)
    }
}
