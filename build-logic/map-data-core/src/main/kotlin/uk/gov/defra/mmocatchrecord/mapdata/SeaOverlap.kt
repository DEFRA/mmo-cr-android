package uk.gov.defra.mmocatchrecord.mapdata

/**
 * Sea-overlap classification for a subrectangle: samples a 5x5 grid of points across the subrectangle's
 * bounding box and tests each against the land geometry. A subrectangle is "sea-overlapping" (and therefore
 * selectable — see docs/development/offline-map.md) unless *every* valid sample point falls inside land.
 * Bbox-vs-bbox pre-filtering against each land polygon's bounds avoids a full point-in-polygon test for
 * land features nowhere near the subrectangle.
 */
object SeaOverlap {
    private const val GRID_SIZE = 5

    fun isSeaOverlapping(bbox: BBox, land: List<MultiPolygon>, landBounds: List<BBox>): Boolean {
        val candidateLand =
            land.filterIndexed { index, _ -> landBounds.getOrNull(index)?.overlaps(bbox) != false }
        if (candidateLand.isEmpty()) return true

        val samples = sampleGrid(bbox)
        return samples.any { point -> candidateLand.none { it.containsPoint(point) } }
    }

    private fun sampleGrid(bbox: BBox): List<GeoPoint> {
        val lonStep = (bbox.maxLon - bbox.minLon) / (GRID_SIZE - 1).coerceAtLeast(1)
        val latStep = (bbox.maxLat - bbox.minLat) / (GRID_SIZE - 1).coerceAtLeast(1)
        val points = mutableListOf<GeoPoint>()
        for (row in 0 until GRID_SIZE) {
            for (col in 0 until GRID_SIZE) {
                val lon = if (bbox.maxLon == bbox.minLon) bbox.minLon else bbox.minLon + col * lonStep
                val lat = if (bbox.maxLat == bbox.minLat) bbox.minLat else bbox.minLat + row * latStep
                points.add(GeoPoint(lon, lat))
            }
        }
        return points
    }
}
