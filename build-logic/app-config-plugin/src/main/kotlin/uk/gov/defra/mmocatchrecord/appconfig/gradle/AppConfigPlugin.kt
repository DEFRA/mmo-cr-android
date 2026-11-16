package uk.gov.defra.mmocatchrecord.appconfig.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.util.Properties

/**
 * Adds the stage configuration asset to debug variants only (ADR 0024), from local.properties. Release
 * builds stay config-free; the release pipeline injects the asset at packaging (ADR 0023). Applied to `:app`.
 */
class AppConfigPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val androidComponents = target.extensions.findByType(AndroidComponentsExtension::class.java)
        checkNotNull(androidComponents) {
            "uk.gov.defra.mmocatchrecord.appconfig must be applied after an Android Gradle plugin"
        }
        val schema = target.rootProject.layout.projectDirectory.file("config/app-config.schema.json")
        val localValues = readLocalValues(target.rootProject.file("local.properties"))

        androidComponents.onVariants { variant ->
            if (variant.buildType != "debug") return@onVariants
            val taskName = "generate${variant.name.replaceFirstChar { it.uppercase() }}AppConfig"
            val task =
                target.tasks.register(taskName, GenerateDebugAppConfigTask::class.java) { task ->
                    task.schema.set(schema)
                    task.localValues.set(localValues)
                    task.outputDir.set(target.layout.buildDirectory.dir("generated/appConfig/${variant.name}"))
                }
            variant.sources.assets?.addGeneratedSourceDirectory(task, GenerateDebugAppConfigTask::outputDir)
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
