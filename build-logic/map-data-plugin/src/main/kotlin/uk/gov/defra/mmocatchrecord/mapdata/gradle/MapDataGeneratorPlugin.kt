package uk.gov.defra.mmocatchrecord.mapdata.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers one [GenerateMapDataTask] per Android variant and wires its output directory into that
 * variant's generated assets via AGP's variant API — see docs/development/offline-map.md. Applied to `:app`
 * as `id("uk.gov.defra.mmocatchrecord.mapdata.generator")`.
 */
class MapDataGeneratorPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val sourceDataDir =
            "src/main/java/uk/gov/defra/mmocatchrecord/feature/catchrecord/presentation/wizard/map/data"

        val androidComponents = target.extensions.findByType(AndroidComponentsExtension::class.java)
        checkNotNull(androidComponents) {
            "uk.gov.defra.mmocatchrecord.mapdata.generator must be applied after an Android Gradle plugin"
        }

        androidComponents.onVariants { variant ->
            val task =
                target.tasks.register(
                    "generate${variant.name.replaceFirstChar { it.uppercase() }}MapData",
                    GenerateMapDataTask::class.java,
                ) { task ->
                    task.landGeoJson.set(target.layout.projectDirectory.file("$sourceDataDir/map.geojson"))
                    task.subRectanglesGeoJson.set(
                        target.layout.projectDirectory.file("$sourceDataDir/subrectangles.geojson"),
                    )
                    task.portsGeoJson.set(target.layout.projectDirectory.file("$sourceDataDir/ports.geojson"))
                    task.outputDir.set(target.layout.buildDirectory.dir("generated/mapData/${variant.name}"))
                }
            variant.sources.assets?.addGeneratedSourceDirectory(task, GenerateMapDataTask::outputDir)
        }
    }
}
