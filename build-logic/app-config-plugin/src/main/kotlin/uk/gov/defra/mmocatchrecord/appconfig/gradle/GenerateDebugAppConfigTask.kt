package uk.gov.defra.mmocatchrecord.appconfig.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Debug builds only: writes the config asset from the developer's git-ignored local.properties values. */
abstract class GenerateDebugAppConfigTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schema: RegularFileProperty

    @get:Input
    abstract val localValues: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val result = AppConfigJson.fromLocalValues(schema.get().asFile.readText(), localValues.get())
        result.missingKeys.forEach { key ->
            logger.warn(
                "w: ${AppConfigJson.LOCAL_PROPERTY_PREFIX}$key is not set in local.properties; " +
                    "the app will show a configuration error",
            )
        }
        val directory = outputDir.get().asFile
        directory.mkdirs()
        directory.resolve(AppConfigJson.ASSET_NAME).writeText(result.json)
    }
}
