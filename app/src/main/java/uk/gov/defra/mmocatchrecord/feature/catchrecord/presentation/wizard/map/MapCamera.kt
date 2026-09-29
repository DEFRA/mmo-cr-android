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
        const val MIN_VISIBLE_WIDTH_METRES = 2_000.0
        const val MAX_VISIBLE_WIDTH_METRES = 4_000_000.0

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
    private const val DEFAULT_VISIBLE_WIDTH_METRES = 150_000.0
    private const val NEARBY_VISIBLE_WIDTH_METRES = 90_000.0
    private const val PORT_MATCH_VISIBLE_WIDTH_METRES = 60_000.0

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

        return MapCamera(DEFAULT_UK_WATERS_LON, DEFAULT_UK_WATERS_LAT, DEFAULT_VISIBLE_WIDTH_METRES)
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
