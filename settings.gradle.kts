/*
 * Endless Sea — settings.gradle.kts
 *
 * Multi-module Gradle project. Every functional area is an isolated module so the
 * player, the download engine or the extension system can be replaced without
 * rewriting the application (spec §25).
 */

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "endless-sea"

include(":app")
include(":core")
include(":extensions-api")
include(":extensions-loader")
include(":downloader")
include(":player")
include(":data")

// Demo / officially maintained extensions (legal sources only, spec §21)
include(":demo-extensions:archive-org")

project(":demo-extensions:archive-org").projectDir =
    File(rootDir, "demo-extensions/archive-org")
