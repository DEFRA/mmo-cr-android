package geopipeline

/** Bounding box helpers + point-in-polygon sea-overlap sampling (build-time only) — see plan R2/R3. */
object GeometryMath {
    /** Axis-aligned bounding box of every ring's vertex across [polygons]. */
    fun boundingBoxOf(polygons: List<RawPolygon>): BoundingBox {
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var minLng = Double.POSITIVE_INFINITY
        var maxLng = Double.NEGATIVE_INFINITY
        polygons.forEach { polygon ->
            polygon.rings.forEach { ring ->
                ring.forEach { point ->
                    if (point.lat < minLat) minLat = point.lat
                    if (point.lat > maxLat) maxLat = point.lat
                    if (point.lng < minLng) minLng = point.lng
                    if (point.lng > maxLng) maxLng = point.lng
                }
            }
        }
        return BoundingBox(minLat, maxLat, minLng, maxLng)
    }

    /**
     * The bounding box's own centroid (midpoint), used for sub-rectangle code labels — **never** the
     * source data's shared `stat_x`/`stat_y` parent-rectangle centre, which would stack sibling labels on
     * top of each other (see plan R2).
     */
    fun boundingBoxCentroid(box: BoundingBox): LatLng =
        LatLng(lat = (box.minLat + box.maxLat) / 2.0, lng = (box.minLng + box.maxLng) / 2.0)

    /**
     * Whether [box] overlaps the sea by 5x5 point-in-polygon sampling against [landPolygons]: a
     * sub-rectangle is selectable/labelled only if at least one sample point falls outside every land
     * polygon (i.e. is not wholly land-locked) — see plan step 4/R3.
     */
    fun isSeaOverlapping(
        box: BoundingBox,
        landPolygons: List<RawPolygon>,
    ): Boolean {
        val samplePoints = sampleGrid(box, gridSize = SEA_OVERLAP_SAMPLE_GRID_SIZE)
        return samplePoints.any { point -> landPolygons.none { polygon -> pointInPolygon(point, polygon) } }
    }

    private fun sampleGrid(
        box: BoundingBox,
        gridSize: Int,
    ): List<LatLng> {
        val latStep = (box.maxLat - box.minLat) / (gridSize - 1).coerceAtLeast(1)
        val lngStep = (box.maxLng - box.minLng) / (gridSize - 1).coerceAtLeast(1)
        return buildList {
            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    add(LatLng(lat = box.minLat + latStep * row, lng = box.minLng + lngStep * col))
                }
            }
        }
    }

    /** Even-odd (ray casting) point-in-polygon test, honouring interior-ring holes. */
    fun pointInPolygon(
        point: LatLng,
        polygon: RawPolygon,
    ): Boolean {
        if (polygon.rings.isEmpty()) return false
        val insideExterior = pointInRing(point, polygon.rings.first())
        if (!insideExterior) return false
        val insideAnyHole = polygon.rings.drop(1).any { hole -> pointInRing(point, hole) }
        return !insideAnyHole
    }

    private fun pointInRing(
        point: LatLng,
        ring: List<LatLng>,
    ): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val vi = ring[i]
            val vj = ring[j]
            val intersects =
                (vi.lat > point.lat) != (vj.lat > point.lat) &&
                    point.lng < (vj.lng - vi.lng) * (point.lat - vi.lat) / (vj.lat - vi.lat) + vi.lng
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    private const val SEA_OVERLAP_SAMPLE_GRID_SIZE = 5
}

data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
)
