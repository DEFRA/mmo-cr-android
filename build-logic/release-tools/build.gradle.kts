plugins {
    kotlin("jvm") version "2.4.10"
    application
}

group = "uk.gov.defra.mmocatchrecord.releasetools"
version = "unspecified"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("uk.gov.defra.mmocatchrecord.releasetools.MainKt")
}

dependencies {
    implementation(libs.aapt2.proto)
    implementation(libs.bundletool)
    implementation(libs.protobuf.java)

    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
