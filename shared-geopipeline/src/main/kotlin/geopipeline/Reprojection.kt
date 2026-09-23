package geopipeline

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

/**
 * Coordinate-pair reprojection heuristic (build-time only) — ports the iOS 3-step rule verified against
 * the real `subrectangles.geojson` (whose `crs` header declares EPSG:3857, Web Mercator metres, while
 * `map.geojson`/`ports.geojson` are plain WGS84 degrees):
 *
 * 1. Accept numeric-string coordinate components (some source tooling emits quoted numbers) — handled by
 *    the caller via [NumberLike.asDoubleOrNull] before this function is reached.
 * 2. Try WGS84 degrees first — a raw `(x, y)` pair already within valid `[-180, 180]`/`[-90, 90]` ranges is
 *    trusted as-is (this is deliberately **not** conditional on the declared `crs` header, so it stays
 *    correct even if a future source omits/mis-declares it — see plan R1/Q2).
 * 3. Otherwise, fall back to inverse Web Mercator (EPSG:3857 → 4326), since values wildly out of degree
 *    range (e.g. `-1264519.10`) are Web Mercator metres, not degrees.
 */
object Reprojection {
    private const val MAX_LNG_DEGREES = 180.0
    private const val MAX_LAT_DEGREES = 90.0
    private const val EARTH_RADIUS_METRES = 6378137.0

    /** Reprojects a raw `(x, y)` geometry coordinate pair to WGS84 `(lat, lng)` degrees. */
    fun toWgs84(
        x: Double,
        y: Double,
    ): LatLng =
        if (isPlausibleWgs84(x, y)) {
            LatLng(lat = y, lng = x)
        } else {
            inverseWebMercator(x, y)
        }

    private fun isPlausibleWgs84(
        x: Double,
        y: Double,
    ): Boolean = x in -MAX_LNG_DEGREES..MAX_LNG_DEGREES && y in -MAX_LAT_DEGREES..MAX_LAT_DEGREES

    private fun inverseWebMercator(
        x: Double,
        y: Double,
    ): LatLng {
        val lng = (x / EARTH_RADIUS_METRES) * (180.0 / PI)
        val lat = (2 * atan(exp(y / EARTH_RADIUS_METRES)) - PI / 2) * (180.0 / PI)
        return LatLng(lat = lat, lng = lng)
    }

    /** Forward Web Mercator projection — used only by tests to build known-good round-trip fixtures. */
    internal fun toWebMercator(point: LatLng): Pair<Double, Double> {
        val x = point.lng * (PI / 180.0) * EARTH_RADIUS_METRES
        val latRad = point.lat * (PI / 180.0)
        val y = EARTH_RADIUS_METRES * ln(tan(PI / 4 + latRad / 2))
        return x to y
    }
}
