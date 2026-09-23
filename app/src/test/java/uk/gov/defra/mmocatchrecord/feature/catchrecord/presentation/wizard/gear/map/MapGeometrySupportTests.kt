package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoBoundingBox
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoPoint
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapPort
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.StatisticalSubRectangleGeometry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map.MapProjection.CameraState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map.MapProjection.ScreenPoint
import kotlin.math.abs

private const val DELTA = 0.5

class MapGeometrySupportTests {
    // --- MapProjection: worldToScreen/screenToWorld are exact inverses (round-trip), and the camera is
    // applied to a single logical CameraState value that a caller can construct/compare directly — the
    // Compose-layer's "camera applied once, never auto-refit" behaviour (remember(cameraResetKey) in
    // StatisticalAreaMapCanvas) is therefore fully covered by this pure CameraState round-trip, without
    // needing a Compose test host. ---

    @Test
    fun worldToScreenThenScreenToWorldRoundTripsWithinFloatingPointTolerance() {
        val camera = CameraState(centre = GeoPoint(lat = 50.85, lng = 0.57), zoom = 2f)
        val original = GeoPoint(lat = 50.9, lng = 0.6)

        val screen = MapProjection.worldToScreen(original, camera, viewportWidthPx = 800f, viewportHeightPx = 600f)
        val roundTripped = MapProjection.screenToWorld(screen, camera, viewportWidthPx = 800f, viewportHeightPx = 600f)

        assertTrue(abs(roundTripped.lat - original.lat) < 1e-6)
        assertTrue(abs(roundTripped.lng - original.lng) < 1e-6)
    }

    @Test
    fun cameraCentreProjectsToViewportCentreWithNoPan() {
        val camera = CameraState(centre = GeoPoint(lat = 51.0, lng = -1.0), zoom = 1f)

        val screen =
            MapProjection.worldToScreen(camera.centre, camera, viewportWidthPx = 800f, viewportHeightPx = 600f)

        assertEquals(400f, screen.x, 0.01f)
        assertEquals(300f, screen.y, 0.01f)
    }

    @Test
    fun panOffsetShiftsTheProjectedScreenPointByExactlyThePanAmount() {
        val noPan = CameraState(centre = GeoPoint(lat = 51.0, lng = -1.0), zoom = 1f)
        val panned = noPan.copy(panOffsetPx = ScreenPoint(x = 25f, y = -10f))
        val point = GeoPoint(lat = 51.05, lng = -0.9)

        val screenNoPan = MapProjection.worldToScreen(point, noPan, 800f, 600f)
        val screenPanned = MapProjection.worldToScreen(point, panned, 800f, 600f)

        assertEquals(screenNoPan.x + 25f, screenPanned.x, 0.01f)
        assertEquals(screenNoPan.y - 10f, screenPanned.y, 0.01f)
    }

    // --- MapHitTesting ------------------------------------------------------------------------------------

    private fun squareRectangle(
        code: String,
        seaOverlapping: Boolean,
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
    ) = StatisticalSubRectangleGeometry(
        subCode = code,
        parentIcesName = "ICES-$code",
        rings =
            listOf(
                listOf(
                    GeoPoint(minLat, minLng),
                    GeoPoint(minLat, maxLng),
                    GeoPoint(maxLat, maxLng),
                    GeoPoint(maxLat, minLng),
                ),
            ),
        bboxCentroid = GeoPoint((minLat + maxLat) / 2, (minLng + maxLng) / 2),
        boundingBox = GeoBoundingBox(minLat, maxLat, minLng, maxLng),
        seaOverlapping = seaOverlapping,
    )

    @Test
    fun hitTestReturnsTheSubCodeOfTheSeaOverlappingRectangleContainingThePoint() {
        val rectangles =
            listOf(
                squareRectangle(
                    "27D86",
                    seaOverlapping = true,
                    minLat = 50.0,
                    maxLat = 51.0,
                    minLng = 0.0,
                    maxLng = 1.0,
                ),
                squareRectangle(
                    "27D87",
                    seaOverlapping = true,
                    minLat = 51.0,
                    maxLat = 52.0,
                    minLng = 0.0,
                    maxLng = 1.0,
                ),
            )

        val hit = MapHitTesting.hitTest(GeoPoint(lat = 50.5, lng = 0.5), rectangles)

        assertEquals("27D86", hit)
    }

