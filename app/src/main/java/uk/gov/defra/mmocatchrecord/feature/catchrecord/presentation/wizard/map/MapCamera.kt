package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import androidx.compose.runtime.saveable.Saver
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.mapdata.BBox
import uk.gov.defra.mmocatchrecord.mapdata.GeoPoint
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset
import uk.gov.defra.mmocatchrecord.mapdata.Projection
import uk.gov.defra.mmocatchrecord.mapdata.SerializableSubRectangle
import uk.gov.defra.mmocatchrecord.mapdata.toBBox
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

/**
 * Camera (centre + visible span) for [MapCanvas] — WGS84 centre in degrees, span expressed as the metre
 * width (Web Mercator, EPSG:3857 — see [Projection]) visible across the viewport, so pan/zoom maths stay in
 * one linear unit. Supplied exactly once by the caller (see [MapSupport.initialCameraFor]); never reset by
 * [MapCanvas] itself on recomposition/selection/state updates — only explicit user pan/zoom gestures update
 * it (see [CameraMath.pan]/[CameraMath.zoom]).
 */
data class MapCamera(
    val centerLon: Double,
    val centerLat: Double,
    val visibleWidthMetres: Double,
) {
    companion object {
        /**
         * The drawn/selectable grid cell on the map is the **statistical sub-rectangle**
         * ([SerializableSubRectangle]/`sub_code`), not the larger 1°lon x 0.5°lat ICES rectangle it's carved
         * from: each ICES rectangle is split into a 3x3 grid of 9 sub-rectangles, so one sub-rectangle is
         * (1/3)° of longitude wide (see docs/development/offline-map.md and `sub_code`/`sub_str` in the bundled
         * `subrectangles.geojson`, e.g. adjacent sub-rectangles' x-coordinates are exactly 37,106.4969 Web
         * Mercator metres apart). Web Mercator's x-axis is a linear function of longitude only (see
         * [Projection.wgs84ToMetres]) — unlike its y-axis, which is not linear in latitude — so this metre
         * width is exact and latitude-independent, making it the only reliable basis for expressing
         * "N sub-rectangles visible" as a [visibleWidthMetres].
         */
        private const val SUB_RECTANGLE_LON_DEGREES = 1.0 / 3.0
        private const val DEGREES_TO_RADIANS = PI / 180.0
        private const val SUB_RECTANGLE_WIDTH_METRES =
            SUB_RECTANGLE_LON_DEGREES * DEGREES_TO_RADIANS * Projection.EARTH_RADIUS_METRES

        // Camera zoom is expressed as the number of statistical sub-rectangles visible across the viewport
        // width: 2 is the closest permitted zoom-in, 3 is the default view (~3-4 rows, given the canvas
        // aspect ratio and sub-rectangles' near-square real-world shape), 4 is the furthest permitted
        // zoom-out — the user cannot zoom out past that.
        private const val MIN_RECTANGLES_ACROSS = 2
        private const val DEFAULT_RECTANGLES_ACROSS = 3
        private const val MAX_RECTANGLES_ACROSS = 4

        /** Closest permitted zoom-in — 2 statistical sub-rectangles visible across the viewport width. */
        const val MIN_VISIBLE_WIDTH_METRES = MIN_RECTANGLES_ACROSS * SUB_RECTANGLE_WIDTH_METRES

        /** Default camera view — about 3 statistical sub-rectangles visible across the viewport width. */
        const val DEFAULT_VISIBLE_WIDTH_METRES = DEFAULT_RECTANGLES_ACROSS * SUB_RECTANGLE_WIDTH_METRES

        /** Furthest permitted zoom-out — 4 statistical sub-rectangles visible across the viewport width; the
         * maximum. */
        const val MAX_VISIBLE_WIDTH_METRES = MAX_RECTANGLES_ACROSS * SUB_RECTANGLE_WIDTH_METRES

        /** Saver for `rememberSaveable` — a flat `DoubleArray` survives config changes/process death. */
        val Saver: Saver<MapCamera, DoubleArray> =
            Saver(
                save = { doubleArrayOf(it.centerLon, it.centerLat, it.visibleWidthMetres) },
                restore = { MapCamera(it[0], it[1], it[2]) },
            )
    }
}

/** Pure screen<->geo projection and pan/zoom maths for [MapCamera] — see [MapCamera]. */
object CameraMath {
    fun geoToScreen(
        point: GeoPoint,
        camera: MapCamera,
        widthPx: Float,
        heightPx: Float,
    ): FloatArray {
        val (cx, cy) = Projection.wgs84ToMetres(camera.centerLon, camera.centerLat)
        val (px, py) = Projection.wgs84ToMetres(point.lon, point.lat)
        val metresPerPixel = camera.visibleWidthMetres / widthPx
        val screenX = (px - cx) / metresPerPixel + widthPx / 2f
        val screenY = heightPx / 2f - (py - cy) / metresPerPixel
        return floatArrayOf(screenX.toFloat(), screenY.toFloat())
    }

