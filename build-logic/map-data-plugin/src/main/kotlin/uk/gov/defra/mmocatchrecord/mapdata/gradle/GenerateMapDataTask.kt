package uk.gov.defra.mmocatchrecord.mapdata.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import uk.gov.defra.mmocatchrecord.mapdata.MapDataGenerator
import uk.gov.defra.mmocatchrecord.mapdata.MapDataSerializer

/**
 * Reads the three bundled source GeoJSON files, parses/reprojects/validates them, computes
 * bounds/centroids/sea-overlap, and writes a compact generated [uk.gov.defra.mmocatchrecord.mapdata.MapDataset]
 * asset — plus a copy of the three raw source files under a `fallback/` subfolder for the app's on-device
 * runtime fallback parser (see [uk.gov.defra.mmocatchrecord.mapdata.MapDataGenerator] and
 * docs/development/offline-map.md).
 *
 * `@CacheableTask` with `@InputFile`/`@PathSensitive(RELATIVE)` inputs: Gradle reruns this task whenever any
 * of the three source files changes content, whenever this task's own implementation/classpath changes
 * (task class + `map-data-core` are both tracked automatically via the plugin's classpath), or whenever the
 * output directory is missing — and treats it as UP-TO-DATE (or restores it from the build cache)
 * otherwise.
 */
@CacheableTask
abstract class GenerateMapDataTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val landGeoJson: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val subRectanglesGeoJson: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val portsGeoJson: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val landText = landGeoJson.get().asFile.readText()
        val subRectanglesText = subRectanglesGeoJson.get().asFile.readText()
        val portsText = portsGeoJson.get().asFile.readText()

        val result = MapDataGenerator.generate(landText, subRectanglesText, portsText)

        val outputDirectory = outputDir.get().asFile
        val mapDataDir = outputDirectory.resolve("map_data")
        mapDataDir.mkdirs()
        mapDataDir.resolve("dataset.json").writeText(MapDataSerializer.encode(result.dataset))

        val fallbackDir = mapDataDir.resolve("fallback")
        fallbackDir.mkdirs()
        landGeoJson.get().asFile.copyTo(fallbackDir.resolve("map.geojson"), overwrite = true)
        subRectanglesGeoJson.get().asFile.copyTo(fallbackDir.resolve("subrectangles.geojson"), overwrite = true)
        portsGeoJson.get().asFile.copyTo(fallbackDir.resolve("ports.geojson"), overwrite = true)

        val stats = result.stats
        logger.lifecycle(
            "MapData: land={} (skipped {}), subrects={} (skipped {}, reprojected-coords={}, inland={}), " +
                "ports={} (skipped {})",
            stats.landFeatureCount,
            stats.landSkipped,
            stats.subRectangleCount,
            stats.subRectangleSkipped,
            stats.subRectangleReprojectedCoordinateCount,
            stats.inlandSubRectangleCount,
            stats.portCount,
            stats.portSkipped,
        )
    }
}