    @Test
    fun hitTestExcludesLandLockedNonSeaOverlappingRectanglesEvenWhenTheirGeometryContainsThePoint() {
        val landLocked =
            squareRectangle("00A00", seaOverlapping = false, minLat = 50.0, maxLat = 51.0, minLng = 0.0, maxLng = 1.0)

        val hit = MapHitTesting.hitTest(GeoPoint(lat = 50.5, lng = 0.5), listOf(landLocked))

        assertNull(hit)
    }

    @Test
    fun hitTestReturnsNullForATapOutsideEveryRectangle() {
        val rectangles =
            listOf(
                squareRectangle(
                    "27D86",
                    seaOverlapping = true,
                    minLat = 50.0,
                    maxLat = 51.0,
                    minLng = 0.0,
                    maxLng = 1.0,
                ),
            )

        val hit = MapHitTesting.hitTest(GeoPoint(lat = 60.0, lng = 10.0), rectangles)

        assertNull(hit)
    }

    @Test
    fun pointInRingSupportsAConcavePolygonNotJustAxisAlignedBoxes() {
        // A simple "L"-shape footprint.
        val lShape =
            listOf(
                GeoPoint(0.0, 0.0),
                GeoPoint(0.0, 2.0),
                GeoPoint(1.0, 2.0),
                GeoPoint(1.0, 1.0),
                GeoPoint(2.0, 1.0),
                GeoPoint(2.0, 0.0),
            )

        assertTrue(MapHitTesting.pointInRing(GeoPoint(0.5, 0.5), lShape))
        assertTrue(MapHitTesting.pointInRing(GeoPoint(0.2, 1.8), lShape))
        assertTrue(!MapHitTesting.pointInRing(GeoPoint(1.5, 1.5), lShape)) // inside the "notch", outside the L
    }

    // --- MapCentring --------------------------------------------------------------------------------------

    private val samplePorts =
        listOf(
            MapPort("Hastings", GeoPoint(lat = 50.855, lng = 0.573)),
            MapPort("Dover", GeoPoint(lat = 51.128, lng = 1.311)),
        )

    @Test
    fun initialCentreForMatchesDeparturePortCaseInsensitively() {
        val centre = MapCentring.initialCentreFor("HASTINGS", samplePorts)

        assertEquals(50.855, centre.lat, DELTA)
        assertEquals(0.573, centre.lng, DELTA)
    }

    @Test
    fun initialCentreForFallsBackToTheUkWatersDefaultWhenNoPortNameIsGiven() {
        val centre = MapCentring.initialCentreFor(null, samplePorts)

        assertEquals(MapCentring.defaultCentre, centre)
    }

    @Test
    fun initialCentreForFallsBackToTheUkWatersDefaultWhenThePortNameDoesNotMatchAnyMapPort() {
        val centre = MapCentring.initialCentreFor("Not A Real Port", samplePorts)

        assertEquals(MapCentring.defaultCentre, centre)
    }

    @Test
    fun findPortGeometryReturnsNullWhenPortsListIsEmpty() {
        assertNull(MapCentring.findPortGeometry("Hastings", emptyList()))
    }

    // --- MapViewportCulling --------------------------------------------------------------------------------

    @Test
    fun visiblePortsExcludesPortsOutsideTheCurrentViewport() {
        val viewport = GeoBoundingBox(minLat = 50.0, maxLat = 51.0, minLng = 0.0, maxLng = 1.0)
        val insidePort = MapPort("Hastings", GeoPoint(lat = 50.5, lng = 0.5))
        val outsidePort = MapPort("Faraway", GeoPoint(lat = 60.0, lng = 10.0))

        val visible = MapViewportCulling.visiblePorts(listOf(insidePort, outsidePort), viewport)

        assertEquals(listOf(insidePort), visible)
    }

    @Test
    fun visiblePortsIncludesAPortExactlyOnTheViewportEdge() {
        val viewport = GeoBoundingBox(minLat = 50.0, maxLat = 51.0, minLng = 0.0, maxLng = 1.0)
        val edgePort = MapPort("EdgePort", GeoPoint(lat = 50.0, lng = 1.0))

        assertTrue(MapViewportCulling.isWithin(edgePort.location, viewport))
        assertEquals(listOf(edgePort), MapViewportCulling.visiblePorts(listOf(edgePort), viewport))
    }

    @Test
    fun visiblePortsReturnsEmptyWhenNoPortIsWithinTheViewport() {
        val viewport = GeoBoundingBox(minLat = 50.0, maxLat = 51.0, minLng = 0.0, maxLng = 1.0)
        val outsidePort = MapPort("Faraway", GeoPoint(lat = 60.0, lng = 10.0))

        assertTrue(MapViewportCulling.visiblePorts(listOf(outsidePort), viewport).isEmpty())
    }
}
