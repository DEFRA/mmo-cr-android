// Composite ("included") build providing the offline-map data preprocessing Gradle plugin and its shared
// pure-Kotlin parsing/geometry code — see docs/adr/0013-offline-fisheries-map-rendering.md.
//
// Included twice from the root settings.gradle.kts: once under `pluginManagement { includeBuild(...) }` so
// `:app` can `apply(plugin = "uk.gov.defra.mmocatchrecord.mapdata.generator")`, and once as a normal
// `includeBuild(...)` so `:app` can also depend on `map-data-core` as an ordinary project dependency
// (substituted by group/module coordinates) for its runtime fallback parser. This is the documented Gradle
// pattern for an included build that supplies both a plugin and a library from the same build — see
// https://docs.gradle.org/current/userguide/composite_builds.html#included_build_declaring_substitutions.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    // Share the root project's version catalog so versions have a single source of truth.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":map-data-core")
include(":map-data-plugin")
