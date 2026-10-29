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
import kotlin.math.floor
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

        // Zoom = sub-rectangles visible across: opens at 3x3, zooms out to at most 4x4 and
        // zooms in to 400% of the default (0.75 across).
        private const val DEFAULT_RECTANGLES_ACROSS = 3.0
        private const val MAX_RECTANGLES_ACROSS = 4.0
        private const val MAX_ZOOM_IN_FACTOR = 4.0

        /** Closest permitted zoom-in — 400% of the default view. */
        const val MIN_VISIBLE_WIDTH_METRES =
            DEFAULT_RECTANGLES_ACROSS / MAX_ZOOM_IN_FACTOR * SUB_RECTANGLE_WIDTH_METRES

        /** Default camera view — 3 statistical sub-rectangles across (3x3 with [gridAspectRatioAt]). */
        const val DEFAULT_VISIBLE_WIDTH_METRES = DEFAULT_RECTANGLES_ACROSS * SUB_RECTANGLE_WIDTH_METRES

        /** Furthest permitted zoom-out — 4 statistical sub-rectangles across (4x4). */
        const val MAX_VISIBLE_WIDTH_METRES = MAX_RECTANGLES_ACROSS * SUB_RECTANGLE_WIDTH_METRES

        private const val SUB_RECTANGLE_LAT_DEGREES = 1.0 / 6.0
        private const val HALF = 0.5
        private const val MIN_ASPECT_RATIO = 0.5f
        private const val MAX_ASPECT_RATIO = 1.6f

        /** Canvas height/width so the same number of sub-rectangle rows as columns fit at [lat]. */
        fun gridAspectRatioAt(lat: Double): Float {
            val half = SUB_RECTANGLE_LAT_DEGREES / 2.0
            val top = Projection.wgs84ToMetres(0.0, lat + half)[1]
            val bottom = Projection.wgs84ToMetres(0.0, lat - half)[1]
            return ((top - bottom) / SUB_RECTANGLE_WIDTH_METRES).toFloat().coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
        }

        /** Centre of the sub-rectangle containing ([lon], [lat]), so the default view shows whole cells. */
        fun snapToSubRectangleCentre(
            lon: Double,
            lat: Double,
        ): GeoPoint {
            val snappedLon = (floor(lon / SUB_RECTANGLE_LON_DEGREES) + HALF) * SUB_RECTANGLE_LON_DEGREES
            val south = floor(lat / SUB_RECTANGLE_LAT_DEGREES) * SUB_RECTANGLE_LAT_DEGREES
            val southY = Projection.wgs84ToMetres(0.0, south)[1]
            val northY = Projection.wgs84ToMetres(0.0, south + SUB_RECTANGLE_LAT_DEGREES)[1]
            return GeoPoint(lon = snappedLon, lat = Projection.metresToWgs84(0.0, (southY + northY) / 2.0).lat)
        }

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
            return defaultCameraAt(matchedPort.point.lon, matchedPort.point.lat)
        }

        val nearbyCodes = nearbyRectangles.map { it.code }.toSet()
        val nearbyBounds =
            dataset.subRectangles
                .filter { it.code in nearbyCodes }
                .map { it.bbox.toBBox() }
        val unioned = BBox.union(nearbyBounds)
        if (unioned != null) {
            val centroid = unioned.centroid
            return defaultCameraAt(centroid.lon, centroid.lat)
        }

        return defaultCameraAt(DEFAULT_UK_WATERS_LON, DEFAULT_UK_WATERS_LAT)
    }

    private fun defaultCameraAt(
        lon: Double,
        lat: Double,
    ): MapCamera {
        val centre = MapCamera.snapToSubRectangleCentre(lon, lat)
        return MapCamera(centre.lon, centre.lat, MapCamera.DEFAULT_VISIBLE_WIDTH_METRES)
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
