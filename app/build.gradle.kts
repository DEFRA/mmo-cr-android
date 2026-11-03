import java.util.Base64
import java.util.Properties

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
    id("uk.gov.defra.mmocatchrecord.appconfig")
}

val appVersion =
    Properties().apply {
        rootProject.file("version.properties").inputStream().use { stream -> load(stream) }
    }

// Upload-key signing for the release build type (Play App Signing — ci-cd.instructions.md). CI supplies
// UPLOAD_KEYSTORE_BASE64 + passwords as env vars; locally, set the local.properties `upload.*` keys instead.
val uploadKeystoreBase64: String? = System.getenv("UPLOAD_KEYSTORE_BASE64")
val uploadKeystorePassword: String? =
    System.getenv("UPLOAD_KEYSTORE_PASSWORD") ?: localProperties.getProperty("upload.keystore.password")
val uploadKeyAlias: String? = System.getenv("UPLOAD_KEY_ALIAS") ?: localProperties.getProperty("upload.key.alias")
val uploadKeyPassword: String? =
    System.getenv("UPLOAD_KEY_PASSWORD") ?: localProperties.getProperty("upload.key.password")
val uploadKeystoreFile: File? =
    when {
        uploadKeystoreBase64 != null ->
            layout.buildDirectory.file("upload-keystore.jks").get().asFile.apply {
                parentFile.mkdirs()
                writeBytes(Base64.getDecoder().decode(uploadKeystoreBase64))
            }
        localProperties.getProperty("upload.keystore.path") != null ->
            file(localProperties.getProperty("upload.keystore.path")!!)
        else -> null
    }
// PKCS12 (.p12/.pfx) keystores need an explicit storeType; JKS auto-detects.
val uploadKeystoreType: String? =
    uploadKeystoreFile
        ?.extension
        ?.lowercase()
        ?.takeIf { it == "p12" || it == "pfx" }
        ?.let { "PKCS12" }

android {
    namespace = "uk.gov.defra.mmocatchrecord"
    // Pinned to 37 due to library AAR metadata requirements (androidx.core, lifecycle, navigation).
    // targetSdk is 36 per the Google Play target API level policy.
    compileSdk = 37

    defaultConfig {
        applicationId = "mmo.catchrecording.android"
        minSdk = 26
        //noinspection OldTargetApi,EditedTargetSdkVersion
        targetSdk = 36
        versionCode = appVersion.getProperty("VERSION_CODE").toInt()
        versionName = appVersion.getProperty("VERSION_NAME")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Traceability only; the release lane passes the tagged commit, local builds say "local".
        buildConfigField(
            "String",
            "GIT_COMMIT_SHA",
            "\"${providers.gradleProperty("crGitSha").getOrElse("local")}\"",
        )
    }

    // Unsigned if no keystore is configured (e.g. a dev machine without upload-key access); CI and
    // release-signing developers always provide the keys resolved above.
    signingConfigs {
        if (uploadKeystoreFile != null) {
            create("upload") {
                storeFile = uploadKeystoreFile
                storeType = uploadKeystoreType
                storePassword = uploadKeystorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Lets a local debug build sit beside the Play-installed app on one device.
            applicationIdSuffix = ".debug"
        }
        // Built unsigned; the release lane signs the AAB with the upload key (ADR 0018).
        release {
            signingConfigs.findByName("upload")?.let { signingConfig = it }
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
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
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

// JaCoCo engine filters Compose-compiler synthetic branches ($changed/$default/skipping) that tests cannot reach.
kover {
    useJacoco(libs.versions.jacoco.get())
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
    implementation(libs.map.data.core)

    // Structured, redacted logging (see ADR 0011 / security instructions "no PII/secrets in logs").
    implementation(libs.timber)

    // App-wide language preference persistence (DataStore Preferences — see ADR 0012).
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Reference-data API networking foundation (ADR 0015/0016) — see core/network and di/NetworkModule.
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)

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

    // MockWebServer test harness for Retrofit/OkHttp (ADR 0015) — JVM-only, no device/emulator needed.
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.okhttp.mockwebserver3.junit4)

    // Robolectric Compose tests count toward Kover/Sonar coverage; connected tests aren't merged.
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
