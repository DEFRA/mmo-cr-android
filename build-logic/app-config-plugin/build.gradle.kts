plugins {
    kotlin("jvm") version "2.4.10"
    `java-gradle-plugin`
}

group = "uk.gov.defra.mmocatchrecord.appconfig"
version = "unspecified"

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    compileOnly(libs.gradle)

    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        create("appConfig") {
            id = "uk.gov.defra.mmocatchrecord.appconfig"
            implementationClass = "uk.gov.defra.mmocatchrecord.appconfig.gradle.AppConfigPlugin"
        }
    }
}
