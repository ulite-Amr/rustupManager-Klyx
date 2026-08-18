val hasGhCredentials =
    providers.environmentVariable("GITHUB_ACTOR").isPresent &&
        providers.environmentVariable("GITHUB_TOKEN").isPresent

pluginManagement {
    repositories {
        if (hasGhCredentials) {
            maven {
                url = uri("https://maven.pkg.github.com/ulite-Amr/klyx")
                credentials {
                    username = providers.environmentVariable("GITHUB_ACTOR").getOrElse("")
                    password = providers.environmentVariable("GITHUB_TOKEN").getOrElse("")
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
        if (hasGhCredentials) {
            maven {
                url = uri("https://maven.pkg.github.com/ulite-Amr/klyx")
                credentials {
                    username = providers.environmentVariable("GITHUB_ACTOR").getOrElse("")
                    password = providers.environmentVariable("GITHUB_TOKEN").getOrElse("")
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
