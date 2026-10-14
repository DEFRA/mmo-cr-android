package uk.gov.defra.mmocatchrecord.appconfig.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.util.Properties

/**
 * Adds the stage configuration asset to every variant (ADR 0017): debug builds from local.properties,
 * release builds from the Fastlane-validated GitHub Environment values. Applied to `:app`.
 */
class AppConfigPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val androidComponents = target.extensions.findByType(AndroidComponentsExtension::class.java)
        checkNotNull(androidComponents) {
            "uk.gov.defra.mmocatchrecord.appconfig must be applied after an Android Gradle plugin"
        }
        val schema = target.rootProject.layout.projectDirectory.file("config/app-config.schema.json")
        val localValues = readLocalValues(target.rootProject.file("local.properties"))
        val rootDir = target.rootDir
        val releaseConfig =
            target.providers.gradleProperty("crAppConfigFile").map { path ->
                File(path).takeIf { it.isAbsolute } ?: rootDir.resolve(path)
            }

        androidComponents.onVariants { variant ->
            val taskName = "generate${variant.name.replaceFirstChar { it.uppercase() }}AppConfig"
            val outputDir = target.layout.buildDirectory.dir("generated/appConfig/${variant.name}")
            if (variant.buildType == "debug") {
                val task =
                    target.tasks.register(taskName, GenerateDebugAppConfigTask::class.java) { task ->
                        task.schema.set(schema)
                        task.localValues.set(localValues)
                        task.outputDir.set(outputDir)
                    }
                variant.sources.assets?.addGeneratedSourceDirectory(task, GenerateDebugAppConfigTask::outputDir)
            } else {
                val task =
                    target.tasks.register(taskName, PackageReleaseAppConfigTask::class.java) { task ->
                        task.configFile.fileProvider(releaseConfig)
                        task.outputDir.set(outputDir)
                    }
                variant.sources.assets?.addGeneratedSourceDirectory(task, PackageReleaseAppConfigTask::outputDir)
            }
        }
    }

    private fun readLocalValues(file: File): Map<String, String> {
        if (!file.exists()) return emptyMap()
        val properties = Properties().apply { file.inputStream().use { load(it) } }
        return properties.stringPropertyNames()
            .filter { it.startsWith(AppConfigJson.LOCAL_PROPERTY_PREFIX) }
            .associate { it.removePrefix(AppConfigJson.LOCAL_PROPERTY_PREFIX) to properties.getProperty(it) }
    }
}
