package uk.gov.defra.mmocatchrecord.mapdata

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * WGS84 (lon/lat degrees) <-> "Web Mercator" (EPSG:3857, metres) conversions, plus the coordinate
 * validation/reprojection rule used while parsing the bundled GeoJSON — see
 * [MapDataParser] and docs/development/offline-map.md.
 *
 * Web Mercator (rather than a cos(lat)-scaled equirectangular projection) is used because the bundled
 * `subrectangles.geojson` source file is itself authored in EPSG:3857 (large metre-scale coordinates), so
 * reprojecting *to* WGS84 for the shared pure geometry model and then projecting *back* to Web Mercator
 * screen space for drawing keeps a single, well-understood, invertible projection throughout, rather than
 * mixing two different projections for different data sources.
 */
object Projection {
    const val EARTH_RADIUS_METRES = 6378137.0
    private const val MAX_LATITUDE_DEGREES = 85.05112878
    private const val MIN_LONGITUDE_DEGREES = -180.0
    private const val MAX_LONGITUDE_DEGREES = 180.0
    private const val MIN_LATITUDE_DEGREES = -90.0
    private const val MAX_LATITUDE_DEGREES_WGS84 = 90.0
    private const val HALF_TURN_DEGREES = 180.0

    fun isValidWgs84(lon: Double, lat: Double): Boolean =
        lon in MIN_LONGITUDE_DEGREES..MAX_LONGITUDE_DEGREES && lat in MIN_LATITUDE_DEGREES..MAX_LATITUDE_DEGREES_WGS84

    /** Inverse Web Mercator: metres (EPSG:3857) -> WGS84 degrees, clamped to the valid Mercator latitude range. */
    fun metresToWgs84(x: Double, y: Double): GeoPoint {
        val lon = x / EARTH_RADIUS_METRES * HALF_TURN_DEGREES / PI
        val lat = (2.0 * atan(exp(y / EARTH_RADIUS_METRES)) - PI / 2.0) * HALF_TURN_DEGREES / PI
        return GeoPoint(lon = lon, lat = lat.coerceIn(-MAX_LATITUDE_DEGREES, MAX_LATITUDE_DEGREES))
    }

    /** Forward Web Mercator: WGS84 degrees -> metres (EPSG:3857), clamped to the valid Mercator latitude range. */
    fun wgs84ToMetres(lon: Double, lat: Double): DoubleArray {
        val clampedLat = lat.coerceIn(-MAX_LATITUDE_DEGREES, MAX_LATITUDE_DEGREES)
        val x = lon * PI / HALF_TURN_DEGREES * EARTH_RADIUS_METRES
        val y = ln(tan(PI / 4.0 + clampedLat * PI / (2.0 * HALF_TURN_DEGREES))) * EARTH_RADIUS_METRES
        return doubleArrayOf(x, y)
    }

    /**
     * Interprets one raw coordinate pair as described in the offline-map data contract: try WGS84 lon/lat
     * first; if invalid, treat the pair as EPSG:3857 metres and inverse-project; if the reprojected result
     * is still invalid, return `null` (the caller skips the coordinate/feature and records a diagnostic).
     */
    fun resolveCoordinatePair(a: Double, b: Double): CoordinateResolution? =
        when {
            isValidWgs84(a, b) -> CoordinateResolution(GeoPoint(lon = a, lat = b), reprojected = false)
            else -> {
                val candidate = metresToWgs84(a, b)
                if (isValidWgs84(candidate.lon, candidate.lat)) {
                    CoordinateResolution(candidate, reprojected = true)
                } else {
                    null
                }
            }
        }

    data class CoordinateResolution(val point: GeoPoint, val reprojected: Boolean)
}

/** A WGS84 coordinate in degrees. */
data class GeoPoint(val lon: Double, val lat: Double)

/** Axis-aligned bounding box in WGS84 degrees. */
data class BBox(val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double) {
    val centroid: GeoPoint get() = GeoPoint(lon = (minLon + maxLon) / 2.0, lat = (minLat + maxLat) / 2.0)

    fun contains(point: GeoPoint): Boolean =
        point.lon in minLon..maxLon && point.lat in minLat..maxLat

    fun overlaps(other: BBox): Boolean =
        minLon <= other.maxLon && maxLon >= other.minLon && minLat <= other.maxLat && maxLat >= other.minLat

    companion object {
        fun of(points: List<GeoPoint>): BBox {
            var minLon = Double.POSITIVE_INFINITY
            var minLat = Double.POSITIVE_INFINITY
            var maxLon = Double.NEGATIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            for (p in points) {
                minLon = min(minLon, p.lon)
                minLat = min(minLat, p.lat)
                maxLon = max(maxLon, p.lon)
                maxLat = max(maxLat, p.lat)
            }
            return BBox(minLon, minLat, maxLon, maxLat)
        }

        fun union(boxes: List<BBox>): BBox? =
            boxes.reduceOrNull { a, b ->
                BBox(
                    minLon = min(a.minLon, b.minLon),
                    minLat = min(a.minLat, b.minLat),
                    maxLon = max(a.maxLon, b.maxLon),
                    maxLat = max(a.maxLat, b.maxLat),
                )
            }
    }
}

/** A closed linear ring: outer boundary or a hole, WGS84 degrees. First/last point need not be repeated. */
data class Ring(val points: List<GeoPoint>) {
    /** Even-odd ("ray casting") point-in-ring test; ignores whether this ring is an outer boundary or hole. */
    fun containsPoint(point: GeoPoint): Boolean {
        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val pi = points[i]
            val pj = points[j]
            val intersects =
                (pi.lat > point.lat) != (pj.lat > point.lat) &&
                    point.lon <
                    (pj.lon - pi.lon) * (point.lat - pi.lat) / (pj.lat - pi.lat) + pi.lon
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }
}

/** One polygon: an outer [shell] ring plus zero or more excluded [holes]. */
data class GeoPolygon(val shell: Ring, val holes: List<Ring> = emptyList()) {
    fun containsPoint(point: GeoPoint): Boolean =
        shell.containsPoint(point) && holes.none { it.containsPoint(point) }
}

/** One or more [GeoPolygon]s treated as a single feature (a plain Polygon is modelled as a 1-element list). */
data class MultiPolygon(val polygons: List<GeoPolygon>) {
    fun containsPoint(point: GeoPoint): Boolean = polygons.any { it.containsPoint(point) }
}
