package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoBoundingBox
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoPoint
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapPort
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.StatisticalSubRectangleGeometry
import kotlin.math.cos
import kotlin.math.sqrt

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

    /**
     * `cos(latitude)`, clamped away from zero — shared by [worldToScreen]/[screenToWorld] (equirectangular
     * longitude scaling) and by [MapZoomBounds] (deriving zoom bounds from a cell's real-world lng/lat
     * span), so both use the exact same latitude-scaling maths.
     */
    internal fun cosLatitudeFor(latDeg: Double): Float =
        cos(Math.toRadians(latDeg)).toFloat().coerceAtLeast(MIN_COS_LATITUDE)

    /** Pixels drawn per degree of longitude at `zoom == 1f` — see [MapZoomBounds] for its other use. */
    internal const val PIXELS_PER_DEGREE_AT_ZOOM_1 = BASE_PIXELS_PER_DEGREE

    fun worldToScreen(
        point: GeoPoint,
        camera: CameraState,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
    ): ScreenPoint {
        val scale = BASE_PIXELS_PER_DEGREE * camera.zoom
        val cosLat = cosLatitudeFor(camera.centre.lat)
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
        val cosLat = cosLatitudeFor(camera.centre.lat)
        val dx = screen.x - viewportWidthPx / 2f - camera.panOffsetPx.x
        val dy = screen.y - viewportHeightPx / 2f - camera.panOffsetPx.y
        val lng = camera.centre.lng + (dx / scale / cosLat)
        val lat = camera.centre.lat - (dy / scale)
        return GeoPoint(lat = lat, lng = lng)
    }

    /**
     * Projects a [GeoBoundingBox]'s four corners to screen space and returns their axis-aligned screen
     * bounds — used by [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map.AccessibleStatisticalAreaMap]
     * to position an accessible overlay element over each nearby sub-rectangle cell, using the exact same
     * camera/projection maths [StatisticalAreaMapCanvas] itself draws with — so the overlay never drifts
     * from what is actually drawn, including during pan/zoom.
     */
    fun projectedBounds(
        box: GeoBoundingBox,
        camera: CameraState,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
    ): ScreenRect {
        val corners =
            listOf(
                worldToScreen(GeoPoint(box.minLat, box.minLng), camera, viewportWidthPx, viewportHeightPx),
                worldToScreen(GeoPoint(box.minLat, box.maxLng), camera, viewportWidthPx, viewportHeightPx),
                worldToScreen(GeoPoint(box.maxLat, box.minLng), camera, viewportWidthPx, viewportHeightPx),
                worldToScreen(GeoPoint(box.maxLat, box.maxLng), camera, viewportWidthPx, viewportHeightPx),
            )
        return ScreenRect(
            left = corners.minOf { it.x },
            top = corners.minOf { it.y },
            right = corners.maxOf { it.x },
            bottom = corners.maxOf { it.y },
        )
    }

    /** An axis-aligned screen-space rectangle (pixels), Compose-free for testability — see [projectedBounds]. */
    data class ScreenRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top

        /** True when this rectangle has no overlap at all with a viewport of the given pixel size. */
        fun isOutside(
            viewportWidthPx: Float,
            viewportHeightPx: Float,
        ): Boolean = right < 0f || bottom < 0f || left > viewportWidthPx || top > viewportHeightPx
    }
}