    fun screenToGeo(
        x: Float,
        y: Float,
        camera: MapCamera,
        widthPx: Float,
        heightPx: Float,
    ): GeoPoint {
        val (cx, cy) = Projection.wgs84ToMetres(camera.centerLon, camera.centerLat)
        val metresPerPixel = camera.visibleWidthMetres / widthPx
        val metresX = cx + (x - widthPx / 2f) * metresPerPixel
        val metresY = cy - (y - heightPx / 2f) * metresPerPixel
        return Projection.metresToWgs84(metresX, metresY)
    }

    fun pan(
        camera: MapCamera,
        dxPx: Float,
        dyPx: Float,
        widthPx: Float,
    ): MapCamera {
        val metresPerPixel = camera.visibleWidthMetres / widthPx
        val (cx, cy) = Projection.wgs84ToMetres(camera.centerLon, camera.centerLat)
        val newCx = cx - dxPx * metresPerPixel
        val newCy = cy + dyPx * metresPerPixel
        val newCentre = Projection.metresToWgs84(newCx, newCy)
        return camera.copy(centerLon = newCentre.lon, centerLat = newCentre.lat)
    }

    fun zoom(
        camera: MapCamera,
        factor: Float,
    ): MapCamera {
        val newWidth =
            (camera.visibleWidthMetres / factor)
                .coerceIn(MapCamera.MIN_VISIBLE_WIDTH_METRES, MapCamera.MAX_VISIBLE_WIDTH_METRES)
        return camera.copy(visibleWidthMetres = newWidth)
    }

    private operator fun DoubleArray.component1() = this[0]

    private operator fun DoubleArray.component2() = this[1]
}

/** Derives the [MapCamera] a [MapScreen] Grid-mode map should open with — see docs/development/offline-map.md
 * "port-coordinate linkage gap". */
object MapCameraSupport {
    private const val DEFAULT_UK_WATERS_LON = -4.0
    private const val DEFAULT_UK_WATERS_LAT = 56.0

    // A specific port/nearby-rectangle match is the closest known context, so it opens at the closest
    // permitted zoom (see MapCamera.MIN_VISIBLE_WIDTH_METRES) rather than an arbitrary tighter width that
    // would now fall outside the enforced "4 rectangles" zoom-in limit.
    private const val NEARBY_VISIBLE_WIDTH_METRES = MapCamera.MIN_VISIBLE_WIDTH_METRES
    private const val PORT_MATCH_VISIBLE_WIDTH_METRES = MapCamera.MIN_VISIBLE_WIDTH_METRES

    fun initialCameraFor(
        departurePort: Port?,
        dataset: MapDataset,
        nearbyRectangles: List<StatisticalSubRectangle>,
    ): MapCamera {
        val matchedPort =
            departurePort?.let { port ->
                dataset.ports.firstOrNull { normalise(it.name) == normalise(port.name) }
            }
        if (matchedPort != null) {
            return MapCamera(matchedPort.point.lon, matchedPort.point.lat, PORT_MATCH_VISIBLE_WIDTH_METRES)
        }

        val nearbyCodes = nearbyRectangles.map { it.code }.toSet()
        val nearbyBounds =
            dataset.subRectangles
                .filter { it.code in nearbyCodes }
                .map { it.bbox.toBBox() }
        val unioned = BBox.union(nearbyBounds)
        if (unioned != null) {
            val centroid = unioned.centroid
            return MapCamera(centroid.lon, centroid.lat, NEARBY_VISIBLE_WIDTH_METRES)
        }

        return MapCamera(DEFAULT_UK_WATERS_LON, DEFAULT_UK_WATERS_LAT, MapCamera.DEFAULT_VISIBLE_WIDTH_METRES)
    }

    private fun normalise(value: String) = value.trim().lowercase()
}

/** Viewport (in WGS84 degrees) currently visible for [camera] over a `widthPx` x `heightPx` canvas. */
fun viewportBBox(
    camera: MapCamera,
    widthPx: Float,
    heightPx: Float,
): BBox {
    val topLeft = CameraMath.screenToGeo(0f, 0f, camera, widthPx, heightPx)
    val bottomRight = CameraMath.screenToGeo(widthPx, heightPx, camera, widthPx, heightPx)
    return BBox(
        minLon = min(topLeft.lon, bottomRight.lon),
        minLat = min(topLeft.lat, bottomRight.lat),
        maxLon = max(topLeft.lon, bottomRight.lon),
        maxLat = max(topLeft.lat, bottomRight.lat),
    )
}

/** True if [subRectangle]'s precomputed bbox overlaps [viewport] — cheap viewport culling before drawing. */
fun isSubRectangleInViewport(
    subRectangle: SerializableSubRectangle,
    viewport: BBox,
): Boolean = subRectangle.bbox.toBBox().overlaps(viewport)
