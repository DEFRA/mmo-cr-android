plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
}

group = "uk.gov.defra.mmocatchrecord.mapdata"
version = "unspecified"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        // Shipped inside :app, so emit Java 21 bytecode Studio Preview can read — see ADR 0026.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjdk-release=21")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
