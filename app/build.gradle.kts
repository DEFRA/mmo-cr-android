plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    id("uk.gov.defra.mmocatchrecord.mapdata.generator")
}

android {
    namespace = "uk.gov.defra.mmocatchrecord"
    // Pinned to 37 due to library AAR metadata requirements (androidx.core, lifecycle, navigation).
    // Runtime behavior is unchanged; targetSdk remains pinned at 35.
    compileSdk = 37

    defaultConfig {
        applicationId = "uk.gov.defra.mmocatchrecord"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    sourceSets {
        // Bundles the exported Room schema JSON into the test APK so MigrationTestHelper can read it.
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.testLogging {
                    events("passed", "skipped", "failed")
                }
                // Asking for tests must actually run them: an UP-TO-DATE skip reports green
                // with no executed tests, which hides whether the suite really passed.
                it.outputs.upToDateWhen { false }
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

room {
    schemaDirectory("$projectDir/schemas")
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Offline-first sync (Phase 8): background retry of a queued catch-record submission once
    // connectivity returns — see ADR 0009.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Type-safe Navigation Compose routes (ADR 0007) — @Serializable route classes/objects.
    implementation(libs.kotlinx.serialization.json)

    // Shared offline-map pure parsing/geometry/generator code (build-time preprocessing task + on-device
    // runtime fallback parser) — see docs/adr/0013-offline-fisheries-map-rendering.md.
    implementation("uk.gov.defra.mmocatchrecord.mapdata:map-data-core")

    // Structured, redacted logging (see ADR 0011 / security instructions "no PII/secrets in logs").
    implementation(libs.timber)

    // App-wide language preference persistence (DataStore Preferences — see ADR 0012).
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)

    // Compose UI testing on the JVM via Robolectric: coverage from `testDebugUnitTest` is what
    // Kover reports and SonarCloud imports, whereas `connectedDebugAndroidTest` coverage is not
    // merged in. Compose screens therefore need Robolectric unit tests under `src/test` to count
    // towards the quality gate. The BoM keeps these aligned with the implementation Compose
    // artifacts; `ui-test-manifest` is already supplied via `debugImplementation` below.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Needed for the CatchRecordMigrationTest instrumented migration test (CRAR-152 Phase C).
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
