package uk.gov.defra.mmocatchrecord.appconfig.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Non-debug builds: packages the config Fastlane already validated against the schema (-PcrAppConfigFile). */
abstract class PackageReleaseAppConfigTask : DefaultTask() {
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val configFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun packageConfig() {
        val source =
            configFile.orNull?.asFile
                ?: throw GradleException(
                    "Release builds need -PcrAppConfigFile=<validated JSON>; " +
                        "build them with `bundle exec fastlane release` (ADR 0017).",
                )
        val text = source.readText()
        AppConfigJson.requireJsonObject(text)
        val directory = outputDir.get().asFile
        directory.mkdirs()
        directory.resolve(AppConfigJson.ASSET_NAME).writeText(text)
    }
}