/**
 * Derives the nearby map's zoom bounds/initial zoom from the **actual** nearby cell dimensions and viewport
 * size — replacing fixed, geography-unrelated magic-number constants (the previous flat `MIN_ZOOM = 0.4f`/
 * `MAX_ZOOM = 8f`) with values that genuinely keep the number of visible statistical sub-rectangle boxes
 * within the approved bounds: **no more than 16** visible at maximum zoom-out, **no fewer than 9** visible
 * at maximum zoom-in, with an initial (default) zoom chosen to show roughly a 3-wide arrangement of
 * columns, matching the confirmed reference screenshot's grid density.
 *
 * Derivation: statistical sub-rectangles are (approximately) a uniform lat/lng grid, so the *mean* lng/lat
 * span across the loaded [StatisticalSubRectangleGeometry.boundingBox] set is a faithful stand-in for "one
 * cell"'s real-world size (see [representativeCellSizeDegrees]). [MapProjection.worldToScreen]'s own scale
 * (`PIXELS_PER_DEGREE_AT_ZOOM_1 * zoom`, longitude additionally scaled by `cos(latitude)`) means the number
 * of cells visible across a `viewportWidthPx` × `viewportHeightPx` viewport at a given `zoom` is
 * `(viewportWidthPx * viewportHeightPx) /
 * (cellWidthDeg * cellHeightDeg * cosLat * PIXELS_PER_DEGREE_AT_ZOOM_1^2 * zoom^2)`
 * — i.e. inversely proportional to `zoom^2`. Solving that for `zoom` at the two required cell-count bounds
 * (16 and 9) gives [ZoomBounds.minZoom]/[ZoomBounds.maxZoom] directly; the initial zoom instead solves for
 * `zoom` at "3 cells fit across the viewport width", then is clamped into `[minZoom, maxZoom]` so it can
 * never itself violate the 9–16 bound.
 */
object MapZoomBounds {
    private const val MIN_VISIBLE_CELLS = 9.0
    private const val MAX_VISIBLE_CELLS = 16.0
    private const val INITIAL_CELLS_ACROSS = 3.0

    /** A sensible, geography-unrelated fallback for when there is no nearby geometry to derive bounds from. */
    val fallback = ZoomBounds(minZoom = 0.4f, maxZoom = 8f, initialZoom = 1f)

    data class ZoomBounds(
        val minZoom: Float,
        val maxZoom: Float,
        val initialZoom: Float,
    )

    /**
     * The mean lng/lat span, in degrees, across [geometry]'s bounding boxes — `null` if [geometry] is
     * empty (there is then no real cell size to derive bounds from; see [fallback]).
     */
    fun representativeCellSizeDegrees(geometry: List<StatisticalSubRectangleGeometry>): Pair<Double, Double>? {
        if (geometry.isEmpty()) return null
        val widthDeg = geometry.map { it.boundingBox.maxLng - it.boundingBox.minLng }.average()
        val heightDeg = geometry.map { it.boundingBox.maxLat - it.boundingBox.minLat }.average()
        return widthDeg to heightDeg
    }

    /** See the class doc comment above for the full derivation. */
    fun forCells(
        cellWidthDeg: Double,
        cellHeightDeg: Double,
        centreLatDeg: Double,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
    ): ZoomBounds {
        if (!hasPositiveInputs(cellWidthDeg, cellHeightDeg, viewportWidthPx, viewportHeightPx)) return fallback
        val cosLat = MapProjection.cosLatitudeFor(centreLatDeg)
        val pixelsPerDegree = MapProjection.PIXELS_PER_DEGREE_AT_ZOOM_1.toDouble()
        val cellsVisibleAtZoom1 =
            (viewportWidthPx.toDouble() * viewportHeightPx.toDouble()) /
                (cellWidthDeg * cellHeightDeg * cosLat * pixelsPerDegree * pixelsPerDegree)
        val minZoom = sqrt(cellsVisibleAtZoom1 / MAX_VISIBLE_CELLS).toFloat()
        val maxZoom = sqrt(cellsVisibleAtZoom1 / MIN_VISIBLE_CELLS).toFloat()
        val unclampedInitialZoom =
            (viewportWidthPx / (INITIAL_CELLS_ACROSS * cellWidthDeg * cosLat * pixelsPerDegree)).toFloat()
        return ZoomBounds(
            minZoom = minZoom,
            maxZoom = maxZoom,
            initialZoom = unclampedInitialZoom.coerceIn(minZoom, maxZoom),
        )
    }

    private fun hasPositiveInputs(
        cellWidthDeg: Double,
        cellHeightDeg: Double,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
    ): Boolean {
        val cellSizeIsPositive = cellWidthDeg > 0.0 && cellHeightDeg > 0.0
        val viewportIsPositive = viewportWidthPx > 0f && viewportHeightPx > 0f
        return cellSizeIsPositive && viewportIsPositive
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
