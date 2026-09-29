package uk.gov.defra.mmocatchrecord.mapdata

/**
 * Orchestrates the full offline-map preprocessing pipeline — parse, reproject/validate, compute
 * bounds/centroids, compute sea-overlap — shared verbatim by the Gradle build task and the app's on-device
 * runtime fallback (see docs/development/offline-map.md). Pure Kotlin; no Gradle/Android dependency.
 */
object MapDataGenerator {
    data class GenerationResult(val dataset: MapDataset, val stats: GenerationStats)

    data class GenerationStats(
        val landFeatureCount: Int,
        val landSkipped: Int,
        val subRectangleCount: Int,
        val subRectangleSkipped: Int,
        val subRectangleReprojectedCoordinateCount: Int,
        val inlandSubRectangleCount: Int,
        val portCount: Int,
        val portSkipped: Int,
    )

    fun generate(landGeoJson: String, subRectanglesGeoJson: String, portsGeoJson: String): GenerationResult {
        val landResult = MapDataParser.parseLandFeatures(landGeoJson)
        val subRectResult = MapDataParser.parseSubRectangleFeatures(subRectanglesGeoJson)
        val portResult = MapDataParser.parsePortFeatures(portsGeoJson)

        val landBounds = landResult.values.map { multiPolygon -> BBox.of(multiPolygon.polygons.flatMap { it.shell.points }) }

        var inlandCount = 0
        val subRectangles =
            subRectResult.values.map { raw ->
                val points = raw.geometry.polygons.flatMap { it.shell.points }
                val bbox = BBox.of(points)
                val isSeaOverlapping = SeaOverlap.isSeaOverlapping(bbox, landResult.values, landBounds)
                if (!isSeaOverlapping) inlandCount++
                SerializableSubRectangle(
                    code = raw.code,
                    icesName = raw.icesName,
                    areaKm2 = raw.areaKm2,
                    bbox = bbox.toSerializable(),
                    centroid = bbox.centroid.toSerializable(),
                    geometry = raw.geometry.toSerializable(),
                    isSeaOverlapping = isSeaOverlapping,
                )
            }

        val ports =
            portResult.values.map { raw ->
                SerializablePort(portCode = raw.portCode, name = raw.name, point = raw.point.toSerializable())
            }

        val dataset =
            MapDataset(
                formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
                land = landResult.values.map { it.toSerializable() },
                subRectangles = subRectangles,
                ports = ports,
            )

        val stats =
            GenerationStats(
                landFeatureCount = landResult.values.size,
                landSkipped = landResult.diagnostics.skippedFeatures.size,
                subRectangleCount = subRectangles.size,
                subRectangleSkipped = subRectResult.diagnostics.skippedFeatures.size,
                subRectangleReprojectedCoordinateCount = subRectResult.diagnostics.reprojectedCoordinateCount,
                inlandSubRectangleCount = inlandCount,
                portCount = ports.size,
                portSkipped = portResult.diagnostics.skippedFeatures.size,
            )

        return GenerationResult(dataset, stats)
    }
}
