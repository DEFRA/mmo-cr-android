package geopipeline

import java.io.File
import java.io.OutputStream

/**
 * Pure orchestration of the precompute pipeline (parse → reproject → sea-overlap → write), kept separate
 * from [GeoPrecomputeTask] so it is unit-testable without spinning up a Gradle test fixture. See ADR 0013.
 */
object GeoPrecomputePipeline {
    /** Fails the build if fewer sub-rectangles parse than this — guards against a silently-broken parser. */
    const val MIN_EXPECTED_SUB_RECTANGLES = 1000

    fun run(
        landGeoJson: String,
        subRectanglesGeoJson: String,
        portsGeoJson: String,
        output: OutputStream,
    ) {
        val landPolygons = GeoJsonParser.parseLandPolygons(landGeoJson)
        val rawSubRectangles = GeoJsonParser.parseSubRectangles(subRectanglesGeoJson)
        val ports = GeoJsonParser.parsePorts(portsGeoJson)

        check(rawSubRectangles.size >= MIN_EXPECTED_SUB_RECTANGLES) {
            "Only ${rawSubRectangles.size} sub-rectangles parsed from source GeoJSON " +
                "(expected at least $MIN_EXPECTED_SUB_RECTANGLES) — the parser may be broken; failing the " +
                "build rather than shipping an incomplete/wrong derived asset."
        }

        val precomputed =
            rawSubRectangles.map { raw ->
                val boundingBox = GeometryMath.boundingBoxOf(raw.polygons)
                PrecomputedSubRectangle(
                    subCode = raw.subCode,
                    icesName = raw.icesName,
                    polygons = raw.polygons,
                    seaOverlapping = GeometryMath.isSeaOverlapping(boundingBox, landPolygons),
                    bboxCentroid = GeometryMath.boundingBoxCentroid(boundingBox),
                    boundingBox = boundingBox,
                )
            }

        GeoBinaryWriter.write(output, landPolygons, precomputed, ports)
    }

    fun run(
        landGeoJsonFile: File,
        subRectanglesGeoJsonFile: File,
        portsGeoJsonFile: File,
        outputFile: File,
    ) {
        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().use { output ->
            run(
                landGeoJson = landGeoJsonFile.readText(),
                subRectanglesGeoJson = subRectanglesGeoJsonFile.readText(),
                portsGeoJson = portsGeoJsonFile.readText(),
                output = output,
            )
        }
    }
}
