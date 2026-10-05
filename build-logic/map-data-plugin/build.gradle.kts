plugins {
    kotlin("jvm") version "2.4.10"
    `java-gradle-plugin`
}

group = "uk.gov.defra.mmocatchrecord.mapdata"
version = "unspecified"

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":map-data-core"))
    implementation(libs.kotlinx.serialization.json)
    compileOnly(libs.gradle)

    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        create("mapDataGenerator") {
            id = "uk.gov.defra.mmocatchrecord.mapdata.generator"
            implementationClass = "uk.gov.defra.mmocatchrecord.mapdata.gradle.MapDataGeneratorPlugin"
        }
    }
}
