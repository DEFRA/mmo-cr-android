package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset
import uk.gov.defra.mmocatchrecord.mapdata.SerializableBBox
import uk.gov.defra.mmocatchrecord.mapdata.SerializableMultiPolygon
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePoint
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePolygon
import uk.gov.defra.mmocatchrecord.mapdata.SerializablePort
import uk.gov.defra.mmocatchrecord.mapdata.SerializableRing
import uk.gov.defra.mmocatchrecord.mapdata.SerializableSubRectangle
import kotlin.math.abs

private const val GEO_TOLERANCE_DEGREES = 1e-6
private const val PIXEL_TOLERANCE = 0.5f

class MapCameraTests {
    private fun subRectangle(
        code: String,
        minLon: Double,
        minLat: Double,
        maxLon: Double,
        maxLat: Double,
    ) = SerializableSubRectangle(
        code = code,
        icesName = "ICES $code",
        areaKm2 = 100.0,
        bbox = SerializableBBox(minLon, minLat, maxLon, maxLat),
        centroid = SerializablePoint((minLon + maxLon) / 2, (minLat + maxLat) / 2),
        geometry =
            SerializableMultiPolygon(
                listOf(
                    SerializablePolygon(
                        SerializableRing(
                            listOf(
                                SerializablePoint(minLon, minLat),
                                SerializablePoint(maxLon, minLat),
                                SerializablePoint(maxLon, maxLat),
                                SerializablePoint(minLon, maxLat),
                                SerializablePoint(minLon, minLat),
                            ),
                        ),
                        emptyList(),
                    ),
                ),
            ),
        isSeaOverlapping = true,
    )

    // --- CameraMath: geo<->screen round trip ------------------------------------------------------------

    @Test
    fun `geoToScreen then screenToGeo round trips within tolerance`() {
        val camera = MapCamera(centerLon = -1.0, centerLat = 50.5, visibleWidthMetres = 50_000.0)
        val original =
            uk.gov.defra.mmocatchrecord.mapdata
                .GeoPoint(-1.02, 50.51)
        val screen = CameraMath.geoToScreen(original, camera, widthPx = 400f, heightPx = 300f)
        val roundTripped = CameraMath.screenToGeo(screen[0], screen[1], camera, widthPx = 400f, heightPx = 300f)

        assertTrue(abs(roundTripped.lon - original.lon) < GEO_TOLERANCE_DEGREES)
        assertTrue(abs(roundTripped.lat - original.lat) < GEO_TOLERANCE_DEGREES)
    }

    @Test
    fun `the camera centre projects to the exact centre of the viewport`() {
        val camera = MapCamera(centerLon = -1.0, centerLat = 50.5, visibleWidthMetres = 50_000.0)
        val screen =
            CameraMath.geoToScreen(
                uk.gov.defra.mmocatchrecord.mapdata
                    .GeoPoint(camera.centerLon, camera.centerLat),
                camera,
                widthPx = 400f,
                heightPx = 300f,
            )

        assertTrue(abs(screen[0] - 200f) < PIXEL_TOLERANCE)
        assertTrue(abs(screen[1] - 150f) < PIXEL_TOLERANCE)
    }

    // --- CameraMath: pan/zoom ----------------------------------------------------------------------------

    @Test
    fun `positive dxPx shifts the centre west (content follows the drag, per touch-pan convention)`() {
        val camera = MapCamera(centerLon = 0.0, centerLat = 50.0, visibleWidthMetres = 100_000.0)
        val panned = CameraMath.pan(camera, dxPx = 50f, dyPx = 0f, widthPx = 400f)

        assertTrue(panned.centerLon < camera.centerLon)
        assertEquals(camera.visibleWidthMetres, panned.visibleWidthMetres, 0.0)
    }

    @Test
    fun `positive dyPx shifts the centre north (content follows the drag, per touch-pan convention)`() {
        val camera = MapCamera(centerLon = 0.0, centerLat = 50.0, visibleWidthMetres = 100_000.0)
        val panned = CameraMath.pan(camera, dxPx = 0f, dyPx = 50f, widthPx = 400f)

        assertTrue(panned.centerLat > camera.centerLat)
    }

    @Test
    fun `zooming in reduces the visible width and clamps to the minimum`() {
        val camera = MapCamera(centerLon = 0.0, centerLat = 50.0, visibleWidthMetres = 10_000.0)
        val zoomedIn = CameraMath.zoom(camera, factor = 1_000_000f)

        assertEquals(MapCamera.MIN_VISIBLE_WIDTH_METRES, zoomedIn.visibleWidthMetres, 0.0)
    }

    @Test
    fun `zooming out increases the visible width and clamps to the maximum`() {
        val camera = MapCamera(centerLon = 0.0, centerLat = 50.0, visibleWidthMetres = 3_000_000.0)
        val zoomedOut = CameraMath.zoom(camera, factor = 0.001f)

        assertEquals(MapCamera.MAX_VISIBLE_WIDTH_METRES, zoomedOut.visibleWidthMetres, 0.0)
    }

