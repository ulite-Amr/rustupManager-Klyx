pluginManagement {
    repositories {
        // KLYX_GHP_TOKEN is exported by build.yml from the KLYX_GHP_READ_TOKEN
        // repository secret (classic PAT, read:packages on ulite-Amr/klyx).
        // This repo carries the klyx-api snapshot that includes the
        // LanguageServerProvider.initializationOptions() SDK hook; once the
        // feature is merged upstream and the official sonatype snapshot has it,
        // this block can be removed.
        val ghToken = System.getenv("KLYX_GHP_TOKEN")
        if (!ghToken.isNullOrBlank()) {
            maven {
                url = uri("https://maven.pkg.github.com/ulite-Amr/klyx")
                credentials {
                    username = "ulite-Amr"
                    password = ghToken
                }
                content {
                    includeGroup("io.github.klyx-dev")
                }
            }
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven { url = uri("https://central.sonatype.com/repository/maven-snapshots") }
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val ghToken = System.getenv("KLYX_GHP_TOKEN")
        if (!ghToken.isNullOrBlank()) {
            maven {
                url = uri("https://maven.pkg.github.com/ulite-Amr/klyx")
                credentials {
                    username = "ulite-Amr"
                    password = ghToken
                }
                content {
                    includeGroup("io.github.klyx-dev")
                }
            }
        }
        google()
        mavenCentral()
        maven { url = uri("https://central.sonatype.com/repository/maven-snapshots") }
    }
}

rootProject.name = "RustupManager"
include(":app")
