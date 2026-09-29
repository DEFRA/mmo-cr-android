pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MMO Catch Record"
// Included a second time (not just under pluginManagement above) so `:app` can also depend on
// `map-data-core` as an ordinary project dependency for its runtime fallback parser — see
// build-logic/settings.gradle.kts and docs/adr/0013-offline-fisheries-map-rendering.md.
includeBuild("build-logic")
include(":app")
 