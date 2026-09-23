package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoBoundingBox
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoPoint
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapPort
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.StatisticalSubRectangleGeometry
import kotlin.math.cos

/**
 * Pure (no Android/Compose dependency) camera/projection/hit-testing helpers for the statistical-sub-area
 * map primitive — see `StatisticalAreaMapCanvas`. Kept separate and pure so the camera-maths, hit-testing
 * and centring logic are fully unit-testable (per copilot-instructions §4.7 / testing.instructions.md
 * "core business logic ≥95%").
 *
 * **Deliberate simplification vs. a full Web-Mercator on-screen projection:** the *source data* reprojection
 * (EPSG:3857 metres -> WGS84 degrees, see `GeoPrecomputeTask`/ADR 0013) must be exact, but the on-screen
 * camera projection below intentionally uses a simple cos(latitude)-scaled equirectangular projection
 * rather than full Web Mercator — this is a custom, non-navigational schematic view (not a real map) over a
 * small local pan region (a few degrees at most), so the extra north/south stretching a full Mercator
 * correction would add is not worth its complexity here. Flagged as a deliberate scope simplification, not
 * an oversight.
 */
object MapProjection {
    /** Screen-space pixel position (Compose `Offset`-shaped, but Compose-free for testability). */
    data class ScreenPoint(
        val x: Float,
        val y: Float,
    )

    /**
     * The camera's world position + zoom. [pixelsPerDegreeAtZoom1] is the base scale (pixels per degree of
     * longitude) at `zoom == 1f`; [zoom] multiplies it. [panOffsetPx] is additional screen-space pan
     * accumulated from drag gestures, on top of the centred [centre].
     */
    data class CameraState(
        val centre: GeoPoint,
        val zoom: Float,
        val panOffsetPx: ScreenPoint = ScreenPoint(0f, 0f),
    )

    private const val BASE_PIXELS_PER_DEGREE = 120f
    private const val MIN_COS_LATITUDE = 0.15f

    fun worldToScreen(
        point: GeoPoint,
        camera: CameraState,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
    ): ScreenPoint {
        val scale = BASE_PIXELS_PER_DEGREE * camera.zoom
        val cosLat = cos(Math.toRadians(camera.centre.lat)).toFloat().coerceAtLeast(MIN_COS_LATITUDE)
        val dx = (point.lng - camera.centre.lng).toFloat() * cosLat * scale
        val dy = (camera.centre.lat - point.lat).toFloat() * scale // screen y grows downward; north is up
        return ScreenPoint(
            x = viewportWidthPx / 2f + dx + camera.panOffsetPx.x,
            y = viewportHeightPx / 2f + dy + camera.panOffsetPx.y,
        )
    }

    fun screenToWorld(
        screen: ScreenPoint,
        camera: CameraState,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
    ): GeoPoint {
        val scale = BASE_PIXELS_PER_DEGREE * camera.zoom
        val cosLat = cos(Math.toRadians(camera.centre.lat)).toFloat().coerceAtLeast(MIN_COS_LATITUDE)
        val dx = screen.x - viewportWidthPx / 2f - camera.panOffsetPx.x
        val dy = screen.y - viewportHeightPx / 2f - camera.panOffsetPx.y
        val lng = camera.centre.lng + (dx / scale / cosLat)
        val lat = camera.centre.lat - (dy / scale)
        return GeoPoint(lat = lat, lng = lng)
    }
}

/** Even-odd point-in-polygon hit-testing, restricted to sea-overlapping sub-rectangles only (see ADR 0013). */
object MapHitTesting {
    /** Returns the tapped sub-rectangle's [StatisticalSubRectangleGeometry.subCode], or `null` if none hit. */
    fun hitTest(
        point: GeoPoint,
        subRectangles: List<StatisticalSubRectangleGeometry>,
    ): String? =
        subRectangles
            .asSequence()
            .filter { it.seaOverlapping }
            .firstOrNull { isInside(point, it) }
            ?.subCode

    private fun isInside(
        point: GeoPoint,
        geometry: StatisticalSubRectangleGeometry,
    ): Boolean = geometry.rings.any { ring -> pointInRing(point, ring) }

    fun pointInRing(
        point: GeoPoint,
        ring: List<GeoPoint>,
    ): Boolean {
        if (ring.size < MIN_RING_VERTICES) return false
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

    private const val MIN_RING_VERTICES = 3
}

/** Resolves the initial map camera centre — "centre on the departure port's area" (approved decision 2). */
object MapCentring {
    /** A sensible mid-UK-waters default when no departure port / no matching port geometry is available. */
    val defaultCentre = GeoPoint(lat = 52.0, lng = 0.0)

    /** Case-insensitive match on [MapPort.name] — the only key the two datasets currently share. */
    fun findPortGeometry(
        departurePortName: String?,
        ports: List<MapPort>,
    ): MapPort? = departurePortName?.let { name -> ports.firstOrNull { it.name.equals(name, ignoreCase = true) } }

    fun initialCentreFor(
        departurePortName: String?,
        ports: List<MapPort>,
    ): GeoPoint = findPortGeometry(departurePortName, ports)?.location ?: defaultCentre
}

/**
 * Viewport culling for map layers that lack a precomputed bounding box of their own (ports — a single
 * point, unlike sub-rectangles/land polygons which already carry a [GeoBoundingBox] used directly by
 * `StatisticalAreaMapCanvas`'s `intersects` checks). Kept as a small pure/testable function so the "off
 * -screen ports are excluded from the per-frame draw list" behaviour can be unit-tested without a Compose
 * test host (see the finding "port viewport culling not actually applied").
 */
object MapViewportCulling {
    /** True when [point] falls within [viewport] (inclusive of its edges). */
    fun isWithin(
        point: GeoPoint,
        viewport: GeoBoundingBox,
    ): Boolean = point.lat in viewport.minLat..viewport.maxLat && point.lng in viewport.minLng..viewport.maxLng

    /** Filters [ports] down to only those whose [MapPort.location] falls within [viewport]. */
    fun visiblePorts(
        ports: List<MapPort>,
        viewport: GeoBoundingBox,
    ): List<MapPort> = ports.filter { isWithin(it.location, viewport) }
}
