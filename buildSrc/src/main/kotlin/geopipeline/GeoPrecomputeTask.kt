package geopipeline

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Build-time precompute Gradle task — turns the 3 unshipped source GeoJSON files in `app/geo-source/`
 * (`map.geojson`, `subrectangles.geojson`, `ports.geojson`) into one compact derived binary asset bundled
 * under `app/src/main/assets/` (registered as an asset source directory in `app/build.gradle.kts`, wired via
 * this task's [outputDir] provider so AGP's merge-assets task automatically depends on it).
 *
 * **Incremental by construction**: [sourceDir] is a Gradle `@InputDirectory` and [outputDir] an
 * `@OutputDirectory` — Gradle's own up-to-date checking (content hash of the 3 source files vs. the
 * previous task outputs) skips re-running this task when the sources are unchanged, satisfying the plan's
 * "make it incremental" requirement without any bespoke tracking code. `@CacheableTask` additionally lets
 * the Gradle build cache skip re-running it across clean builds/CI runners when the inputs are identical.
 *
 * See ADR 0013 for the format/pipeline decision and [GeoPrecomputePipeline] for the actual parse/reproject/
 * sea-overlap/write logic (kept separate and pure so it is unit-testable without a Gradle test fixture).
 */
@CacheableTask
abstract class GeoPrecomputeTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun precompute() {
        val source = sourceDir.get().asFile
        val land = source.resolve("map.geojson")
        val subRectangles = source.resolve("subrectangles.geojson")
        val ports = source.resolve("ports.geojson")
        listOf(land, subRectangles, ports).forEach { file ->
            check(file.exists()) { "Expected source GeoJSON file missing: ${file.absolutePath}" }
        }

        val outputFile = outputDir.get().asFile.resolve(DERIVED_ASSET_FILE_NAME)
        GeoPrecomputePipeline.run(
            landGeoJsonFile = land,
            subRectanglesGeoJsonFile = subRectangles,
            portsGeoJsonFile = ports,
            outputFile = outputFile,
        )
        logger.lifecycle(
            "GeoPrecomputeTask: wrote ${outputFile.name} " +
                "(${outputFile.length() / BYTES_PER_KB} KB) to ${outputFile.parentFile}",
        )

        // Also bundle the raw source GeoJSON as an app asset, under a distinct sub-directory, so
        // AssetMapGeometryRepository can genuinely re-parse it on-device (via the same shared
        // GeoPrecomputePipeline/GeoJsonParser logic above) if the derived binary asset above is ever
        // missing/corrupt — see ADR 0013's documented fallback design. This is a deliberate, flagged
        // exception to "raw GeoJSON is never shipped" for the sake of a genuinely-working fallback path,
        // not an oversight.
        val fallbackDir = outputDir.get().asFile.resolve(FALLBACK_GEOJSON_ASSET_DIR).apply { mkdirs() }
        listOf(land, subRectangles, ports).forEach { file -> file.copyTo(fallbackDir.resolve(file.name), overwrite = true) }
        logger.lifecycle("GeoPrecomputeTask: copied raw fallback GeoJSON into $fallbackDir")
    }

    companion object {
        /** Also referenced by the runtime decoder — see `MapGeometryBinaryDecoder.DERIVED_ASSET_FILE_NAME`. */
        const val DERIVED_ASSET_FILE_NAME = "map_geometry.bin"

        /** Also referenced by `AssetMapGeometryRepository.FALLBACK_GEOJSON_ASSET_DIR` — keep in lock-step. */
        const val FALLBACK_GEOJSON_ASSET_DIR = "geo-source-fallback"
        private const val BYTES_PER_KB = 1024
    }
}
