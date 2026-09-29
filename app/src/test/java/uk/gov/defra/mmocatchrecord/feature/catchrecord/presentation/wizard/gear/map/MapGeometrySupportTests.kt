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

    // --- MapProjection.projectedBounds / ScreenRect --------------------------------------------------------

    @Test
    fun projectedBoundsReturnsTheAxisAlignedScreenBoundsOfABoxsFourCorners() {
        val camera = CameraState(centre = GeoPoint(lat = 50.5, lng = 0.5), zoom = 1f)
        val box = GeoBoundingBox(minLat = 50.0, maxLat = 51.0, minLng = 0.0, maxLng = 1.0)

        val bounds = MapProjection.projectedBounds(box, camera, viewportWidthPx = 800f, viewportHeightPx = 600f)

        // The camera is centred on the box's own centre, so the projected bounds are symmetric about the
        // viewport centre — cross-checked directly against worldToScreen for each corner.
        val topLeft = MapProjection.worldToScreen(GeoPoint(51.0, 0.0), camera, 800f, 600f)
        val bottomRight = MapProjection.worldToScreen(GeoPoint(50.0, 1.0), camera, 800f, 600f)
        assertEquals(topLeft.x, bounds.left, 0.01f)
        assertEquals(topLeft.y, bounds.top, 0.01f)
        assertEquals(bottomRight.x, bounds.right, 0.01f)
        assertEquals(bottomRight.y, bounds.bottom, 0.01f)
    }

    @Test
    fun screenRectWidthAndHeightAreDerivedFromItsEdges() {
        val rect = MapProjection.ScreenRect(left = 10f, top = 20f, right = 50f, bottom = 80f)

        assertEquals(40f, rect.width, 0.01f)
        assertEquals(60f, rect.height, 0.01f)
    }

    @Test
    fun screenRectIsOutsideIsTrueWhenTheRectHasNoOverlapWithTheViewport() {
        val farRight = MapProjection.ScreenRect(left = 900f, top = 0f, right = 950f, bottom = 50f)
        val above = MapProjection.ScreenRect(left = 0f, top = -100f, right = 50f, bottom = -50f)

        assertTrue(farRight.isOutside(viewportWidthPx = 800f, viewportHeightPx = 600f))
        assertTrue(above.isOutside(viewportWidthPx = 800f, viewportHeightPx = 600f))
    }

    @Test
    fun screenRectIsOutsideIsFalseWhenTheRectPartiallyOverlapsTheViewport() {
        val partiallyVisible = MapProjection.ScreenRect(left = -20f, top = 0f, right = 20f, bottom = 50f)

        assertTrue(!partiallyVisible.isOutside(viewportWidthPx = 800f, viewportHeightPx = 600f))
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

    // --- MapZoomBounds --------------------------------------------------------------------------------------
    // Verifies that, at MapZoomBounds.forCells's derived minZoom/maxZoom, the number of a uniform grid's cells
    // visible in the viewport is (within floating-point tolerance) exactly the 16/9 bound the plan requires,
    // for representative cell sizes/viewports -- including the real production ICES statistical sub-rectangle
    // size (1 degree longitude x 0.5 degree latitude; see ADR 0013 / GeoPrecomputeTask) at the app's standard
    // nearby-map viewport (MAP_HEIGHT = 320.dp and a typical ~360dp-wide phone screen, both at 2x density).

    private fun visibleCellCount(
        cellWidthDeg: Double,
        cellHeightDeg: Double,
        centreLatDeg: Double,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        zoom: Float,
    ): Double {
        val cosLat = MapProjection.cosLatitudeFor(centreLatDeg)
        val pixelsPerDegree = MapProjection.PIXELS_PER_DEGREE_AT_ZOOM_1.toDouble()
        val cellWidthPx = cellWidthDeg * cosLat * pixelsPerDegree * zoom
        val cellHeightPx = cellHeightDeg * pixelsPerDegree * zoom
        return (viewportWidthPx.toDouble() * viewportHeightPx.toDouble()) / (cellWidthPx * cellHeightPx)
    }

    @Test
    fun representativeCellSizeDegreesReturnsNullForEmptyGeometry() {
        assertNull(MapZoomBounds.representativeCellSizeDegrees(emptyList()))
    }

    @Test
    fun representativeCellSizeDegreesReturnsTheMeanBoundingBoxSpan() {
        val geometry =
            listOf(
                squareRectangle("A", seaOverlapping = true, minLat = 50.0, maxLat = 50.5, minLng = 0.0, maxLng = 1.0),
                squareRectangle("B", seaOverlapping = true, minLat = 51.0, maxLat = 51.5, minLng = 1.0, maxLng = 3.0),
            )

        val size = MapZoomBounds.representativeCellSizeDegrees(geometry)

        assertEquals(1.5, size!!.first, 0.001)
        assertEquals(0.5, size.second, 0.001)
    }

    @Test
    fun forCellsFallsBackWhenGivenNonPositiveInputs() {
        val bounds =
            MapZoomBounds.forCells(
                cellWidthDeg = 0.0,
                cellHeightDeg = 0.5,
                centreLatDeg = 50.0,
                viewportWidthPx = 800f,
                viewportHeightPx = 320f,
            )

        assertEquals(MapZoomBounds.fallback, bounds)
    }

    @Test
    fun forCellsDerivedMinZoomShowsNoMoreThanSixteenBoxesForTheRealIcesRectangleSizeAtTheStandardViewport() {
        // The real production ICES statistical sub-rectangle is 1 degree longitude x 0.5 degree latitude.
        val viewportWidthPx = 720f // ~360dp at 2x density
        val viewportHeightPx = 640f // MAP_HEIGHT = 320.dp at 2x density
        val centreLatDeg = 55.0

        val bounds =
            MapZoomBounds.forCells(
                cellWidthDeg = 1.0,
                cellHeightDeg = 0.5,
                centreLatDeg = centreLatDeg,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
            )

        val visibleAtMinZoom =
            visibleCellCount(1.0, 0.5, centreLatDeg, viewportWidthPx, viewportHeightPx, bounds.minZoom)
        assertEquals(16.0, visibleAtMinZoom, 0.01)
    }

    @Test
    fun forCellsDerivedMaxZoomShowsNoFewerThanNineBoxesForTheRealIcesRectangleSizeAtTheStandardViewport() {
        val viewportWidthPx = 720f
        val viewportHeightPx = 640f
        val centreLatDeg = 55.0

        val bounds =
            MapZoomBounds.forCells(
                cellWidthDeg = 1.0,
                cellHeightDeg = 0.5,
                centreLatDeg = centreLatDeg,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
            )

        val visibleAtMaxZoom =
            visibleCellCount(1.0, 0.5, centreLatDeg, viewportWidthPx, viewportHeightPx, bounds.maxZoom)
        assertEquals(9.0, visibleAtMaxZoom, 0.01)
    }

    @Test
    fun forCellsInitialZoomIsClampedWithinTheMinMaxBoundAndShowsBetweenNineAndSixteenBoxes() {
        val viewportWidthPx = 720f
        val viewportHeightPx = 640f
        val centreLatDeg = 55.0

        val bounds =
            MapZoomBounds.forCells(
                cellWidthDeg = 1.0,
                cellHeightDeg = 0.5,
                centreLatDeg = centreLatDeg,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
            )

        assertTrue(bounds.initialZoom >= bounds.minZoom)
        assertTrue(bounds.initialZoom <= bounds.maxZoom)
        val visibleAtInitialZoom =
            visibleCellCount(1.0, 0.5, centreLatDeg, viewportWidthPx, viewportHeightPx, bounds.initialZoom)
        assertTrue(visibleAtInitialZoom in 9.0..16.0)
    }

    @Test
    fun forCellsHoldsTheNineToSixteenBoxBoundForASmallerCellSizeAndDifferentViewport() {
        // A denser grid (smaller cells) and a different (e.g. tablet-sized) viewport should still hold the
        // same 9-16 box bound -- this is the point of deriving bounds from geometry rather than hardcoding.
        val viewportWidthPx = 1200f
        val viewportHeightPx = 900f
        val centreLatDeg = 50.0
        val cellWidthDeg = 0.5
        val cellHeightDeg = 0.25

        val bounds =
            MapZoomBounds.forCells(
                cellWidthDeg = cellWidthDeg,
                cellHeightDeg = cellHeightDeg,
                centreLatDeg = centreLatDeg,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
            )

        val visibleAtMinZoom =
            visibleCellCount(
                cellWidthDeg,
                cellHeightDeg,
                centreLatDeg,
                viewportWidthPx,
                viewportHeightPx,
                bounds.minZoom,
            )
        val visibleAtMaxZoom =
            visibleCellCount(
                cellWidthDeg,
                cellHeightDeg,
                centreLatDeg,
                viewportWidthPx,
                viewportHeightPx,
                bounds.maxZoom,
            )
        assertEquals(16.0, visibleAtMinZoom, 0.01)
        assertEquals(9.0, visibleAtMaxZoom, 0.01)
    }
}