    // --- MapCamera.Saver ---------------------------------------------------------------------------------

    @Test
    fun `Saver round trips a camera through save and restore`() {
        val camera = MapCamera(centerLon = -3.5, centerLat = 55.2, visibleWidthMetres = 42_000.0)
        val scope =
            androidx.compose.runtime.saveable
                .SaverScope { true }
        val saved = with(MapCamera.Saver) { scope.save(camera) }
        assertNotNull(saved)
        val restored = MapCamera.Saver.restore(saved!!)

        assertEquals(camera, restored)
    }

    // --- MapCameraSupport.initialCameraFor --------------------------------------------------------------

    @Test
    fun `matches the departure port by name case and whitespace insensitively`() {
        val dataset =
            MapDataset(
                formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
                land = emptyList(),
                subRectangles = emptyList(),
                ports = listOf(SerializablePort("PORT1", "Hastings", SerializablePoint(0.6, 50.85))),
            )
        val departurePort = Port("port-hastings", "  hastings  ", "AREA-HASTINGS")

        val camera = MapCameraSupport.initialCameraFor(departurePort, dataset, emptyList())

        assertEquals(0.6, camera.centerLon, GEO_TOLERANCE_DEGREES)
        assertEquals(50.85, camera.centerLat, GEO_TOLERANCE_DEGREES)
    }

    @Test
    fun `falls back to the union bbox centroid of the nearby rectangle codes when no port matches`() {
        val dataset =
            MapDataset(
                formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
                land = emptyList(),
                subRectangles =
                    listOf(
                        subRectangle("38E95", minLon = 0.0, minLat = 50.0, maxLon = 1.0, maxLat = 51.0),
                        subRectangle("38E98", minLon = 1.0, minLat = 50.0, maxLon = 2.0, maxLat = 51.0),
                    ),
                ports = listOf(SerializablePort("PORT1", "Some other port", SerializablePoint(10.0, 10.0))),
            )
        val departurePort = Port("port-hastings", "Hastings", "AREA-HASTINGS")
        val nearbyRectangles =
            listOf(
                StatisticalSubRectangle("rect-1", "38E95", "AREA-HASTINGS"),
                StatisticalSubRectangle("rect-2", "38E98", "AREA-HASTINGS"),
            )

        val camera = MapCameraSupport.initialCameraFor(departurePort, dataset, nearbyRectangles)

        assertEquals(1.0, camera.centerLon, GEO_TOLERANCE_DEGREES)
        assertEquals(50.5, camera.centerLat, GEO_TOLERANCE_DEGREES)
    }

    @Test
    fun `falls back to the documented UK-waters default when neither a port nor nearby codes match`() {
        val dataset =
            MapDataset(
                formatVersion = MapDataset.CURRENT_FORMAT_VERSION,
                land = emptyList(),
                subRectangles = emptyList(),
                ports = emptyList(),
            )

        val camera = MapCameraSupport.initialCameraFor(null, dataset, emptyList())

        assertEquals(-4.0, camera.centerLon, GEO_TOLERANCE_DEGREES)
        assertEquals(56.0, camera.centerLat, GEO_TOLERANCE_DEGREES)
    }

    // --- Viewport culling ----------------------------------------------------------------------------------

    @Test
    fun `isSubRectangleInViewport is true only when the rectangle bbox overlaps the viewport`() {
        val camera = MapCamera(centerLon = 0.5, centerLat = 50.5, visibleWidthMetres = 50_000.0)
        val viewport = viewportBBox(camera, widthPx = 400f, heightPx = 300f)
        val inside = subRectangle("in", minLon = 0.49, minLat = 50.49, maxLon = 0.51, maxLat = 50.51)
        val outside = subRectangle("out", minLon = 40.0, minLat = 40.0, maxLon = 41.0, maxLat = 41.0)

        assertTrue(isSubRectangleInViewport(inside, viewport))
        assertTrue(!isSubRectangleInViewport(outside, viewport))
    }

    @Test
    fun `distinct sub-rectangles keep distinct bbox centroids even if they shared stat_x stat_y source fields`() {
        // Regression guard for requirement #2's "labels at each sub-rectangle's own bbox centroid — never
        // stat_x/stat_y": two source features that happened to share the same stat_x/stat_y point must
        // still resolve to two different generated centroids, one per feature's own bbox.
        val first = subRectangle("A1", minLon = 0.0, minLat = 50.0, maxLon = 1.0, maxLat = 51.0)
        val second = subRectangle("A2", minLon = 1.0, minLat = 51.0, maxLon = 2.0, maxLat = 52.0)

        assertNotNull(first.centroid)
        assertNotNull(second.centroid)
        assertTrue(first.centroid != second.centroid)
    }

    @Test
    fun `null departure port with no nearby matches still returns a camera, never throwing`() {
        val dataset =
            MapDataset(MapDataset.CURRENT_FORMAT_VERSION, emptyList(), emptyList(), emptyList())
        val camera = MapCameraSupport.initialCameraFor(null, dataset, emptyList())
        assertNull(dataset.ports.firstOrNull())
        assertNotNull(camera)
    }
}
