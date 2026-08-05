plugins {
    alias(libs.plugins.klyx)
}

klyx {
    outputDirectory = rootProject.file("output")
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
