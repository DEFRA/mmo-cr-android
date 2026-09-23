plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
}

repositories {
    google()
    mavenCentral()
}

// The pure (no Gradle-API) parse/reproject/sea-overlap/binary-write geometry logic in
// `shared-geopipeline/` is shared verbatim between this build-time pipeline and the app's runtime
// on-device GeoJSON fallback parser (see ADR 0013 / AssetMapGeometryRepository) — a single source
// directory is added to both builds' Kotlin compilation rather than duplicating the logic, since
// buildSrc is a separate Gradle build and cannot declare a normal `project(...)` dependency on an
// app-module/library subproject of the root build.
sourceSets {
    main {
        kotlin.srcDir("../shared-geopipeline/src/main/kotlin")
    }
}

dependencies {
    compileOnly(gradleApi())
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

